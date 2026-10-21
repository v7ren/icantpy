package net.icantpy.dungeon.dragons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragonSettingsCodecTest {
    @Test
    fun encodeThenDecodeRoundTrips() {
        val file = DragonFile(
            settings = DragonSettings(
                normalPower = 25,
                soloDebuff = SoloDebuffClass.HEALER,
                timerStyle = DragonTimerStyle.TICKS,
                hudX = 5,
                scale = 1.5f,
                paulBuff = true,
            ),
            personalBestsMs = mapOf(WitherDragon.Red to 5_000L),
        )

        val decoded = DragonSettingsCodec.decode(DragonSettingsCodec.encode(file))

        assertNull(decoded.error)
        assertEquals(file, decoded.file)
    }

    @Test
    fun malformedJsonReportsError() {
        assertNotNull(DragonSettingsCodec.decode("{not json").error)
        assertNotNull(DragonSettingsCodec.decode("[1,2]").error)
        assertNotNull(DragonSettingsCodec.decode("").error)
    }

    @Test
    fun missingAndMistypedFieldsUseDefaults() {
        val json = """{"normalPower":"lots","enabled":false,"soloDebuff":"NOPE"}"""

        val settings = DragonSettingsCodec.decode(json).file!!.settings

        assertEquals(false, settings.enabled)
        assertEquals(DragonSettings().normalPower, settings.normalPower)
        assertEquals(SoloDebuffClass.TANK, settings.soloDebuff)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val settings = DragonSettingsCodec.decode("""{"normalPower":99,"hudX":-4}""").file!!.settings

        assertEquals(DragonSettings.MAX_POWER, settings.normalPower)
        assertEquals(0, settings.hudX)
    }

    @Test
    fun invalidPersonalBestsAreIgnored() {
        val json = """{"personalBestsMs":{"Red":-5,"Blue":"x","Green":7000}}"""

        val bests = DragonSettingsCodec.decode(json).file!!.personalBestsMs

        assertEquals(mapOf(WitherDragon.Green to 7_000L), bests)
    }

    @Test
    fun personalBestOnlyImprovesOnFasterRealKills() {
        val first = DragonPersonalBests.record(emptyMap(), WitherDragon.Red, 5_000L, realHealthKill = true)
        assertTrue(first.improved)
        assertEquals(5_000L, first.bests[WitherDragon.Red])

        val slower = DragonPersonalBests.record(first.bests, WitherDragon.Red, 6_000L, realHealthKill = true)
        assertTrue(!slower.improved)

        val faster = DragonPersonalBests.record(first.bests, WitherDragon.Red, 4_000L, realHealthKill = true)
        assertEquals(4_000L, faster.bests[WitherDragon.Red])

        val estimate = DragonPersonalBests.record(first.bests, WitherDragon.Red, 1_000L, realHealthKill = false)
        assertTrue(!estimate.improved)
        assertEquals(5_000L, estimate.bests[WitherDragon.Red])
    }
}
