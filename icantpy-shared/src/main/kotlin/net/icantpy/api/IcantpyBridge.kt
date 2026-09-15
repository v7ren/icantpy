package net.icantpy.api

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.state.BlockState

object IcantpyBridge {
    @Volatile
    private var current: IcantpyPayload? = null

    fun setPayload(payload: IcantpyPayload?) {
        IcantpyInboundPackets.invalidate()
        current = payload
    }

    fun payload(): IcantpyPayload? = current

    fun dispatch(event: IcantpyRuntimeEvent): IcantpyDispatchResult {
        val payload = current ?: return IcantpyDispatchResult.PASS
        return try {
            payload.dispatch(event)
        } catch (_: AbstractMethodError) {
            // Payloads compiled before the generic contract existed do not have the
            // new JVM method; route them through the legacy compatibility table.
            dispatchLegacy(payload, event)
        } catch (_: NoSuchMethodError) {
            dispatchLegacy(payload, event)
        }
    }

    fun query(query: IcantpyRuntimeQuery): IcantpyQueryResult {
        val payload = current ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
        return try {
            payload.query(query)
        } catch (_: AbstractMethodError) {
            queryLegacy(payload, query)
        } catch (_: NoSuchMethodError) {
            queryLegacy(payload, query)
        }
    }

    fun onOutgoingChat(message: String): Boolean =
        dispatch(IcantpyRuntimeEvent("chat.outgoing", context = mapOf("message" to message))) !=
            IcantpyDispatchResult.PASS

    fun onIncomingChat(message: String) {
        dispatch(IcantpyRuntimeEvent("chat.incoming", context = mapOf("message" to message)))
    }

    fun onBossBar(name: String, progress: Float) {
        dispatch(
            IcantpyRuntimeEvent(
                "network.boss_bar",
                context = mapOf("name" to name, "progress" to progress),
            ),
        )
    }

    fun onBlock(pos: BlockPos, state: BlockState) {
        dispatch(IcantpyRuntimeEvent("world.block", context = mapOf("pos" to pos, "state" to state)))
    }

    fun onRenderWorld() {
        dispatch(IcantpyRuntimeEvent("render.world"))
    }

    fun onRenderHud(graphics: GuiGraphicsExtractor) {
        dispatch(IcantpyRuntimeEvent("render.hud", context = mapOf("graphics" to graphics)))
    }

    fun onTick() {
        dispatch(IcantpyRuntimeEvent("lifecycle.tick"))
    }

    fun onServerTick() {
        dispatch(IcantpyRuntimeEvent("lifecycle.server_tick"))
    }

    fun onDisconnect() {
        IcantpyInboundPackets.clear()
        dispatch(IcantpyRuntimeEvent("lifecycle.disconnect"))
    }

    fun openGui() {
        dispatch(IcantpyRuntimeEvent("input.open_gui"))
    }

    fun addWaypointAtLook() {
        dispatch(IcantpyRuntimeEvent("input.add_waypoint"))
    }

    fun openStats() {
        dispatch(IcantpyRuntimeEvent("input.open_stats"))
    }

    fun openLoadout() {
        dispatch(IcantpyRuntimeEvent("input.open_loadout"))
    }

    fun customItemName(stack: net.minecraft.world.item.ItemStack, vanilla: Component): Component =
        query(
            IcantpyRuntimeQuery(
                "appearance.item.name",
                context = mapOf("stack" to stack, "vanilla" to vanilla),
            ),
        ).value as? Component ?: vanilla

    fun customGlintOverride(stack: net.minecraft.world.item.ItemStack): Boolean? =
        query(IcantpyRuntimeQuery("appearance.item.glint.override", context = mapOf("stack" to stack))).value as? Boolean

    fun customGlintColor(stack: net.minecraft.world.item.ItemStack): Int? =
        query(IcantpyRuntimeQuery("appearance.item.glint.color", context = mapOf("stack" to stack))).value as? Int

    fun customLeatherColor(stack: net.minecraft.world.item.ItemStack, vanilla: Int): Int =
        query(
            IcantpyRuntimeQuery(
                "appearance.item.leather.color",
                context = mapOf("stack" to stack, "vanilla" to vanilla),
            ),
        ).value as? Int ?: vanilla

    @Suppress("UNCHECKED_CAST")
    fun customTooltip(stack: net.minecraft.world.item.ItemStack): List<Component> =
        query(IcantpyRuntimeQuery("appearance.item.tooltip", context = mapOf("stack" to stack))).value as? List<Component>
            ?: emptyList()

    fun wantsLeapMenu(title: String): Boolean =
        query(IcantpyRuntimeQuery("gui.menu.wants.leap", context = mapOf("title" to title))).value == true

    fun openLeapMenu(menu: AbstractContainerMenu, title: Component): Boolean =
        query(
            IcantpyRuntimeQuery("gui.menu.open.leap", context = mapOf("menu" to menu, "title" to title)),
        ).value == true

    fun renderLeapContainerOverlay(
        screen: Screen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ): Boolean = current?.renderLeapContainerOverlay(screen, graphics, mouseX, mouseY, partialTick) == true

    fun leapMouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean): Boolean? =
        current?.leapMouseClicked(screen, event, doubleClick)

    fun leapMouseReleased(screen: Screen, event: MouseButtonEvent): Boolean? =
        current?.leapMouseReleased(screen, event)

    fun leapKeyPressed(screen: Screen, event: KeyEvent): Boolean? =
        current?.leapKeyPressed(screen, event)

    fun leapOverlayReady(screen: Screen): Boolean =
        current?.leapOverlayReady(screen) == true

    fun hideOdinLeapMenu(): Boolean =
        current?.hideOdinLeapMenu() == true

    fun wantsStatsMenu(title: String): Boolean =
        query(IcantpyRuntimeQuery("gui.menu.wants.stats", context = mapOf("title" to title))).value == true

    fun openStatsMenu(menu: AbstractContainerMenu, title: Component): Boolean =
        query(
            IcantpyRuntimeQuery("gui.menu.open.stats", context = mapOf("menu" to menu, "title" to title)),
        ).value == true

    fun wantsLoadoutMenu(title: String): Boolean =
        query(IcantpyRuntimeQuery("gui.menu.wants.loadout", context = mapOf("title" to title))).value == true

    fun openLoadoutMenu(menu: AbstractContainerMenu, title: Component): Boolean =
        query(
            IcantpyRuntimeQuery("gui.menu.open.loadout", context = mapOf("menu" to menu, "title" to title)),
        ).value == true
}
