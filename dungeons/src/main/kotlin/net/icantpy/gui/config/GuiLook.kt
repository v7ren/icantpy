package net.icantpy.gui.config

import com.google.gson.JsonObject
import net.icantpy.util.HexColor

enum class GuiLayoutId {
    RAIL,
    RIBBON,
    COMPACT,
    ;

    fun label(): String = when (this) {
        RAIL -> "Rail"
        RIBBON -> "Ribbon"
        COMPACT -> "Compact"
    }

    fun caption(): String = when (this) {
        RAIL -> "Left nav, grouped cards"
        RIBBON -> "Top tabs, full-width rows"
        COMPACT -> "Icon strip, dense list"
    }

    companion object {
        fun parse(raw: String?): GuiLayoutId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: RAIL
    }
}

enum class GuiThemeId {
    MIDNIGHT,
    PHOSPHOR,
    PAPER,
    ;

    fun label(): String = when (this) {
        MIDNIGHT -> "Midnight"
        PHOSPHOR -> "Phosphor"
        PAPER -> "Paper"
    }

    fun caption(): String = when (this) {
        MIDNIGHT -> "Black shell, white accent"
        PHOSPHOR -> "Black terminal, green"
        PAPER -> "Light page, ink"
    }

    companion object {
        fun parse(raw: String?): GuiThemeId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MIDNIGHT
    }
}

enum class GuiFontId {
    SEGOE,
    TAHOMA,
    VERDANA,
    ARIAL,
    CALIBRI,
    CONSOLAS,
    CASCADIA,
    YAHEI,
    NUNITO,
    ;

    fun label(): String = when (this) {
        SEGOE -> "Segoe UI"
        TAHOMA -> "Tahoma"
        VERDANA -> "Verdana"
        ARIAL -> "Arial"
        CALIBRI -> "Calibri"
        CONSOLAS -> "Consolas"
        CASCADIA -> "Cascadia Mono"
        YAHEI -> "Microsoft JhengHei"
        NUNITO -> "Nunito"
    }

    fun next(): GuiFontId = entries[(ordinal + 1) % entries.size]

    fun previous(): GuiFontId {
        val last = entries.size - 1
        return entries[if (ordinal == 0) last else ordinal - 1]
    }

    companion object {
        fun parse(raw: String?): GuiFontId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SEGOE
    }
}

data class GuiLookSettings(
    val layout: GuiLayoutId = GuiLayoutId.RAIL,
    val theme: GuiThemeId = GuiThemeId.MIDNIGHT,
    val font: GuiFontId = GuiFontId.SEGOE,
    val accentHex: String = "",
    val radius: Int = 2,
    val controlRadius: Int = 0,
    val backgroundBlur: Boolean = false,
) {
    fun resolvedAccent(): Int? =
        accentHex.trim().takeIf { it.isNotEmpty() }?.let { HexColor.parse(it).getOrNull() }

    fun withRadius(value: Int): GuiLookSettings = copy(radius = value.coerceIn(0, 12))

    fun withControlRadius(value: Int): GuiLookSettings = copy(controlRadius = value.coerceIn(0, 12))

    companion object {
        val RADIUS_PRESETS: List<Int> = listOf(0, 2, 4, 8)

        fun fromJson(obj: JsonObject?): GuiLookSettings {
            if (obj == null) return GuiLookSettings()
            return GuiLookSettings(
                layout = GuiLayoutId.parse(string(obj, "layout")),
                theme = GuiThemeId.parse(string(obj, "theme")),
                font = GuiFontId.parse(string(obj, "font")),
                accentHex = string(obj, "accent").orEmpty(),
                radius = int(obj, "radius", 2).coerceIn(0, 12),
                controlRadius = int(obj, "controlRadius", 0).coerceIn(0, 12),
                backgroundBlur = bool(obj, "blur", false),
            )
        }

        fun toJson(look: GuiLookSettings): JsonObject {
            val obj = JsonObject()
            obj.addProperty("layout", look.layout.name.lowercase())
            obj.addProperty("theme", look.theme.name.lowercase())
            obj.addProperty("font", look.font.name.lowercase())
            obj.addProperty("accent", look.accentHex)
            obj.addProperty("radius", look.radius)
            obj.addProperty("controlRadius", look.controlRadius)
            obj.addProperty("blur", look.backgroundBlur)
            return obj
        }

        private fun string(obj: JsonObject, key: String): String? =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else null

        private fun int(obj: JsonObject, key: String, default: Int): Int =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asInt else default

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asBoolean else default
    }
}

internal data class GuiPalette(
    val scrim: Int,
    val window: Int,
    val sidebar: Int,
    val header: Int,
    val group: Int,
    val rowHover: Int,
    val text: Int,
    val secondary: Int,
    val tertiary: Int,
    val accent: Int,
    val accentSoft: Int,
    val accentInk: Int,
    val line: Int,
    val trackOff: Int,
    val knob: Int,
    val danger: Int,
    val dangerSoft: Int,
    val field: Int,
)

internal data class GuiChrome(
    val palette: GuiPalette,
    val typefaces: GuiTypefaces,
    val layout: GuiLayoutId,
    val radius: Float,
    val controlRadius: Float,
) {
    val cardRadius: Float
        get() = when (layout) {
            GuiLayoutId.RAIL -> radius
            GuiLayoutId.RIBBON -> 0f
            GuiLayoutId.COMPACT -> 0f
        }

    companion object {
        fun of(look: GuiLookSettings): GuiChrome = GuiChrome(
            palette = look.theme.palette(look.resolvedAccent()),
            typefaces = GuiFonts.of(look.font),
            layout = look.layout,
            radius = look.radius.toFloat(),
            controlRadius = look.controlRadius.toFloat(),
        )
    }
}

internal data class ConfigMetrics(
    val screenW: Int,
    val screenH: Int,
    val panelX: Int,
    val panelY: Int,
    val panelW: Int,
    val panelH: Int,
    val header: Int,
    val footer: Int,
    val sidebar: Int,
    val tabBar: Int,
    val pageBar: Int,
    val row: Int,
) {
    val viewTop: Int get() = panelY + header + tabBar + pageBar
    val viewBottom: Int get() = panelY + panelH - footer
    val viewHeight: Int get() = (viewBottom - viewTop).coerceAtLeast(1)
    val contentLeft: Int get() = panelX + sidebar
    val innerLeft: Int get() = contentLeft + if (sidebar == 0) 18 else 16
    val innerWidth: Int get() = panelW - sidebar - if (sidebar == 0) 36 else 32
    fun rightX(controlWidth: Int): Int = panelX + panelW - 24 - controlWidth

    fun railNavPitch(): Int {
        val available = panelH - header - 12
        return (available / ConfigTab.entries.size).coerceIn(28, 48)
    }

    fun compactNavPitch(): Int {
        val available = panelH - 16
        return (available / ConfigTab.entries.size).coerceIn(24, 42)
    }

    companion object {
        fun compute(screenW: Int, screenH: Int, look: GuiLookSettings): ConfigMetrics {
            val panelW = (screenW - 28).coerceAtMost(760).coerceAtLeast(minOf(520, screenW - 16))
            val panelH = (screenH - 24).coerceAtMost(500).coerceAtLeast(minOf(340, screenH - 14))
            val panelX = (screenW - panelW) / 2
            val panelY = (screenH - panelH) / 2
            return when (look.layout) {
                GuiLayoutId.RAIL -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 44, footer = 28, sidebar = 160, tabBar = 0, pageBar = 32, row = 46,
                )
                GuiLayoutId.RIBBON -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 44, footer = 28, sidebar = 0, tabBar = 34, pageBar = 32, row = 44,
                )
                GuiLayoutId.COMPACT -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 40, footer = 28, sidebar = 52, tabBar = 0, pageBar = 28, row = 38,
                )
            }
        }
    }
}

internal fun GuiThemeId.palette(accentRgb: Int?): GuiPalette {
    val base = when (this) {
        GuiThemeId.MIDNIGHT -> GuiPalette(
            scrim = 0xF0000000.toInt(),
            window = 0xFF09090B.toInt(),
            sidebar = 0xFF09090B.toInt(),
            header = 0xFF09090B.toInt(),
            group = 0xFF121215.toInt(),
            rowHover = 0xFF18181B.toInt(),
            text = 0xFFFAFAFA.toInt(),
            secondary = 0xFFA1A1AA.toInt(),
            tertiary = 0xFF52525B.toInt(),
            accent = 0xFFFAFAFA.toInt(),
            accentSoft = 0xFF121215.toInt(),
            accentInk = 0xFF000000.toInt(),
            line = 0xFF27272A.toInt(),
            trackOff = 0xFF09090B.toInt(),
            knob = 0xFF52525B.toInt(),
            danger = 0xFFA1A1AA.toInt(),
            dangerSoft = 0xFF18181B.toInt(),
            field = 0xFF09090B.toInt(),
        )
        GuiThemeId.PHOSPHOR -> GuiPalette(
            scrim = 0xC2000000.toInt(),
            window = 0xF2050705.toInt(),
            sidebar = 0xF2030503.toInt(),
            header = 0xF2080C08.toInt(),
            group = 0xE00A100A.toInt(),
            rowHover = 0x2233FF88,
            text = 0xFFD8FFE6.toInt(),
            secondary = 0xFF7CB894.toInt(),
            tertiary = 0xFF4E7A5E.toInt(),
            accent = 0xFF3DFF8A.toInt(),
            accentSoft = 0x333DFF8A,
            accentInk = 0xFF031208.toInt(),
            line = 0xFF1C3A24.toInt(),
            trackOff = 0xFF152018.toInt(),
            knob = 0xFFE8FFEE.toInt(),
            danger = 0xFFFF6B6B.toInt(),
            dangerSoft = 0x33FF6B6B,
            field = 0xE0060A06.toInt(),
        )
        GuiThemeId.PAPER -> GuiPalette(
            scrim = 0x66000000,
            window = 0xFFF6F3EC.toInt(),
            sidebar = 0xFFEDE8DC.toInt(),
            header = 0xFFE7E1D4.toInt(),
            group = 0xFFF1ECE3.toInt(),
            rowHover = 0x14000000,
            text = 0xFF1B1B1B.toInt(),
            secondary = 0xFF5C584F.toInt(),
            tertiary = 0xFF8A8478.toInt(),
            accent = 0xFF1F5EFF.toInt(),
            accentSoft = 0x221F5EFF,
            accentInk = 0xFFFFFFFF.toInt(),
            line = 0xFFD4CFC3.toInt(),
            trackOff = 0xFFC9C3B6.toInt(),
            knob = 0xFFFFFFFF.toInt(),
            danger = 0xFFC62828.toInt(),
            dangerSoft = 0x22C62828,
            field = 0xFFFFFFFF.toInt(),
        )
    }
    val rgb = accentRgb ?: return base
    val accent = rgb or 0xFF000000.toInt()
    return base.copy(
        accent = accent,
        accentSoft = (accent and 0x00FFFFFF) or 0x33000000,
        accentInk = inkOn(accent),
    )
}

private fun inkOn(accent: Int): Int {
    val r = (accent shr 16) and 0xFF
    val g = (accent shr 8) and 0xFF
    val b = accent and 0xFF
    val lum = (r * 299 + g * 587 + b * 114) / 1000
    return if (lum > 160) 0xFF101418.toInt() else 0xFFF7F7F7.toInt()
}
