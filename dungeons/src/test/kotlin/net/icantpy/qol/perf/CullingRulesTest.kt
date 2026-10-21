package net.icantpy.qol.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CullingRulesTest {
    private fun sq(blocks: Double) = blocks * blocks

    @Test
    fun nothingWithinNearRadiusIsEverSkipped() {
        val near = sq(CullingRules.NEAR_RADIUS_BLOCKS - 0.5)
        assertFalse(CullingRules.skipEntity(CullEntityKind.ARMOR_STAND, near, occluded = true, isProtected = false))
        assertFalse(CullingRules.skipEntity(CullEntityKind.DROPPED_ITEM, near, occluded = true, isProtected = false))
        assertFalse(CullingRules.skipBlockEntity(CullBlockEntityKind.DECORATIVE, near, isProtected = false))
        assertFalse(CullingRules.skipParticle(near, drawnSoFar = CullingRules.MAX_PARTICLES_PER_FRAME))
        assertFalse(CullingRules.hideNameTag(CullNameTagKind.MOB_OR_STAND, near, isProtected = false))
    }

    @Test
    fun protectedEntitiesAreNeverSkippedAtAnyDistance() {
        val far = sq(500.0)
        assertFalse(CullingRules.skipEntity(CullEntityKind.ARMOR_STAND, far, occluded = true, isProtected = true))
        assertFalse(CullingRules.skipEntity(CullEntityKind.OTHER, far, occluded = true, isProtected = true))
        assertFalse(CullingRules.skipBlockEntity(CullBlockEntityKind.DECORATIVE, far, isProtected = true))
        assertFalse(CullingRules.hideNameTag(CullNameTagKind.PLAYER, far, isProtected = true))
    }

    @Test
    fun largeEntitiesAreNeverSkippedEvenFarAndOccluded() {
        assertFalse(
            CullingRules.skipEntity(CullEntityKind.OTHER, sq(200.0), occluded = true, isProtected = false, large = true),
        )
        assertFalse(
            CullingRules.skipEntity(CullEntityKind.ARMOR_STAND, sq(100.0), occluded = false, isProtected = false, large = true),
        )
    }

    @Test
    fun largeBoxThresholdUsesLargestSideAboveThreeBlocks() {
        assertFalse(CullingRules.isLargeBox(widthBlocks = 0.6f, heightBlocks = 1.8f))
        assertFalse(CullingRules.isLargeBox(widthBlocks = 3.0f, heightBlocks = 3.0f))
        assertTrue(CullingRules.isLargeBox(widthBlocks = 3.1f, heightBlocks = 1.0f))
        assertTrue(CullingRules.isLargeBox(widthBlocks = 1.0f, heightBlocks = 4.0f))
    }

    @Test
    fun armorStandsSkippedOnlyBeyond48Blocks() {
        assertFalse(skipArmorStand(47.9))
        assertTrue(skipArmorStand(48.1))
    }

    @Test
    fun itemFramesAndDroppedItemsSkippedOnlyBeyond32Blocks() {
        assertFalse(CullingRules.skipEntity(CullEntityKind.ITEM_FRAME, sq(31.9), false, false))
        assertTrue(CullingRules.skipEntity(CullEntityKind.ITEM_FRAME, sq(32.1), false, false))
        assertFalse(CullingRules.skipEntity(CullEntityKind.DROPPED_ITEM, sq(31.9), false, false))
        assertTrue(CullingRules.skipEntity(CullEntityKind.DROPPED_ITEM, sq(32.1), false, false))
    }

    @Test
    fun occludedEntitiesSkippedAtAnyKindButNeverWhenNear() {
        assertTrue(CullingRules.skipEntity(CullEntityKind.OTHER, sq(20.0), occluded = true, isProtected = false))
        assertFalse(CullingRules.skipEntity(CullEntityKind.OTHER, sq(20.0), occluded = false, isProtected = false))
        assertFalse(CullingRules.skipEntity(CullEntityKind.OTHER, sq(5.0), occluded = true, isProtected = false))
    }

    @Test
    fun plainEntitiesWithoutOcclusionAreKept() {
        assertFalse(CullingRules.skipEntity(CullEntityKind.OTHER, sq(200.0), occluded = false, isProtected = false))
    }

    @Test
    fun decorativeBlockEntitiesSkippedOnlyBeyond48Blocks() {
        assertFalse(CullingRules.skipBlockEntity(CullBlockEntityKind.DECORATIVE, sq(47.9), isProtected = false))
        assertTrue(CullingRules.skipBlockEntity(CullBlockEntityKind.DECORATIVE, sq(48.1), isProtected = false))
    }

    @Test
    fun beaconsAndBeamsAreNeverSkipped() {
        assertFalse(CullingRules.skipBlockEntity(CullBlockEntityKind.ALWAYS_DRAWN, sq(300.0), isProtected = false))
    }

    @Test
    fun particlesSkippedBeyond32Blocks() {
        assertFalse(CullingRules.skipParticle(sq(31.9), drawnSoFar = 0))
        assertTrue(CullingRules.skipParticle(sq(32.1), drawnSoFar = 0))
    }

    @Test
    fun particleCapStopsDrawingAtTwoThousand() {
        val cap = CullingRules.MAX_PARTICLES_PER_FRAME
        assertEquals(2000, cap)
        assertFalse(CullingRules.skipParticle(sq(10.0), drawnSoFar = cap - 1))
        assertTrue(CullingRules.skipParticle(sq(10.0), drawnSoFar = cap))
    }

    @Test
    fun nameTagRangesDifferByOwnerKind() {
        assertFalse(CullingRules.hideNameTag(CullNameTagKind.MOB_OR_STAND, sq(23.9), isProtected = false))
        assertTrue(CullingRules.hideNameTag(CullNameTagKind.MOB_OR_STAND, sq(24.1), isProtected = false))
        assertFalse(CullingRules.hideNameTag(CullNameTagKind.PLAYER, sq(63.9), isProtected = false))
        assertTrue(CullingRules.hideNameTag(CullNameTagKind.PLAYER, sq(64.1), isProtected = false))
        assertFalse(CullingRules.hideNameTag(CullNameTagKind.NONE, sq(500.0), isProtected = false))
    }

    @Test
    fun occlusionCacheReportsHiddenOnlyWhileFresh() {
        val cache = OcclusionCache(HiddenTest { _, _ -> true }, raysPerFrame = 100)
        cache.request(7, frame = 0L)
        cache.advance(frame = 0L)
        assertTrue(cache.isHidden(7, frame = 0L))
        assertTrue(cache.isHidden(7, frame = OcclusionCache.REFRESH_FRAMES))
        assertFalse(cache.isHidden(7, frame = OcclusionCache.REFRESH_FRAMES + 1))
        assertFalse(cache.isHidden(99, frame = 0L))
    }

    @Test
    fun occlusionCacheSpendsRayBudgetRoundRobin() {
        val tested = mutableListOf<Int>()
        val cache = OcclusionCache(
            HiddenTest { id, rays ->
                tested += id
                if (rays.take()) false else null
            },
            raysPerFrame = 2,
        )
        cache.request(1, 0L)
        cache.request(2, 0L)
        cache.advance(0L)
        cache.advance(1L)
        assertEquals(listOf(1, 2), tested.take(2))
    }

    @Test
    fun configParseFallsBackPerField() {
        val parsed = CullingConfig.parse("""{"enabled": true, "entities": "no", "particles": false}""")
        assertTrue(parsed.enabled)
        assertTrue(parsed.entities)
        assertTrue(parsed.blockEntities)
        assertFalse(parsed.particles)
        assertTrue(parsed.nameTags)
    }

    @Test
    fun configParseMalformedOrNonObjectUsesDefaults() {
        assertEquals(CullingConfig(), CullingConfig.parse("{not json"))
        assertEquals(CullingConfig(), CullingConfig.parse("[1, 2]"))
        assertEquals(CullingConfig(), CullingConfig.parse(""))
    }

    private fun skipArmorStand(blocks: Double) =
        CullingRules.skipEntity(CullEntityKind.ARMOR_STAND, sq(blocks), occluded = false, isProtected = false)

    @Test
    fun invisibleArmorStandWithoutNameOrEquipmentIsAMarker() {
        assertEquals(
            CullEntityKind.MARKER_ARMOR_STAND,
            CullingRules.armorStandKind(invisible = true, hasCustomName = false, hasEquipment = false),
        )
        assertEquals(
            CullEntityKind.ARMOR_STAND,
            CullingRules.armorStandKind(invisible = true, hasCustomName = true, hasEquipment = false),
        )
        assertEquals(
            CullEntityKind.ARMOR_STAND,
            CullingRules.armorStandKind(invisible = false, hasCustomName = false, hasEquipment = false),
        )
        assertEquals(
            CullEntityKind.ARMOR_STAND,
            CullingRules.armorStandKind(invisible = false, hasCustomName = true, hasEquipment = false),
        )
    }

    @Test
    fun invisibleArmorStandWearingOrHoldingItemsIsNotAMarker() {
        // Skull-wearing or item-holding stands on SkyBlock are decorations or mob parts; keep their range.
        assertEquals(
            CullEntityKind.ARMOR_STAND,
            CullingRules.armorStandKind(invisible = true, hasCustomName = false, hasEquipment = true),
        )
        assertFalse(CullingRules.skipEntity(
            CullingRules.armorStandKind(invisible = true, hasCustomName = false, hasEquipment = true),
            sq(20.0), occluded = false, isProtected = false,
        ))
        assertTrue(CullingRules.skipEntity(
            CullingRules.armorStandKind(invisible = true, hasCustomName = false, hasEquipment = true),
            sq(48.1), occluded = false, isProtected = false,
        ))
    }

    @Test
    fun markerArmorStandsSkippedBeyond16Blocks() {
        val kind = CullEntityKind.MARKER_ARMOR_STAND
        assertFalse(CullingRules.skipEntity(kind, sq(15.9), occluded = false, isProtected = false))
        assertTrue(CullingRules.skipEntity(kind, sq(16.1), occluded = false, isProtected = false))
        assertFalse(CullingRules.skipEntity(kind, sq(16.1), occluded = false, isProtected = true))
        assertFalse(CullingRules.skipEntity(kind, sq(5.0), occluded = true, isProtected = false))
    }

    @Test
    fun particleCapTightensAsFrameRateDrops() {
        assertEquals(CullingRules.MAX_PARTICLES_PER_FRAME, CullingRules.particleCapForFps(144.0))
        assertEquals(CullingRules.MAX_PARTICLES_PER_FRAME, CullingRules.particleCapForFps(90.0))
        assertEquals(1500, CullingRules.particleCapForFps(60.0))
        assertEquals(1000, CullingRules.particleCapForFps(40.0))
        assertEquals(600, CullingRules.particleCapForFps(25.0))
        assertEquals(300, CullingRules.particleCapForFps(10.0))
        assertEquals(300, CullingRules.particleCapForFps(Double.NaN))
    }

    @Test
    fun particleCapIsMonotonicInFrameRate() {
        var previous = Int.MIN_VALUE
        var fps = 0.0
        while (fps <= 300.0) {
            val cap = CullingRules.particleCapForFps(fps)
            assertTrue(cap >= previous, "cap fell at $fps fps")
            assertTrue(cap in 300..CullingRules.MAX_PARTICLES_PER_FRAME)
            previous = cap
            fps += 0.5
        }
    }

    @Test
    fun explicitParticleCapIsRespected() {
        assertFalse(CullingRules.skipParticle(sq(10.0), drawnSoFar = 599, cap = 600))
        assertTrue(CullingRules.skipParticle(sq(10.0), drawnSoFar = 600, cap = 600))
        assertTrue(CullingRules.skipParticle(sq(40.0), drawnSoFar = 0, cap = 600))
    }

    @Test
    fun frameRateSmoothingMovesTowardInstantRate() {
        val fromSixty = CullingRules.smoothFrameRate(60.0, 1_000_000_000L / 30)
        assertTrue(fromSixty < 60.0 && fromSixty > 30.0)
        val unchanged = CullingRules.smoothFrameRate(60.0, 0L)
        assertEquals(60.0, unchanged)
        val clamped = CullingRules.smoothFrameRate(60.0, 1L)
        assertTrue(clamped <= 60.0 * 0.9 + 1000.0 * 0.1 + 1e-9)
    }

    @Test
    fun hookOnlyInstalledWhenEnabledAndSomethingIsOn() {
        assertFalse(CullingRules.hookNeeded(false, true, true, true, true))
        assertFalse(CullingRules.hookNeeded(true, false, false, false, false))
        assertTrue(CullingRules.hookNeeded(true, false, false, true, false))
        assertTrue(CullingRules.hookNeeded(true, false, false, false, true))
    }

    @Test
    fun occlusionOwnerDetectedFromLoadedMods() {
        assertEquals(null, CullingRules.occlusionOwner { false })
        assertEquals("entityculling", CullingRules.occlusionOwner { it == "entityculling" })
        // MoreCulling handles block faces, item frames and signs, not entity occlusion, so it does not
        // disable our ray occlusion.
        assertEquals(null, CullingRules.occlusionOwner { it == "moreculling" })
        assertEquals(null, CullingRules.occlusionOwner { it == "sodium" || it == "lithium" })
    }

    @Test
    fun occlusionCacheTouchQueuesOnceThenAnswersFromCache() {
        val tested = mutableListOf<Int>()
        val cache = OcclusionCache(
            HiddenTest { id, rays ->
                tested += id
                if (rays.take()) true else null
            },
            raysPerFrame = 4,
        )
        assertFalse(cache.touch(5, 0L))
        assertFalse(cache.touch(5, 0L))
        cache.advance(0L)
        assertEquals(listOf(5), tested)
        assertTrue(cache.touch(5, 1L))
        assertTrue(cache.isHidden(5, 1L))
        assertFalse(cache.touch(5, OcclusionCache.REFRESH_FRAMES + 2))
        cache.advance(OcclusionCache.REFRESH_FRAMES + 2)
        assertEquals(listOf(5, 5), tested)
    }

    @Test
    fun occlusionCacheStopsAtCapacityByEvicting() {
        val cache = OcclusionCache(HiddenTest { _, rays -> if (rays.take()) false else null }, raysPerFrame = 100, maxEntries = 2)
        for (id in 1..3) cache.request(id, 0L)
        cache.advance(0L)
        assertEquals(2, cache.size)
    }

    @Test
    fun occlusionCacheClearDropsEverything() {
        val cache = OcclusionCache(HiddenTest { _, rays -> if (rays.take()) true else null }, raysPerFrame = 10)
        cache.request(3, 0L)
        cache.advance(0L)
        cache.clear()
        assertEquals(0, cache.size)
        assertFalse(cache.isHidden(3, 0L))
    }
}
