package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.gui.IcantpyGui
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

class LeapMenuScreen(
    teammates: List<LeapPlayer>,
    private val returnToConfig: Boolean = false,
    private val liveMenu: AbstractContainerMenu? = null,
    title: Component = Component.empty(),
) : Screen(title), ContainerListener {
    private val mc: Minecraft = Minecraft.getInstance()
    private val leapTeammates: MutableList<LeapPlayer> = teammates.toMutableList()

    init {
        liveMenu?.addSlotListener(this)
    }

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        McUi.releaseMouse(mc)
        instance = this
    }

    override fun tick() {
        super.tick()
        if (liveMenu == null) return
        val player = mc.player
        if (player == null || !player.isAlive || player.isRemoved) {
            player?.closeContainer()
            return
        }
        if (player.containerMenu !== liveMenu && McUi.currentScreen(mc) === this) {
            McUi.setScreen(mc, null)
        }
    }

    override fun slotChanged(handler: AbstractContainerMenu, slotId: Int, stack: ItemStack) {
        if (handler !== liveMenu) return
        leapTeammates.clear()
        leapTeammates.addAll(currentPlayers())
    }

    override fun dataChanged(handler: AbstractContainerMenu, property: Int, value: Int) {}

    override fun removed() {
        if (instance === this) {
            instance = null
        }
        LeapMenu.dropOverlay()
        val player = mc.player
        val menu = liveMenu
        if (player != null && menu != null) {
            menu.removed(player)
            menu.removeSlotListener(this)
        }
        super.removed()
    }

    override fun onClose() {
        if (liveMenu != null) {
            mc.player?.closeContainer()
            super.onClose()
            return
        }
        dismissDebug()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, LeapMenuRenderer.DIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (McUi.currentScreen(mc) !== this) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick)
            return
        }
        leapTeammates.clear()
        leapTeammates.addAll(currentPlayers())
        LeapMenu.render(graphics, leapTeammates, width, height, mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick)
        }
        if (LeapMenu.settings().onRelease) return true
        clickAt(event.x().toInt(), event.y().toInt())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(event)
        }
        if (!LeapMenu.settings().onRelease) return true
        clickAt(event.x().toInt(), event.y().toInt())
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event)
        }
        if (liveMenu != null && mc.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        val menu = liveMenu
        if (menu != null) {
            return LeapMenu.keyPressed(menu, event) ?: super.keyPressed(event)
        }
        val index = LeapMenu.debugKeyIndex(event.key(), leapTeammates) ?: return super.keyPressed(event)
        val player = leapTeammates.getOrNull(index) ?: return true
        LeapMenu.leapTo(player, null)
        dismissDebug()
        return true
    }

    private fun clickAt(mouseX: Int, mouseY: Int) {
        val menu = liveMenu
        if (menu != null) {
            LeapMenu.clickAt(menu, width, height, mouseX, mouseY)
            return
        }
        LeapMenu.clickDebug(leapTeammates, width, height, mouseX, mouseY)
        dismissDebug()
    }

    private fun currentPlayers(): List<LeapPlayer> {
        val menu = liveMenu
        if (menu != null) return LeapMenu.teammatesFor(menu)
        return DebugParty.leapPlayers(
            mc.player?.name?.string,
            LeapRoster.selfClass(LeapMenu.settings()),
        )
    }

    private fun dismissDebug() {
        if (returnToConfig) {
            IcantpyGui.showConfig()
        } else {
            McUi.setScreen(mc, null)
        }
    }

    companion object {
        var instance: LeapMenuScreen? = null
            private set

        fun isOpen(): Boolean = instance != null

        fun live(menu: AbstractContainerMenu, title: Component): LeapMenuScreen =
            LeapMenuScreen(emptyList(), returnToConfig = false, liveMenu = menu, title = title)

        fun open(players: List<LeapPlayer>) {
            val mc = Minecraft.getInstance()
            McUi.runOnClientThread(mc) {
                val current = McUi.currentScreen(mc)
                if (instance != null && current is LeapMenuScreen && current.liveMenu == null) {
                    current.leapTeammates.clear()
                    current.leapTeammates.addAll(players)
                    return@runOnClientThread
                }
                McUi.setScreen(mc, LeapMenuScreen(players, IcantpyGui.isOurScreen(current)))
            }
        }
    }
}
