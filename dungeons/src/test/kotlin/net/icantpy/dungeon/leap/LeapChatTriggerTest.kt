package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapChatTriggerTest {
    private fun chatTrigger(id: String = "chat-1", text: String = "abc") = LeapOrientTrigger(
        id = id,
        event = LeapTriggerEvent.CHAT_MESSAGE,
        chatText = text,
    )

    @Test
    fun chatFieldsHaveDefaults() {
        val trigger = chatTrigger()

        assertEquals(ChatSender.SELF, trigger.chatSender)
        assertEquals("", trigger.chatPlayer)
        assertEquals(ChatChannel.ANY, trigger.chatChannel)
        assertEquals(ChatTextMode.CONTAINS, trigger.chatMode)
        assertEquals(1000, trigger.chatCooldownMs)
    }

    @Test
    fun jsonRoundTripKeepsChatFields() {
        val trigger = chatTrigger().copy(
            chatSender = ChatSender.PLAYER,
            chatPlayer = "Alpha",
            chatChannel = ChatChannel.PARTY,
            chatMode = ChatTextMode.REGEX,
            chatText = "ab+c",
            chatCooldownMs = 5000,
        )
        val obj = JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(listOf(trigger))) }

        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(obj.toString()).asJsonObject)!!.single()

        assertEquals(trigger.chatSender, parsed.chatSender)
        assertEquals(trigger.chatPlayer, parsed.chatPlayer)
        assertEquals(trigger.chatChannel, parsed.chatChannel)
        assertEquals(trigger.chatMode, parsed.chatMode)
        assertEquals(trigger.chatText, parsed.chatText)
        assertEquals(trigger.chatCooldownMs, parsed.chatCooldownMs)
    }

    @Test
    fun oldConfigWithoutChatFieldsLoadsWithDefaults() {
        val text = """{"triggers":[{"id":"old","event":"BOSS_STORM_END","steps":[]}]}"""

        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!.single()

        assertEquals(LeapTriggerEvent.BOSS_STORM_END, parsed.event)
        assertEquals(ChatSender.SELF, parsed.chatSender)
        assertEquals(ChatChannel.ANY, parsed.chatChannel)
        assertEquals(ChatTextMode.CONTAINS, parsed.chatMode)
        assertEquals(1000, parsed.chatCooldownMs)
    }

    @Test
    fun chatTitleNamesSenderChannelAndText() {
        val trigger = chatTrigger(text = "abc").copy(chatChannel = ChatChannel.PARTY)

        assertEquals("Chat (Me, Party) contains \"abc\"", trigger.chatTitle())
        assertEquals(
            "Chat (Alpha, Party) contains \"abc\"",
            trigger.copy(chatSender = ChatSender.PLAYER, chatPlayer = "Alpha").chatTitle(),
        )
    }

    @Test
    fun cooldownBlocksUntilWindowPasses() {
        val cooldown = LeapChatCooldown()

        assertTrue(cooldown.ready("a", now = 0L, cooldownMs = 1000))
        assertFalse(cooldown.ready("a", now = 999L, cooldownMs = 1000))
        assertTrue(cooldown.ready("a", now = 1000L, cooldownMs = 1000))
    }

    @Test
    fun cooldownIsTrackedPerCard() {
        val cooldown = LeapChatCooldown()

        assertTrue(cooldown.ready("a", now = 0L, cooldownMs = 5000))
        assertTrue(cooldown.ready("b", now = 10L, cooldownMs = 5000))
        assertFalse(cooldown.ready("a", now = 20L, cooldownMs = 5000))
    }

    @Test
    fun zeroCooldownAlwaysFires() {
        val cooldown = LeapChatCooldown()

        assertTrue(cooldown.ready("a", now = 0L, cooldownMs = 0))
        assertTrue(cooldown.ready("a", now = 0L, cooldownMs = 0))
    }

    @Test
    fun clearForgetsCooldowns() {
        val cooldown = LeapChatCooldown()
        cooldown.ready("a", now = 0L, cooldownMs = 5000)

        cooldown.clear()

        assertTrue(cooldown.ready("a", now = 1L, cooldownMs = 5000))
    }

    @Test
    fun selfAndOthersSendersSplitOwnMessages() {
        val own = ParsedChat(ChatChannel.PARTY, "Me_Player", "abc")
        val teammate = ParsedChat(ChatChannel.PARTY, "Alpha", "abc")

        assertTrue(ChatMessageMatch.matches(own, "Me_Player", ChatSender.SELF, "", ChatChannel.ANY, ChatTextMode.CONTAINS, "abc"))
        assertFalse(ChatMessageMatch.matches(own, "Me_Player", ChatSender.OTHERS, "", ChatChannel.ANY, ChatTextMode.CONTAINS, "abc"))
        assertTrue(ChatMessageMatch.matches(teammate, "Me_Player", ChatSender.OTHERS, "", ChatChannel.ANY, ChatTextMode.CONTAINS, "abc"))
        assertFalse(ChatMessageMatch.matches(teammate, "Me_Player", ChatSender.SELF, "", ChatChannel.ANY, ChatTextMode.CONTAINS, "abc"))
    }
}

class LeapChatEventPersistenceTest {
    @Test
    fun savedEventsLoadByNameEvenAfterChatMessageWasAdded() {
        val text = """{"triggers":[{"id":"x","event":"BOSS_STORM_START","steps":[]},{"id":"y","event":"SELF_LEAP","steps":[]}]}"""

        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!

        assertEquals(LeapTriggerEvent.BOSS_STORM_START, parsed[0].event)
        assertEquals(LeapTriggerEvent.SELF_LEAP, parsed[1].event)
    }

    @Test
    fun chatMessageRoundTripsByName() {
        val obj = JsonObject().apply {
            add("triggers", LeapTriggerJson.triggersToJson(listOf(LeapOrientTrigger(id = "c", event = LeapTriggerEvent.CHAT_MESSAGE))))
        }

        assertEquals("CHAT_MESSAGE", obj.getAsJsonArray("triggers")[0].asJsonObject.get("event").asString)
    }
}
