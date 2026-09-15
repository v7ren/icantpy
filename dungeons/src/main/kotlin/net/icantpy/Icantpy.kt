package net.icantpy

import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyClientCommands
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.api.IcantpyPayload
import net.icantpy.gui.Composite
import net.icantpy.gui.IcantpyGui
import net.icantpy.modules.impl.appearance.CustomRename
import net.icantpy.modules.impl.appearance.CustomRenameEditorSession
import net.icantpy.modules.impl.appearance.ItemCustomizeClock
import net.icantpy.modules.impl.appearance.ItemInfo
import net.icantpy.modules.impl.dungeon.DungeonListener
import net.icantpy.modules.impl.dungeon.leaporient.LeapMenu
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrient
import net.icantpy.modules.impl.render.IcantpyHud
import net.icantpy.modules.impl.render.WorldEsp
import net.icantpy.modules.impl.stats.MaskTimers
import net.icantpy.modules.impl.stats.StatsArmor
import net.icantpy.modules.impl.loadout.Loadout
import net.icantpy.modules.impl.timer.TickTimers
import net.icantpy.modules.impl.waypoint.CommandWaypoints
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Icantpy : ClientModInitializer, IcantpyPayload {
    const val MOD_ID: String = "icantpy"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    private val CONFIG_BODIES = setOf("", "gui")

    override fun onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("icantpy_loader")) {
            LOGGER.info("icantpy loader present; skipping standalone Fabric init")
            return
        }
        startStandalone()
    }

    override fun onLoad() {
        Composite.init(MOD_ID)
        TickTimers.load()
        LeapOrient.load()
        CustomRename.load()
        CommandWaypoints.reset()
        CustomRenameEditorSession.close()
        ItemCustomizeClock.reset()
        LOGGER.info("icantpy payload loaded")
    }

    override fun onUnload() {
        IcantpyGui.closeIfOpen()
        CustomRenameEditorSession.close()
        CustomRename.clearTransientCache()
        ItemCustomizeClock.reset()
        DungeonListener.reset()
        LeapOrient.onDisconnect()
        LOGGER.info("icantpy payload unloaded")
    }

    override fun onOutgoingChat(message: String): Boolean {
        if (CustomRename.isEditorCommand(message)) {
            IcantpyGui.showItemCustomize()
            return true
        }
        ItemInfo.handle(message)?.let { reply ->
            tell(reply)
            return true
        }
        CustomRename.handleCommand(message)?.let { reply ->
            tell(reply)
            return true
        }
        CommandWaypoints.handleCommand(message)?.let { reply ->
            CommandWaypoints.tell(reply)
            return true
        }
        LeapOrient.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        TickTimers.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        if (isConfigCommand(message)) {
            IcantpyGui.toggle()
            return true
        }
        StatsArmor.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        Loadout.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        return false
    }

    private fun isConfigCommand(message: String): Boolean {
        val body = IcantpyCommandPrefix.body(message) ?: return false
        return body.lowercase() in CONFIG_BODIES
    }

    override fun onIncomingChat(message: String) {
        TickTimers.onChat(message)
        LeapOrient.onIncomingChat(message)
        MaskTimers.onChat(message)
    }

    override fun onBlock(pos: BlockPos, state: BlockState) {
        TickTimers.onBlock(pos, state)
    }

    override fun onBossBar(name: String, progress: Float) {
        LeapOrient.onBossBar(name, progress)
    }

    override fun onRenderWorld() {
        WorldEsp.render()
    }

    override fun onRenderHud(graphics: GuiGraphicsExtractor) {
        IcantpyHud.render(graphics)
    }

    override fun onTick() {
        IcantpyGui.tick()
        StatsArmor.tick()
        Loadout.tick()
        DungeonListener.tick()
        TickTimers.onClientTick()
        LeapOrient.onTick()
        CommandWaypoints.tick()
    }

    override fun onServerTick() {
        MaskTimers.onServerTick()
        if (DungeonListener.state.inBoss) LeapOrient.bossTracker.onServerTick()
        TickTimers.onServerTick()
    }

    override fun onDisconnect() {
        TickTimers.reset(force = true)
        DungeonListener.reset()
        LeapOrient.onDisconnect()
        CommandWaypoints.reset()
        StatsArmor.onDisconnect()
        Loadout.onDisconnect()
        MaskTimers.onDisconnect()
        if (CustomRenameEditorSession.current() != null) CustomRename.persist()
        CustomRenameEditorSession.close()
        CustomRename.clearTransientCache()
        ItemCustomizeClock.reset()
    }

    override fun openGui() {
        IcantpyGui.toggle()
    }

    override fun openStats() {
        StatsArmor.openAltered()
    }

    override fun openLoadout() {
        Loadout.openAltered()
    }

    override fun addWaypointAtLook() {
        CommandWaypoints.tell(CommandWaypoints.addAtLook())
    }

    override fun customItemName(stack: ItemStack, vanilla: Component): Component =
        CustomRename.customItemName(stack, vanilla)

    override fun customGlintOverride(stack: ItemStack): Boolean? = CustomRename.customGlintOverride(stack)

    override fun customGlintColor(stack: ItemStack): Int? = CustomRename.customGlintColor(stack)

    override fun customLeatherColor(stack: ItemStack, vanilla: Int): Int =
        CustomRename.customLeatherColor(stack, vanilla)

    override fun customTooltip(stack: ItemStack): List<Component> = CustomRename.customTooltip(stack)

    override fun wantsLeapMenu(title: String): Boolean = LeapMenu.shouldTakeOver(title)

    override fun openLeapMenu(menu: AbstractContainerMenu, title: Component): Boolean {
        if (!LeapMenu.shouldTakeOver(title.string)) return false
        LeapMenu.openLive(menu, title)
        return true
    }

    override fun renderLeapContainerOverlay(
        screen: Screen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ): Boolean {
        val chest = screen as? AbstractContainerScreen<*> ?: return false
        return LeapMenu.renderOverlay(chest, graphics, mouseX, mouseY, partialTick)
    }

    override fun leapMouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean): Boolean? {
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        return LeapMenu.mouseClicked(chest, event, doubleClick)
    }

    override fun leapMouseReleased(screen: Screen, event: MouseButtonEvent): Boolean? {
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        return LeapMenu.mouseReleased(chest, event)
    }

    override fun leapKeyPressed(screen: Screen, event: KeyEvent): Boolean? {
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        return LeapMenu.keyPressed(chest, event)
    }

    override fun leapOverlayReady(screen: Screen): Boolean = LeapMenu.overlayReady(screen)

    override fun hideOdinLeapMenu(): Boolean = LeapMenu.shouldTakeOver()

    override fun wantsStatsMenu(title: String): Boolean = StatsArmor.wantsMenu(title)

    override fun openStatsMenu(menu: AbstractContainerMenu, title: Component): Boolean =
        StatsArmor.openMenu(menu, title)

    override fun wantsLoadoutMenu(title: String): Boolean = Loadout.wantsMenu(title)

    override fun openLoadoutMenu(menu: AbstractContainerMenu, title: Component): Boolean =
        Loadout.openMenu(menu, title)

    fun id(path: String): Identifier =
        Identifier.fromNamespaceAndPath(MOD_ID, path)

    private fun tell(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(text))
    }

    private fun startStandalone() {
        Composite.init(MOD_ID)
        IcantpyBridge.setPayload(this)
        IcantpyGui.register()
        TickTimers.load()
        LeapOrient.load()
        CustomRename.load()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(IcantpyClientCommands.tree("icantpy"))
            dispatcher.register(IcantpyClientCommands.tree("crypt"))
        }
        HudElementRegistry.addLast(id("tick-timers")) { graphics, _ ->
            IcantpyHud.render(graphics)
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            IcantpyGui.tick()
            StatsArmor.tick()
            Loadout.tick()
            DungeonListener.tick()
            TickTimers.onClientTick()
            LeapOrient.onTick()
            CommandWaypoints.tick()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            IcantpyBridge.onDisconnect()
        }
        LOGGER.info("icantpy loaded as a normal Fabric mod")
    }
}
