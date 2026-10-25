package net.icantpy.dungeon.terminal

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.util.HexColor
import kotlin.math.roundToInt

/**
 * JSON codec for [TerminalSettings]. [decode] returns null for malformed input (not an object, or a field with the
 * wrong type); missing fields take their defaults, numbers are clamped and colours are "#RRGGBB" strings.
 */
object TerminalSettingsCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(settings: TerminalSettings): String {
        val json = JsonObject()
        json.addProperty("enabled", settings.enabled)
        json.addProperty("panes", settings.panes)
        json.addProperty("inOrder", settings.inOrder)
        json.addProperty("startsWith", settings.startsWith)
        json.addProperty("selectColor", settings.selectColor)
        json.addProperty("rubix", settings.rubix)
        json.addProperty("melody", settings.melody)
        json.addProperty("scale", settings.scale)
        json.addProperty("gap", settings.gap)
        json.addProperty("offsetX", settings.offsetX)
        json.addProperty("offsetY", settings.offsetY)
        json.addProperty("showTitle", settings.showTitle)
        json.addProperty("showRemaining", settings.showRemaining)
        json.addProperty("showItems", settings.showItems)
        json.addProperty("hideWrong", settings.hideWrong)
        json.addProperty("hideDone", settings.hideDone)
        json.addProperty("clickButton", settings.clickButton.name)
        json.addProperty("blockWrongClicks", settings.blockWrongClicks)
        json.addProperty("predictClicks", settings.predictClicks)
        json.addProperty("rubixDisplay", settings.rubixDisplay.name)
        json.addProperty("panelColor", HexColor.format(settings.panelRgb))
        json.addProperty("panelAlpha", settings.panelAlpha)
        json.addProperty("targetColor", HexColor.format(settings.targetRgb))
        json.addProperty("doneColor", HexColor.format(settings.doneRgb))
        json.addProperty("wrongColor", HexColor.format(settings.wrongRgb))
        json.addProperty("orderSecondColor", HexColor.format(settings.orderSecondRgb))
        json.addProperty("orderThirdColor", HexColor.format(settings.orderThirdRgb))
        json.addProperty("rubixLeftColor", HexColor.format(settings.rubixLeftRgb))
        json.addProperty("rubixRightColor", HexColor.format(settings.rubixRightRgb))
        json.addProperty("textColor", HexColor.format(settings.textRgb))
        json.addProperty("cellStyle", settings.cellStyle.name)
        json.addProperty("cellAlpha", settings.cellAlpha)
        json.addProperty("cellRadius", settings.cellRadius)
        json.addProperty("outlineWidth", settings.outlineWidth)
        json.addProperty("panelRadius", settings.panelRadius)
        json.addProperty("panelBorder", settings.panelBorder)
        json.addProperty("borderColor", HexColor.format(settings.borderRgb))
        json.addProperty("textScale", settings.textScale)
        json.addProperty("font", settings.font.name)
        json.addProperty("customFont", settings.customFont)
        json.addProperty("customFontSize", settings.customFontSize)
        json.addProperty("customFontShift", settings.customFontShift)
        json.addProperty("textShadow", settings.textShadow)
        json.addProperty("showHover", settings.showHover)
        json.addProperty("hoverColor", HexColor.format(settings.hoverRgb))
        json.addProperty("pulseNext", settings.pulseNext)
        json.addProperty("fadeDone", settings.fadeDone)
        json.addProperty("headerPosition", settings.headerPosition.name)
        json.addProperty("titleAlign", settings.titleAlign.name)
        json.addProperty("padding", settings.padding)
        json.addProperty("screenDim", settings.screenDim)
        json.addProperty("firstClickDelayMs", settings.firstClickDelayMs)
        return gson.toJson(json)
    }

    fun decode(text: String): TerminalSettings? {
        val root = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return null
        }
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val d = TerminalSettings()
        val offset = TerminalSettings.MAX_OFFSET
        return TerminalSettings(
            enabled = bool(obj, "enabled", d.enabled) ?: return null,
            panes = bool(obj, "panes", d.panes) ?: return null,
            inOrder = bool(obj, "inOrder", d.inOrder) ?: return null,
            startsWith = bool(obj, "startsWith", d.startsWith) ?: return null,
            selectColor = bool(obj, "selectColor", d.selectColor) ?: return null,
            rubix = bool(obj, "rubix", d.rubix) ?: return null,
            melody = bool(obj, "melody", d.melody) ?: return null,
            scale = float(obj, "scale", d.scale, TerminalSettings.MIN_SCALE, TerminalSettings.MAX_SCALE) ?: return null,
            gap = int(obj, "gap", d.gap, TerminalSettings.MIN_GAP, TerminalSettings.MAX_GAP) ?: return null,
            offsetX = int(obj, "offsetX", d.offsetX, -offset, offset) ?: return null,
            offsetY = int(obj, "offsetY", d.offsetY, -offset, offset) ?: return null,
            showTitle = bool(obj, "showTitle", d.showTitle) ?: return null,
            showRemaining = bool(obj, "showRemaining", d.showRemaining) ?: return null,
            showItems = bool(obj, "showItems", d.showItems) ?: return null,
            hideWrong = bool(obj, "hideWrong", d.hideWrong) ?: return null,
            hideDone = bool(obj, "hideDone", d.hideDone) ?: return null,
            clickButton = enum(obj, "clickButton", d.clickButton) ?: return null,
            blockWrongClicks = bool(obj, "blockWrongClicks", d.blockWrongClicks) ?: return null,
            predictClicks = bool(obj, "predictClicks", d.predictClicks) ?: return null,
            rubixDisplay = enum(obj, "rubixDisplay", d.rubixDisplay) ?: return null,
            panelRgb = color(obj, "panelColor", d.panelRgb) ?: return null,
            panelAlpha = float(obj, "panelAlpha", d.panelAlpha, 0f, 1f) ?: return null,
            targetRgb = color(obj, "targetColor", d.targetRgb) ?: return null,
            doneRgb = color(obj, "doneColor", d.doneRgb) ?: return null,
            wrongRgb = color(obj, "wrongColor", d.wrongRgb) ?: return null,
            orderSecondRgb = color(obj, "orderSecondColor", d.orderSecondRgb) ?: return null,
            orderThirdRgb = color(obj, "orderThirdColor", d.orderThirdRgb) ?: return null,
            rubixLeftRgb = color(obj, "rubixLeftColor", d.rubixLeftRgb) ?: return null,
            rubixRightRgb = color(obj, "rubixRightColor", d.rubixRightRgb) ?: return null,
            textRgb = color(obj, "textColor", d.textRgb) ?: return null,
            cellStyle = enum(obj, "cellStyle", d.cellStyle) ?: return null,
            cellAlpha = float(obj, "cellAlpha", d.cellAlpha, 0f, 1f) ?: return null,
            cellRadius = int(obj, "cellRadius", d.cellRadius, 0, TerminalSettings.MAX_CELL_RADIUS) ?: return null,
            outlineWidth = int(obj, "outlineWidth", d.outlineWidth, TerminalSettings.MIN_OUTLINE, TerminalSettings.MAX_OUTLINE) ?: return null,
            panelRadius = int(obj, "panelRadius", d.panelRadius, 0, TerminalSettings.MAX_PANEL_RADIUS) ?: return null,
            panelBorder = bool(obj, "panelBorder", d.panelBorder) ?: return null,
            borderRgb = color(obj, "borderColor", d.borderRgb) ?: return null,
            textScale = float(obj, "textScale", d.textScale, TerminalSettings.MIN_TEXT_SCALE, TerminalSettings.MAX_TEXT_SCALE) ?: return null,
            textShadow = bool(obj, "textShadow", d.textShadow) ?: return null,
            showHover = bool(obj, "showHover", d.showHover) ?: return null,
            hoverRgb = color(obj, "hoverColor", d.hoverRgb) ?: return null,
            pulseNext = bool(obj, "pulseNext", d.pulseNext) ?: return null,
            fadeDone = bool(obj, "fadeDone", d.fadeDone) ?: return null,
            headerPosition = enum(obj, "headerPosition", d.headerPosition) ?: return null,
            titleAlign = enum(obj, "titleAlign", d.titleAlign) ?: return null,
            font = enum(obj, "font", d.font) ?: return null,
            customFont = fontName(obj, "customFont", d.customFont) ?: return null,
            customFontSize = float(obj, "customFontSize", d.customFontSize, TerminalSettings.MIN_CUSTOM_FONT_SIZE, TerminalSettings.MAX_CUSTOM_FONT_SIZE) ?: return null,
            customFontShift = float(obj, "customFontShift", d.customFontShift, -TerminalSettings.MAX_CUSTOM_FONT_SHIFT, TerminalSettings.MAX_CUSTOM_FONT_SHIFT) ?: return null,
            padding = int(obj, "padding", d.padding, 0, TerminalSettings.MAX_PADDING) ?: return null,
            screenDim = float(obj, "screenDim", d.screenDim, 0f, 1f) ?: return null,
            firstClickDelayMs = int(obj, "firstClickDelayMs", d.firstClickDelayMs, 0, TerminalSettings.MAX_FIRST_CLICK_DELAY_MS) ?: return null,
        )
    }

    /** Missing key gives [default]; a present value of the wrong type gives null (malformed). */
    private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean? {
        val value = obj.get(key) ?: return default
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
        return value.asBoolean
    }

    private fun int(obj: JsonObject, key: String, default: Int, min: Int, max: Int): Int? {
        val value = obj.get(key) ?: return default
        if (!isNumber(value)) return null
        return value.asDouble.roundToInt().coerceIn(min, max)
    }

    private fun float(obj: JsonObject, key: String, default: Float, min: Float, max: Float): Float? {
        val value = obj.get(key) ?: return default
        if (!isNumber(value)) return null
        return value.asFloat.coerceIn(min, max)
    }

    /** A bare file name; anything that looks like a path is dropped rather than rejecting the file. */
    private fun fontName(obj: JsonObject, key: String, default: String): String? {
        val value = obj.get(key) ?: return default
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        val name = value.asString
        val isBareName = name.length <= TerminalSettings.MAX_FONT_NAME && name.none { it == '/' || it == '\\' } && name != ".."
        return if (isBareName) name else ""
    }

    /** An unknown enum name keeps the default instead of rejecting the whole file. */
    private inline fun <reified E : Enum<E>> enum(obj: JsonObject, key: String, default: E): E? {
        val value = obj.get(key) ?: return default
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return enumValues<E>().firstOrNull { it.name == value.asString } ?: default
    }

    private fun color(obj: JsonObject, key: String, default: Int): Int? {
        val value = obj.get(key) ?: return default
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return HexColor.parse(value.asString).getOrNull()
    }

    private fun isNumber(value: JsonElement): Boolean = value.isJsonPrimitive && value.asJsonPrimitive.isNumber
}
