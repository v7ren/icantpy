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
    GLASS_DARK,
    GLASS_LIGHT,
    MIDNIGHT,
    PHOSPHOR,
    PAPER,
    ;

    fun label(): String = when (this) {
        GLASS_DARK -> "Glass dark"
        GLASS_LIGHT -> "Glass light"
        MIDNIGHT -> "Midnight"
        PHOSPHOR -> "Phosphor"
        PAPER -> "Paper"
    }

    fun caption(): String = when (this) {
        GLASS_DARK -> "Frosted grey, soft white"
        GLASS_LIGHT -> "Frosted white, ink"
        MIDNIGHT -> "Black shell, white accent"
        PHOSPHOR -> "Black terminal, green"
        PAPER -> "Light page, ink"
    }

    companion object {
        fun parse(raw: String?): GuiThemeId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GLASS_DARK
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
    val theme: GuiThemeId = GuiThemeId.GLASS_DARK,
    val font: GuiFontId = GuiFontId.SEGOE,
    val accentHex: String = "",
    val radius: Int = 12,
    val controlRadius: Int = 8,
    val backgroundBlur: Boolean = true,
    /** Window opacity in percent for the glass themes. */
    val opacity: Int = DEFAULT_OPACITY,
) {
    fun resolvedAccent(): Int? =
        accentHex.trim().takeIf { it.isNotEmpty() }?.let { HexColor.parse(it).getOrNull() }

    fun withOpacity(value: Int): GuiLookSettings = copy(opacity = value.coerceIn(MIN_OPACITY, MAX_OPACITY))

    fun withRadius(value: Int): GuiLookSettings = copy(radius = value.coerceIn(0, 12))

    fun withControlRadius(value: Int): GuiLookSettings = copy(controlRadius = value.coerceIn(0, 12))

    companion object {
        val RADIUS_PRESETS: List<Int> = listOf(0, 4, 8, 12)

        const val MIN_OPACITY = 25
        const val MAX_OPACITY = 95
        const val DEFAULT_OPACITY = 62

        /** Saved looks from before the glass design (no "design" key) move to the glass dark theme once. */
        private const val DESIGN_VERSION = 2

        fun fromJson(obj: JsonObject?): GuiLookSettings {
            if (obj == null) return GuiLookSettings()
            return GuiLookSettings(
                layout = GuiLayoutId.parse(string(obj, "layout")),
                theme = if (int(obj, "design", 1) < DESIGN_VERSION) GuiThemeId.GLASS_DARK else GuiThemeId.parse(string(obj, "theme")),
                font = GuiFontId.parse(string(obj, "font")),
                accentHex = string(obj, "accent").orEmpty(),
                radius = int(obj, "radius", 12).coerceIn(0, 12),
                controlRadius = int(obj, "controlRadius", 8).coerceIn(0, 12),
                backgroundBlur = bool(obj, "blur", true),
                opacity = int(obj, "opacity", DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY),
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
            obj.addProperty("opacity", look.opacity)
            obj.addProperty("design", DESIGN_VERSION)
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
    val activeAccent: Int,
    val accentSoft: Int,
    val accentInk: Int,
    val line: Int,
    val trackOff: Int,
    val knob: Int,
    val danger: Int,
    val dangerSoft: Int,
    val field: Int,
    /** Flat frosted look: no boxed rows, divider lines instead. */
    val glass: Boolean = false,
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
            GuiLayoutId.RAIL -> radius.coerceAtMost(10f)
            GuiLayoutId.RIBBON -> 0f
            GuiLayoutId.COMPACT -> 0f
        }

    companion object {
        fun of(look: GuiLookSettings): GuiChrome = GuiChrome(
            palette = look.theme.palette(look.resolvedAccent(), look.opacity),
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
    /** Caps the width of page rows so wide windows do not stretch controls across the panel. */
    val maxContentWidth: Int = Int.MAX_VALUE,
    /** Width of the right-hand "on this page" outline; 0 when the window is too narrow for it. */
    val toc: Int = 0,
) {
    val viewTop: Int get() = panelY + header + tabBar + pageBar
    val viewBottom: Int get() = panelY + panelH - footer
    val viewHeight: Int get() = (viewBottom - viewTop).coerceAtLeast(1)
    val contentLeft: Int get() = panelX + sidebar
    val innerLeft: Int get() = contentLeft + if (sidebar == 0) 18 else 16
    val innerWidth: Int get() = (panelW - sidebar - toc - if (sidebar == 0) 36 else 32).coerceAtMost(maxContentWidth)
    /** Right edge for a control, inset 8px from the row edge like the other row controls. */
    fun rightX(controlWidth: Int): Int = innerLeft + innerWidth - 8 - controlWidth

    companion object {
        fun compute(screenW: Int, screenH: Int, look: GuiLookSettings): ConfigMetrics {
            val panelW = (screenW - 36).coerceAtMost(700).coerceAtLeast(minOf(460, screenW - 16))
            val panelH = (screenH - 36).coerceAtMost(460).coerceAtLeast(minOf(300, screenH - 14))
            val panelX = (screenW - panelW) / 2
            val panelY = (screenH - panelH) / 2
            return when (look.layout) {
                GuiLayoutId.RAIL -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 50, footer = 0, sidebar = 156, tabBar = 0, pageBar = 30, row = 38,
                    toc = if (panelW >= TOC_MIN_PANEL_WIDTH) TOC_WIDTH else 0,
                )
                GuiLayoutId.RIBBON -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 34, footer = 0, sidebar = 0, tabBar = 30, pageBar = 30, row = 38,
                )
                GuiLayoutId.COMPACT -> ConfigMetrics(
                    screenW, screenH, panelX, panelY, panelW, panelH,
                    header = 32, footer = 0, sidebar = 42, tabBar = 0, pageBar = 28, row = 34,
                )
            }
        }
    }
}

private const val TOC_MIN_PANEL_WIDTH = 640
private const val TOC_WIDTH = 118

internal fun GuiThemeId.palette(accentRgb: Int?, opacity: Int = GuiLookSettings.DEFAULT_OPACITY): GuiPalette {
    val windowAlpha = (opacity.coerceIn(GuiLookSettings.MIN_OPACITY, GuiLookSettings.MAX_OPACITY) * 255 / 100) shl 24
    val base = when (this) {
        GuiThemeId.GLASS_DARK -> GuiPalette(
            scrim = 0x59000000,
            window = windowAlpha or 0x1E2026,
            sidebar = 0x09FFFFFF,
            header = 0x06FFFFFF,
            group = 0x0DFFFFFF,
            rowHover = 0x14FFFFFF,
            text = 0xFFEEF0F4.toInt(),
            secondary = 0xFF9AA1AE.toInt(),
            tertiary = 0xFF6F7684.toInt(),
            accent = 0xFFD5D9E1.toInt(),
            activeAccent = 0xFFD5D9E1.toInt(),
            accentSoft = 0x1FFFFFFF,
            accentInk = 0xFF14161A.toInt(),
            line = 0x17FFFFFF,
            trackOff = 0x33FFFFFF,
            knob = 0xFFFFFFFF.toInt(),
            danger = 0xFFF87171.toInt(),
            dangerSoft = 0x33F87171,
            field = 0x14FFFFFF,
            glass = true,
        )
        GuiThemeId.GLASS_LIGHT -> GuiPalette(
            scrim = 0x40000000,
            window = windowAlpha or 0xFFFFFF,
            sidebar = 0x66F0F2F6,
            header = 0x4DFFFFFF,
            group = 0x66FFFFFF,
            rowHover = 0x14141928,
            text = 0xFF12141A.toInt(),
            secondary = 0xFF454B58.toInt(),
            tertiary = 0xFF5E6575.toInt(),
            accent = 0xFF2B2E36.toInt(),
            activeAccent = 0xFF2B2E36.toInt(),
            accentSoft = 0x1A141928,
            accentInk = 0xFFFFFFFF.toInt(),
            line = 0x1A141928,
            trackOff = 0x403C4254,
            knob = 0xFFFFFFFF.toInt(),
            danger = 0xFFC62828.toInt(),
            dangerSoft = 0x22C62828,
            field = 0x99FFFFFF.toInt(),
            glass = true,
        )

        GuiThemeId.MIDNIGHT -> GuiPalette(
            // Aether reference: charcoal glass, lavender primary, and cyan/pink ambient accents.
            scrim = 0xF0050607.toInt(),
            window = 0xF01B1D22.toInt(),
            sidebar = 0xF00C0D0F.toInt(),
            header = 0xC21A1D23.toInt(),
            group = 0xE81F2125.toInt(),
            rowHover = 0xF026282D.toInt(),
            text = 0xFFEEF0F3.toInt(),
            secondary = 0xFFA1A6AE.toInt(),
            tertiary = 0xFF696E77.toInt(),
            accent = 0xFFC9BBFF.toInt(),
            activeAccent = 0xFFA68BF0.toInt(),
            accentSoft = 0x2BA589F0,
            accentInk = 0xFFF5F1FF.toInt(),
            line = 0x14FFFFFF,
            trackOff = 0xFF23272A.toInt(),
            knob = 0xFF9496A1.toInt(),
            danger = 0xFFF87171.toInt(),
            dangerSoft = 0x33F87171,
            field = 0xFF15171B.toInt(),
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
            activeAccent = 0xFF3DFF8A.toInt(),
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
            secondary = 0xFF47433B.toInt(),
            tertiary = 0xFF6B665A.toInt(),
            accent = 0xFF1F5EFF.toInt(),
            activeAccent = 0xFF1F5EFF.toInt(),
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
        activeAccent = accent,
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
