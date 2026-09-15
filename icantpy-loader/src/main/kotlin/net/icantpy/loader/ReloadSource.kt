package net.icantpy.loader

enum class ReloadSource {
    LOCAL,
    UPDATE,
    ;

    fun toggle(): ReloadSource = when (this) {
        LOCAL -> UPDATE
        UPDATE -> LOCAL
    }

    companion object {
        fun fromName(value: String?): ReloadSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UPDATE
    }
}
