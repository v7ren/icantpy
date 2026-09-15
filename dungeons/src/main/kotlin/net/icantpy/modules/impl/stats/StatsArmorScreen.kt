package net.icantpy.modules.impl.stats

import net.icantpy.gui.McUi
import net.icantpy.modules.impl.timer.TickTimers
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerListener
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class StatsArmorScreen private constructor(
    private val liveMenu: AbstractContainerMenu,
    title: Component,
) : Screen(title), ContainerListener {
    private val mc = Minecraft.getInstance()
    private var setupMode = false
    private var setupAddMode = false
    private var pending: ArmorPreset? = null
    private var scroll = 0
    private var scrollLimit = 0
    private var contentHeight = 0
    private var hits: List<Hit> = emptyList()

    init {
        liveMenu.addSlotListener(this)
    }

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        McUi.releaseMouse(mc)
        instance = this
    }

    override fun tick() {
        super.tick()
        val player = mc.player
        if (player == null || !player.isAlive || player.isRemoved) {
            player?.closeContainer()
        } else if (player.containerMenu !== liveMenu && McUi.currentScreen(mc) === this) {
            McUi.setScreen(mc, null)
        }
    }

    override fun slotChanged(handler: AbstractContainerMenu, slotId: Int, stack: ItemStack) {
        StatsArmor.invalidateResolutionCache()
    }

    override fun dataChanged(handler: AbstractContainerMenu, property: Int, value: Int) {}

    override fun removed() {
        if (instance === this) instance = null
        mc.player?.let { liveMenu.removed(it) }
        liveMenu.removeSlotListener(this)
        super.removed()
    }

    override fun onClose() {
        mc.player?.closeContainer()
        super.onClose()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0x50000000)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelW = min(340, width - 20).coerceAtLeast(260)
        val panelH = min(if (setupMode) 320 else 250, height - 20).coerceAtLeast(220)
        val panelX = (width - panelW) / 2
        val panelY = (height - panelH) / 2
        val contentTop = panelY + 50
        val contentBottom = panelY + panelH - if (setupMode) 48 else 30
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE5101015.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF9B4DFF.toInt())
        graphics.text(mc.font, "Stats & Equipment", panelX + 18, panelY + 14, 0xFFFFFFFF.toInt(), true)
        graphics.text(
            mc.font,
            when {
                setupAddMode -> "Add favorite · select an item, then choose its armor slot"
                setupMode -> "Manage favorites · add, remove, and set their order"
                else -> "Click a replacement to equip it"
            },
            panelX + 18,
            panelY + 32,
            0xFFAAAAAA.toInt(),
        )
        val headerHits = listOf(
            Hit(
                panelX + panelW - 84,
                panelY + 12,
                66,
                22,
                leftAction = {
                    if (setupAddMode) {
                        setupAddMode = false
                    } else {
                        setupMode = !setupMode
                    }
                    pending = null
                    scroll = 0
                },
            ),
        )
        button(
            graphics,
            panelX + panelW - 84,
            panelY + 12,
            66,
            22,
            when {
                setupAddMode -> "BACK"
                setupMode -> "DONE"
                else -> "SETUP"
            },
            setupMode || setupAddMode,
        )

        hits = headerHits + if (setupMode) {
            renderSetup(graphics, panelX, contentTop, panelW, contentBottom, mouseX, mouseY)
        } else {
            renderArmor(graphics, panelX, contentTop, panelW, contentBottom, mouseX, mouseY)
        }
        graphics.text(
            mc.font,
            when {
                setupAddMode -> "Choose an inventory item · Esc cancels"
                setupMode -> "↑↓ reorder · × remove · Add favorite"
                else -> "Esc closes"
            },
            panelX + 18,
            panelY + panelH - 20,
            0xFF777777.toInt(),
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val hit = hits.lastOrNull { it.contains(event.x().toInt(), event.y().toInt()) } ?: return true
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            hit.rightAction?.invoke()
        } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            hit.leftAction?.invoke()
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - (scrollY * 28).toInt()).coerceIn(0, scrollLimit)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    private fun renderArmor(
        graphics: GuiGraphicsExtractor,
        panelX: Int,
        top: Int,
        panelW: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Hit> {
        val nextHits = mutableListOf<Hit>()
        val currentX = panelX + 18
        val currentW = 64
        val targetX = currentX + currentW + 8
        val cardW = 32
        val cardH = 32
        val gap = 2
        val columns = ((panelW - (targetX - panelX) - 18) / (cardW + gap)).coerceAtLeast(1)
        var y = top - scroll
        StatsArmorSlot.entries.forEach { armorSlot ->
            val presets = TickTimers.settings.statsArmor.presets(armorSlot)
                .mapNotNull { preset -> StatsArmor.resolve(liveMenu, preset)?.let { preset to it } }
            val rows = max(1, ceil(presets.size / columns.toDouble()).toInt())
            val rowH = max(42, rows * (cardH + gap) + 6)
            if (y + rowH > top && y < bottom) {
                graphics.text(mc.font, armorSlot.label, currentX, y + 2, 0xFFB9B9B9.toInt(), true)
                val current = StatsArmor.currentArmor(armorSlot) ?: ItemStack.EMPTY
                val currentTimer = if (TickTimers.settings.statsArmor.showMaskTimers) MaskTimers.textFor(current) else null
                card(graphics, currentX, y + 12, currentW, cardH, current, 0xFF245D42.toInt(), mouseX, mouseY, currentTimer)
                presets.forEachIndexed { index, (preset, slot) ->
                    val x = targetX + (index % columns) * (cardW + gap)
                    val cardY = y + 12 + (index / columns) * (cardH + gap)
                    val stack = slot.item
                    val timer = if (TickTimers.settings.statsArmor.showMaskTimers) MaskTimers.textFor(stack) else null
                    card(graphics, x, cardY, cardW, cardH, stack, 0xFF642F3A.toInt(), mouseX, mouseY, timer)
                    nextHits += Hit(x, cardY, cardW, cardH, leftAction = { performSwap(preset) })
                }
            }
            y += rowH
        }
        contentHeight = y - top + scroll
        return nextHits
    }

    private fun renderSetup(
        graphics: GuiGraphicsExtractor,
        panelX: Int,
        top: Int,
        panelW: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Hit> = if (setupAddMode) {
        renderSetupPicker(graphics, panelX, top, panelW, bottom, mouseX, mouseY)
    } else {
        renderSetupManage(graphics, panelX, top, panelW, bottom, mouseX, mouseY)
    }

    private fun renderSetupManage(
        graphics: GuiGraphicsExtractor,
        panelX: Int,
        top: Int,
        panelW: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Hit> {
        val nextHits = mutableListOf<Hit>()
        val left = panelX + 18
        var y = top + 2 - scroll
        graphics.text(mc.font, "Favorites", left, y, 0xFF9B4DFF.toInt(), true)
        graphics.text(mc.font, "Items are ready when they appear in your inventory", left + 72, y, 0xFF777777.toInt())
        y += 22
        val favorites = StatsArmorSlot.entries.flatMap { armorSlot ->
            TickTimers.settings.statsArmor.presets(armorSlot).map { armorSlot to it }
        }
        if (favorites.isEmpty()) {
            graphics.text(mc.font, "No favorites yet. Add one from your inventory.", left, y, 0xFFAAAAAA.toInt())
            y += 30
        }
        favorites.forEach { (armorSlot, preset) ->
            val rowY = y
            val resolved = StatsArmor.resolve(liveMenu, preset)
            if (rowY + 22 > top && rowY < bottom) {
                graphics.fill(left, rowY, panelX + panelW - 18, rowY + 22, 0xFF18181E.toInt())
                if (resolved != null) graphics.item(resolved.item, left + 3, rowY + 3)
                graphics.text(
                    mc.font,
                    "${armorSlot.label}: ${preset.itemName.take(20)}",
                    left + 25,
                    rowY + 6,
                    if (resolved != null) 0xFFFFFFFF.toInt() else 0xFF777777.toInt(),
                )
                val upX = panelX + panelW - 74
                val downX = panelX + panelW - 52
                val removeX = panelX + panelW - 30
                button(graphics, upX, rowY + 2, 18, 18, "↑", true)
                button(graphics, downX, rowY + 2, 18, 18, "↓", true)
                button(graphics, removeX, rowY + 2, 18, 18, "×", true)
                nextHits += Hit(upX, rowY + 2, 18, 18, leftAction = {
                    TickTimers.updateStatsArmor { it.move(preset.id, -1) }
                })
                nextHits += Hit(downX, rowY + 2, 18, 18, leftAction = {
                    TickTimers.updateStatsArmor { it.move(preset.id, 1) }
                })
                nextHits += Hit(removeX, rowY + 2, 18, 18, leftAction = {
                    TickTimers.updateStatsArmor { it.remove(preset.id) }
                })
            }
            y += 24
        }
        contentHeight = y - top + scroll
        scrollLimit = (contentHeight - (bottom - top)).coerceAtLeast(0)
        val addY = bottom + 4
        button(graphics, left, addY, panelW - 36, 22, "+  ADD FAVORITE FROM INVENTORY", true)
        nextHits += Hit(left, addY, panelW - 36, 22, leftAction = {
            setupAddMode = true
            pending = null
            scroll = 0
        })
        return nextHits
    }

    private fun renderSetupPicker(
        graphics: GuiGraphicsExtractor,
        panelX: Int,
        top: Int,
        panelW: Int,
        bottom: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Hit> {
        val nextHits = mutableListOf<Hit>()
        val slots = StatsArmor.sourceSlots(liveMenu).filterNot { it.item.isEmpty }
        val selected = pending?.inventorySlot
        val left = panelX + 18
        val cell = 36
        val columns = ((panelW - 36) / cell).coerceAtLeast(1)
        val gridTop = top + 20 - scroll
        val chooserY = bottom - 22
        val gridBottom = chooserY - 24
        graphics.text(mc.font, "Select an inventory item", left, top + 2, 0xFF9B4DFF.toInt(), true)
        slots.forEachIndexed { index, slot ->
            val x = left + (index % columns) * cell
            val cellY = gridTop + (index / columns) * cell
            if (cellY + 32 > top && cellY < gridBottom) {
                val color = if (slot.containerSlot == selected) 0xFF9B4DFF.toInt() else 0xFF33333B.toInt()
                graphics.fill(x, cellY, x + 32, cellY + 32, color)
                graphics.fill(x + 2, cellY + 2, x + 30, cellY + 30, 0xFF18181E.toInt())
                graphics.item(slot.item, x + 8, cellY + 7)
                graphics.itemDecorations(mc.font, slot.item, x + 8, cellY + 7)
                if (mouseX in x until x + 32 && mouseY in cellY until cellY + 32) {
                    graphics.setTooltipForNextFrame(mc.font, slot.item, mouseX, mouseY)
                }
                nextHits += Hit(
                    x,
                    cellY,
                    32,
                    32,
                    leftAction = { pending = StatsArmor.capture(slot) },
                    rightAction = {
                        val fingerprint = ItemFingerprint.fromStack(slot.item)
                        val saved = TickTimers.settings.statsArmor.presets
                            .lastOrNull { it.fingerprint().matches(fingerprint) }
                        saved?.let { preset -> TickTimers.updateStatsArmor { it.remove(preset.id) } }
                    },
                )
            }
        }
        val gridHeight = ceil(slots.size / columns.toDouble()).toInt() * cell + 22
        contentHeight = gridHeight
        scrollLimit = (contentHeight - (gridBottom - top)).coerceAtLeast(0)
        graphics.text(
            mc.font,
            pending?.let { "Selected: ${it.itemName.take(28)}" } ?: "Select an item, then choose a slot",
            left,
            chooserY - 18,
            0xFFB9B9B9.toInt(),
        )
        val buttonGap = 4
        val buttonWidth = (panelW - 36 - buttonGap * 3) / 4
        StatsArmorSlot.entries.forEachIndexed { index, armorSlot ->
            val x = left + index * (buttonWidth + buttonGap)
            val enabled = pending != null
            button(graphics, x, chooserY, buttonWidth, 22, armorSlot.label, enabled)
            if (enabled) nextHits += Hit(x, chooserY, buttonWidth, 22, leftAction = {
                pending?.let { capture ->
                    TickTimers.updateStatsArmor { it.add(armorSlot, capture) }
                    pending = null
                }
            })
        }
        return nextHits
    }

    private fun performSwap(preset: ArmorPreset) {
        if (!StatsArmor.swap(liveMenu, preset)) {
            mc.player?.sendSystemMessage(Component.literal("§cThat configured item is no longer in your inventory."))
        }
    }

    private fun card(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        stack: ItemStack,
        color: Int,
        mouseX: Int,
        mouseY: Int,
        timer: String?,
        fallbackName: String? = null,
        available: Boolean = true,
    ) {
        val hovered = mouseX in x until x + w && mouseY in y until y + h
        val border = if (hovered) 0xFFE3A2FF.toInt() else if (available) color else 0xFF34343D.toInt()
        graphics.fill(x, y, x + w, y + h, border)
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF17171D.toInt())
        if (!stack.isEmpty) {
            graphics.item(stack, x + (w - 16) / 2, y + 5)
            graphics.itemDecorations(mc.font, stack, x + (w - 16) / 2, y + 5)
            timer?.let { graphics.text(mc.font, it, x + 3, y + h - 13, 0xFFFFD166.toInt(), false) }
            if (hovered) graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY)
        } else if (!fallbackName.isNullOrBlank()) {
            graphics.text(mc.font, "Saved", x + 3, y + 6, 0xFF777777.toInt(), false)
            graphics.text(mc.font, fallbackName.take(6), x + 3, y + h - 13, 0xFF777777.toInt(), false)
            if (hovered) graphics.setTooltipForNextFrame(
                mc.font,
                Component.literal("Not in inventory: $fallbackName"),
                mouseX,
                mouseY,
            )
        }
    }

    private fun button(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        label: String,
        enabled: Boolean,
    ) {
        graphics.fill(x, y, x + w, y + h, if (enabled) 0xFF713AA6.toInt() else 0xFF3B3B45.toInt())
        graphics.centeredText(mc.font, label, x + w / 2, y + 7, 0xFFFFFFFF.toInt())
    }

    private data class Hit(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val leftAction: (() -> Unit)? = null,
        val rightAction: (() -> Unit)? = null,
    ) {
        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in x until x + w && mouseY in y until y + h
    }

    companion object {
        var instance: StatsArmorScreen? = null
            private set

        fun live(menu: AbstractContainerMenu, title: Component): StatsArmorScreen = StatsArmorScreen(menu, title)
    }
}
