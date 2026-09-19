package net.icantpy.qol.loadout

import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerListener
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.min

class LoadoutScreen private constructor(
    private val liveMenu: AbstractContainerMenu,
    title: Component,
) : Screen(title), ContainerListener {
    private val mc = Minecraft.getInstance()
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

    override fun slotChanged(handler: AbstractContainerMenu, slotId: Int, stack: ItemStack) {}

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
        val icons = Loadout.iconSlots(liveMenu)
        val previous = Loadout.navigationSlot(liveMenu, next = false)
        val next = Loadout.navigationSlot(liveMenu, next = true)
        val panelW = min(260, width - 20).coerceAtLeast(200)
        val columns = 3
        val card = 48
        val gap = 6
        val rows = ceil(icons.size / columns.toDouble()).toInt().coerceAtLeast(1)
        val panelH = (96 + rows * (card + gap) + if (previous != null || next != null) 34 else 0)
            .coerceAtMost(height - 20)
            .coerceAtLeast(170)
        val panelX = (width - panelW) / 2
        val panelY = (height - panelH) / 2
        val contentTop = panelY + 48

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE5101015.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF9B4DFF.toInt())
        graphics.text(mc.font, "Loadouts", panelX + 16, panelY + 13, 0xFFFFFFFF.toInt(), true)
        graphics.text(mc.font, "Click a loadout to equip it", panelX + 16, panelY + 30, 0xFFAAAAAA.toInt())

        val nextHits = mutableListOf<Hit>()
        icons.forEachIndexed { index, slot ->
            val x = panelX + 16 + (index % columns) * (card + gap)
            val y = contentTop + (index / columns) * (card + gap)
            val hovered = mouseX in x until x + card && mouseY in y until y + card
            graphics.fill(x, y, x + card, y + card, if (hovered) 0xFFE3A2FF.toInt() else 0xFF642F3A.toInt())
            graphics.fill(x + 2, y + 2, x + card - 2, y + card - 2, 0xFF17171D.toInt())
            graphics.item(slot.item, x + 16, y + 8)
            graphics.itemDecorations(mc.font, slot.item, x + 16, y + 8)
            if (hovered) graphics.setTooltipForNextFrame(mc.font, slot.item, mouseX, mouseY)
            nextHits += Hit(x, y, card, card, leftAction = { Loadout.click(liveMenu, slot) })
        }

        val navY = panelY + panelH - 32
        if (previous != null) {
            button(graphics, panelX + 16, navY, 112, 22, "‹  Previous", mouseX, mouseY)
            nextHits += Hit(panelX + 16, navY, 112, 22, leftAction = { Loadout.click(liveMenu, previous) })
        }
        if (next != null) {
            button(graphics, panelX + panelW - 128, navY, 112, 22, "Next  ›", mouseX, mouseY)
            nextHits += Hit(panelX + panelW - 128, navY, 112, 22, leftAction = { Loadout.click(liveMenu, next) })
        }
        hits = nextHits
        graphics.text(mc.font, "Esc closes", panelX + 16, panelY + panelH - 14, 0xFF777777.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val hit = hits.lastOrNull { it.contains(event.x().toInt(), event.y().toInt()) } ?: return true
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) hit.leftAction?.invoke()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    private fun button(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        label: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in x until x + w && mouseY in y until y + h
        graphics.fill(x, y, x + w, y + h, if (hovered) 0xFFE3A2FF.toInt() else 0xFF713AA6.toInt())
        graphics.centeredText(mc.font, label, x + w / 2, y + 7, 0xFFFFFFFF.toInt())
    }

    private data class Hit(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val leftAction: (() -> Unit)? = null,
    ) {
        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in x until x + w && mouseY in y until y + h
    }

    companion object {
        var instance: LoadoutScreen? = null
            private set

        fun live(menu: AbstractContainerMenu, title: Component): LoadoutScreen = LoadoutScreen(menu, title)
    }
}
