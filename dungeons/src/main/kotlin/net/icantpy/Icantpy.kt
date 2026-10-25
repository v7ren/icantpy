package net.icantpy

import net.icantpy.api.AppearanceOwnerHook
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
import net.icantpy.qol.visual.Visuals
import net.icantpy.qol.perf.Culling
import net.icantpy.qol.perf.FrameGen
import net.icantpy.qol.perf.MotionBlur
import net.icantpy.qol.perf.PerfHud
import net.icantpy.qol.perf.PerformancePreset
import net.icantpy.cosmetics.items.ItemCustomizeClock
import net.icantpy.cosmetics.items.ItemInfo
import net.icantpy.cosmetics.items.RenameVisuals
import net.icantpy.cosmetics.morph.MorphCameraLook
import net.icantpy.cosmetics.morph.MorphCrosshair
import net.icantpy.cosmetics.morph.PlayerDisguise
import net.icantpy.dungeon.DropUltimate
import net.icantpy.dungeon.DungeonListener
import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.dungeon.leap.LeapDungeonClass
import net.icantpy.dungeon.leap.LeapMenu
import net.icantpy.dungeon.leap.LeapRoster
import net.icantpy.dungeon.leap.LeapOrient
import net.icantpy.location.SkyblockLocation
import net.icantpy.hud.IcantpyHud
import net.icantpy.hud.WorldEsp
import net.icantpy.slayer.SlayerCarryCounter
import net.icantpy.slayer.blaze.FirePit
import net.icantpy.slayer.blaze.QuaziiBeamDodge
import net.icantpy.slayer.aim.BossAim
import net.icantpy.slayer.aim.BossStrafe
import net.icantpy.qol.path.Navigator
import net.icantpy.qol.stats.MaskTimers
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.qol.loadout.Loadout
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.qol.waypoint.CommandWaypoints
import net.icantpy.qol.shards.ShardChecklistController
import net.icantpy.qol.invsort.InvSortController
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.dungeon.score.ScoreTracker
import net.icantpy.dungeon.splits.RunSplitsController
import net.icantpy.dungeon.terminal.TerminalOverlay
import net.icantpy.dungeon.terminal.TerminalSimulator
import net.icantpy.dungeon.terminal.TerminalUserFonts
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.event.player.UseItemCallback
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
import org.slf4j.LoggerFactory

object Icantpy : ClientModInitializer, IcantpyPayload {
    const val MOD_ID: String = "icantpy"
    val LOGGER: IcantpyLog = IcantpyLog(LoggerFactory.getLogger(MOD_ID))
    private val CONFIG_BODIES = setOf("", "gui")

    override fun onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("icantpy_loader")) {
            LOGGER.info("icantpy loader present; skipping standalone Fabric init")
            return
        }
        net.icantpy.proxy.ProxyClient.init()
        startStandalone()
    }

    override fun onLoad() {
        ShardChecklistController.load()
        InvSortController.load()
        Composite.init(MOD_ID)
        TickTimers.load()
        LeapOrient.load()
        net.icantpy.hud.markers.PlayerMarkers.classResolver = { token -> LeapRoster.nameForMarkerClass(token) }
        loadDragons()
        CustomRename.load()
        Freelook.load()
        MotionBlur.load()
        PerformancePreset.load()
        Culling.load()
        Visuals.load()
        FirePit.load()
        FrameGen.load()
        PerfHud.load()
        PlayerDisguise.load()
        ScoreTracker.load()
        TerminalOverlay.load()
        BossAim.load()
        Navigator.load()
        CommandWaypoints.reset()
        CustomRenameEditorSession.close()
        ItemCustomizeClock.reset()
        LOGGER.info("icantpy payload loaded")
    }

    override fun onUnload() {
        AppearanceOwnerHook.wanted = true
        net.icantpy.hud.markers.PlayerMarkerRenderer.releaseGlow()
        net.icantpy.hud.markers.PlayerMarkers.clearAll()
        ShardChecklistController.onUnload()
        IcantpyGui.closeIfOpen()
        CustomRenameEditorSession.close()
        CustomCosmeticsShare.onUnload()
        AlertSounds.onUnload()
        InvSortController.onUnload()
        CustomRename.clearTransientCache()
        Freelook.onUnload()
        MotionBlur.unload()
        FrameGen.unload()
        PerfHud.unload()
        Culling.unload()
        Visuals.unload()
        FirePit.unload()
        QuaziiBeamDodge.reset()
        BossStrafe.reset()
        BossAim.unload()
        Navigator.unload()
        PlayerDisguise.onUnload()
        ItemCustomizeClock.reset()
        DungeonListener.reset()
        DropUltimate.reset()
        SlayerCarryCounter.reset()
        DragonController.onUnload()
        net.icantpy.hud.markers.PlayerMarkers.clearAll()
        LeapOrient.onDisconnect()
        RunSplitsController.onUnload()
        ScoreTracker.onDisconnect()
        TerminalSimulator.stop()
        TerminalOverlay.reset()
        TerminalUserFonts.close()
        LOGGER.info("icantpy payload unloaded")
        IcantpyLogFile.close()
    }

    override fun onOutgoingChat(message: String): Boolean {
        ShardChecklistController.handleCommand(message)?.let { return true }
        InvSortController.handleCommand(message)?.let { return true }
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
        SkyblockLocation.handleCommand(message)?.let { reply ->
            tell(reply)
            return true
        }
        LeapOrient.handleCommand(message)?.let { reply ->
            if (reply.isNotEmpty()) tell(reply)
            return true
        }
        TerminalSimulator.handleCommand(message)?.let { reply ->
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
        Navigator.handleCommand(message)?.let { reply ->
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
        DragonController.onChat(message)
        MaskTimers.onChat(message)
        QuaziiBeamDodge.onChat(message)
        Loadout.onChat(message)
    }

    override fun onBlock(pos: BlockPos, state: BlockState) {
        TickTimers.onBlock(pos, state)
    }

    override fun onBossBar(name: String, progress: Float) {
        TickTimers.onBossBar(name, progress)
        LeapOrient.onBossBar(name, progress)
    }

    override fun onRenderWorld() {
        PerfHud.onFrameStart()
        MotionBlur.onFrameStart()
        Culling.onFrameStart()
        Visuals.onFrameStart()
        BossAim.onFrameStart()
        Navigator.onFrameStart()
        AppearanceOwnerScope.syncWanted()
        WorldEsp.render()
    }

    override fun onRenderHud(graphics: GuiGraphicsExtractor) {
        IcantpyHud.render(graphics)
        PerfHud.render(graphics)
    }

    override fun onTick() {
        IcantpyLogFile.flush()
        ShardChecklistController.tick()
        InvSortController.tick()
        IcantpyGui.tick()
        StatsArmor.tick()
        Loadout.tick()
        DungeonListener.tick()
        TickTimers.onClientTick()
        DragonController.tick(Minecraft.getInstance())
        LeapOrient.onTick()
        RunSplitsController.tick()
        ScoreTracker.tick()
        TerminalSimulator.tick()
        TerminalOverlay.tick()
        CommandWaypoints.tick()
        CustomCosmeticsShare.tick()
        HeadTextures.tick()
        Freelook.tick()
        Visuals.tick()
        FirePit.tick()
        BossAim.tick()
        BossStrafe.tick()
        Navigator.tick()
        DropUltimate.tick()
        SlayerCarryCounter.tick()
    }

    override fun onServerTick() {
        DragonController.onServerTick()
        MaskTimers.onServerTick()
        if (DungeonListener.state.inBoss) LeapOrient.bossTracker.onServerTick()
        TickTimers.onServerTick()
    }

    override fun onDisconnect() {
        DragonController.onDisconnect()
        net.icantpy.hud.markers.PlayerMarkers.clearAll()
        ShardChecklistController.onDisconnect()
        InvSortController.onDisconnect()
        IcantpyGui.clearPending()
        TickTimers.reset(force = true)
        DungeonListener.reset()
        DropUltimate.reset()
        SlayerCarryCounter.reset()
        LeapOrient.onDisconnect()
        RunSplitsController.onDisconnect()
        ScoreTracker.onDisconnect()
        TerminalSimulator.stop()
        TerminalOverlay.reset()
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
        // Hot path: fires twice per entity per frame, so it is checked before the lifecycle/dragon ids.
        if (event.id == "render.appearance.owner") {
            AppearanceOwnerScope.handle(event)
            return IcantpyDispatchResult.HANDLED
        }
        if (event.id == "lifecycle.start_tick") {
            Freelook.tick()
            QuaziiBeamDodge.startTick()
            return IcantpyDispatchResult.PASS
        }
        if (event.id == DRAGON_FLAME_EVENT) forwardDragonFlame(event.context)
        return super.dispatch(event)
    }

    override fun query(query: IcantpyRuntimeQuery): IcantpyQueryResult {
        if (query.id == "input.attack.start" || query.id == "input.attack.pre") {
            BossStrafe.onAttack()
            val clickCount = (query.context["clickCount"] as? Number)?.toInt() ?: 1
            if (clickCount > 0 && net.icantpy.dungeon.leap.LeapAutoLeapController.onAttack()) {
                // pre-attack expects true to cancel; startAttack expects false (no attack).
                return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, query.id == "input.attack.pre")
            }
        }
        if (query.id == "render.entity.glow") {
            val entityId = (query.context["entityId"] as? Number)?.toInt()
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            val color = net.icantpy.hud.markers.PlayerMarkers.glowColorFor(entityId)
                ?: return IcantpyQueryResult(IcantpyDispatchResult.PASS)
            return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, color)
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
        if (TerminalOverlay.render(chest, graphics, mouseX, mouseY)) return true
        return LeapMenu.renderOverlay(chest, graphics, mouseX, mouseY, partialTick)
    }

    // Container HEAD hooks run before vanilla handling. The auto-run input lock is applied first so it
    // holds for every container, including the ones an auto run opens; a non-null result cancels vanilla.
    override fun leapMouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean): Boolean? {
        net.icantpy.qol.auto.AutoInputGate.mixinVerdict(net.icantpy.qol.auto.AutoInputEvent.MOUSE_PRESS, event)
            ?.let { return it }
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        TerminalOverlay.mouseClicked(chest, event)?.let { return it }
        return LeapMenu.mouseClicked(chest, event, doubleClick)
    }

    override fun leapMouseReleased(screen: Screen, event: MouseButtonEvent): Boolean? {
        net.icantpy.qol.auto.AutoInputGate.mixinVerdict(net.icantpy.qol.auto.AutoInputEvent.OTHER, event)
            ?.let { return it }
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        TerminalOverlay.mouseReleased(chest)?.let { return it }
        return LeapMenu.mouseReleased(chest, event)
    }

    override fun leapKeyPressed(screen: Screen, event: KeyEvent): Boolean? {
        val kind = if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            net.icantpy.qol.auto.AutoInputEvent.ESCAPE
        } else {
            net.icantpy.qol.auto.AutoInputEvent.OTHER
        }
        net.icantpy.qol.auto.AutoInputGate.mixinVerdict(kind, event)?.let { return it }
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        return LeapMenu.keyPressed(chest, event)
    }

    override fun leapOverlayReady(screen: Screen): Boolean =
        TerminalOverlay.overlayReady(screen) || LeapMenu.overlayReady(screen)

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

    private const val DRAGON_FLAME_EVENT = "world.particle.flame"

    /** Passes the tracker the dragon spawn particle fields. Missing or mistyped fields are dropped. */
    private fun forwardDragonFlame(context: Map<String, Any?>) {
        val x = context["x"] as? Double ?: return
        val y = context["y"] as? Double ?: return
        val z = context["z"] as? Double ?: return
        val count = (context["count"] as? Number)?.toInt() ?: return
        val xDist = (context["xDist"] as? Number)?.toFloat() ?: return
        val yDist = (context["yDist"] as? Number)?.toFloat() ?: return
        val zDist = (context["zDist"] as? Number)?.toFloat() ?: return
        val maxSpeed = (context["maxSpeed"] as? Number)?.toFloat() ?: return
        DragonController.onParticleEvent("flame", x, y, z, count, xDist, yDist, zDist, maxSpeed)
    }

    /** Binds live player state for spawn priority. Power and time blessing levels are not tracked yet, so they read as 0 and off. */
    private fun loadDragons() {
        DragonController.load(FabricLoader.getInstance().configDir.resolve("icantpy-dragons.json"))
        DragonController.bindPriorityInputs(
            playerClass = { LeapRoster.selfClass(LeapOrient.settings()).takeIf { it.isReal() }?.name },
            powerBlessing = { 0 },
            timeBlessing = { false },
        )
    }

    private fun startStandalone() {
        Composite.init(MOD_ID)
        ShardChecklistController.load()
        InvSortController.load()
        IcantpyBridge.setPayload(this)
        IcantpyGui.register()
        TickTimers.load()
        LeapOrient.load()
        loadDragons()
        CustomRename.load()
        Freelook.load()
        MotionBlur.load()
        PerformancePreset.load()
        Culling.load()
        Visuals.load()
        FirePit.load()
        FrameGen.load()
        PerfHud.load()
        PlayerDisguise.load()
        ScoreTracker.load()
        TerminalOverlay.load()
        BossAim.load()
        Navigator.load()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(IcantpyClientCommands.tree("icantpy"))
            dispatcher.register(IcantpyClientCommands.tree("crypt"))
        }
        HudElementRegistry.addLast(id("tick-timers")) { graphics, _ ->
            IcantpyHud.render(graphics)
            PerfHud.render(graphics)
        }
        ClientTickEvents.START_CLIENT_TICK.register { QuaziiBeamDodge.startTick() }
        // The loader forwards this as the "input.use.item" query; without it, standalone registers it directly.
        UseItemCallback.EVENT.register { player, world, hand -> LeapOrient.onUseItem(player, world, hand) }
        ClientTickEvents.END_CLIENT_TICK.register {
            IcantpyLogFile.flush()
            ShardChecklistController.tick()
            InvSortController.tick()
            IcantpyGui.tick()
            StatsArmor.tick()
            Loadout.tick()
            DungeonListener.tick()
            TickTimers.onClientTick()
            DragonController.tick(Minecraft.getInstance())
            LeapOrient.onTick()
            RunSplitsController.tick()
            ScoreTracker.tick()
            TerminalSimulator.tick()
            TerminalOverlay.tick()
            CommandWaypoints.tick()
            CustomCosmeticsShare.tick()
            HeadTextures.tick()
            Freelook.tick()
            Visuals.tick()
            FirePit.tick()
            BossAim.tick()
            BossStrafe.tick()
            Navigator.tick()
            DropUltimate.tick()
            SlayerCarryCounter.tick()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ShardChecklistController.onDisconnect()
            InvSortController.onDisconnect()
            IcantpyGui.clearPending()
            RunSplitsController.onDisconnect()
            DragonController.onDisconnect()
            ScoreTracker.onDisconnect()
            TerminalOverlay.reset()
            IcantpyBridge.onDisconnect()
        }
        LOGGER.info("icantpy loaded as a normal Fabric mod")
    }
}
