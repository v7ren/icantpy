package net.icantpy.qol.perf

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.icantpy.api.CullHook
import net.icantpy.compat.McCompat
import net.icantpy.hud.markers.PlayerMarkers
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BannerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.level.block.entity.HangingSignBlockEntity
import net.minecraft.world.level.block.entity.LecternBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

/**
 * Render culling for crowded scenes. Installs a [CullHook.Provider] only while a sub-feature is enabled. Provider
 * methods run on the render thread. [CullHook] already treats provider exceptions as "draw it".
 *
 * Wiring: [onFrameStart] must be called once per frame, from a render-start hook, so the cached camera position,
 * protected-entity set, frame rate, particle cap and occlusion budget advance.
 *
 * Occlusion ownership: when EntityCulling or MoreCulling is loaded, it already hides occluded entities and block
 * entities, so this object runs no ray tests and keeps only distance and type rules. Mode is chosen at [load].
 */
object Culling {
    /** Squared distance (blocks^2) under which a hit is treated as the target's own surface. */
    private const val EDGE_CONTACT_SQ = 0.0625

    /** Frames a marker result for one armor stand is reused before the equipment scan runs again. */
    private const val MARKER_REFRESH_FRAMES = 20L
    private const val MARKER_CACHE_LIMIT = 2048
    private const val MARKER_NONE = Long.MIN_VALUE

    private val EQUIPMENT_SLOTS = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
    )

    private val lock = Any()

    @Volatile
    private var current = CullingConfig()

    /** Current settings. Replaced as a whole by each setter. */
    val config: CullingConfig get() = current

    private val occlusion = OcclusionCache(::testFullyHidden)

    /** Id of the mod doing occlusion culling instead of us, or null when we do it ourselves. */
    private var occlusionOwnerMod: String? = null
    private var modeLogged = false

    /** Set by [syncHook]: whether the provider is installed, so [onFrameStart] can exit early. */
    private var hookActive = false

    private var frame = 0L
    private var camX = 0.0
    private var camY = 0.0
    private var camZ = 0.0
    private var eye: Vec3 = Vec3.ZERO
    private var particlesDrawn = 0
    private var particleCap = CullingRules.MAX_PARTICLES_PER_FRAME
    private var frameRate = 60.0
    private var lastFrameNanos = 0L
    private var lastLevel: Any? = null

    /** Ids that are never culled this frame: camera entity and the player's vehicle tree. Rebuilt per frame. */
    private val protectedIds = IntOpenHashSet()

    /** Per-class kind caches, so the hot path runs no instanceof chain per object. Render thread only. */
    private val entityKinds = HashMap<Class<*>, CullEntityKind>()
    private val blockEntityKinds = HashMap<Class<*>, CullBlockEntityKind>()

    /** Armor stand marker results by entity id, see [isMarkerStand]. Render thread only. */
    private val markerCache = Int2LongOpenHashMap().apply { defaultReturnValue(MARKER_NONE) }

    private val provider: CullHook.Provider = object : CullHook.Provider {
        override fun skipEntity(entity: Any?): Boolean = skipEntityObject(entity)
        override fun skipBlockEntity(blockEntity: Any?): Boolean = skipBlockEntityObject(blockEntity)
        override fun skipParticle(x: Double, y: Double, z: Double): Boolean = skipParticleAt(x, y, z)
        override fun hideNameTag(entity: Any?): Boolean = hideNameTagOf(entity)
    }

    fun load() {
        synchronized(lock) {
            resolveOcclusionMode()
            current = CullingStore.load()
            syncHook()
        }
    }

    fun unload() {
        synchronized(lock) {
            CullHook.install(null)
            hookActive = false
            occlusion.clear()
            markerCache.clear()
            protectedIds.clear()
            lastLevel = null
        }
    }

    /**
     * Advances per-frame state: frame counter, particle counter and cap, frame-rate estimate, camera position,
     * protected ids and this frame's occlusion budget. Does nothing else while the hook is not installed.
     */
    fun onFrameStart() {
        frame++
        particlesDrawn = 0
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) frameRate = CullingRules.smoothFrameRate(frameRate, now - lastFrameNanos)
        lastFrameNanos = now
        particleCap = CullingRules.particleCapForFps(frameRate)
        if (!hookActive) return
        val settings = current
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level !== lastLevel) {
            occlusion.clear()
            markerCache.clear()
            lastLevel = level
        }
        val camera = McCompat.mainCamera(mc).position()
        camX = camera.x
        camY = camera.y
        camZ = camera.z
        eye = camera
        refreshProtected(mc)
        if (settings.entities && occlusionOwnerMod == null) occlusion.advance(frame)
    }

    fun setEnabled(v: Boolean) = update { it.copy(enabled = v) }

    fun setEntities(v: Boolean) = update { it.copy(entities = v) }

    fun setBlockEntities(v: Boolean) = update { it.copy(blockEntities = v) }

    fun setParticles(v: Boolean) = update { it.copy(particles = v) }

    fun setNameTags(v: Boolean) = update { it.copy(nameTags = v) }

    private fun update(transform: (CullingConfig) -> CullingConfig) {
        synchronized(lock) {
            val next = transform(current)
            current = next
            CullingStore.save(next)
            syncHook()
        }
    }

    private fun resolveOcclusionMode() {
        val owner = CullingRules.occlusionOwner { FabricLoader.getInstance().isModLoaded(it) }
        occlusionOwnerMod = owner
        if (!modeLogged) {
            modeLogged = true
            if (owner != null) {
                Icantpy.LOGGER.info("Culling: {} performs occlusion culling; icantpy skips its own ray tests", owner)
            } else {
                Icantpy.LOGGER.info("Culling: no occlusion mod found; icantpy runs its own ray occlusion")
            }
        }
    }

    private fun syncHook() {
        val settings = current
        val want = CullingRules.hookNeeded(
            settings.enabled,
            settings.entities,
            settings.blockEntities,
            settings.particles,
            settings.nameTags,
        )
        if (want) {
            CullHook.install(provider)
        } else {
            CullHook.install(null)
        }
        hookActive = want
        if (!want || !settings.entities) occlusion.clear()
        if (!want || !settings.entities) markerCache.clear()
    }

    /**
     * Rebuilds the protected id set once per frame: the camera entity and the full vehicle tree of the camera and
     * player roots. Traversal allocates only the passenger lists of those few entities, once per frame.
     */
    private fun refreshProtected(mc: Minecraft) {
        protectedIds.clear()
        mc.cameraEntity?.let { addVehicleTree(it.rootVehicle) }
        mc.player?.let { addVehicleTree(it.rootVehicle) }
    }

    private fun addVehicleTree(entity: Entity) {
        protectedIds.add(entity.id)
        for (passenger in entity.passengers) addVehicleTree(passenger)
    }

    private fun skipEntityObject(obj: Any?): Boolean {
        val settings = current
        if (!settings.enabled || !settings.entities) return false
        val entity = obj as? Entity ?: return false
        val distSq = distanceSqTo(entity.x, entity.y, entity.z)
        if (CullingRules.isNear(distSq)) return false
        val large = CullingRules.isLargeBox(entity.bbWidth, entity.bbHeight)
        val kind = entityKind(entity)
        val guarded = isProtected(entity)
        // Range-only check first: a far entity never needs a ray test.
        var skip = CullingRules.skipEntity(kind, distSq, occluded = false, isProtected = guarded, large = large)
        if (!skip && occlusionOwnerMod == null && !guarded && !large) {
            val hidden = occlusion.touch(entity.id, frame)
            skip = CullingRules.skipEntity(kind, distSq, occluded = hidden, isProtected = false, large = false)
        }
        if (skip) PerfCounters.culledEntities++
        return skip
    }

    private fun skipBlockEntityObject(obj: Any?): Boolean {
        val settings = current
        if (!settings.enabled || !settings.blockEntities) return false
        val blockEntity = obj as? BlockEntity ?: return false
        val pos = blockEntity.blockPos
        val distSq = distanceSqTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val kind = blockEntityKinds.getOrPut(blockEntity.javaClass) { blockEntityKindOf(blockEntity) }
        val skip = CullingRules.skipBlockEntity(kind, distSq, isProtected = false)
        if (skip) PerfCounters.culledBlockEntities++
        return skip
    }

    private fun skipParticleAt(x: Double, y: Double, z: Double): Boolean {
        val settings = current
        if (!settings.enabled || !settings.particles) return false
        val skip = CullingRules.skipParticle(distanceSqTo(x, y, z), particlesDrawn, particleCap)
        if (skip) {
            PerfCounters.culledParticles++
        } else {
            particlesDrawn++
        }
        return skip
    }

    private fun hideNameTagOf(obj: Any?): Boolean {
        val settings = current
        if (!settings.enabled || !settings.nameTags) return false
        val entity = obj as? Entity ?: return false
        val kind = nameTagKind(entity)
        if (kind == CullNameTagKind.NONE) return false
        val distSq = distanceSqTo(entity.x, entity.y, entity.z)
        val hide = CullingRules.hideNameTag(kind, distSq, isProtected(entity))
        if (hide) PerfCounters.hiddenNameTags++
        return hide
    }

    private fun distanceSqTo(x: Double, y: Double, z: Double): Double {
        val dx = x - camX
        val dy = y - camY
        val dz = z - camZ
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Never culled: camera entity and player vehicle tree (per-frame set), glowing or marker-highlighted entities,
     * bosses, and anything riding a protected or directly protected root. Protected entities keep their name tags.
     */
    private fun isProtected(entity: Entity): Boolean {
        if (protectedIds.contains(entity.id)) return true
        if (isDirectlyProtected(entity)) return true
        val root = entity.rootVehicle
        if (root === entity) return false
        return protectedIds.contains(root.id) || isDirectlyProtected(root)
    }

    private fun isDirectlyProtected(entity: Entity): Boolean =
        entity is EnderDragon || entity is WitherBoss || entity.isCurrentlyGlowing || PlayerMarkers.isHighlighted(entity.id)

    private fun entityKind(entity: Entity): CullEntityKind {
        val base = entityKinds.getOrPut(entity.javaClass) { classEntityKind(entity) }
        if (base != CullEntityKind.ARMOR_STAND) return base
        return if (isMarkerStand(entity as ArmorStand)) CullEntityKind.MARKER_ARMOR_STAND else base
    }

    /**
     * Marker result per entity id, refreshed every [MARKER_REFRESH_FRAMES] frames so the six-slot equipment scan
     * does not run per frame. Packed like [OcclusionCache] entries: checkedFrame << 1 | markerBit.
     */
    private fun isMarkerStand(stand: ArmorStand): Boolean {
        val cached = markerCache.get(stand.id)
        if (cached != MARKER_NONE && frame - (cached shr 1) <= MARKER_REFRESH_FRAMES) return (cached and 1L) != 0L
        if (markerCache.size >= MARKER_CACHE_LIMIT) markerCache.clear()
        val marker = CullingRules.armorStandKind(
            invisible = stand.isInvisible,
            hasCustomName = stand.hasCustomName(),
            hasEquipment = hasEquipment(stand),
        ) == CullEntityKind.MARKER_ARMOR_STAND
        markerCache.put(stand.id, (frame shl 1) or (if (marker) 1L else 0L))
        return marker
    }

    /** True when any of the six worn or held slots holds an item. Body and saddle slots are not checked. */
    private fun hasEquipment(stand: ArmorStand): Boolean {
        for (slot in EQUIPMENT_SLOTS) {
            if (!stand.getItemBySlot(slot).isEmpty) return true
        }
        return false
    }

    private fun classEntityKind(entity: Entity): CullEntityKind = when (entity) {
        is ArmorStand -> CullEntityKind.ARMOR_STAND
        is ItemFrame -> CullEntityKind.ITEM_FRAME
        is ItemEntity -> CullEntityKind.DROPPED_ITEM
        else -> CullEntityKind.OTHER
    }

    private fun nameTagKind(entity: Entity): CullNameTagKind = when (entity) {
        is Player -> CullNameTagKind.PLAYER
        is ArmorStand, is LivingEntity -> CullNameTagKind.MOB_OR_STAND
        else -> CullNameTagKind.NONE
    }

    /** Decorative tile entities are culled by distance. Beacons, conduits, gateways and unknown types are kept. */
    private fun blockEntityKindOf(blockEntity: BlockEntity): CullBlockEntityKind = when (blockEntity) {
        is SkullBlockEntity, is SignBlockEntity, is HangingSignBlockEntity, is BannerBlockEntity,
        is ChestBlockEntity, is EnderChestBlockEntity, is TrappedChestBlockEntity,
        is ShulkerBoxBlockEntity, is EnchantingTableBlockEntity, is LecternBlockEntity,
        is BrewingStandBlockEntity,
        -> CullBlockEntityKind.DECORATIVE
        else -> CullBlockEntityKind.ALWAYS_DRAWN
    }

    /**
     * Ray test for [OcclusionCache]: the bounding-box center, then its eight corners, cast from the cached eye.
     * Stops at the first visible sample. Only view-blocking blocks count, so glass does not hide what is behind it.
     */
    private fun testFullyHidden(entityId: Int, rays: RayBudget): Boolean? {
        val level = Minecraft.getInstance().level ?: return false
        val entity = level.getEntity(entityId) ?: return false
        val box = entity.boundingBox
        if (!rays.take()) return null
        if (!isViewBlocked(level, eye, box.center)) return false
        for (corner in 0 until 8) {
            if (!rays.take()) return null
            val x = if ((corner and 1) == 0) box.minX else box.maxX
            val y = if ((corner and 2) == 0) box.minY else box.maxY
            val z = if ((corner and 4) == 0) box.minZ else box.maxZ
            if (!isViewBlocked(level, eye, Vec3(x, y, z))) return false
        }
        return true
    }

    private fun isViewBlocked(level: Level, from: Vec3, to: Vec3): Boolean {
        val hit = level.clip(
            ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()),
        )
        if (hit.type != HitResult.Type.BLOCK) return false
        // A hit right at the target is its own floor or contact surface. Count it as visible to stay conservative.
        if (hit.location.distanceToSqr(to) < EDGE_CONTACT_SQ) return false
        return level.getBlockState(hit.blockPos).isViewBlocking(level, hit.blockPos)
    }
}
