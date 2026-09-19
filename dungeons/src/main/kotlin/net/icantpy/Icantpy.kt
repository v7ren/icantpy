package net.icantpy

import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyClientCommands
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.api.IcantpyPayload
import net.icantpy.api.IcantpyQueryResult
import net.icantpy.api.IcantpyRuntimeEvent
import net.icantpy.api.IcantpyRuntimeQuery
import net.icantpy.gui.Composite
import net.icantpy.gui.IcantpyGui
import net.icantpy.gui.customize.HeadTextures
import net.icantpy.cosmetics.items.AppearanceOwnerScope
import net.icantpy.cosmetics.items.CustomCosmeticsShare
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameEditorSession
import net.icantpy.qol.camera.Freelook
import net.icantpy.cosmetics.items.ItemCustomizeClock
import net.icantpy.cosmetics.items.ItemInfo
import net.icantpy.cosmetics.items.RenameVisuals
import net.icantpy.cosmetics.morph.MorphCameraLook
import net.icantpy.cosmetics.morph.MorphCrosshair
import net.icantpy.cosmetics.morph.PlayerDisguise
import net.icantpy.dungeon.DropUltimate
import net.icantpy.dungeon.DungeonListener
import net.icantpy.dungeon.leap.LeapMenu
import net.icantpy.dungeon.leap.LeapOrient
import net.icantpy.hud.IcantpyHud
import net.icantpy.hud.WorldEsp
import net.icantpy.slayer.SlayerCarryCounter
import net.icantpy.qol.stats.MaskTimers
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.qol.loadout.Loadout
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.qol.waypoint.CommandWaypoints
import net.icantpy.qol.shards.ShardChecklistController
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
        ShardChecklistController.load()
        Composite.init(MOD_ID)
        TickTimers.load()
        LeapOrient.load()
        CustomRename.load()
        Freelook.load()
        PlayerDisguise.load()
        CommandWaypoints.reset()
        CustomRenameEditorSession.close()
        ItemCustomizeClock.reset()
        LOGGER.info("icantpy payload loaded")
    }

    override fun onUnload() {
        ShardChecklistController.onUnload()
        IcantpyGui.closeIfOpen()
        CustomRenameEditorSession.close()
        CustomCosmeticsShare.onUnload()
        CustomRename.clearTransientCache()
        Freelook.onUnload()
        PlayerDisguise.onUnload()
        ItemCustomizeClock.reset()
        DungeonListener.reset()
        DropUltimate.reset()
        SlayerCarryCounter.reset()
        LeapOrient.onDisconnect()
        LOGGER.info("icantpy payload unloaded")
    }

    override fun onOutgoingChat(message: String): Boolean {
        ShardChecklistController.handleCommand(message)?.let { return true }
        if (CustomRename.isEditorCommand(message)) {
            IcantpyGui.showItemCustomize()
            return true
        }
        PlayerDisguise.handleCommand(message)?.let { reply ->
            tell(reply)
            return true
        }
        ItemInfo.handle(message)?.let { reply ->
            tell(reply)
            return true
        }
        RenameVisuals.handle(message)?.let { reply ->
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
        SlayerCarryCounter.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        if (isConfigCommand(message)) {
            IcantpyGui.toggle()
            return true
        }
        if (isCustomCommand(message)) {
            IcantpyGui.showCustomize()
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

    private fun isCustomCommand(message: String): Boolean {
        val body = IcantpyCommandPrefix.body(message) ?: return false
        return body.trim().lowercase() == "custom"
    }

    override fun onIncomingChat(message: String) {
        ShardChecklistController.onIncomingChat(message)
        TickTimers.onChat(message)
        LeapOrient.onIncomingChat(message)
        MaskTimers.onChat(message)
    }

    override fun onBlock(pos: BlockPos, state: BlockState) {
        TickTimers.onBlock(pos, state)
    }

    override fun onBossBar(name: String, progress: Float) {
        TickTimers.onBossBar(name, progress)
        LeapOrient.onBossBar(name, progress)
    }

    override fun onRenderWorld() {
        WorldEsp.render()
    }

    override fun onRenderHud(graphics: GuiGraphicsExtractor) {
        IcantpyHud.render(graphics)
    }

    override fun onTick() {
        ShardChecklistController.tick()
        IcantpyGui.tick()
        StatsArmor.tick()
        Loadout.tick()
        DungeonListener.tick()
        TickTimers.onClientTick()
        LeapOrient.onTick()
        CommandWaypoints.tick()
        CustomCosmeticsShare.tick()
        HeadTextures.tick()
        Freelook.tick()
        DropUltimate.tick()
        SlayerCarryCounter.tick()
    }

    override fun onServerTick() {
        MaskTimers.onServerTick()
        if (DungeonListener.state.inBoss) LeapOrient.bossTracker.onServerTick()
        TickTimers.onServerTick()
    }

    override fun onDisconnect() {
        ShardChecklistController.onDisconnect()
        TickTimers.reset(force = true)
        DungeonListener.reset()
        DropUltimate.reset()
        SlayerCarryCounter.reset()
        LeapOrient.onDisconnect()
        CommandWaypoints.reset()
        StatsArmor.onDisconnect()
        Loadout.onDisconnect()
        MaskTimers.onDisconnect()
        PlayerDisguise.onDisconnect()
        Freelook.onUnload()
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

    override fun customItemModel(stack: ItemStack): String? = CustomRename.customItemModel(stack)

    override fun customHeadTexture(stack: ItemStack): String? = CustomRename.customHeadTexture(stack)

    override fun customTrim(stack: ItemStack): Any? = CustomRename.customTrim(stack)

    override fun renderProxy(entity: net.minecraft.world.entity.Entity, partialTick: Float): net.minecraft.world.entity.Entity? =
        PlayerDisguise.renderProxy(entity, partialTick)

    override fun adaptRenderState(
        entity: net.minecraft.world.entity.Entity,
        proxy: net.minecraft.world.entity.Entity,
        state: net.minecraft.client.renderer.entity.state.EntityRenderState,
        partialTick: Float,
    ) {
        PlayerDisguise.adaptRenderState(entity, proxy, state, partialTick)
    }

    override fun dispatch(event: IcantpyRuntimeEvent): IcantpyDispatchResult {
        if (event.id == "lifecycle.start_tick") {
            Freelook.tick()
            return IcantpyDispatchResult.PASS
        }
        if (event.id == "render.appearance.owner") {
            AppearanceOwnerScope.handle(event)
            return IcantpyDispatchResult.HANDLED
        }
        return super.dispatch(event)
    }

    override fun query(query: IcantpyRuntimeQuery): IcantpyQueryResult {
        if (query.id == "input.attack.start" || query.id == "input.attack.pre") {
            val clickCount = (query.context["clickCount"] as? Number)?.toInt() ?: 1
            if (clickCount > 0 && net.icantpy.dungeon.leap.LeapAutoLeapController.onAttack()) {
                // pre-attack expects true to cancel; startAttack expects false (no attack).
                return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, query.id == "input.attack.pre")
            }
        }
        if (query.id == "input.keyboard.key") {
            val event = query.context["event"] as? KeyEvent
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val action = (query.context["action"] as? Number)?.toInt()
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val handled = DropUltimate.handleKey(event, action) || SlayerCarryCounter.handleKey(event, action)
            return if (handled) {
                IcantpyQueryResult(IcantpyDispatchResult.HANDLED)
            } else {
                IcantpyQueryResult(IcantpyDispatchResult.PASS)
            }
        }
        if (query.id == "input.player.turn") {
            val consumed = Freelook.consumeTurn(query.context["yaw"], query.context["pitch"])
            return if (consumed) IcantpyQueryResult(IcantpyDispatchResult.HANDLED, true)
            else IcantpyQueryResult(IcantpyDispatchResult.PASS)
        }
        if (query.id == "render.camera.look_direction") {
            val camera = query.context["camera"] as? net.minecraft.client.Camera
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val freelook = Freelook.queryLook(camera)
            if (freelook.result != IcantpyDispatchResult.PASS) return freelook
            return MorphCameraLook.query(camera)
        }
        if (query.id == "render.hud.crosshair.offset") {
            val width = query.context["width"] as? Int ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val height = query.context["height"] as? Int ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val partialTick = query.context["partialTick"] as? Float ?: 0f
            return MorphCrosshair.query(width, height, partialTick)
        }
        if (query.id == "render.camera.eye_height") {
            val entity = query.context["entity"] as? net.minecraft.world.entity.Entity
            val height = PlayerDisguise.cameraEyeHeight(entity) ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, height)
        }
        if (query.id == "input.use.item") {
            val player = query.context["player"] as? net.minecraft.world.entity.player.Player
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val world = query.context["world"] as? net.minecraft.world.level.Level
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val hand = query.context["hand"] as? net.minecraft.world.InteractionHand
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            return IcantpyQueryResult(
                IcantpyDispatchResult.HANDLED,
                LeapOrient.onUseItem(player, world, hand),
            )
        }
        return super.query(query)
    }

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
        ShardChecklistController.load()
        IcantpyBridge.setPayload(this)
        IcantpyGui.register()
        TickTimers.load()
        LeapOrient.load()
        CustomRename.load()
        Freelook.load()
        PlayerDisguise.load()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(IcantpyClientCommands.tree("icantpy"))
            dispatcher.register(IcantpyClientCommands.tree("crypt"))
        }
        HudElementRegistry.addLast(id("tick-timers")) { graphics, _ ->
            IcantpyHud.render(graphics)
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            ShardChecklistController.tick()
            IcantpyGui.tick()
            StatsArmor.tick()
            Loadout.tick()
            DungeonListener.tick()
            TickTimers.onClientTick()
            LeapOrient.onTick()
            CommandWaypoints.tick()
            CustomCosmeticsShare.tick()
            HeadTextures.tick()
            Freelook.tick()
            DropUltimate.tick()
            SlayerCarryCounter.tick()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ShardChecklistController.onDisconnect()
            IcantpyBridge.onDisconnect()
        }
        LOGGER.info("icantpy loaded as a normal Fabric mod")
    }
}
