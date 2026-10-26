package net.icantpy.dungeon.terminal

import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu

/**
 * The simulator's chest. It exists only on the client, so closing it must not send the vanilla close packet for a
 * container the server never opened; [onClose] hands the menu back locally instead.
 */
class TerminalSimScreen(menu: ChestMenu, inventory: Inventory, title: Component) : ContainerScreen(menu, inventory, title) {
    override fun onClose() {
        TerminalSimulator.stop()
    }
}
