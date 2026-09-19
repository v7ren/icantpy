package net.icantpy.gui.neurename

import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameEditorSession
import net.icantpy.cosmetics.items.CustomRenameText
import net.icantpy.cosmetics.items.ItemCustomizeBaseline
import net.icantpy.cosmetics.items.ItemCustomizeClock
import net.icantpy.cosmetics.items.ItemCustomizeColor
import net.icantpy.cosmetics.items.ItemCustomizeColorEvaluator
import net.icantpy.cosmetics.items.ItemCustomizeState
import net.icantpy.cosmetics.items.RenameTextEditor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW

private const val TOOLTIP_LINE_HEIGHT = 10

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
    private var tooltipScroll = 0
    private var tooltipMaxScroll = 0
    private var lastTooltipCursor = -1
    private var tooltipPanelX = 0
    private var tooltipPanelY = 0
    private var tooltipPanelW = 0
    private var tooltipPanelH = 0
    private var tooltipTextTop = 0
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
        tooltipPanelW = (width - 40).coerceIn(200, 380)
        tooltipPanelH = (height - 40).coerceIn(120, 240)
        tooltipPanelX = (width - tooltipPanelW) / 2
        tooltipPanelY = (height - tooltipPanelH) / 2
        NeurenameDraw.floatingRectDark(graphics, tooltipPanelX, tooltipPanelY, tooltipPanelW, tooltipPanelH)
        NeurenameDraw.centered(graphics, formatted("\u00A7d\u00A7lCustom Tooltip"), width / 2, tooltipPanelY + 8 + 3, 0x404040, shadow = true)

        val textTop = tooltipPanelY + 26
        val textBottom = tooltipPanelY + tooltipPanelH - 20
        tooltipTextTop = textTop
        graphics.fill(tooltipPanelX + 8, textTop, tooltipPanelX + tooltipPanelW - 8, textBottom, 0xFF000000.toInt())

        val lines = tooltipEditor.text.split('\n')
        val visible = ((textBottom - textTop - 4) / TOOLTIP_LINE_HEIGHT).coerceAtLeast(1)
        val maxScroll = (lines.size - visible).coerceAtLeast(0)
        tooltipMaxScroll = maxScroll
        val (cursorLine, cursorCol) = tooltipCursorLineCol(lines)
        if (tooltipEditor.cursor != lastTooltipCursor) {
            // Only follow the caret when it moves, so manual scrolling is not fought.
            if (cursorLine < tooltipScroll) tooltipScroll = cursorLine
            if (cursorLine >= tooltipScroll + visible) tooltipScroll = cursorLine - visible + 1
            lastTooltipCursor = tooltipEditor.cursor
        }
        tooltipScroll = tooltipScroll.coerceIn(0, maxScroll)

        val font = Minecraft.getInstance().font
        graphics.enableScissor(tooltipPanelX + 8, textTop, tooltipPanelX + tooltipPanelW - 8, textBottom)
        for (row in 0 until visible) {
            val index = tooltipScroll + row
            if (index >= lines.size) break
            graphics.text(font, formatted(lines[index].ifEmpty { " " }), tooltipPanelX + 12, textTop + 2 + row * TOOLTIP_LINE_HEIGHT, 0xFFFFFFFF.toInt())
        }
        graphics.disableScissor()

        if (System.currentTimeMillis() % 1000 > 500) {
            val colText = lines.getOrElse(cursorLine) { "" }.take(cursorCol)
            val cx = tooltipPanelX + 12 + font.width(formatted(colText))
            val cy = textTop + 1 + (cursorLine - tooltipScroll) * TOOLTIP_LINE_HEIGHT
            graphics.fill(cx, cy, cx + 1, cy + TOOLTIP_LINE_HEIGHT, 0xFFFFFFFF.toInt())
        }

        if (lines.size > visible) {
            val trackTop = textTop + 2
            val trackH = (textBottom - textTop - 4).coerceAtLeast(1)
            val thumbH = (trackH * visible / lines.size).coerceAtLeast(8)
            val thumbY = trackTop + (trackH - thumbH) * tooltipScroll / maxScroll.coerceAtLeast(1)
            graphics.fill(tooltipPanelX + tooltipPanelW - 7, trackTop, tooltipPanelX + tooltipPanelW - 5, textBottom - 2, 0x40FFFFFF)
            graphics.fill(tooltipPanelX + tooltipPanelW - 7, thumbY, tooltipPanelX + tooltipPanelW - 5, thumbY + thumbH, 0xFFAAAAAA.toInt())
        }

        graphics.text(
            font,
            formatted("\u00A78&& colours, Enter = new line, wheel = scroll, Esc = done"),
            tooltipPanelX + 8,
            tooltipPanelY + tooltipPanelH - 14,
            0xFF808080.toInt(),
        )
    }

    private fun tooltipCursorLineCol(lines: List<String>): Pair<Int, Int> {
        var remaining = tooltipEditor.cursor
        for ((index, line) in lines.withIndex()) {
            if (remaining <= line.length) return index to remaining
            remaining -= line.length + 1
        }
        val last = lines.lastIndex.coerceAtLeast(0)
        return last to (lines.getOrNull(last)?.length ?: 0)
    }

    private fun tooltipCursorFromMouse(mouseX: Int, mouseY: Int): Int {
        val lines = tooltipEditor.text.split('\n')
        val line = (tooltipScroll + (mouseY - tooltipTextTop - 1) / TOOLTIP_LINE_HEIGHT).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val target = lines.getOrElse(line) { "" }
        val font = Minecraft.getInstance().font
        val relX = mouseX - (tooltipPanelX + 12)
        var col = 0
        while (col < target.length && font.width(formatted(target.substring(0, col + 1))) <= relX) col++
        var index = 0
        for (i in 0 until line) index += lines[i].length + 1
        return (index + col).coerceIn(0, tooltipEditor.text.length)
    }

    private fun moveTooltipVertical(delta: Int): Int {
        val lines = tooltipEditor.text.split('\n')
        val (line, col) = tooltipCursorLineCol(lines)
        val target = (line + delta).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        var index = 0
        for (i in 0 until target) index += lines[i].length + 1
        return index + col.coerceAtMost(lines[target].length)
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
            if (mx in tooltipPanelX until tooltipPanelX + tooltipPanelW &&
                my in tooltipPanelY until tooltipPanelY + tooltipPanelH
            ) {
                if (my in tooltipTextTop until tooltipPanelY + tooltipPanelH - 20) {
                    tooltipEditor = tooltipEditor.moveTo(tooltipCursorFromMouse(mx, my), false)
                }
                return true
            }
            tooltipOpen = false
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
                    val seed = state.customTooltip.ifBlank { existingTooltipText() }
                    tooltipEditor = RenameTextEditor().withText(seed).moveTo(0, false)
                    tooltipScroll = 0
                    tooltipMaxScroll = 0
                    lastTooltipCursor = 0
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (tooltipOpen) {
            val delta = when {
                scrollY > 0 -> -1
                scrollY < 0 -> 1
                else -> 0
            }
            tooltipScroll = (tooltipScroll + delta).coerceIn(0, tooltipMaxScroll)
            lastTooltipCursor = tooltipEditor.cursor
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
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
                GLFW.GLFW_KEY_UP -> editTooltip(tooltipEditor.moveTo(moveTooltipVertical(-1), shift(event)))
                GLFW.GLFW_KEY_DOWN -> editTooltip(tooltipEditor.moveTo(moveTooltipVertical(1), shift(event)))
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

    /** Seeds the tooltip editor with the item's own lore (not other mods' additions). */
    private fun existingTooltipText(): String {
        val stack = session?.stack ?: return ""
        val lore = stack.components.get(net.minecraft.core.component.DataComponents.LORE) ?: return ""
        return lore.lines().joinToString("\n") { CustomRenameText.toLegacy(it) }
    }

    private fun editTooltip(next: RenameTextEditor) {
        val converted = tooltipShortcuts(next.text)
        tooltipEditor = RenameTextEditor(converted, next.cursor.coerceAtMost(converted.length))
        state = state.withTooltip(tooltipEditor.text)
        applyLive()
    }

    /** Typed-tooltip shortcuts (newlines preserved, no length cap, unlike the name editor). */
    private fun tooltipShortcuts(raw: String): String = raw
        .replace("&&", "\u00A7")
        .replace("**", CustomRenameText.MASTER_STAR_GLYPH.toString())
        .replace(Regex("\\*([1-9])")) { match ->
            (CustomRenameText.MASTER_STAR_FIRST.code + (match.groupValues[1][0].code - '1'.code)).toChar().toString()
        }

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
