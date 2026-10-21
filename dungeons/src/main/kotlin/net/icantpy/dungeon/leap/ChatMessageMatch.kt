package net.icantpy.dungeon.leap

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class ChatChannel(val label: String) { ANY("Any chat"), PARTY("Party"), GUILD("Guild"), PRIVATE("Private msg"), PUBLIC("All chat") }
enum class ChatSender(val label: String) { SELF("Me"), ANYONE("Anyone"), OTHERS("Others"), PLAYER("Player…") }
enum class ChatTextMode(val label: String) { CONTAINS("Contains"), EXACT("Exact"), STARTS_WITH("Starts with"), REGEX("Regex") }

data class ParsedChat(val channel: ChatChannel, val sender: String, val text: String)

object ChatMessageMatch {
    /** Sender marker used for outgoing private messages ("To [rank] Name: ..."). */
    const val SELF_MARKER = "@self"

    private const val MAX_REGEX_LENGTH = 200
    private const val REGEX_CACHE_SIZE = 32

    /** Leading level tags, rank tags ([VIP], [MVP++], ...) and emblem symbols (♲, ⚒, ✿ ...). */
    private const val PREFIX = """(?:(?:\[\d+\]|\[[A-Za-z]{1,12}\+{0,3}\]|[^\w\s\[\]:>.,]+)\s*)*"""
    private const val NAME = """(\w{1,16})"""

    private val publicRegex = Regex("""^$PREFIX$NAME:\s?(.*)$""")
    private val guildRegex = Regex("""^Guild > $PREFIX$NAME(?:\s*\[[^\]:]{1,20}\])?:\s?(.*)$""")
    private val partyRegex = Regex("""^Party > $PREFIX$NAME:\s?(.*)$""")
    private val coopRegex = Regex("""^Co-op > $PREFIX$NAME:\s?(.*)$""")
    private val fromRegex = Regex("""^From $PREFIX$NAME:\s?(.*)$""")
    private val toRegex = Regex("""^To $PREFIX$NAME:\s?(.*)$""")

    /** Lines that are system/NPC/boss output and must never count as player chat. */
    private val systemPrefixes = listOf(
        "[NPC]", "[BOSS]", "[STATUE]", "[SECURITY]", "[Auction]", "[Bazaar]", "Party Finder >",
    )

    private data class CacheKey(val pattern: String, val ignoreCase: Boolean)

    /** Bounded LRU of compiled user regexes. A null value caches an invalid pattern. */
    private val regexCache = object : LinkedHashMap<CacheKey, Pattern?>(REGEX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Pattern?>?): Boolean =
            size > REGEX_CACHE_SIZE
    }

    fun parse(line: String): ParsedChat? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (systemPrefixes.any { trimmed.startsWith(it) }) return null

        guildRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.GUILD, m.groupValues[1], m.groupValues[2].trim())
        }
        partyRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.PARTY, m.groupValues[1], m.groupValues[2].trim())
        }
        coopRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.PARTY, m.groupValues[1], m.groupValues[2].trim())
        }
        fromRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.PRIVATE, m.groupValues[1], m.groupValues[2].trim())
        }
        toRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.PRIVATE, SELF_MARKER, m.groupValues[2].trim())
        }
        publicRegex.matchEntire(trimmed)?.let { m ->
            return ParsedChat(ChatChannel.PUBLIC, m.groupValues[1], m.groupValues[2].trim())
        }
        return null
    }

    fun matches(
        chat: ParsedChat,
        selfName: String,
        sender: ChatSender,
        playerName: String,
        channel: ChatChannel,
        mode: ChatTextMode,
        pattern: String,
        ignoreCase: Boolean = true,
    ): Boolean {
        if (channel != ChatChannel.ANY && chat.channel != channel) return false
        if (!senderMatches(chat, selfName, sender, playerName)) return false
        return textMatches(chat.text.trim(), mode, pattern, ignoreCase)
    }

    /** True when the pattern compiles as a regex and is within the length limit. */
    fun validRegex(pattern: String): Boolean {
        if (pattern.isBlank() || pattern.length > MAX_REGEX_LENGTH) return false
        return compile(pattern, ignoreCase = false) != null
    }

    private fun senderMatches(chat: ParsedChat, selfName: String, sender: ChatSender, playerName: String): Boolean {
        val isSelf = chat.sender.equals(SELF_MARKER, ignoreCase = true) ||
            (selfName.isNotBlank() && chat.sender.equals(selfName.trim(), ignoreCase = true))
        return when (sender) {
            ChatSender.ANYONE -> true
            ChatSender.SELF -> isSelf
            ChatSender.OTHERS -> !isSelf
            ChatSender.PLAYER -> playerName.isNotBlank() && chat.sender.equals(playerName.trim(), ignoreCase = true)
        }
    }

    private fun textMatches(text: String, mode: ChatTextMode, pattern: String, ignoreCase: Boolean): Boolean {
        if (pattern.isBlank()) return false
        return when (mode) {
            ChatTextMode.CONTAINS -> text.contains(pattern.trim(), ignoreCase)
            ChatTextMode.EXACT -> text.equals(pattern.trim(), ignoreCase)
            ChatTextMode.STARTS_WITH -> text.startsWith(pattern.trim(), ignoreCase)
            ChatTextMode.REGEX -> {
                if (pattern.length > MAX_REGEX_LENGTH) return false
                val compiled = compile(pattern, ignoreCase) ?: return false
                compiled.matcher(text).find()
            }
        }
    }

    private fun compile(pattern: String, ignoreCase: Boolean): Pattern? {
        val key = CacheKey(pattern, ignoreCase)
        synchronized(regexCache) {
            if (regexCache.containsKey(key)) return regexCache[key]
            val compiled = try {
                val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0
                Pattern.compile(pattern, flags)
            } catch (e: PatternSyntaxException) {
                null
            }
            regexCache[key] = compiled
            return compiled
        }
    }
}
