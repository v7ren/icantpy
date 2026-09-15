package net.icantpy.gui.neurename

import net.icantpy.gui.McUi
import net.icantpy.modules.impl.appearance.CustomRename
import net.icantpy.modules.impl.appearance.CustomRenameEditorSession
import net.icantpy.modules.impl.appearance.CustomRenameText
import net.icantpy.modules.impl.appearance.ItemCustomizeBaseline
import net.icantpy.modules.impl.appearance.ItemCustomizeClock
import net.icantpy.modules.impl.appearance.ItemCustomizeColor
import net.icantpy.modules.impl.appearance.ItemCustomizeColorEvaluator
import net.icantpy.modules.impl.appearance.ItemCustomizeState
import net.icantpy.modules.impl.appearance.RenameTextEditor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW

/** 1:1 reproduction of NEU's GuiItemCustomize for modern Minecraft. */
class NeurenameScreen : Screen(Component.literal("NEU Item Customizer")) {
    private val session = CustomRenameEditorSession.current()
    private var state: ItemCustomizeState = session?.stack
        ?.let { CustomRename.editorState(it) }
        ?: ItemCustomizeState(ItemCustomizeBaseline(uuid = "", vanillaGlint = false))

    private var editor: RenameTextEditor = RenameTextEditor()
    private var nameFocused = false
    private var nameText = ""
    private var picker: NeurenameColourPicker? = null
    private var toggle: NeurenameToggle = NeurenameToggle(0, 0, state.effectiveGlint, ::setGlint)
    private var tooltipOpen = false
    private var tooltipEditor = RenameTextEditor()
    private var tooltipStripY = 0
    private var reveal = 0f
    private var lastFrame = 0L
    private var renderHeight = 0

    private var xCenter = 0
    private var yTopStart = 0
    private var nameX = 0
    private var nameY = 0
    private var nameW = 158
    private var helpX = 0
    private var glintStripY = 0
    private var leatherStripY = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        McUi.releaseMouse(Minecraft.getInstance())
        if (session == null) {
            onClose()
            return
        }
        lastFrame = System.currentTimeMillis()
        nameText = state.customName
        editor = RenameTextEditor().withText(nameText)
        tooltipEditor = RenameTextEditor().withText(state.customTooltip)
        toggle.setValue(state.effectiveGlint)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractTransparentBackground(graphics)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        updateReveal()

        xCenter = width / 2
        var yTop = (height - renderHeight) / 2
        yTopStart = yTop

        NeurenameDraw.floatingRectDark(graphics, xCenter - 100, yTop - 9, 200, renderHeight + 11)
        NeurenameDraw.floatingRectDark(graphics, xCenter - 90, yTop - 5, 180, 14)
        NeurenameDraw.centered(graphics, formatted("\u00A75\u00A7lNEU Item Customizer"), xCenter, yTop - 1 + 3, 0x404040, shadow = true)

        yTop += 14
        paintNameField(graphics, mouseX, mouseY, yTop)
        paintHelpTooltip(graphics, mouseX, mouseY)

        yTop += 25
        NeurenameDraw.floatingRectDark(graphics, xCenter - 90, yTop, 180, 110)
        paintPreview(graphics, xCenter - 48, yTop + 7)

        yTop += 115
        NeurenameDraw.floatingRectDark(graphics, xCenter - 90, yTop, 180, 20)
        NeurenameDraw.text(graphics, "Enchant Glint", xCenter - 85, yTop + 7, 0xFF8040CC.toInt())
        toggle.x = xCenter + 90 - 5 - NeurenameToggle.WIDTH
        toggle.y = yTop + 3
        toggle.render(graphics)

        yTop += 25
        glintStripY = yTop - 5
        if (reveal > 0f) {
            val glintColor = stripArgb(state.glintPickerSeed)
            val stripY = yTop - 5
            val dy = (reveal - 17f).toInt()
            val sy = stripY + dy
            graphics.enableScissor(0, stripY, width, height)
            graphics.fill(xCenter - 90, sy, xCenter + 92, sy + 17, 0x70000000)
            graphics.fill(xCenter - 90, sy, xCenter + 90, sy + 15, 0xFF101016.toInt())
            graphics.fill(xCenter - 89, sy + 1, xCenter + 89, sy + 14, glintColor)
            NeurenameDraw.centered(graphics, formatted("\u00A7a\u00A7lCustom Glint Colour"), xCenter, sy + 4 + 3, 0x404040, shadow = true)
            NeurenameDraw.texture(graphics, NeurenameAssets.reset, xCenter + 90 - 12, sy + 2, 10, 11)
            graphics.disableScissor()
            yTop += reveal.toInt() + 3
        }

        tooltipStripY = yTop
        if (state.leatherArmor) {
            val leatherColor = stripArgb(state.leatherPickerSeed ?: ItemCustomizeColor(0xA06540))
            graphics.fill(xCenter - 90, yTop, xCenter + 92, yTop + 17, 0x70000000)
            graphics.fill(xCenter - 90, yTop, xCenter + 90, yTop + 15, 0xFF101016.toInt())
            graphics.fill(xCenter - 89, yTop + 1, xCenter + 89, yTop + 14, leatherColor)
            NeurenameDraw.centered(graphics, formatted("\u00A7b\u00A7lCustom Leather Colour"), xCenter, yTop + 4 + 3, 0x404040, shadow = true)
            NeurenameDraw.texture(graphics, NeurenameAssets.reset, xCenter + 90 - 12, yTop + 2, 10, 11)
            yTop += 20
        }

        // Custom tooltip/lore row (icantpy extension).
        tooltipStripY = yTop
        graphics.fill(xCenter - 90, yTop, xCenter + 92, yTop + 17, 0x70000000)
        graphics.fill(xCenter - 90, yTop, xCenter + 90, yTop + 15, 0xFF101016.toInt())
        val tooltipPreview = state.customTooltip.lineSequence().firstOrNull().orEmpty().ifBlank { "\u00A78(none)" }
        NeurenameDraw.text(graphics, formatted(tooltipPreview), xCenter - 85, yTop + 4, 0xFFFFFFFF.toInt())
        graphics.fill(xCenter + 90 - 13, yTop, xCenter + 90, yTop + 15, 0x55000000)
        NeurenameDraw.texture(graphics, NeurenameAssets.reset, xCenter + 90 - 12, yTop + 2, 10, 11)
        yTop += 20

        renderHeight = yTop - yTopStart

        picker?.render(graphics)
        if (tooltipOpen) paintTooltipEditor(graphics)
    }

    private fun paintTooltipEditor(graphics: GuiGraphicsExtractor) {
        val panelW = 230
        val panelH = 120
        val px = (width - panelW) / 2
        val py = (height - panelH) / 2
        NeurenameDraw.floatingRectDark(graphics, px, py, panelW, panelH)
        NeurenameDraw.centered(graphics, formatted("\u00A7d\u00A7lCustom Tooltip"), width / 2, py + 8 + 3, 0x404040, shadow = true)
        graphics.fill(px + 8, py + 26, px + panelW - 8, py + panelH - 20, 0xFF000000.toInt())

        val font = Minecraft.getInstance().font
        val lines = tooltipEditor.text.split('\n')
        var cursorLine = 0
        var cursorCol = 0
        run {
            var remaining = tooltipEditor.cursor
            for ((index, line) in lines.withIndex()) {
                if (remaining <= line.length) {
                    cursorLine = index
                    cursorCol = remaining
                    return@run
                }
                remaining -= line.length + 1
            }
            cursorLine = lines.lastIndex
            cursorCol = lines.lastOrNull()?.length ?: 0
        }
        graphics.enableScissor(px + 8, py + 26, px + panelW - 8, py + panelH - 20)
        lines.forEachIndexed { index, line ->
            val y = py + 30 + index * 10
            if (y > py + panelH - 24) return@forEachIndexed
            graphics.text(font, formatted(line.ifEmpty { " " }), px + 12, y, 0xFFFFFFFF.toInt())
        }
        graphics.disableScissor()
        if (System.currentTimeMillis() % 1000 > 500) {
            val colText = lines.getOrElse(cursorLine) { "" }.take(cursorCol)
            val cx = px + 12 + font.width(formatted(colText))
            val cy = py + 29 + cursorLine * 10
            graphics.fill(cx, cy, cx + 1, cy + 10, 0xFFFFFFFF.toInt())
        }
        NeurenameDraw.text(graphics, "\u00A78&& colour codes, Enter = new line, Esc = done", px + 8, py + panelH - 14, 0xFF808080.toInt())
    }

    private fun paintNameField(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, y: Int) {
        nameY = y
        val focused = nameFocused
        val shown = if (focused) markerDisplay(editor.text) else markerDisplay(nameText)
        nameW = if (focused) maxOf(158, NeurenameDraw.width(shown) + 10) else 158
        nameX = xCenter - nameW / 2 - 10

        val border = if (focused) 0xFF55FF55.toInt() else 0xFFFFFFFF.toInt()
        graphics.fill(nameX - 1, y - 1, nameX + nameW + 1, y + 21, border)
        graphics.fill(nameX, y, nameX + nameW, y + 20, 0xFF000000.toInt())

        if (!focused && nameText.isEmpty()) {
            NeurenameDraw.text(graphics, "Enter Custom Name...", nameX + 5, y + 6, 0xFF808080.toInt())
        } else {
            graphics.enableScissor(nameX + 5, 0, nameX + nameW, height)
            NeurenameDraw.text(graphics, component(shown), nameX + 5, y + 6, 0xFFFFFFFF.toInt(), false)
            graphics.disableScissor()
        }

        if (focused && System.currentTimeMillis() % 1000 > 500) {
            val cursorText = markerDisplay(editor.text.substring(0, editor.cursor))
            graphics.fill(nameX + 5 + NeurenameDraw.width(cursorText), y + 5, nameX + 6 + NeurenameDraw.width(cursorText), y + 15, 0xFFFFFFFF.toInt())
        }

        helpX = xCenter + nameW / 2 - 5
        NeurenameDraw.texture(graphics, NeurenameAssets.help, helpX, y, 20, 20)
    }

    private fun paintPreview(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val stack = session?.stack ?: return
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(6f, 6f)
        graphics.item(stack, 0, 0)
        pose.popMatrix()
    }

    private fun paintHelpTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (mouseX < helpX || mouseX > helpX + 20 || mouseY < nameY || mouseY > nameY + 20) return
        val lines = listOf(
            formatted("\u00A7bSet a custom name for the item"),
            formatted("\u00A7a"),
            formatted("\u00A7aType \"&&\" to use colour codes"),
            formatted("\u00A7aType \"**\" for \u272A"),
            formatted("\u00A7aType \"*1-9\" for \u278A-\u2792"),
            formatted("\u00A7a"),
            formatted("\u00A7aAvailable colour codes:"),
            formatted("\u00A7a\u00B6z = Chroma"),
            formatted("\u00A71\u00B61 = Dark Blue"),
            formatted("\u00A72\u00B62 = Dark Green"),
            formatted("\u00A73\u00B63 = Dark Aqua"),
            formatted("\u00A74\u00B64 = Dark Red"),
            formatted("\u00A75\u00B65 = Dark Purple"),
            formatted("\u00A76\u00B66 = Gold"),
            formatted("\u00A77\u00B67 = Gray"),
            formatted("\u00A78\u00B68 = Dark Gray"),
            formatted("\u00A79\u00B69 = Blue"),
            formatted("\u00A7a\u00B6a = Green"),
            formatted("\u00A7b\u00B6b = Aqua"),
            formatted("\u00A7c\u00B6c = Red"),
            formatted("\u00A7d\u00B6d = Purple"),
            formatted("\u00A7e\u00B6e = Yellow"),
            formatted("\u00A7f\u00B6f = White"),
            formatted("\u00A7aAvailable formatting codes:"),
            formatted("\u00A77\u00B6k = Obfuscated"),
            formatted("\u00A77\u00B6l = Bold"),
            formatted("\u00A77\u00B6m = Strikethrough"),
            formatted("\u00A77\u00B6n = Underline"),
            formatted("\u00A77\u00B6o = Italic"),
        )
        val font = Minecraft.getInstance().font
        val widthMax = lines.maxOf { font.width(it) }
        var x = mouseX + 12
        var y = mouseY - 12
        if (x + widthMax + 8 > width) x = width - widthMax - 8
        if (y + lines.size * 11 + 8 > height) y = height - lines.size * 11 - 8
        val boxH = lines.size * 11 + 6
        graphics.fill(x - 4, y - 4, x + widthMax + 4, y + boxH, 0xF0100010.toInt())
        graphics.fill(x - 4, y - 4, x + widthMax + 4, y - 3, 0xFF5000FF.toInt())
        graphics.fill(x - 4, y + boxH - 1, x + widthMax + 4, y + boxH, 0xFF28007F.toInt())
        lines.forEachIndexed { index, line ->
            graphics.text(font, line, x, y + index * 11, 0xFFFFFFFF.toInt(), true)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (tooltipOpen) {
            val panelW = 230
            val panelH = 120
            val px = (width - panelW) / 2
            val py = (height - panelH) / 2
            if (mx !in px until px + panelW || my !in py until py + panelH) tooltipOpen = false
            return true
        }
        val active = picker
        if (active != null) {
            if (active.mouseClicked(mx, my, event.button())) return true
            picker = null
            return true
        }
        toggle.mousePressed(mx, my, event.button())

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (mx in nameX until nameX + nameW && my in nameY until nameY + 20) {
                nameFocused = true
            } else {
                nameFocused = false
                syncFromState()
            }
            if (state.effectiveGlint && mx in xCenter - 90..xCenter + 90 &&
                my in glintStripY until glintStripY + 17
            ) {
                if (mx >= xCenter + 90 - 12) {
                    state = state.resetGlintColor()
                    applyLive()
                } else {
                    picker = NeurenameColourPicker(mx, my, width, height, state.glintPickerSeed, NeurenameColourTarget.GLINT, ::onGlintColour)
                }
            }
            if (state.leatherArmor && mx in xCenter - 90..xCenter + 90 && my in leatherStripY until leatherStripY + 17) {
                if (mx >= xCenter + 90 - 12) {
                    state = state.resetLeatherColor()
                    applyLive()
                } else {
                    val seed = state.leatherPickerSeed ?: ItemCustomizeColor(0xA06540)
                    picker = NeurenameColourPicker(mx, my, width, height, seed, NeurenameColourTarget.LEATHER, ::onLeatherColour)
                }
            }
            if (mx in xCenter - 90..xCenter + 90 && my in tooltipStripY until tooltipStripY + 17) {
                if (mx >= xCenter + 90 - 13) {
                    state = state.withTooltip("")
                    tooltipEditor = RenameTextEditor()
                    applyLive()
                } else {
                    tooltipEditor = RenameTextEditor().withText(state.customTooltip)
                    tooltipOpen = true
                }
            }
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val active = picker
        if (active != null) {
            active.mouseReleased()
            return true
        }
        toggle.mouseReleased(event.x().toInt(), event.y().toInt(), event.button())
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        picker?.mouseDragged(event.x().toInt(), event.y().toInt())
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (tooltipOpen) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> tooltipOpen = false
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> editTooltip(tooltipEditor.insert("\n"))
                GLFW.GLFW_KEY_BACKSPACE -> editTooltip(tooltipEditor.backspace())
                GLFW.GLFW_KEY_DELETE -> editTooltip(tooltipEditor.delete())
                GLFW.GLFW_KEY_LEFT -> editTooltip(tooltipEditor.moveHorizontal(-1, shift(event)))
                GLFW.GLFW_KEY_RIGHT -> editTooltip(tooltipEditor.moveHorizontal(1, shift(event)))
                GLFW.GLFW_KEY_HOME -> editTooltip(tooltipEditor.moveHome(shift(event)))
                GLFW.GLFW_KEY_END -> editTooltip(tooltipEditor.moveEnd(shift(event)))
                GLFW.GLFW_KEY_V -> if (control(event)) {
                    editTooltip(tooltipEditor.insert(Minecraft.getInstance().keyboardHandler.clipboard.replace("\r", "")))
                }
            }
            return true
        }
        val active = picker
        if (active != null) {
            if (active.keyPressed(event.key())) return true
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                picker = null
                return true
            }
        }
        if (nameFocused) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    nameFocused = false
                    syncFromState()
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    nameFocused = false
                    syncFromState()
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> edit(editor.backspace())
                GLFW.GLFW_KEY_DELETE -> edit(editor.delete())
                GLFW.GLFW_KEY_LEFT -> edit(editor.moveHorizontal(-1, shift(event)))
                GLFW.GLFW_KEY_RIGHT -> edit(editor.moveHorizontal(1, shift(event)))
                GLFW.GLFW_KEY_HOME -> edit(editor.moveHome(shift(event)))
                GLFW.GLFW_KEY_END -> edit(editor.moveEnd(shift(event)))
                GLFW.GLFW_KEY_A -> if (control(event)) edit(editor.selectAll())
                GLFW.GLFW_KEY_C -> if (control(event)) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(editor.selectedText())
                    return true
                }
                GLFW.GLFW_KEY_V -> if (control(event)) {
                    edit(editor.insert(Minecraft.getInstance().keyboardHandler.clipboard))
                }
                else -> return false
            }
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val codepoint = event.codepoint()
        if (tooltipOpen) {
            if (codepoint < 32) return true
            var typed = Character.toString(codepoint)
            if (typed == "\u00B6") typed = "\u00A7"
            editTooltip(tooltipEditor.insert(typed))
            return true
        }
        picker?.let { if (it.charTyped(codepoint)) return true }
        if (!nameFocused) return super.charTyped(event)
        if (codepoint < 32) return true
        var typed = Character.toString(codepoint)
        if (typed == "\u00B6") typed = "\u00A7"
        edit(editor.insert(typed))
        return true
    }

    override fun onClose() {
        persist()
        CustomRenameEditorSession.close()
        picker = null
        McUi.setScreen(Minecraft.getInstance(), null)
    }

    private fun edit(next: RenameTextEditor) {
        editor = next
        val normalized = CustomRenameText.normalizeEditor(editor.text)
        if (normalized != editor.text) {
            editor = RenameTextEditor(normalized, editor.cursor.coerceAtMost(normalized.length))
        }
        nameText = editor.text
        state = state.withName(editor.text)
        applyLive()
    }

    private fun syncFromState() {
        nameText = state.customName
        editor = RenameTextEditor().withText(nameText)
    }

    private fun editTooltip(next: RenameTextEditor) {
        val converted = tooltipShortcuts(next.text)
        tooltipEditor = RenameTextEditor(converted, next.cursor.coerceAtMost(converted.length))
        state = state.withTooltip(tooltipEditor.text)
        applyLive()
    }

    /** Typed-tooltip shortcuts (newlines preserved, unlike the single-line name editor). */
    private fun tooltipShortcuts(raw: String): String = raw
        .replace("&&", "\u00A7")
        .replace("**", CustomRenameText.MASTER_STAR_GLYPH.toString())
        .replace(Regex("\\*([1-9])")) { match ->
            (CustomRenameText.MASTER_STAR_FIRST.code + (match.groupValues[1][0].code - '1'.code)).toChar().toString()
        }
        .take(600)

    private fun setGlint(value: Boolean) {
        state = state.setGlint(value)
        applyLive()
    }

    private fun onGlintColour(color: ItemCustomizeColor) {
        state = state.setGlintColor(color)
        applyLive()
    }

    private fun onLeatherColour(color: ItemCustomizeColor) {
        state = state.setLeatherColor(color)
        applyLive()
    }

    private fun applyLive() {
        val open = session ?: return
        CustomRename.applyState(open.identity, state, persist = false)
    }

    private fun persist() {
        val open = session ?: return
        CustomRename.applyState(open.identity, state, persist = true)
    }

    private fun updateReveal() {
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrame).coerceIn(0L, 100L)) / 1000f
        lastFrame = now
        val target = if (state.effectiveGlint) 17f else 0f
        val step = dt / 0.2f * 17f
        reveal = if (reveal < target) minOf(target, reveal + step) else maxOf(target, reveal - step)
    }

    private fun markerDisplay(raw: String): String {
        val builder = StringBuilder(raw.length * 2)
        var index = 0
        while (index < raw.length) {
            if (raw[index] == '\u00A7' && index + 1 < raw.length) {
                builder.append('\u00A7').append(raw[index + 1]).append('\u00B6').append(raw[index + 1])
                index += 2
            } else {
                builder.append(raw[index])
                index++
            }
        }
        return builder.toString()
    }

    private fun formatted(raw: String): Component = CustomRenameText.component(raw, Style.EMPTY)

    private fun component(raw: String): Component = CustomRenameText.component(raw, Style.EMPTY)

    private fun stripArgb(color: ItemCustomizeColor): Int =
        0xFF000000.toInt() or (color.argbAt(ItemCustomizeClock.elapsedSeconds()) and 0xFFFFFF)

    private fun shift(event: KeyEvent): Boolean = (event.modifiers() and GLFW.GLFW_MOD_SHIFT) != 0

    private fun control(event: KeyEvent): Boolean = (event.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0
}
