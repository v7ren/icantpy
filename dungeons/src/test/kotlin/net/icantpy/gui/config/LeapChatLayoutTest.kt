package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapCenterTarget
import net.icantpy.dungeon.leap.LeapChatCooldown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapChatLayoutTest {
    private fun chatShape(fromPlayer: Boolean) = LeapCardShape(
        clock = false,
        leaped = false,
        chain = false,
        groupedPreset = false,
        centerTarget = LeapCenterTarget.CLASS,
        chat = true,
        chatFromPlayer = fromPlayer,
    )

    private fun rows(shape: LeapCardShape): List<LeapCardRow> = LeapTriggerLayout.rows(shape).map { it.row }

    @Test
    fun playerRowShownOnlyWhenFromIsPlayer() {
        assertTrue(LeapCardRow.CHAT_PLAYER in rows(chatShape(fromPlayer = true)))
        assertFalse(LeapCardRow.CHAT_PLAYER in rows(chatShape(fromPlayer = false)))
    }

    @Test
    fun chatRowsKeepOrderAndSitAfterTrigger() {
        val list = rows(chatShape(fromPlayer = false))

        assertEquals(LeapCardRow.TRIGGER, list.first())
        assertEquals(
            listOf(
                LeapCardRow.CHAT_SENDER,
                LeapCardRow.CHAT_CHANNEL,
                LeapCardRow.CHAT_MODE,
                LeapCardRow.CHAT_TEXT,
                LeapCardRow.CHAT_COOLDOWN,
            ),
            list.filter { it.name.startsWith("CHAT_") && it != LeapCardRow.CHAT_PLAYER },
        )
    }

    @Test
    fun playerRowFollowsSenderRow() {
        val list = rows(chatShape(fromPlayer = true))

        assertEquals(list.indexOf(LeapCardRow.CHAT_SENDER) + 1, list.indexOf(LeapCardRow.CHAT_PLAYER))
    }

    @Test
    fun nonChatCardHasNoChatRows() {
        val plain = rows(
            LeapCardShape(
                clock = false,
                leaped = false,
                chain = false,
                groupedPreset = false,
                centerTarget = LeapCenterTarget.CLASS,
            ),
        )

        assertFalse(plain.any { it.name.startsWith("CHAT_") })
    }

    @Test
    fun cooldownChoicesAreOrderedAndStartAtHalfSecond() {
        val choices = LeapChatCooldown.CHOICES_MS

        assertEquals(500, choices.first())
        assertEquals(choices.sorted(), choices)
    }
}
