package net.icantpy.cosmetics.morph

import net.icantpy.Icantpy
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.cosmetics.items.CustomCosmeticsShare
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Client-only player appearance replacement. The proxy is never added to the world. */
object PlayerDisguise {
    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    @Volatile
    private var selectedEntityId: String? = null

    @Volatile
    private var followMorphEyeHeight: Boolean = false

    @Volatile
    private var selectedCameraMode: MorphCameraMode = MorphCameraMode.LOOK_AT

    @Volatile
    private var crosshairHookAvailable: Boolean = false

    @Volatile
    private var cameraLookHookAvailable: Boolean = false

    @Volatile
    private var proxy: Entity? = null

    private val remoteProxies = ConcurrentHashMap<UUID, Entity>()

    @Volatile
    private var failedEntityId: String? = null

    private var animationTick: Int? = null

    fun load() {
        cameraLookHookAvailable = false
        crosshairHookAvailable = false
        val config = PlayerDisguiseStore.load()
        selectedEntityId = config.entityId
        followMorphEyeHeight = config.followEyeHeight
        selectedCameraMode = config.cameraMode
        clearProxy()
    }

    fun onUnload() {
        cameraLookHookAvailable = false
        crosshairHookAvailable = false
        selectedEntityId = null
        followMorphEyeHeight = false
        selectedCameraMode = MorphCameraMode.LOOK_AT
        clearProxy()
        remoteProxies.clear()
    }

    fun onDisconnect() {
        clearProxy()
        remoteProxies.clear()
    }

    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw)?.trim() ?: return null
        val command = body.split(Regex("\\s+"), limit = 2)
        if (command.firstOrNull()?.lowercase() !in setOf("morph", "disguise")) return null

        val argument = command.getOrNull(1)?.trim().orEmpty()
        if (argument.isBlank()) {
            val current = selectedEntityId
            val camera = if (followMorphEyeHeight) "camera follows morph eye height" else "camera uses player eye height"
            return if (current == null) {
                "morph: use morph <entity>, morph camera on|off, or morph off ($camera)"
            } else {
                "morph is $current; $camera; use morph off to restore your player"
            }
        }
        if (argument.startsWith("camera", ignoreCase = true)) {
            val value = argument.substringAfter(' ', missingDelimiterValue = "").trim().lowercase()
            if (value == "mode") return "morph camera mode: ${selectedCameraMode.label}; use crosshair|look"
            if (value.startsWith("mode ")) {
                val mode = MorphCameraMode.fromId(value.substringAfter(' '))
                    ?: return "morph camera mode crosshair|look"
                return setCameraMode(mode)
            }
            return when (value) {
                "", "status" -> if (followMorphEyeHeight) {
                    morphCameraStatus()
                } else {
                    "morph camera uses your player eye height"
                }
                "on", "true", "eye", "follow" -> setFollowEyeHeight(true)
                "off", "false", "player" -> setFollowEyeHeight(false)
                else -> "morph camera on|off or morph camera mode crosshair|look"
            }
        }
        if (argument.equals("off", ignoreCase = true) || argument.equals("clear", ignoreCase = true)) {
            selectedEntityId = null
            clearProxy()
            persist()
            return "restored your player appearance"
        }

        val normalized = PlayerDisguiseConfig.normalizeEntityId(argument)
            ?: return "invalid entity id; use a name such as cat or minecraft:cat"
        val identifier = Identifier.tryParse(normalized)
            ?: return "invalid entity id; use a name such as cat or minecraft:cat"
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return "unknown entity: $normalized"
        }
        if (normalized == "minecraft:player") return "the player entity cannot be used as a morph"

        selectedEntityId = normalized
        failedEntityId = null
        clearProxy()
        persist()
        return "morphed your player into $normalized; other icantpy users see the morph"
    }

    fun entityId(): String? = selectedEntityId

    fun followEyeHeight(): Boolean = followMorphEyeHeight

    fun cameraMode(): MorphCameraMode = selectedCameraMode

    internal fun usesLookDirection(): Boolean = selectedCameraMode.usesLookDirection(followMorphEyeHeight)

    fun setCameraMode(mode: MorphCameraMode): String {
        selectedCameraMode = mode
        persist(notifyAppearance = false)
        return "morph camera mode: ${mode.label}; ${if (followMorphEyeHeight) morphCameraStatus() else "enable with morph camera on"}"
    }

    fun setFollowEyeHeight(enabled: Boolean): String {
        followMorphEyeHeight = enabled
        persist(notifyAppearance = false)
        return if (enabled) {
            morphCameraStatus()
        } else {
            "morph camera uses your player eye height"
        }
    }

    fun isProxy(entity: Entity): Boolean =
        isLocalProxy(entity) || isRemoteProxy(entity)

    internal fun onCrosshairHook() {
        crosshairHookAvailable = true
    }

    internal fun onCameraLookHook() {
        cameraLookHookAvailable = true
        crosshairHookAvailable = true
    }

    private fun morphCameraStatus(): String = if (crosshairHookAvailable && selectedCameraMode == MorphCameraMode.SHIFTED_CROSSHAIR) {
        "morph eyes follow your vanilla facing; the crosshair moves to your actual vanilla target"
    } else if (cameraLookHookAvailable) {
        "morph eyes follow a fixed vanilla look reference; crosshair stays centered; player aim stays unchanged"
    } else if (crosshairHookAvailable) {
        "crosshair stays centered; look-at view needs loader 1.0.3.30 and a restart"
    } else {
        "morph camera follows third-person eyes; first-person mob eyes need loader 1.0.3.27 and a restart"
    }

    fun isLocalProxy(entity: Entity): Boolean = entity === proxy

    fun isRemoteProxy(entity: Entity): Boolean = remoteProxies.values.any { it === entity }

    fun ownerUuid(entity: Entity?): UUID? {
        if (entity == null) return null
        if (entity === proxy) return Minecraft.getInstance().player?.uuid
        remoteProxies.forEach { (uuid, remote) -> if (remote === entity) return uuid }
        return null
    }

    /** Override only the render camera; the player's eye position and picking remain vanilla. */
    fun cameraEyeHeight(entity: Entity?): Float? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        if (entity !== player) return null
        val entityId = selectedEntityId ?: return null
        val firstPerson = mc.options.getCameraType().isFirstPerson()
        val type = if (followMorphEyeHeight && (!firstPerson || crosshairHookAvailable)) resolveType(entityId) else null
        val morphHeight = type?.let {
            proxy?.takeIf { current -> current.getType() == it }?.getEyeHeight()
                ?: it.getDimensions().eyeHeight()
        }
        return MorphCameraHeight.resolve(
            firstPerson, followMorphEyeHeight, player.getEyeHeight(), morphHeight, crosshairHookAvailable,
        )
    }

    private fun persist(notifyAppearance: Boolean = true) {
        PlayerDisguiseStore.save(PlayerDisguiseConfig(selectedEntityId, followMorphEyeHeight, selectedCameraMode))
        if (notifyAppearance) CustomCosmeticsShare.onLocalAppearanceChanged()
    }

    /** Called from the resident render hook before vanilla extracts the player's state. */
    fun renderProxy(entity: Entity, partialTick: Float): Entity? {
        val mc = Minecraft.getInstance()
        val player = entity as? Player ?: return null
        return if (player === mc.player) {
            renderOwned(player, selectedEntityId, mc, local = true)
        } else {
            renderOwned(player, CustomCosmeticsShare.morphEntityId(player.uuid), mc, local = false)
        }
    }

    /**
     * Keeps the selected vanilla renderer's entity-specific state, while
     * replacing shared movement and facing data with the local player's state.
     */
    fun adaptRenderState(entity: Entity, proxy: Entity, state: EntityRenderState, partialTick: Float) {
        if (proxy !== this.proxy && remoteProxies[entity.uuid] !== proxy) return

        val livingPlayer = entity as? LivingEntity ?: return
        val livingState = state as? LivingEntityRenderState ?: return
        // The local cosmetic pose uses a fixed reference on vanilla facing, never a hit block.
        val cosmeticLook = if (entity === Minecraft.getInstance().player && usesLookDirection()) {
            MorphLookDirection.fromVanillaLook(
                livingPlayer.getViewYRot(partialTick), livingPlayer.getViewXRot(partialTick),
                (entity.getEyeHeight() - proxy.getEyeHeight()).toDouble(),
            )
        } else null
        livingState.bodyRot = cosmeticLook?.yaw ?: livingPlayer.getViewYRot(partialTick)
        livingState.yRot = 0.0f
        livingState.xRot = cosmeticLook?.pitch ?: livingPlayer.getViewXRot(partialTick)
        livingState.walkAnimationPos = livingPlayer.walkAnimation.position(partialTick)
        livingState.walkAnimationSpeed = livingPlayer.walkAnimation.speed(partialTick)
        livingState.ageInTicks = livingPlayer.tickCount + partialTick
    }

    private fun renderOwned(player: Player, entityId: String?, mc: Minecraft, local: Boolean): Entity? {
        val level = mc.level ?: return null
        if (entityId == null) {
            if (local) clearProxy() else remoteProxies.remove(player.uuid)
            return null
        }
        if (local && failedEntityId == entityId) return null

        val type = resolveType(entityId) ?: return null
        val current = if (local) proxy else remoteProxies[player.uuid]
        val newProxy = current == null || current.getType() != type || current.level() !== level
        val next = if (newProxy) {
            createProxy(type, level, entityId, player, recordFailure = local) ?: return null
        } else {
            current
        }
        if (local && newProxy) animationTick = null
        syncProxy(player, next, mc)
        if (local) proxy = next else remoteProxies[player.uuid] = next
        return next
    }

    private fun resolveType(entityId: String) =
        BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityId)).orElse(null)

    private fun createProxy(
        type: net.minecraft.world.entity.EntityType<*>,
        level: net.minecraft.client.multiplayer.ClientLevel,
        entityId: String,
        owner: Entity,
        recordFailure: Boolean,
    ): Entity? = try {
        type.create(level, EntitySpawnReason.COMMAND)?.also { proxy ->
            // Proxies are never added to the world, so vanilla never assigns an ID.
            // SkyHanni and other render mixins hash Entity.getId() and crash otherwise.
            proxy.setId(owner.id)
            proxy.setNoGravity(true)
            proxy.setSilent(true)
        }
    } catch (exception: Exception) {
        if (recordFailure) failedEntityId = entityId
        Icantpy.LOGGER.warn("Could not create client disguise proxy for {}", entityId, exception)
        null
    }

    private fun syncProxy(player: Entity, target: Entity, mc: Minecraft) {
        target.setId(player.id)
        target.copyPosition(player)
        target.setPos(player.position())
        target.setOldPosAndRot(player.oldPosition(), player.yRotO, player.xRotO)
        target.setYRot(player.yRot)
        target.yRotO = player.yRotO
        target.setXRot(player.xRot)
        target.setDeltaMovement(player.deltaMovement)
        target.setOnGround(player.onGround())
        target.setSharedFlagOnFire(player.isOnFire)
        target.setGlowingTag(player.hasGlowingTag())
        target.tickCount = player.tickCount

        val livingPlayer = player as? LivingEntity ?: return
        val livingTarget = target as? LivingEntity ?: return
        // Living renderers derive the model's facing from the head/body pair,
        // not Entity.yRot. Use the player's actual view yaw so the proxy turns
        // immediately even when the local player's body fields are one tick
        // behind the camera.
        livingTarget.setYBodyRot(player.yRot)
        livingTarget.yBodyRotO = player.yRotO
        livingTarget.setYHeadRot(player.yRot)
        livingTarget.yHeadRotO = player.yRotO
        livingTarget.swinging = livingPlayer.swinging
        livingTarget.swingingArm = livingPlayer.swingingArm
        livingTarget.swingTime = livingPlayer.swingTime
        livingTarget.hurtTime = livingPlayer.hurtTime
        livingTarget.hurtDuration = livingPlayer.hurtDuration
        livingTarget.deathTime = livingPlayer.deathTime
        livingTarget.oAttackAnim = livingPlayer.oAttackAnim
        livingTarget.attackAnim = livingPlayer.attackAnim
        // update() expects the desired speed, not the current animation phase.
        // Advance it once per client tick because this proxy is not in the world
        // and therefore does not receive its own LivingEntity tick updates.
        if (animationTick != player.tickCount) {
            livingTarget.walkAnimation.update(livingPlayer.walkAnimation.speed(), 1.0f, 1.0f)
            animationTick = player.tickCount
        } else {
            livingTarget.walkAnimation.setSpeed(livingPlayer.walkAnimation.speed())
        }
        livingTarget.setSprinting(livingPlayer.isSprinting)
        syncArmor(livingPlayer, livingTarget, mc)
    }

    private fun syncArmor(player: LivingEntity, target: LivingEntity, mc: Minecraft) {
        val renderer = try {
            mc.entityRenderDispatcher.getRenderer(target)
        } catch (_: Exception) {
            null
        }
        val canRenderArmor = renderer is HumanoidMobRenderer<*, *, *>
        for (slot in ARMOR_SLOTS) {
            val stack = if (canRenderArmor && target.canUseSlot(slot)) {
                player.getItemBySlot(slot).copy()
            } else {
                ItemStack.EMPTY
            }
            target.setItemSlot(slot, stack)
        }
    }

    private fun clearProxy() {
        proxy = null
        failedEntityId = null
        animationTick = null
    }
}
