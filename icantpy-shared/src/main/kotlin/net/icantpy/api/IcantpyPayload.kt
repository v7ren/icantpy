package net.icantpy.api

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.state.BlockState

interface IcantpyPayload {
    fun onLoad()

    fun onUnload()

    /** Generic extension point; legacy callbacks are used by the default implementation. */
    fun dispatch(event: IcantpyRuntimeEvent): IcantpyDispatchResult = dispatchLegacy(this, event)

    /** Synchronous query extension point. Unknown queries retain vanilla behavior. */
    fun query(query: IcantpyRuntimeQuery): IcantpyQueryResult = queryLegacy(this, query)

    fun onOutgoingChat(message: String): Boolean = false

    fun onIncomingChat(message: String) {}

    fun onBossBar(name: String, progress: Float) {}

    fun onBlock(pos: BlockPos, state: BlockState) {}

    fun onRenderWorld() {}

    fun onRenderHud(graphics: GuiGraphicsExtractor) {}

    fun onTick() {}

    fun onServerTick() {}

    fun onDisconnect() {}

    fun openGui() {}

    fun addWaypointAtLook() {}

    fun openStats() {}

    fun openLoadout() {}

    fun customItemName(stack: net.minecraft.world.item.ItemStack, vanilla: Component): Component = vanilla

    /** Client-only appearance hooks used by the NEU-compatible rename editor. */
    fun customGlintOverride(stack: net.minecraft.world.item.ItemStack): Boolean? = null

    fun customGlintColor(stack: net.minecraft.world.item.ItemStack): Int? = null

    fun customLeatherColor(stack: net.minecraft.world.item.ItemStack, vanilla: Int): Int = vanilla

    /** Extra client-side tooltip lines (custom lore) for an item, empty when none. */
    fun customTooltip(stack: net.minecraft.world.item.ItemStack): List<Component> = emptyList()

    fun wantsLeapMenu(title: String): Boolean = false

    fun openLeapMenu(menu: AbstractContainerMenu, title: Component): Boolean = false

    fun renderLeapContainerOverlay(
        screen: Screen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ): Boolean = false

    fun leapMouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean): Boolean? = null

    fun leapMouseReleased(screen: Screen, event: MouseButtonEvent): Boolean? = null

    fun leapKeyPressed(screen: Screen, event: KeyEvent): Boolean? = null

    fun leapOverlayReady(screen: Screen): Boolean = false

    fun hideOdinLeapMenu(): Boolean = false

    fun wantsStatsMenu(title: String): Boolean = false

    fun openStatsMenu(menu: AbstractContainerMenu, title: Component): Boolean = false

    fun wantsLoadoutMenu(title: String): Boolean = false

    fun openLoadoutMenu(menu: AbstractContainerMenu, title: Component): Boolean = false
}
