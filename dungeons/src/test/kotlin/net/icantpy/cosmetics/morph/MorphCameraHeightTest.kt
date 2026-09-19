package net.icantpy.cosmetics.morph

import kotlin.test.Test
import kotlin.test.assertEquals

class MorphCameraHeightTest {
    @Test
    fun firstPersonPreservesShortAndTallMorphEyes() {
        for (morphHeight in listOf(0.35f, 1.1f, 2.9f)) {
            assertEquals(morphHeight, MorphCameraHeight.resolve(true, true, 1.62f, morphHeight))
        }
    }

    @Test
    fun morphEyesStayEnabledWhileThePlayerCrouchesOrSwims() {
        for (playerHeight in listOf(1.27f, 0.4f)) {
            assertEquals(2.9f, MorphCameraHeight.resolve(true, true, playerHeight, 2.9f))
        }
    }

    @Test
    fun thirdPersonStillFollowsTheMorphWhenEnabled() {
        assertEquals(0.35f, MorphCameraHeight.resolve(false, true, 1.62f, 0.35f))
        assertEquals(2.9f, MorphCameraHeight.resolve(false, true, 1.62f, 2.9f))
    }

    @Test
    fun disabledOrUnavailableMorphCameraKeepsPlayerHeight() {
        assertEquals(1.62f, MorphCameraHeight.resolve(false, false, 1.62f, 0.35f))
        assertEquals(1.62f, MorphCameraHeight.resolve(false, true, 1.62f, null))
    }

    @Test
    fun installedLoaderWithoutCrosshairHookRetainsAlignedFirstPersonFallback() {
        assertEquals(1.62f, MorphCameraHeight.resolve(true, true, 1.62f, 0.35f, false))
        assertEquals(0.35f, MorphCameraHeight.resolve(false, true, 1.62f, 0.35f, false))
    }
}
