package net.icantpy.cosmetics.items

import com.google.gson.Gson
import net.icantpy.Icantpy
import net.icantpy.cosmetics.morph.PlayerDisguise
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object CustomCosmeticsShare {
    private val gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val remote = ConcurrentHashMap<String, SharedCosmetics>()
    private val generation = AtomicLong(0)
    private val busy = AtomicBoolean(false)
    private var executor = newExecutor()
    private var lastFingerprint: String? = null
    private var lastPublishAt = 0L
    private var lastPollAt = 0L
    private var lastPolled: Set<String> = emptySet()

    private val publishSlots = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
    )

    fun appearance(playerUuid: String, skyblockId: String?, displayName: String? = null): CustomRenameItemData? {
        val key = CustomCosmeticsCodec.normalizeUuid(playerUuid) ?: return null
        return remote[key]?.appearanceFor(skyblockId, displayName)
    }

    fun morphEntityId(playerUuid: java.util.UUID): String? {
        val key = CustomCosmeticsCodec.normalizeUuid(playerUuid.toString()) ?: return null
        return remote[key]?.morphEntityId
    }

    fun tick() {
        CustomRename.refreshLocalIndex()
        val now = System.currentTimeMillis()
        localSnapshot()?.let { snapshot ->
            val toSend = snapshotForPublish(snapshot, CustomRename.shareWithOthers())
            if (toSend.fingerprint() != lastFingerprint && now - lastPublishAt >= PUBLISH_INTERVAL_MS) {
                publish(toSend)
            }
        }
        val nearby = nearbyPlayerUuids()
        if (nearby.isNotEmpty() && (nearby != lastPolled || now - lastPollAt >= POLL_INTERVAL_MS)) {
            poll(nearby)
        }
    }

    fun onLocalAppearanceChanged() {
        lastFingerprint = null
        lastPublishAt = 0L
    }

    fun onShareToggled(enabled: Boolean) {
        lastFingerprint = null
        val snapshot = localSnapshot() ?: return
        publish(snapshotForPublish(snapshot, enabled))
    }

    fun onUnload() {
        generation.incrementAndGet()
        executor.shutdownNow()
        executor = newExecutor()
        remote.clear()
        lastFingerprint = null
        lastPolled = emptySet()
        AppearanceOwnerScope.clear()
    }

    internal fun snapshotForPublish(snapshot: SharedCosmetics, shareCosmetics: Boolean): SharedCosmetics =
        if (shareCosmetics) snapshot else snapshot.copy(slots = emptyMap())

    private fun localSnapshot(): SharedCosmetics? {
        val player = Minecraft.getInstance().player ?: return null
        val slots = linkedMapOf<String, SharedCosmeticSlot>()
        for (slot in publishSlots) {
            val stack = player.getItemBySlot(slot)
            val skyblockId = HypixelItemData.skyblockId(stack)
            val displayName = HypixelItemData.matchName(stack)
            if (skyblockId == null && displayName == null) continue
            val data = CustomRename.localAppearance(stack) ?: continue
            if (!data.hasOverride()) continue
            slots[slot.getName()] = SharedCosmeticSlot(skyblockId, data, displayName)
        }
        return SharedCosmetics(
            playerUuid = player.uuid.toString(),
            playerName = player.gameProfile.name,
            updatedAt = System.currentTimeMillis(),
            slots = slots,
            morphEntityId = PlayerDisguise.entityId(),
        )
    }

    private fun nearbyPlayerUuids(): Set<String> {
        val mc = Minecraft.getInstance()
        val self = mc.player?.uuid ?: return emptySet()
        val ids = linkedSetOf<String>()
        mc.level?.players()?.forEach { player ->
            if (player.uuid != self) CustomCosmeticsCodec.normalizeUuid(player.uuid.toString())?.let(ids::add)
        }
        mc.connection?.onlinePlayerIds?.forEach { uuid ->
            if (uuid != self) CustomCosmeticsCodec.normalizeUuid(uuid.toString())?.let(ids::add)
        }
        return ids.take(24).toSet()
    }

    private fun publish(payload: SharedCosmetics) {
        val token = generation.get()
        val body = gson.toJson(CustomCosmeticsCodec.toJson(payload))
        lastFingerprint = payload.fingerprint()
        lastPublishAt = System.currentTimeMillis()
        submit(token) {
            for (base in bases()) {
                val request = HttpRequest.newBuilder(URI.create("$base/cosmetics/${payload.playerUuid}"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                try {
                    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() in 200..299) return@submit
                } catch (exception: Exception) {
                    Icantpy.LOGGER.debug("Cosmetics publish to {} failed", base, exception)
                }
            }
        }
    }

    private fun poll(ids: Set<String>) {
        val token = generation.get()
        lastPolled = ids
        lastPollAt = System.currentTimeMillis()
        val query = ids.joinToString(",")
        submit(token) {
            for (base in bases()) {
                val request = HttpRequest.newBuilder(URI.create("$base/cosmetics?ids=$query"))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build()
                try {
                    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() !in 200..299) continue
                    val parsed = CustomCosmeticsCodec.parseBatch(response.body())
                    if (generation.get() != token) return@submit
                    parsed.forEach { (uuid, cosmetics) -> remote[uuid] = cosmetics }
                    return@submit
                } catch (exception: Exception) {
                    Icantpy.LOGGER.debug("Cosmetics poll from {} failed", base, exception)
                }
            }
        }
    }

    private fun submit(token: Long, work: () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (generation.get() == token) work()
            } catch (exception: Exception) {
                Icantpy.LOGGER.warn("Cosmetics sync failed", exception)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun bases(): List<String> {
        val configured = System.getenv("ICANTPY_UPDATE_BASE")?.trim()?.trimEnd('/')
        val publicBase = if (configured.isNullOrBlank()) DEFAULT_PUBLIC_BASE else configured
        return listOf(publicBase, LOCAL_BASE).distinct()
    }

    private fun newExecutor() = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "icantpy-cosmetics").apply { isDaemon = true }
    }

    private fun SharedCosmetics.fingerprint(): String =
        gson.toJson(CustomCosmeticsCodec.toJson(copy(updatedAt = 0L)))

    private const val DEFAULT_PUBLIC_BASE = "https://mod.v7ren.com/icantpy"
    private const val LOCAL_BASE = "http://127.0.0.1:8964/icantpy"
    private const val PUBLISH_INTERVAL_MS = 2_000L
    private const val POLL_INTERVAL_MS = 5_000L
}
