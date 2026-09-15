package net.icantpy.api

object IcantpyCommandPrefix {
    val prefixes: List<String> = listOf(
        ".icantpy",
        ",icantpy",
        ".crypt",
        ",crypt",
        "/icantpy",
        "/crypt",
        ".neurename",
        ",neurename",
        "/neurename",
    )

    fun body(raw: String): String? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length).trim()
            }
        }
        return null
    }

    fun canonical(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val dotted = when {
            trimmed.startsWith("/") -> ".${trimmed.drop(1).trim()}"
            trimmed.startsWith(",") -> ".${trimmed.drop(1).trim()}"
            trimmed.startsWith(".") -> ".${trimmed.drop(1).trim()}"
            else -> {
                val lower = trimmed.lowercase()
                if (lower == "icantpy" || lower.startsWith("icantpy ")) ".$trimmed" else return null
            }
        }
        return dotted.takeIf { body(it) != null }
    }

    fun toDotMessage(name: String, rest: String = ""): String {
        val command = name.trim().trimStart('/')
        val body = rest.trim()
        return if (body.isEmpty()) ".$command" else ".$command $body"
    }
}
