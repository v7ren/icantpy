package net.icantpy.qol.perf

/** Entity categories the culling rules distinguish. Classified by the Minecraft adapter, decided here. */
enum class CullEntityKind {
    ARMOR_STAND,

    /** Invisible armor stand with no custom name: a pure marker that draws nothing useful at range. */
    MARKER_ARMOR_STAND,
    ITEM_FRAME,
    DROPPED_ITEM,
    OTHER,
}

/** Block entity categories. Only [DECORATIVE] ones may be culled by distance. */
enum class CullBlockEntityKind { DECORATIVE, ALWAYS_DRAWN }

/** Name tag owners: players use the longer range, mobs and armor stands the shorter one. */
enum class CullNameTagKind { PLAYER, MOB_OR_STAND, NONE }

/**
 * Pure culling decisions. Distances are squared block distances from the camera. No Minecraft types, so the
 * rules can be tested directly. Every function returns true to skip or hide.
 */
object CullingRules {
    /** Nothing closer than this is ever skipped or hidden. Units: blocks. */
    const val NEAR_RADIUS_BLOCKS: Double = 8.0

    const val ARMOR_STAND_RANGE_BLOCKS: Double = 48.0
    const val MARKER_ARMOR_STAND_RANGE_BLOCKS: Double = 16.0
    const val ITEM_FRAME_RANGE_BLOCKS: Double = 32.0
    const val DROPPED_ITEM_RANGE_BLOCKS: Double = 32.0
    const val BLOCK_ENTITY_RANGE_BLOCKS: Double = 48.0
    const val MOB_NAME_TAG_RANGE_BLOCKS: Double = 24.0
    const val PLAYER_NAME_TAG_RANGE_BLOCKS: Double = 64.0
    const val PARTICLE_RANGE_BLOCKS: Double = 32.0

    /** Entities whose largest bounding-box side exceeds this are never culled. */
    const val LARGE_BOX_BLOCKS: Float = 3.0f

    /** Particle cap at high frame rates. Lower frame rates get tighter caps, see [particleCapForFps]. */
    const val MAX_PARTICLES_PER_FRAME: Int = 2000

    /** Mods that perform their own entity occlusion culling. When one is loaded, we skip our ray occlusion. */
    val OCCLUSION_MODS: List<String> = listOf("entityculling")

    private const val NEAR_SQ = NEAR_RADIUS_BLOCKS * NEAR_RADIUS_BLOCKS

    fun isNear(distanceSq: Double): Boolean = distanceSq <= NEAR_SQ

    /**
     * Entities are skipped when they are not protected, not near, and either beyond their kind's range or
     * fully hidden behind blocks ([occluded], from [OcclusionCache]).
     */
    fun skipEntity(
        kind: CullEntityKind,
        distanceSq: Double,
        occluded: Boolean,
        isProtected: Boolean,
        large: Boolean = false,
    ): Boolean {
        if (isProtected || large || isNear(distanceSq)) return false
        if (occluded) return true
        val range = entityRange(kind) ?: return false
        return distanceSq > range * range
    }

    /** True when the largest bounding-box side exceeds [LARGE_BOX_BLOCKS]; sampled occlusion is unreliable for these. */
    fun isLargeBox(widthBlocks: Float, heightBlocks: Float): Boolean =
        maxOf(widthBlocks, heightBlocks) > LARGE_BOX_BLOCKS

    /**
     * Invisible armor stands with no custom name and no equipped items (all six slots empty) are markers.
     * A stand wearing a skull or holding anything is decoration and keeps the normal armor stand range.
     */
    fun armorStandKind(invisible: Boolean, hasCustomName: Boolean, hasEquipment: Boolean): CullEntityKind =
        if (invisible && !hasCustomName && !hasEquipment) CullEntityKind.MARKER_ARMOR_STAND else CullEntityKind.ARMOR_STAND

    /**
     * Whether the render hook must be installed at all. False when every sub-feature is off, so the mixins
     * return to a single volatile read.
     */
    fun hookNeeded(enabled: Boolean, entities: Boolean, blockEntities: Boolean, particles: Boolean, nameTags: Boolean): Boolean =
        enabled && (entities || blockEntities || particles || nameTags)

    /**
     * The first known occlusion-culling mod that is loaded, or null when none is. When non-null, the payload
     * leaves entity and block entity occlusion to that mod and keeps only its distance and type rules.
     */
    fun occlusionOwner(isLoaded: (String) -> Boolean): String? = OCCLUSION_MODS.firstOrNull(isLoaded)

    private fun entityRange(kind: CullEntityKind): Double? = when (kind) {
        CullEntityKind.ARMOR_STAND -> ARMOR_STAND_RANGE_BLOCKS
        CullEntityKind.MARKER_ARMOR_STAND -> MARKER_ARMOR_STAND_RANGE_BLOCKS
        CullEntityKind.ITEM_FRAME -> ITEM_FRAME_RANGE_BLOCKS
        CullEntityKind.DROPPED_ITEM -> DROPPED_ITEM_RANGE_BLOCKS
        CullEntityKind.OTHER -> null
    }

    /** Only decorative block entities beyond [BLOCK_ENTITY_RANGE_BLOCKS] are skipped; beacons and beams never. */
    fun skipBlockEntity(kind: CullBlockEntityKind, distanceSq: Double, isProtected: Boolean): Boolean {
        if (isProtected || isNear(distanceSq)) return false
        if (kind == CullBlockEntityKind.ALWAYS_DRAWN) return false
        return distanceSq > BLOCK_ENTITY_RANGE_BLOCKS * BLOCK_ENTITY_RANGE_BLOCKS
    }

    /**
     * Skips particles beyond [PARTICLE_RANGE_BLOCKS], and any particle once [drawnSoFar] reached [cap].
     * [cap] normally comes from [particleCapForFps].
     */
    fun skipParticle(distanceSq: Double, drawnSoFar: Int, cap: Int = MAX_PARTICLES_PER_FRAME): Boolean {
        if (isNear(distanceSq)) return false
        if (distanceSq > PARTICLE_RANGE_BLOCKS * PARTICLE_RANGE_BLOCKS) return true
        return drawnSoFar >= cap
    }

    /**
     * Per-frame particle cap by frame rate. Low frame rates get tighter caps so particle work shrinks when the
     * game is already struggling. Monotonic in [fps], never above [MAX_PARTICLES_PER_FRAME], never below 300.
     * NaN counts as low.
     */
    fun particleCapForFps(fps: Double): Int = when {
        fps.isNaN() -> 300
        fps >= 90.0 -> MAX_PARTICLES_PER_FRAME
        fps >= 60.0 -> 1500
        fps >= 40.0 -> 1000
        fps >= 25.0 -> 600
        else -> 300
    }

    /**
     * Exponential moving average of the frame rate from the time one frame took. Non-positive frame times leave
     * the estimate unchanged. Instantaneous rate is clamped to 1..1000 FPS so a stall cannot wreck the average.
     */
    fun smoothFrameRate(current: Double, frameNanos: Long): Double {
        if (frameNanos <= 0L) return current
        val instant = (1_000_000_000.0 / frameNanos.toDouble()).coerceIn(1.0, 1000.0)
        return current * 0.9 + instant * 0.1
    }

    fun hideNameTag(kind: CullNameTagKind, distanceSq: Double, isProtected: Boolean): Boolean {
        if (isProtected || isNear(distanceSq)) return false
        val range = when (kind) {
            CullNameTagKind.PLAYER -> PLAYER_NAME_TAG_RANGE_BLOCKS
            CullNameTagKind.MOB_OR_STAND -> MOB_NAME_TAG_RANGE_BLOCKS
            CullNameTagKind.NONE -> return false
        }
        return distanceSq > range * range
    }
}
