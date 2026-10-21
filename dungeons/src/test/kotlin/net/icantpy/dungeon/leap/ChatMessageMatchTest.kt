package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageMatchTest {
    private fun parsed(line: String): ParsedChat = requireNotNull(ChatMessageMatch.parse(line)) { "no parse: $line" }

    private fun match(
        line: String,
        sender: ChatSender = ChatSender.ANYONE,
        playerName: String = "",
        channel: ChatChannel = ChatChannel.ANY,
        mode: ChatTextMode = ChatTextMode.CONTAINS,
        pattern: String = "hello",
        ignoreCase: Boolean = true,
        selfName: String = "Me_Player",
    ): Boolean = ChatMessageMatch.matches(
        parsed(line), selfName, sender, playerName, channel, mode, pattern, ignoreCase,
    )

    @Test
    fun partyFormatsParse() {
        assertEquals(ParsedChat(ChatChannel.PARTY, "Name", "hello"), parsed("Party > [MVP+] Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PARTY, "Name", "hello"), parsed("Party > Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PARTY, "Name", "hi there"), parsed("  Party > [MVP++] Name: hi there  "))
    }

    @Test
    fun coopParsesAsParty() {
        assertEquals(ParsedChat(ChatChannel.PARTY, "Name", "hi"), parsed("Co-op > Name: hi"))
    }

    @Test
    fun guildFormatsParse() {
        assertEquals(ParsedChat(ChatChannel.GUILD, "Name", "hello"), parsed("Guild > [VIP] Name [Rank]: hello"))
        assertEquals(ParsedChat(ChatChannel.GUILD, "Name", "hello"), parsed("Guild > Name: hello"))
        assertEquals(ParsedChat(ChatChannel.GUILD, "Name", "hello"), parsed("Guild > [MVP+] Name [Officer]: hello"))
    }

    @Test
    fun privateFormatsParse() {
        assertEquals(ParsedChat(ChatChannel.PRIVATE, "Friend", "hello"), parsed("From [MVP+] Friend: hello"))
        assertEquals(ParsedChat(ChatChannel.PRIVATE, "Friend", "hello"), parsed("From Friend: hello"))
    }

    @Test
    fun outgoingPrivateIsSelf() {
        val chat = parsed("To [MVP+] Friend: hello")
        assertEquals(ChatChannel.PRIVATE, chat.channel)
        assertEquals("@self", chat.sender)
        assertEquals("hello", chat.text)
        assertTrue(match("To [MVP+] Friend: hello", sender = ChatSender.SELF, channel = ChatChannel.PRIVATE))
        assertFalse(match("To [MVP+] Friend: hello", sender = ChatSender.OTHERS, channel = ChatChannel.PRIVATE))
    }

    @Test
    fun publicFormatsParse() {
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name", "hello"), parsed("[123] [MVP+] Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name", "hello"), parsed("[MVP+] Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name", "hello"), parsed("Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name", "hello"), parsed("[45] ♲ Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name", "hello"), parsed("[99] ⚒ [PIG+++] Name: hello"))
        assertEquals(ParsedChat(ChatChannel.PUBLIC, "Name_1", "hello"), parsed("✿ [YOUTUBE] Name_1: hello"))
    }

    @Test
    fun systemLinesReturnNull() {
        assertNull(ChatMessageMatch.parse("[BOSS] Maxor: YOU KNOW I'M NOT A FAN OF THE CROWN"))
        assertNull(ChatMessageMatch.parse("[NPC] Mort: Here, take this."))
        assertNull(ChatMessageMatch.parse("[STATUE] Oruo: hi"))
        assertNull(ChatMessageMatch.parse("[SECURITY] Name: hi"))
        assertNull(ChatMessageMatch.parse("You equipped Loadout 4!"))
        assertNull(ChatMessageMatch.parse("Party Finder > Name joined the dungeon group!"))
        assertNull(ChatMessageMatch.parse("   "))
        assertNull(ChatMessageMatch.parse(""))
    }

    @Test
    fun senderModes() {
        val self = "Party > [MVP+] Me_Player: hello"
        val other = "Party > [MVP+] Friend: hello"
        assertTrue(match(self, sender = ChatSender.SELF))
        assertFalse(match(other, sender = ChatSender.SELF))
        assertFalse(match(self, sender = ChatSender.OTHERS))
        assertTrue(match(other, sender = ChatSender.OTHERS))
        assertTrue(match(self, sender = ChatSender.ANYONE))
        assertTrue(match(other, sender = ChatSender.PLAYER, playerName = "friend"))
        assertFalse(match(other, sender = ChatSender.PLAYER, playerName = "someone"))
        assertFalse(match(other, sender = ChatSender.PLAYER, playerName = ""))
    }

    @Test
    fun selfMatchIsCaseInsensitiveAndBlankSelfNameIsSafe() {
        assertTrue(match("Party > me_player: hello", sender = ChatSender.SELF, selfName = "Me_Player"))
        assertFalse(match("Party > Friend: hello", sender = ChatSender.SELF, selfName = ""))
    }

    @Test
    fun channelFiltering() {
        val party = "Party > Name: hello"
        assertTrue(match(party, channel = ChatChannel.ANY))
        assertTrue(match(party, channel = ChatChannel.PARTY))
        assertFalse(match(party, channel = ChatChannel.GUILD))
        assertFalse(match(party, channel = ChatChannel.PUBLIC))
        assertTrue(match("Guild > Name: hello", channel = ChatChannel.GUILD))
        assertTrue(match("From Name: hello", channel = ChatChannel.PRIVATE))
        assertTrue(match("Name: hello", channel = ChatChannel.PUBLIC))
    }

    @Test
    fun textModes() {
        val line = "Party > Name: Hello World"
        assertTrue(match(line, mode = ChatTextMode.CONTAINS, pattern = "lo wo"))
        assertTrue(match(line, mode = ChatTextMode.EXACT, pattern = "hello world"))
        assertFalse(match(line, mode = ChatTextMode.EXACT, pattern = "hello"))
        assertTrue(match(line, mode = ChatTextMode.STARTS_WITH, pattern = "HELLO"))
        assertFalse(match(line, mode = ChatTextMode.STARTS_WITH, pattern = "World"))
        assertTrue(match(line, mode = ChatTextMode.REGEX, pattern = "^hel+o\\s\\w+$"))
    }

    @Test
    fun caseSensitivityIsRespected() {
        val line = "Party > Name: Hello"
        assertFalse(match(line, mode = ChatTextMode.CONTAINS, pattern = "hello", ignoreCase = false))
        assertTrue(match(line, mode = ChatTextMode.CONTAINS, pattern = "Hello", ignoreCase = false))
        assertFalse(match(line, mode = ChatTextMode.REGEX, pattern = "hello", ignoreCase = false))
        assertTrue(match(line, mode = ChatTextMode.REGEX, pattern = "hello", ignoreCase = true))
    }

    @Test
    fun emptyPatternNeverMatches() {
        val line = "Party > Name: hello"
        for (mode in ChatTextMode.entries) {
            assertFalse(match(line, mode = mode, pattern = ""), mode.name)
            assertFalse(match(line, mode = mode, pattern = "   "), mode.name)
        }
    }

    @Test
    fun invalidRegexDoesNotMatch() {
        val line = "Party > Name: hello ("
        assertFalse(match(line, mode = ChatTextMode.REGEX, pattern = "(unclosed"))
        assertFalse(ChatMessageMatch.validRegex("(unclosed"))
        assertTrue(ChatMessageMatch.validRegex("h.llo"))
        assertFalse(ChatMessageMatch.validRegex(""))
    }

    @Test
    fun longPatternIsRejected() {
        val long = "a".repeat(201)
        assertFalse(ChatMessageMatch.validRegex(long))
        assertFalse(match("Party > Name: aaaa", mode = ChatTextMode.REGEX, pattern = long))
        assertTrue(ChatMessageMatch.validRegex("a".repeat(200)))
    }

    @Test
    fun regexCacheStillMatchesAfterManyPatterns() {
        val line = "Party > Name: hello 7"
        repeat(40) { i -> match(line, mode = ChatTextMode.REGEX, pattern = "x$i") }
        assertTrue(match(line, mode = ChatTextMode.REGEX, pattern = "\\d$"))
    }
}
