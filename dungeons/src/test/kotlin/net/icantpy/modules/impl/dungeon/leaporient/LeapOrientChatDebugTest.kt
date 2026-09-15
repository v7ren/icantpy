package net.icantpy.modules.impl.dungeon.leaporient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeapOrientChatDebugTest {
    @Test
    fun hudCanHideSkippedChatEntries() {
        LeapOrientChatDebug.clear()
        try {
            LeapOrientChatDebug.record(
                LeapOrientChatDebug.Entry(
                    sender = "skip",
                    message = "unrelated",
                    keywordHit = false,
                    selfOk = true,
                    senderOk = true,
                    armed = false,
                    detail = "no keyword",
                ),
            )
            LeapOrientChatDebug.record(
                LeapOrientChatDebug.Entry(
                    sender = "lock",
                    message = "[icantpy] at healer",
                    keywordHit = true,
                    selfOk = true,
                    senderOk = true,
                    armed = true,
                    detail = "armed healer",
                ),
            )

            val filtered = LeapOrientChatDebug.hudLines(showSkips = false)
            assertEquals(1, filtered.size)
            assertTrue(filtered.single().contains("lock"))
            assertEquals(2, LeapOrientChatDebug.hudLines(showSkips = true).size)
        } finally {
            LeapOrientChatDebug.clear()
        }
    }

    @Test
    fun chatDebugPreferencesRoundTripThroughJson() {
        val original = LeapOrientSettings(
            showBossDeathNotifier = false,
            showChatDebugHud = false,
            showChatDebugMessages = true,
            showChatDebugSkips = true,
        )
        val loaded = LeapOrientSettings.fromJson(LeapOrientSettings.toJson(original))

        assertEquals(false, loaded.showBossDeathNotifier)
        assertEquals(false, loaded.showChatDebugHud)
        assertEquals(true, loaded.showChatDebugMessages)
        assertEquals(true, loaded.showChatDebugSkips)
    }

    @Test
    fun liveRosterDoesNotMixInDebugPlayers() {
        val live = listOf(LeapPlayer("LiveHealer", LeapDungeonClass.HEALER))
        val debug = listOf(LeapPlayer("dbgHealer", LeapDungeonClass.HEALER))

        assertEquals(live, LeapRoster.preferLivePlayers(live, debug))
        assertEquals(debug, LeapRoster.preferLivePlayers(emptyList(), debug))
    }

    @Test
    fun bossDeathNotifierRecordsDeathsBeforeLeapResolution() {
        LeapBossDeathNotifier.clear()
        try {
            assertEquals(null, LeapBossDeathNotifier.record(LeapTriggerEvent.BOSS_GOLDOR, "Goldor start", 1_000))
            assertEquals(
                "Storm",
                LeapBossDeathNotifier.record(LeapTriggerEvent.BOSS_STORM_END, "Storm death", 2_000)?.boss,
            )
            assertEquals("Storm died · 2s ago", LeapBossDeathNotifier.status(4_000))
            assertTrue(LeapBossDeathNotifier.hudLine(6_999)?.contains("Storm died") == true)
            assertEquals(null, LeapBossDeathNotifier.hudLine(7_001))
        } finally {
            LeapBossDeathNotifier.clear()
        }
    }
}
