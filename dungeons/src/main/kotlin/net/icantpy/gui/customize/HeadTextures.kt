package net.icantpy.gui.customize

import com.google.common.collect.ArrayListMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.icantpy.Icantpy
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID

/**
 * Loads the skyblock head catalogue used by the head-selection grid. Static heads come from the
 * bundled `custom/heads.json` (skyblock id -> base64 texture); animated heads come from
 * `custom/animatedskulls.json` (`skins` -> `{ticks, textures[]}` where each texture is
 * `<uuid>:<base64>`).
 */
object HeadTextures {
    data class NamedTexture(val name: String, val texture: String)
    data class AnimatedHead(val id: String, val tickThreshold: Int, val textures: List<String>, val frames: List<ResolvableProfile>)

    @Volatile
    private var staticHeads: List<NamedTexture> = emptyList()

    @Volatile
    private var animatedHeads: List<AnimatedHead> = emptyList()

    @Volatile
    private var loaded = false

    /** Game ticks since the mod loaded; advanced by [tick]. */
    @Volatile
    private var ticks = 0L

    fun tick() {
        ticks++
    }

    fun staticHeads(): List<NamedTexture> {
        ensureLoaded()
        return staticHeads
    }

    fun animatedHeads(): List<AnimatedHead> {
        ensureLoaded()
        return animatedHeads
    }

    fun animate(id: String): ResolvableProfile? {
        ensureLoaded()
        val head = animatedHeads.firstOrNull { it.id == id } ?: return null
        if (head.frames.isEmpty()) return null
        val threshold = head.tickThreshold.coerceAtLeast(1)
        val index = ((ticks / threshold) % head.frames.size).toInt()
        return head.frames[index]
    }

    fun animateTexture(id: String): String? {
        ensureLoaded()
        val head = animatedHeads.firstOrNull { it.id == id } ?: return null
        if (head.textures.isEmpty()) return null
        val threshold = head.tickThreshold.coerceAtLeast(1)
        val index = ((ticks / threshold) % head.textures.size).toInt()
        return head.textures[index]
    }

    fun formatName(id: String): String =
        id.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    fun skull(texture: String): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, profileFromTexture(texture))
        return stack
    }

    fun skull(profile: ResolvableProfile): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, profile)
        return stack
    }

    /** Builds a resolved head profile directly, so previews never depend on the installed loader. */
    private fun profileFromTexture(texture: String): ResolvableProfile =
        ResolvableProfile.createResolved(
            GameProfile(
                UUID.nameUUIDFromBytes(texture.toByteArray(Charsets.UTF_8)),
                "custom",
                parseProperties(texture),
            ),
        )

    private fun parseProperties(texture: String): PropertyMap {
        val properties = ArrayListMultimap.create<String, Property>()
        properties.put("textures", Property("textures", texture))
        return PropertyMap(properties)
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            loadStatic()
            loadAnimated()
        }
    }

    private fun loadStatic() {
        try {
            val stream = HeadTextures::class.java.classLoader
                ?.getResourceAsStream("assets/icantpy/custom/heads.json")
                ?: return
            val root = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseString(it.readText()).asJsonObject }
            staticHeads = root.entrySet()
                .filter { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString }
                .map { NamedTexture(it.key, it.value.asString) }
                .sortedBy { it.name.lowercase() }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to load static head textures", exception)
        }
    }

    private fun loadAnimated() {
        try {
            val stream = HeadTextures::class.java.classLoader
                ?.getResourceAsStream("assets/icantpy/custom/animatedskulls.json")
                ?: return
            val root = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseString(it.readText()).asJsonObject }
            val skins = root.getAsJsonObject("skins") ?: return
            animatedHeads = skins.entrySet().mapNotNull { (id, value) ->
                val obj = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val tickThreshold = obj.get("ticks")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                    ?.coerceAtLeast(1) ?: 1
                val parsed = obj.get("textures")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { element ->
                        val raw = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                            ?: return@mapNotNull null
                        parseFrame(raw)
                    }.orEmpty()
                val textures = parsed.map { it.first }
                val frames = parsed.map { it.second }
                if (frames.isEmpty()) null else AnimatedHead(id, tickThreshold, textures, frames)
            }.sortedBy { it.id }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to load animated head textures", exception)
        }
    }

    private fun parseFrame(raw: String): Pair<String, ResolvableProfile>? {
        val split = raw.split(':', limit = 2)
        if (split.size != 2) return null
        val uuid = try {
            UUID.fromString(split[0])
        } catch (_: Exception) {
            UUID.nameUUIDFromBytes(split[0].toByteArray())
        }
        return split[1] to ResolvableProfile.createResolved(
            GameProfile(uuid, "custom", parseProperties(split[1])),
        )
    }

    @Suppress("unused")
    private val INNER_SPACE: Identifier = Identifier.fromNamespaceAndPath("icantpy", "menu_inner_space")
}
