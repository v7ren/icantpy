package net.icantpy.dungeon.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScoreSettingsCodecTest {
    @Test
    fun roundTripKeepsEveryField() {
        val settings = ScoreSettings(
            enabled = false,
            showBreakdown = false,
            alert270 = false,
            alert300 = true,
            threshold1 = 250,
            threshold2 = 290,
            assumeSpiritPet = true,
            paulBonus = true,
            hudX = 120,
            hudY = 44,
            scale = 1.5f,
        )
        assertEquals(settings, ScoreSettingsCodec.decode(ScoreSettingsCodec.encode(settings)))
    }

    @Test
    fun defaultsRoundTrip() {
        val defaults = ScoreSettings()
        assertEquals(defaults, ScoreSettingsCodec.decode(ScoreSettingsCodec.encode(defaults)))
    }

    @Test
    fun missingFieldsTakeDefaults() {
        val decoded = assertNotNull(ScoreSettingsCodec.decode("{\"threshold1\": 260}"))
        assertEquals(260, decoded.threshold1)
        assertEquals(ScoreSettings(threshold1 = 260), decoded)
    }

    @Test
    fun numbersAreClamped() {
        val decoded = assertNotNull(ScoreSettingsCodec.decode("{\"threshold2\": 99999, \"scale\": 40}"))
        assertEquals(ScoreSettings.MAX_THRESHOLD, decoded.threshold2)
        assertEquals(ScoreSettings.MAX_SCALE, decoded.scale)
    }

    @Test
    fun malformedInputIsRejected() {
        assertNull(ScoreSettingsCodec.decode("not json at all {"))
        assertNull(ScoreSettingsCodec.decode("[1, 2, 3]"))
        assertNull(ScoreSettingsCodec.decode("{\"threshold1\": \"abc\"}"))
        assertNull(ScoreSettingsCodec.decode("{\"enabled\": \"yes\"}"))
        assertNull(ScoreSettingsCodec.decode("{\"scale\": null}"))
    }
}
