package net.icantpy.gui.config

import net.icantpy.gui.GuiHit
import net.icantpy.gui.IcantpyGui
import net.icantpy.gui.McUi
import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.icantpy.gui.clampScroll
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.qol.camera.Freelook
import net.icantpy.qol.camera.FreelookConfig
import net.icantpy.qol.visual.AmbientStyle
import net.icantpy.qol.visual.HandShaderMode
import net.icantpy.qol.visual.SwingMode
import net.icantpy.qol.visual.TrailStyle
import net.icantpy.qol.visual.Visuals
import net.icantpy.qol.visual.VisualsConfig
import net.icantpy.qol.perf.Culling
import net.icantpy.qol.perf.FrameGen
import net.icantpy.qol.perf.FrameGenConfig
import net.icantpy.qol.perf.MotionBlur
import net.icantpy.qol.perf.MotionBlurConfig
import net.icantpy.qol.perf.PerfHud
import net.icantpy.qol.perf.PerformancePreset
import net.icantpy.util.HexColor
import net.icantpy.cosmetics.items.ItemCustomizeColorCodec
import net.icantpy.cosmetics.morph.PlayerDisguise
import net.icantpy.qol.shards.ShardChecklistController
import net.icantpy.cosmetics.morph.MorphCameraMode
import net.icantpy.dungeon.DropUltimateSettings
import net.icantpy.dungeon.leap.ClockCrossing
import net.icantpy.dungeon.leap.LeapEventLog
import net.icantpy.dungeon.leap.LeapHoldMode
import net.icantpy.dungeon.leap.LeapHoldSpec
import net.icantpy.dungeon.leap.LeapTriggerEventMenu
import net.icantpy.dungeon.leap.ChatChannel
import net.icantpy.dungeon.leap.ChatMessageMatch
import net.icantpy.dungeon.leap.ChatSender
import net.icantpy.dungeon.leap.ChatTextMode
import net.icantpy.dungeon.leap.LeapChatCooldown
import net.icantpy.location.SkyblockLocation
import net.icantpy.dungeon.leap.ClockMetric
import net.icantpy.dungeon.leap.ClockSpec
import net.icantpy.dungeon.leap.LeapBossDeathNotifier
import net.icantpy.dungeon.leap.LeapCenterTarget
import net.icantpy.dungeon.leap.LeapChatPatterns
import net.icantpy.dungeon.leap.LeapDungeonClass
import net.icantpy.dungeon.leap.LeapHealthHysteresis
import net.icantpy.dungeon.leap.LeapFloor
import net.icantpy.dungeon.leap.LeapGameState
import net.icantpy.dungeon.leap.LeapKeybindMode
import net.icantpy.dungeon.leap.LeapOrient
import net.icantpy.dungeon.leap.LeapOrientEngine
import net.icantpy.dungeon.leap.LeapOrientSettings
import net.icantpy.dungeon.leap.LeapOrientTrigger
import net.icantpy.dungeon.leap.LeapPhase
import net.icantpy.dungeon.leap.LeapPreferKind
import net.icantpy.dungeon.leap.LeapRoster
import net.icantpy.dungeon.leap.LeapStormRoute
import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapBranch
import net.icantpy.dungeon.leap.LeapConditions
import net.icantpy.dungeon.leap.LeapFact
import net.icantpy.dungeon.leap.LeapTerm
import net.icantpy.dungeon.leap.LeapAutoRouter
import net.icantpy.dungeon.leap.LeapAutoStep
import net.icantpy.dungeon.leap.LeapTriggerEvent
import net.icantpy.dungeon.leap.LeapTriggerCard
import net.icantpy.dungeon.leap.LeapTriggerCards
import net.icantpy.dungeon.leap.LeapMarkRouter
import net.icantpy.hud.markers.MarkerLook
import net.icantpy.hud.markers.MarkerShapes
import net.icantpy.hud.markers.MarkerStyle
import net.icantpy.dungeon.leap.OrientSpot
import net.icantpy.qol.auto.CursorGuard
import net.icantpy.qol.invsort.InvSortController
import net.icantpy.qol.invsort.InvSortEditorScreen
import net.icantpy.qol.loadout.LoadoutPlan
import net.icantpy.qol.fishing.FishingMacro
import net.icantpy.qol.fishing.FishingMacroConfig
import net.icantpy.qol.pets.PetMenu
import net.icantpy.qol.pets.PetSwap
import net.icantpy.qol.pets.PetSwapConfig
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.slayer.SlayerCarryCounter
import net.icantpy.slayer.blaze.BeamDodge
import net.icantpy.slayer.blaze.EscapeMode
import net.icantpy.slayer.blaze.FirePit
import net.icantpy.slayer.blaze.FirePitConfig
import net.icantpy.slayer.blaze.PitPhase
import net.icantpy.slayer.aim.AimActivation
import net.icantpy.slayer.aim.AimConfig
import net.icantpy.slayer.aim.AimEasing
import net.icantpy.slayer.aim.AimPoint
import net.icantpy.slayer.aim.BossAim
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.dungeon.score.ScoreTracker
import net.icantpy.dungeon.terminal.TerminalOverlay
import net.icantpy.dungeon.splits.RunSplitsController
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.dungeon.timer.TimerClock
import net.icantpy.dungeon.timer.TimerHud
import net.icantpy.dungeon.timer.TimerTrigger
import net.icantpy.qol.waypoint.CommandWaypoint
import net.icantpy.qol.waypoint.CommandWaypoints
import net.icantpy.qol.waypoint.SkyblockWorlds
import net.icantpy.qol.waypoint.WaypointWorld
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.DyedItemColor
import org.jetbrains.skia.Rect
import org.lwjgl.glfw.GLFW

private const val PAGE_MAX_CONTENT_WIDTH = 520
private const val OVERVIEW_WIDE_MIN_WIDTH = 480
private const val OVERVIEW_CARD_HEIGHT = 58
private const val OVERVIEW_CARD_GAP = 10
private const val OVERVIEW_TOGGLE_RESERVE = 48
private const val CONTENT_TOP_PAD = 12

/** How far above a section the outline scrolls to, and when a section counts as the one being read. */
private const val OUTLINE_LEAD = 12
/** Extra space above a section heading; grouped() already adds 8 after each block, so sections get 16. */
private const val SECTION_GAP = 8
private const val PET_FIELD_WIDTH = 150

/** Gap the pages leave between two sections; a section card ends this far above the next heading. */
private const val CARD_GAP = 8
private const val CARD_PAD = 14

/** Pages drawn as one card per section instead of one box per row. */
private val CARD_CONTENTS = setOf(
    ConfigContent.BLAZE_FIRE,
    ConfigContent.BLAZE_QUAZII,
    ConfigContent.BLAZE_MOBS,
    ConfigContent.BOSS_AIM,
    ConfigContent.BLOOD_HELPER,
    ConfigContent.LIVID_HELPER,
)
private const val SCROLL_STEP = 22
private const val LEAP_ROW_CONTROL_HEIGHT = 24
private const val LEAP_CHEVRON_SIZE = 8

/** Width of the "+ Add action" button, right-aligned in the Actions row. */
private const val ADD_ACTION_BUTTON_WIDTH = 120
private const val LEAP_VIEW_KEY = "leap-view"
private const val MIN_LEAP_DROPDOWN_VIEW = 44
private val LEAP_PHASE_CHOICES = listOf(LeapPhase.ANY, LeapPhase.P1, LeapPhase.P2, LeapPhase.P3, LeapPhase.P4, LeapPhase.P5)
private val LEAP_PHASE_LABELS = listOf("Any phase", "P1", "P2", "P3", "P4", "P5")
private val LEAP_FLOOR_CHOICES = listOf(LeapFloor.ANY, LeapFloor.F7, LeapFloor.M7)
private val LEAP_FLOOR_LABELS = listOf("Any floor", "F7", "M7")
private val LEAP_ROUTE_CHOICES = listOf(LeapStormRoute.ANY, LeapStormRoute.PY, LeapStormRoute.GY)
private val LEAP_ROUTE_LABELS = listOf("Any route", "PY", "GY")
private val LEAP_SECTION_LABELS = listOf("Any section", "S1", "S2", "S3", "S4")

class IcantpyConfigScreen : Screen(Component.literal("icantpy")) {
    private val surface = SkiaSurface()
    private var page = ConfigUiSession.page
    private var subtab = ConfigUiSession.subtabOf(page)
    private val aetherOverrides = HashMap<AetherModuleId, Boolean>()
    private var descriptionHoverKey: String? = null
    private var descriptionHoverSince = 0L
    private var scroll = ConfigUiSession.scrollOf(page, subtab)

    /**
     * Section cards. A card's background has to be painted before its rows, so its height is the one measured
     * on the previous frame (keyed by page, subtab and title); the first frame draws no background.
     */
    private val cardExtent = HashMap<String, Int>()

    /** Sidebar filter: only pages whose feature is switched on. */
    private var onlyEnabled = false

    /** Section titles and their content offsets from the frame being drawn, and the finished list shown in the outline. */
    private val anchorsNext = ArrayList<Pair<String, Int>>()
    private var anchors: List<Pair<String, Int>> = emptyList()
    private var contentOrigin = 0
    private var cardKey: String? = null
    private var cardTop = 0
    private var cardRows = 0
    private var sidebarScroll = 0
    private var contentHeight = 0
    private var hits: List<GuiHit> = emptyList()

    /** The slider being dragged, until the mouse button is released. */
    private var dragging: ((Int) -> Unit)? = null

    /** Last value a slider drag committed, so a drag only saves when the snapped value changes. */
    private var sliderLast = Float.NaN
    private val fields = HashMap<String, String>()
    private val shardPage = ShardChecklistPage()
    private val splitsPage = SplitsPage()
    private val scorePage = ScorePage()
    private val dragonsPage = DragonsPage()
    private val terminalsPage = TerminalsPage()
    private var focused: String? = null
    private var toast: String? = null
    private var toastUntil = 0L
    private var renameItem: String? = null
    private var capturingBind: String? = null
    private var openDebugDropdown: String? = null
    private var leapDropdownSpec: LeapDropdownSpec? = null
    private var openDropdownRects: List<LayoutRect> = emptyList()
    private var pendingRevealId: String? = null
    private var debugBossToken = "storm"
    private var debugFireEvent = LeapTriggerEvent.SECRETS_REACHED
    private var debugPartyToken = "arch"
    private var renamePreview: RenamePreview? = null
    private var metrics = ConfigMetrics.compute(640, 360, GuiLookSettings())

    private data class RenamePreview(val stack: net.minecraft.world.item.ItemStack, val x: Int, val y: Int)

    private data class LeapDropdownSpec(
        val anchor: LayoutRect,
        val x: Int,
        val width: Int,
        val lines: List<DropdownLine>,
        val selected: Int,
        val onSelect: (Int) -> Unit,
    )

    /** Scroll offset of the open leap dropdown, in pixels. Reset when a dropdown opens. */
    private var leapDropdownScroll = 0
    /** Set when a dropdown opens so its selected line is scrolled into view once. */
    private var leapDropdownFocusPending = false

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        SkiaContext.initialize()
        McUi.releaseMouse(Minecraft.getInstance())
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (TickTimers.settings.look.backgroundBlur) {
            extractBlurredBackground(graphics)
        }
        extractTransparentBackground(graphics)
    }

    override fun removed() {
        ConfigUiSession.save(page, subtab, scroll)
        super.removed()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        // The config window always uses the rail shell; older saved layouts map onto it.
        val chrome = GuiChrome.of(TickTimers.settings.look)
        metrics = shellMetrics()
        val m = metrics
        leapDropdownSpec = null
        openDropdownRects = emptyList()
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, m.viewTop, m.viewBottom, m.contentLeft, m.panelX + m.panelW - m.toc, mouseX, mouseY, chrome)
            paintShell(draw, m)
            paintPageContent(draw, m)
            paintOutline(draw, m)
            draw.unclipped { drawLeapDropdownOverlay(draw) }
            if (searchOpen()) draw.unclipped { draw.paintSearchResults(m, ConfigNav.search(field(SEARCH_FIELD_ID)), ::openFromSearch) }
            draw.paintTooltip()
            draw.unclipped { paintToast(draw, m) }
            paintContentScrollbar(draw, m)
            hits = draw.hits
        }
        renamePreview?.let { preview ->
            if (preview.y in (m.viewTop - 24)..m.viewBottom) {
                graphics.item(preview.stack, preview.x, preview.y)
            }
        }
        if (contentHeight > 0) {
            scroll = clampScroll(scroll, contentHeight, m.viewHeight)
            ConfigUiSession.save(page, subtab, scroll)
        }
        sidebarScroll = clampScroll(sidebarScroll, sidebarContentHeight(sidebarState()), sidebarBottom(m) - sidebarTop(m))
    }

    private fun shellMetrics(): ConfigMetrics {
        val base = ConfigMetrics.compute(width, height, TickTimers.settings.look.copy(layout = GuiLayoutId.RAIL))
        return base.copy(
            pageBar = if (page.subtabs.isEmpty()) 0 else base.pageBar,
            maxContentWidth = PAGE_MAX_CONTENT_WIDTH,
        )
    }

    /** Paints the window frame, the page list and the header bar (sidebar stays in its own clip). */
    private fun paintShell(draw: MenuDraw, m: ConfigMetrics) {
        draw.paintShellFrame(m)
        draw.paintSidebar(
            m, page, sidebarScroll, sidebarState(), field(SEARCH_FIELD_ID), focused == SEARCH_FIELD_ID, ::openPage,
        )
        draw.unclipped {
            draw.paintHeader(m, page, headerToggle())
            if (page.subtabs.isNotEmpty()) {
                draw.paintSubtabs(m, page.subtabs, subtab, ::selectSubtab)
            }
        }
    }

    private fun sidebarState(): SidebarState {
        val look = TickTimers.settings.look
        return SidebarState(
            onlyEnabled = onlyEnabled,
            onFilter = { only ->
                onlyEnabled = only
                sidebarScroll = 0
            },
            pageOn = ::pageEnabled,
            enabledCount = ConfigNav.overviewPages().count { pageEnabled(it) == true },
            lightTheme = look.theme == GuiThemeId.GLASS_LIGHT,
            onTheme = {
                TickTimers.updateLook {
                    it.copy(theme = if (it.theme == GuiThemeId.GLASS_LIGHT) GuiThemeId.GLASS_DARK else GuiThemeId.GLASS_LIGHT)
                }
            },
            onAppearance = { openPage(ConfigPage.APPEARANCE) },
        )
    }

    /** On/off state of a page's primary feature, or null when it has no master switch. */
    private fun pageEnabled(target: ConfigPage): Boolean? {
        val module = target.primaryModuleId?.let(AetherModules::byId) ?: return null
        if (!AetherModules.hasMasterSwitch(module.id)) return null
        return moduleEnabled(module)
    }

    private fun paintPageContent(draw: MenuDraw, m: ConfigMetrics) {
        draw.canvas.save()
        draw.canvas.clipRect(
            Rect.makeLTRB(
                m.contentLeft.toFloat(),
                m.viewTop.toFloat(),
                (m.panelX + m.panelW - m.toc - 8).toFloat(),
                m.viewBottom.toFloat(),
            ),
        )
        val endY = drawCurrentPage(draw, m, m.viewTop + CONTENT_TOP_PAD - scroll)
        contentHeight = endY + scroll - m.viewTop + 16
        draw.canvas.restore()
    }

    private fun drawCurrentPage(draw: MenuDraw, m: ConfigMetrics, y: Int): Int {
        cardKey = null
        anchorsNext.clear()
        contentOrigin = y + scroll - m.viewTop
        val heroH = draw.paintHero(innerLeft(), y, innerWidth(), page)
        val end = drawPageBody(draw, m, y + heroH)
        closeCard(end + CARD_GAP)
        anchors = anchorsNext.toList()
        return end
    }

    /** Section titles of the current page and the index being read, for the outline column. */
    private fun paintOutline(draw: MenuDraw, m: ConfigMetrics) {
        if (m.toc <= 0 || anchors.isEmpty()) return
        var active = 0
        anchors.forEachIndexed { index, (_, offset) -> if (offset - OUTLINE_LEAD <= scroll) active = index }
        draw.paintToc(m, anchors.map { it.first }, active) { index ->
            anchors.getOrNull(index)?.let { scroll = clampScroll((it.second - OUTLINE_LEAD).coerceAtLeast(0), contentHeight, m.viewHeight) }
        }
    }

    private fun drawPageBody(draw: MenuDraw, m: ConfigMetrics, y: Int): Int = when (ConfigContents.at(page, subtab)) {
        ConfigContent.OVERVIEW -> drawOverview(draw, y)
        ConfigContent.CLOCKS_DISPLAY -> drawClocksDisplay(draw, y)
        ConfigContent.CLOCKS_DUNGEON -> drawClocksDungeon(draw, y)
        ConfigContent.CLOCKS_MECHANICS -> drawClocksMechanics(draw, y)
        ConfigContent.CLOCKS_DEBUG -> drawClocksDebug(draw, y)
        ConfigContent.ALERTS -> drawNotifications(draw, y)
        ConfigContent.SPLITS -> splitsPage.draw(draw, m, y, ::toast) { openRunHudEditor(RunHudEditorScreen.Target.SPLITS) }
        ConfigContent.SCORE -> scorePage.draw(draw, m, y) { openRunHudEditor(RunHudEditorScreen.Target.SCORE) }
        ConfigContent.DRAGONS -> dragonsPage.draw(draw, m, y, ::toast) { openRunHudEditor(RunHudEditorScreen.Target.DRAGON) }
        ConfigContent.TERMINALS -> terminalsPage.draw(draw, m, y) { McUi.setScreen(Minecraft.getInstance(), TerminalPreviewScreen()) }
        ConfigContent.LEAP_ROUTES -> drawLeapRoute(draw, y)
        ConfigContent.LEAP_LAYOUT -> drawLeapLayout(draw, y)
        ConfigContent.LEAP_LOOK -> drawLeapLook(draw, y)
        ConfigContent.LEAP_QUICK_LEAP -> drawLeapQuickLeap(draw, y)
        ConfigContent.LEAP_KEYBINDS -> drawLeapKeybinds(draw, y)
        ConfigContent.LEAP_DEBUG -> drawLeapDebug(draw, y)
        ConfigContent.ITEMS_CUSTOMIZE -> drawItemCustomize(draw, y)
        ConfigContent.ITEMS_SHARING -> drawItemSharing(draw, y)
        ConfigContent.MORPH -> drawMorph(draw, y)
        ConfigContent.VISUALS_CLEANUP -> drawVisualsCleanup(draw, y)
        ConfigContent.VISUALS_WORLD -> drawVisualsWorld(draw, y)
        ConfigContent.VISUALS_CAMERA -> drawVisualsCamera(draw, y)
        ConfigContent.VISUALS_VIEWMODEL -> drawVisualsViewmodel(draw, y)
        ConfigContent.VISUALS_HAND -> drawVisualsHand(draw, y)
        ConfigContent.VISUALS_EFFECTS -> drawVisualsEffects(draw, y)
        ConfigContent.VISUALS_MODEL -> drawVisualsModel(draw, y)
        ConfigContent.FREELOOK -> drawCamera(draw, y)
        ConfigContent.PERFORMANCE -> drawPerformance(draw, y)
        ConfigContent.WAYPOINTS -> drawWaypoints(draw, y)
        ConfigContent.STATS -> drawStats(draw, y)
        ConfigContent.SHARDS -> shardPage.draw(draw, m, y, focused, ::toast) {
            scroll = 0
            ConfigUiSession.save(page, subtab, 0)
        }
        ConfigContent.INVENTORY_SORT -> drawInventorySort(draw, y)
        ConfigContent.DROP_ULT -> drawCombat(draw, y)
        ConfigContent.CARRIES_COUNTER -> drawCarriesCounter(draw, y)
        ConfigContent.CARRIES_KEYS -> drawCarriesKeys(draw, y)
        ConfigContent.CARRIES_PLAYERS -> drawCarriesPlayers(draw, y)
        ConfigContent.BLAZE_FIRE -> drawBlazeFire(draw, y)
        ConfigContent.BLAZE_QUAZII -> drawBlazeQuazii(draw, y)
        ConfigContent.BLAZE_MOBS -> drawBlazeMobs(draw, y)
        ConfigContent.BLOOD_HELPER -> drawBloodHelper(draw, y)
        ConfigContent.LIVID_HELPER -> drawLividHelper(draw, y)
        ConfigContent.BOSS_AIM -> drawBossAim(draw, y)
        ConfigContent.APPEARANCE_THEME -> drawAppearanceTheme(draw, y)
        ConfigContent.APPEARANCE_TEXT_SHAPE -> drawAppearanceTextShape(draw, y)
        ConfigContent.APPEARANCE_ADVANCED -> drawAppearanceAdvanced(draw, y)
        // HUD Layout opens the HUD editor from the sidebar and never reaches this dispatch.
        ConfigContent.HUD_LAYOUT -> y
    }

    private fun paintToast(draw: MenuDraw, m: ConfigMetrics) {
        val message = toast ?: return
        if (System.currentTimeMillis() > toastUntil) {
            toast = null
            return
        }
        val toastWidth = draw.measure(message, draw.smallFont) + 36
        val toastX = m.panelX + (m.panelW - toastWidth) / 2
        val toastY = m.panelY + m.panelH - 38
        draw.round(toastX, toastY, toastWidth, 28, 14f, draw.palette.window)
        draw.outline(toastX, toastY, toastWidth, 28, 14f, 0x55C9BBFF)
        draw.text(message, toastX + 18, toastY + 7, draw.palette.text, draw.smallFont)
    }

    private fun paintContentScrollbar(draw: MenuDraw, m: ConfigMetrics) {
        if (contentHeight > m.viewHeight) {
            draw.scrollbar(m.panelX + m.panelW - m.toc - 10, m.viewTop + 8, m.viewHeight - 16, contentHeight, scroll)
        }
    }

    private fun searchOpen(): Boolean = focused == SEARCH_FIELD_ID && field(SEARCH_FIELD_ID).isNotBlank()

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick)
        }
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (!inWindow(mx, my)) {
            focused = null
            return true
        }
        if (leapDropdownSpec != null && openDropdownRects.none { it.contains(mx, my) }) {
            closeLeapDropdown()
            return true
        }
        val hit = hits.lastOrNull { it.rect.contains(mx, my) }
        if (hit == null) {
            focused = null
            openDebugDropdown = null
            return true
        }
        focused = hit.fieldId
        hit.action?.invoke()
        hit.drag?.let { drag ->
            sliderLast = Float.NaN
            dragging = drag
            drag(mx)
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val drag = dragging ?: return super.mouseDragged(event, dragX, dragY)
        drag(event.x().toInt())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging == null) return super.mouseReleased(event)
        dragging = null
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        val m = metrics
        val delta = (scrollY * SCROLL_STEP).toInt()
        // An open leap dropdown takes the wheel; its height is clamped when it is painted.
        if (leapDropdownSpec != null && openDropdownRects.getOrNull(1)?.contains(mx, my) == true) {
            leapDropdownScroll = (leapDropdownScroll - delta).coerceAtLeast(0)
            return true
        }
        val sidebarHeight = sidebarBottom(m) - sidebarTop(m)
        if (mx >= m.panelX && mx < m.contentLeft && my >= sidebarTop(m) && my < sidebarBottom(m)) {
            sidebarScroll = clampScroll(sidebarScroll - delta, sidebarContentHeight(sidebarState()), sidebarHeight)
            return true
        }
        if (mx < m.contentLeft || mx >= m.panelX + m.panelW - m.toc || my < m.viewTop || my >= m.viewBottom) {
            return true
        }
        scroll = clampScroll(scroll - delta, contentHeight, m.viewHeight)
        openDebugDropdown = null
        ConfigUiSession.save(page, subtab, scroll)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val bind = capturingBind
        if (bind != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                applyBind(bind, LeapOrientSettings.UNBOUND)
            } else {
                applyBind(bind, event.key())
            }
            capturingBind = null
            toast("bind saved")
            return true
        }
        val id = focused
        if (id != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    if (id == SEARCH_FIELD_ID) fields[SEARCH_FIELD_ID] = ""
                    focused = null
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (id == "r:name") applyRenameField()
                    if (id == SEARCH_FIELD_ID) ConfigNav.search(field(id)).firstOrNull()?.let(::openFromSearch)
                    focused = null
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val next = field(id).dropLast(1)
                    fields[id] = next
                    applyField(id, next)
                    return true
                }
                GLFW.GLFW_KEY_V -> if ((event.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0) {
                    val clip = minecraft.keyboardHandler.clipboard
                    val next = field(id) + clip
                    fields[id] = next
                    applyField(id, next)
                    return true
                }
            }
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val id = focused ?: return super.charTyped(event)
        val ch = event.codepoint()
        if (ch < 32) return true
        val next = field(id) + Character.toString(ch)
        fields[id] = next
        applyField(id, next)
        return true
    }

    private fun drawStats(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Armor stat presets and the fast swap setup.")
        val stats = TickTimers.settings.statsArmor
        var y = section(draw, helpY, "Stats armor", "Use /icantpy stats or the altered-stats keybind for the fast armor menu.")
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Enabled", "Allow the altered stats menu to open.", stats.enabled, false) {
                    TickTimers.updateStatsArmor { it.copy(enabled = !it.enabled) }
                }
                1 -> toggleRow(draw, rowY, "Close after swap", "Close the menu after clicking one replacement.", stats.closeAfterSwap, false) {
                    TickTimers.updateStatsArmor { it.copy(closeAfterSwap = !it.closeAfterSwap) }
                }
                else -> toggleRow(draw, rowY, "Mask timers", "Show Bonzo, Spirit, and Phoenix timers on mask cards.", stats.showMaskTimers, true) {
                    TickTimers.updateStatsArmor { it.copy(showMaskTimers = !it.showMaskTimers) }
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Setup", "Open the altered menu, press SETUP, then capture items from your inventory.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                actionRow(draw, rowY, "Configured items", "${stats.presets.size} saved replacement${if (stats.presets.size == 1) "" else "s"}.", last = false) { cy ->
                    draw.button("Open menu", rightX(88), cy, ButtonStyle.PRIMARY) { StatsArmor.openAltered() }
                }
            } else {
                actionRow(draw, rowY, "Clear replacements", "Remove all captured armor items.", last = true) { cy ->
                    draw.button("Clear", rightX(58), cy, ButtonStyle.DANGER) {
                        TickTimers.updateStatsArmor { it.clear() }
                    }
                }
            }
        }
        return drawFishingMacro(draw, drawPetSwap(draw, y))
    }

    private fun drawFishingMacro(draw: MenuDraw, startY: Int): Int {
        val fishing = FishingMacro.config
        var y = section(
            draw,
            startY + SECTION_GAP,
            "Fishing macro",
            "Casts, waits for the !!! bite marker at your hook and reels in, repeatedly. Hold a fishing rod. Stops itself if the rod leaves your hand, nothing bites, or you disconnect.",
        )
        val startLabel = if (FishingMacro.running) "Stop" else "Start"
        y = actionRow(draw, y, "Macro", if (FishingMacro.running) "Running" else "Off", last = false) { cy ->
            draw.button(startLabel, rightX(draw.measure(startLabel, draw.buttonFont) + 28), cy, ButtonStyle.PRIMARY) {
                FishingMacro.toggle()
            }
        }
        y = bindRow(draw, y, "Toggle key", "fishKey", fishing.key, last = false)
        y = delayPairRow(draw, y, "Reel delay (ms)", "Wait after the bite marker appears, before reeling.", "fish:reel", fishing.reelMinMs, fishing.reelMaxMs)
        y = delayPairRow(draw, y, "Cast delay (ms)", "Wait after a reel before casting again.", "fish:cast", fishing.castMinMs, fishing.castMaxMs)
        val quietId = "fish:quiet"
        y = actionRow(draw, y, "No-bite timeout (s)", "Re-cast after this long without a bite; stops after three in a row.", last = true) { cy ->
            draw.field(quietId, field(quietId, fishing.noBiteSeconds.toString()), "45", rightX(64), cy, 64, focused == quietId)
        }
        return note(
            draw,
            y,
            "To fish with a pet, turn on Swap on cast in Pet swap above: every cast the macro makes then summons that pet. If a bite shows while that swap is still running, the swap is dropped so the reel is not held up.",
        )
    }

    /** One row with a min and a max millisecond field. */
    private fun delayPairRow(draw: MenuDraw, y: Int, title: String, subtitle: String, idPrefix: String, min: Int, max: Int): Int {
        val minId = "$idPrefix:min"
        val maxId = "$idPrefix:max"
        return actionRow(draw, y, title, subtitle, last = false) { cy ->
            draw.field(maxId, field(maxId, max.toString()), "max", rightX(64), cy, 64, focused == maxId)
            draw.field(minId, field(minId, min.toString()), "min", rightX(64) - 8 - 64, cy, 64, focused == minId)
        }
    }

    private fun drawPetSwap(draw: MenuDraw, startY: Int): Int {
        val pets = PetSwap.config
        var y = section(
            draw,
            startY + SECTION_GAP,
            "Pet swap",
            "One key per pet. A press sends /pets, clicks that pet in the menu like a player would, then closes it. A pet that is already out is left alone.",
        )
        y = toggleRow(draw, y, "Enabled", "Allow the pet keys below.", pets.enabled, last = false) {
            PetSwap.edit { it.copy(enabled = !it.enabled) }
        }
        y = toggleRow(draw, y, "Instant", "No delays at all: the swap runs as fast as the server answers.", pets.instant, last = false) {
            PetSwap.edit { it.copy(instant = !it.instant) }
        }
        val minId = "pet:delayMin"
        val maxId = "pet:delayMax"
        y = actionRow(draw, y, "Min delay (ms)", "Shortest random wait before /pets, each click and the close.", last = false) { cy ->
            draw.field(minId, field(minId, pets.delayMinMs.toString()), "80", rightX(64), cy, 64, focused == minId)
        }
        y = actionRow(draw, y, "Max delay (ms)", "Longest random wait. Ignored while Instant is on.", last = false) { cy ->
            draw.field(maxId, field(maxId, pets.delayMaxMs.toString()), "200", rightX(64), cy, 64, focused == maxId)
        }
        y = toggleRow(
            draw, y, "Swap on cast", "Summon the pet below each time you cast a fishing rod. Read from your own hook, no packets.",
            pets.swapOnCast, last = false,
        ) { PetSwap.edit { it.copy(swapOnCast = !it.swapOnCast) } }
        val castId = "pet:castName"
        y = actionRow(draw, y, "Cast pet", "Pet name as shown in /pets, for example Flying Fish.", last = pets.pets.isEmpty()) { cy ->
            draw.field(castId, field(castId, pets.castPet), "Flying Fish", rightX(PET_FIELD_WIDTH), cy, PET_FIELD_WIDTH, focused == castId)
        }
        val removeW = draw.measure("Remove", draw.buttonFont) + 28
        val bindW = draw.measure("Bind", draw.buttonFont) + 28
        pets.pets.forEachIndexed { index, pet ->
            val bindId = "petKey:$index"
            val nameId = "pet:name:$index"
            val keyText = if (capturingBind == bindId) "Press a key…" else keyLabel(pet.key)
            y = actionRow(draw, y, "Pet ${index + 1}", keyText, last = index == pets.pets.lastIndex) { cy ->
                val removeX = rightX(removeW)
                val bindX = removeX - 8 - bindW
                draw.button("Remove", removeX, cy, ButtonStyle.DANGER) {
                    PetSwap.edit { it.withRemoved(index) }
                    fields.keys.removeAll { it.startsWith("pet:name:") }
                    if (capturingBind?.startsWith("petKey:") == true) capturingBind = null
                }
                draw.button("Bind", bindX, cy, ButtonStyle.PRIMARY) {
                    capturingBind = bindId
                    focused = null
                    toast("press a key")
                }
                draw.field(nameId, field(nameId, pet.name), "Golden Dragon", bindX - 8 - PET_FIELD_WIDTH, cy, PET_FIELD_WIDTH, focused == nameId)
            }
        }
        y = actionBar(draw, y + 4, "Add a pet") { cy ->
            val newId = "pet:new"
            val addW = draw.button("Add", rightX(draw.measure("Add", draw.buttonFont) + 28), cy, ButtonStyle.PRIMARY) {
                val before = PetSwap.config.pets.size
                PetSwap.edit { it.withAdded(field(newId)) }
                if (PetSwap.config.pets.size > before) {
                    fields[newId] = ""
                    toast("pet added; press Bind to set its key")
                }
            }
            draw.field(newId, field(newId), "Pet name", rightX(addW) - 8 - PET_FIELD_WIDTH, cy, PET_FIELD_WIDTH, focused == newId)
        }
        return note(
            draw,
            y,
            "Type the pet's name as it shows in /pets, without the level (for example Golden Dragon). Keys only work with no screen open. Every press runs; the only check is inside the Pets menu, where a pet that is already out is left alone.",
        )
    }

    private fun drawLeapRoute(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Triggers arm a leap target or run an inventory sort when a boss event happens.")
        val leap = TickTimers.settings.leap
        var y = grouped(draw, helpY, 3) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Class", "Auto uses Odin. Unknown is not all classes.", last = false) { cy ->
                    var x = rightX(0)
                    LeapDungeonClass.PLAYABLE.asReversed().forEach { clazz ->
                        x -= draw.measure(clazz.shortName, draw.buttonFont) + 24
                        draw.chip(clazz.shortName, x, cy, leap.selfClass == clazz) {
                            TickTimers.updateLeap { it.copy(selfClass = clazz) }
                        }
                        x -= 6
                    }
                    x -= draw.measure("Auto", draw.buttonFont) + 24
                    draw.chip("Auto", x, cy, !leap.selfClass.isReal()) {
                        TickTimers.updateLeap { it.copy(selfClass = LeapDungeonClass.EMPTY) }
                    }
                }
                1 -> actionRow(draw, rowY, "Route", "GY vs PY changes Healer predev and Mage Storm leaps.", last = false) { cy ->
                    var x = rightX(0)
                    listOf(LeapStormRoute.PY, LeapStormRoute.GY).forEach { route ->
                        x -= draw.measure(route.label(), draw.buttonFont) + 24
                        draw.chip(route.label(), x, cy, leap.stormRoute == route) {
                            TickTimers.updateLeap { current ->
                                current.copy(stormRoute = route).applyPreset()
                            }
                        }
                        x -= 6
                    }
                }
                else -> actionRow(draw, rowY, "EE2 owner", "Who receives S1 preleaps. AEE2 is not auto-detected.", last = true) { cy ->
                    var x = rightX(0)
                    listOf(LeapDungeonClass.ARCHER, LeapDungeonClass.MAGE).forEach { clazz ->
                        x -= draw.measure(clazz.shortName, draw.buttonFont) + 24
                        draw.chip(clazz.shortName, x, cy, leap.ee2Class == clazz) {
                            TickTimers.updateLeap { current ->
                                current.copy(ee2Class = clazz).applyPreset()
                            }
                        }
                        x -= 6
                    }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Next leap", LeapOrient.previewNext(), last = true) { }
        }
        y = actionBar(draw, y, "2026 M7 preset") {
            val resetW = draw.button("Reset", rightX(56), it, ButtonStyle.GHOST) {
                TickTimers.updateLeap { it.resetPreset() }
                toast("preset reset")
            }
            draw.button("Apply", rightX(56) - resetW - 8, it, ButtonStyle.PRIMARY) {
                TickTimers.updateLeap { it.applyPreset() }
                toast("applied 2026 M7")
            }
        }
        y = actionBar(draw, y, "Custom trigger") {
            val chainW = draw.button("Chain", rightX(56), it, ButtonStyle.GHOST) {
                // A chain card is only useful with its leap, so this one starts with the leap-target row.
                addLeapTrigger(
                    LeapOrientTrigger.create(LeapTriggerEvent.LEAP_CHAIN, "berserk")
                        .copy(steps = listOf(LeapAutoStep.leapTarget())),
                )
                toast("added chain trigger")
            }
            draw.button("Add", rightX(56) - chainW - 8, it, ButtonStyle.PRIMARY) {
                addLeapTrigger(LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer"))
                toast("added clock trigger")
            }
        }
        val liveClass = LeapRoster.selfClass(leap)
        val viewClass = ConfigUiSession.leapViewClass ?: liveClass
        val viewOptions = listOf<LeapDungeonClass?>(null, LeapDungeonClass.EMPTY) + LeapDungeonClass.PLAYABLE
        val viewLabels = listOf("My class (Auto)", "All classes") + LeapDungeonClass.PLAYABLE.map { it.displayName }
        val viewLabel = when {
            ConfigUiSession.leapViewClass == null && liveClass.isReal() -> "My class: ${liveClass.displayName}"
            ConfigUiSession.leapViewClass == null -> "Auto: All classes"
            !viewClass.isReal() -> "All classes"
            else -> viewClass.displayName
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Preset view", "Browse rules without changing your dungeon class.", last = true) { cy ->
                val selectW = selectWidth(draw, viewLabel)
                val anchor = LayoutRect(rightX(selectW), cy, selectW, LEAP_ROW_CONTROL_HEIGHT)
                leapSelect(draw, LEAP_VIEW_KEY, viewLabel, viewLabels, viewOptions.indexOf(ConfigUiSession.leapViewClass), anchor) { index ->
                    ConfigUiSession.leapViewClass = viewOptions[index]
                }
            }
        }
        // One flat list in stored order: editing a trigger never moves its card.
        LeapTriggerCards.group(leap.visibleTriggers(viewClass)).forEach { card ->
            y = leapTriggerBlock(draw, y, card)
        }
        return y
    }

    private fun selectWidth(draw: MenuDraw, label: String): Int =
        (draw.measure(label, draw.buttonFont) + 48).coerceAtMost(innerWidth() - 28)

    /**
     * Select button whose options open in an overlay. The overlay is painted after the scrolled
     * content (see [drawLeapDropdownOverlay]) so it stays above every card.
     */
    private fun leapSelect(
        draw: MenuDraw,
        key: String,
        shown: String,
        options: List<String>,
        selected: Int,
        anchor: LayoutRect,
        headers: Map<Int, String> = emptyMap(),
        onSelect: (Int) -> Unit,
    ) {
        if (options.isEmpty()) return
        draw.selectButton(shown, anchor.x, anchor.y, anchor.w, anchor.h) {
            val opening = openDebugDropdown != key
            openDebugDropdown = if (openDebugDropdown == key) null else key
            if (opening) {
                leapDropdownScroll = 0
                leapDropdownFocusPending = true
            }
        }
        if (openDebugDropdown != key) return
        val widest = options.maxOf { draw.measure(it, draw.buttonFont) } + 24
        val menuWidth = maxOf(anchor.w, widest)
        val menuX = (anchor.right - menuWidth).coerceAtLeast(innerLeft() + 14)
        val lines = buildList {
            options.forEachIndexed { index, label ->
                headers[index]?.let { add(DropdownLine(it, null)) }
                add(DropdownLine(label, index))
            }
        }
        leapDropdownSpec = LeapDropdownSpec(
            anchor = anchor,
            x = menuX,
            width = menuWidth,
            lines = lines,
            selected = selected.coerceIn(options.indices),
            onSelect = onSelect,
        )
    }

    /**
     * Paints the open leap dropdown below its anchor, or above it when the space below is short. Long
     * lists are capped to the larger of the two spaces and scroll with the mouse wheel.
     */
    private fun drawLeapDropdownOverlay(draw: MenuDraw) {
        val spec = leapDropdownSpec ?: return
        val m = metrics
        val gap = LeapTriggerLayout.DROPDOWN_GAP
        val fullHeight = dropdownLinesHeight(spec.lines)
        val spaceBelow = m.viewBottom - (spec.anchor.bottom + gap)
        val spaceAbove = spec.anchor.y - gap - m.viewTop
        val below = spaceBelow >= fullHeight || spaceBelow >= spaceAbove
        val menuHeight = minOf(fullHeight, (if (below) spaceBelow else spaceAbove).coerceAtLeast(MIN_LEAP_DROPDOWN_VIEW))
        val top = if (below) spec.anchor.bottom + gap else spec.anchor.y - gap - menuHeight
        if (leapDropdownFocusPending) {
            leapDropdownFocusPending = false
            leapDropdownScroll = scrollToDropdownOption(spec.lines, spec.selected, menuHeight)
        }
        leapDropdownScroll = draw.dropdownScrollable(spec.x, top, spec.width, menuHeight, spec.lines, spec.selected, leapDropdownScroll) { index ->
            spec.onSelect(index)
            closeLeapDropdown()
        }
        openDropdownRects = listOf(spec.anchor, LayoutRect(spec.x, top, spec.width, menuHeight))
    }

    /** Scroll offset that puts [option] near the middle of a [viewHeight] window. */
    private fun scrollToDropdownOption(lines: List<DropdownLine>, option: Int, viewHeight: Int): Int {
        var top = 0
        for (line in lines) {
            if (line.option == option) return (top - viewHeight / 2).coerceAtLeast(0)
            top += dropdownLineHeight(line)
        }
        return 0
    }

    /** Headers are placed before the first option of each group. */
    private fun leapEventHeaders(events: List<LeapTriggerEvent>): Map<Int, String> = events.indices
        .filter { it == 0 || events[it].group != events[it - 1].group }
        .associateWith { events[it].group }

    private fun closeLeapDropdown() {
        openDebugDropdown = null
        leapDropdownSpec = null
    }

    /** Event help, plus how a leap target and the other actions run together when a card has both. */
    private fun leapHelpText(trigger: LeapOrientTrigger): String {
        val event = leapEventHelp(trigger.event)
        return if (trigger.armLeap && trigger.queueableSteps().isNotEmpty()) {
            "$event Leap target is applied instantly; other actions run in order."
        } else {
            event
        }
    }

    private fun leapEventHelp(event: LeapTriggerEvent): String = when (event) {
        LeapTriggerEvent.BOSS_STORM_END -> "Storm dies and the party enters the post-Storm route."
        LeapTriggerEvent.MAXOR_START -> "Maxor's intro line plays and the P1 fight begins."
        LeapTriggerEvent.MAXOR_DEATH -> "Maxor dies (his death line, or his health bar drops to zero after being seen alive)."
        LeapTriggerEvent.BOSS_STORM_START -> "Storm starts P2 (Storm's intro line, right after Maxor dies)."
        LeapTriggerEvent.WITHER_KING_DEATH -> "Wither King dies at the end of the F7/M7 run."
        LeapTriggerEvent.BOSS_GOLDOR -> "Goldor starts P3 (his intro line)."
        LeapTriggerEvent.BOSS_CORE -> "The Goldor core opens."
        LeapTriggerEvent.BOSS_GOLDOR_DEATH -> "Goldor dies and the Necron phase begins."
        LeapTriggerEvent.BOSS_NECRON -> "Necron drops into the main fight."
        LeapTriggerEvent.BOSS_NECRON_DEATH -> "Necron dies and relics become available."
        LeapTriggerEvent.BOSS_P5 -> "The P5 relic phase is detected."
        LeapTriggerEvent.RELIC_PICKUP -> "You pick up a relic in P5."
        LeapTriggerEvent.RELIC_PLACED_SELF -> "Your own relic leaves your inventory next to its cauldron (P5). Color filters the relic color."
        LeapTriggerEvent.RELIC_PLACED_ANY -> "Any relic appears in its cauldron (P5), once per color per run. Color filters the relic color."
        LeapTriggerEvent.MAXOR_STUNNED -> "Maxor is stunned by the crystal beam."
        LeapTriggerEvent.BOSS_ENRAGED -> "A boss becomes enraged. The match filters on the boss name."
        LeapTriggerEvent.CRYSTAL_PICKUP -> "A player picks up an Energy Crystal. Match filters on the player."
        LeapTriggerEvent.STORM_CRUSHED -> "Storm's crush line plays (Oof or Ouch, that hurt!)."
        LeapTriggerEvent.STORM_PAD_PHASE -> "Storm's lightning or pad phase starts."
        LeapTriggerEvent.WK_DRAGON_KILLED -> "A Wither King dragon is killed."
        LeapTriggerEvent.DRAGON_SPAWNING -> "A wither dragon starts spawning. Match takes a dragon color, such as Red."
        LeapTriggerEvent.DRAGON_SPAWNED -> "A wither dragon becomes alive. Match takes a dragon color, such as Red."
        LeapTriggerEvent.DRAGON_KILLED -> "A wither dragon is killed. Match takes a dragon color, such as Red."
        LeapTriggerEvent.DRAGON_PRIORITY -> "The dragon chosen to be killed first is decided after a split spawn. Match takes a dragon color."
        LeapTriggerEvent.BAT_KILLED -> "A bonus bat is killed."
        LeapTriggerEvent.LIVID_START -> "Livid introduces himself and the fight begins."
        LeapTriggerEvent.SADAN_GIANTS -> "Sadan releases his giants."
        LeapTriggerEvent.SADAN_DONE -> "Sadan is defeated."
        LeapTriggerEvent.PROFESSOR_GUARDIAN -> "The Professor uses his ultimate technique."
        LeapTriggerEvent.ABILITY_COOLDOWN -> "You try to use an ability that is on cooldown. Fires at most once every 3 seconds."
        LeapTriggerEvent.LEAPED_TO -> "A party member announces that they leaped to someone."
        LeapTriggerEvent.SELF_LEAP -> "You successfully leap to a party member."
        LeapTriggerEvent.LEAP_CHAIN -> "A follow-up rule runs after your own leap. To leap again after a leap target, add a Follow-up action instead; it is the simpler inline option."
        LeapTriggerEvent.CLOCK -> "A dungeon timer reaches the configured threshold."
        LeapTriggerEvent.RUN_START -> "Mort hands you the map and the run begins."
        LeapTriggerEvent.RUN_END -> "The EXTRA STATS summary appears at the end of the run."
        LeapTriggerEvent.BOSS_ENTRY -> "The party enters a boss room and the boss introduces itself."
        LeapTriggerEvent.BLOOD_OPEN -> "The Blood Door opens."
        LeapTriggerEvent.BLOOD_CAMP_DONE -> "The Watcher finishes the blood camp."
        LeapTriggerEvent.BLOOD_CLEAR -> "The Watcher lets the party pass the blood room."
        LeapTriggerEvent.WITHER_KEY -> "A player picks up a Wither Key."
        LeapTriggerEvent.BLOOD_KEY -> "A player picks up a Blood Key."
        LeapTriggerEvent.WITHER_DOOR -> "A player opens a Wither door."
        LeapTriggerEvent.PRINCE_KILLED -> "A Prince is killed."
        LeapTriggerEvent.PUZZLE_FAIL -> "A puzzle fails and names the player who failed it."
        LeapTriggerEvent.TERMINAL_DONE -> "A terminal, lever or device is completed."
        LeapTriggerEvent.SECTION_DONE -> "The last terminal of a P3 section is completed."
        LeapTriggerEvent.GATE_DESTROYED -> "The gate is destroyed."
        LeapTriggerEvent.P5_START -> "Wither King starts P5."
        LeapTriggerEvent.BONZO_DEAD -> "Bonzo dies."
        LeapTriggerEvent.SELF_DEATH -> "You die."
        LeapTriggerEvent.TEAMMATE_DEATH -> "A teammate dies."
        LeapTriggerEvent.MASK_PROC -> "Your Bonzo, Spirit or Phoenix mask saves you."
        LeapTriggerEvent.HEALTH_LOW -> "Your health drops below the chosen share of max health."
        LeapTriggerEvent.ISLAND_ENTERED -> "You arrive on an island. Match takes an island name, such as Crimson Isle."
        LeapTriggerEvent.ISLAND_LEFT -> "You leave an island. Match takes an island name."
        LeapTriggerEvent.DUNGEON_ENTERED -> "A dungeon run starts. Fires once the floor is known, or after 10 seconds without one."
        LeapTriggerEvent.DUNGEON_LEFT -> "You leave the dungeon."
        LeapTriggerEvent.PLAYER_READY -> "A party member readies up. Match filters on the player."
        LeapTriggerEvent.COUNTDOWN -> "The one-second start countdown begins."
        LeapTriggerEvent.CLASS_SELECTED -> "You pick a dungeon class. Match takes a class name."
        LeapTriggerEvent.PF_JOINED -> "A player joins through Party Finder. Match filters on the player."
        LeapTriggerEvent.SECRETS_REACHED -> "The tab-list secret count reaches the At least value. Fires once per run."
        LeapTriggerEvent.CRYPTS_REACHED -> "The tab-list crypt count reaches the At least value. Fires once per run."
        LeapTriggerEvent.CLEARED_REACHED -> "The sidebar cleared percentage reaches the At least value. Fires once per run."
        LeapTriggerEvent.DEATHS_CHANGED -> "The team death count goes up. Fires on every increase."
        LeapTriggerEvent.PUZZLE_SOLVED -> "A puzzle is solved."
        LeapTriggerEvent.MIMIC_DEAD -> "A party member reports the Mimic is dead."
        LeapTriggerEvent.BLESSING -> "A blessing is picked up. Match takes a blessing name, such as Power."
        LeapTriggerEvent.KUUDRA_SUPPLIES -> "Elle announces the supplies phase."
        LeapTriggerEvent.KUUDRA_BUILD_DONE -> "Elle reports the ballista is ready."
        LeapTriggerEvent.KUUDRA_STUN -> "Kuudra is stunned."
        LeapTriggerEvent.KUUDRA_LAIR -> "Elle reaches the final lair."
        LeapTriggerEvent.SLAYER_STARTED -> "A slayer quest starts."
        LeapTriggerEvent.SLAYER_BOSS_SPAWNED -> "The sidebar shows Slay the boss!."
        LeapTriggerEvent.SLAYER_COMPLETE -> "The slayer quest is complete."
        LeapTriggerEvent.SLAYER_FAILED -> "The slayer quest fails."
        LeapTriggerEvent.CHAT_MESSAGE -> "Runs when a chat message matches. Tip: 'Others' lets party members trigger your actions — use carefully."
    }

    private fun drawLeapLayout(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        val helpY = pageHelp(draw, startY, "Master switches and card spacing for the leap menu.")
        var y = grouped(draw, helpY, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Spirit Leap enabled", "Master switch for every trigger card (dungeon, location, lobby, progress, puzzle, Kuudra and slayer events), the leap menu and orient center.", leap.enabled, false) {
                    TickTimers.updateLeap { it.copy(enabled = !it.enabled) }
                }
                1 -> toggleRow(draw, rowY, "Custom menu", "Replace the vanilla / Odin leap GUI.", leap.menuEnabled, false) {
                    TickTimers.updateLeap { it.copy(menuEnabled = !it.menuEnabled) }
                }
                else -> toggleRow(draw, rowY, "Orient center", "Local overlay only. Click still uses the matching head slot.", leap.orientEnabled, true) {
                    TickTimers.updateLeap { it.copy(orientEnabled = !it.orientEnabled) }
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Layout", "Spacing and card positions.")
        y = grouped(draw, y, 3) { rowY, index ->
            if (index == 0) {
                val id = "leap:gap"
                actionRow(draw, rowY, "Target gap", "Gap in pixels before menu scaling (0–64).", last = false) { cy ->
                    draw.field(id, field(id, leap.menuGap.toString()), "8", rightX(48), cy, 48, focused == id)
                }
            } else if (index == 1) {
                toggleRow(draw, rowY, "Only visible cards", "Moved layouts always use card bounds. Otherwise keep large corner regions.", leap.boxesOnly, false) {
                    TickTimers.updateLeap { it.copy(boxesOnly = !it.boxesOnly) }
                }
            } else {
                actionRow(draw, rowY, "Edit layout", "Move and resize leap cards, including several at once.", last = true) { cy ->
                    draw.button("Edit", rightX(52), cy, ButtonStyle.PRIMARY) {
                        McUi.setScreen(Minecraft.getInstance(), net.icantpy.dungeon.leap.LeapMenuEditorScreen())
                    }
                }
            }
        }
        return y
    }

    private fun drawLeapQuickLeap(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        val helpY = pageHelp(draw, startY, "Left-click Spirit Leap, lock chat, and the center priority order.")
        var y = grouped(draw, helpY, 2) { rowY, index ->
            if (index == 0) {
                val durId = "leap:duration"
                actionRow(draw, rowY, "Default target duration", "Seconds a timed leap target stays armed when its card uses the default.", last = false) { cy ->
                    draw.field(durId, field(durId, leap.durationSeconds.toString()), "10", rightX(48), cy, 48, focused == durId)
                }
            } else {
                val keyId = "leap:keyword"
                actionRow(draw, rowY, "Keyword", "Party chat must contain this, then at sss / ee2 / healer.", last = true) { cy ->
                    draw.field(keyId, field(keyId, leap.keyword), "[icantpy]", rightX(96), cy, 96, focused == keyId)
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Quick leap", "Left-click Spirit Leap to open its menu and select the oriented target.")
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Left-click auto leap", "Uses the oriented target, without moving the cursor.", leap.autoLeapEnabled, false) {
                    TickTimers.updateLeap { it.copy(autoLeapEnabled = !it.autoLeapEnabled) }
                }
                1 -> {
                    val id = "leap:autoDelay"
                    actionRow(draw, rowY, "Click delay (ms)", "Wait after the server opens the menu (0–2000).", last = false) { cy ->
                        draw.field(id, field(id, leap.autoLeapDelayMs.toString()), "100", rightX(48), cy, 48, focused == id)
                    }
                }
                else -> toggleRow(draw, rowY, "Door opener leap", "Left-click Spirit Leap to the latest door opener. Shown on the lock HUD. Ignored in boss rooms.", leap.doorOpenerLeapEnabled, true) {
                    TickTimers.updateLeap { it.copy(doorOpenerLeapEnabled = !it.doorOpenerLeapEnabled) }
                }
            }
        }
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                toggleRow(draw, rowY, "Click on release", "Leap when you let go instead of on press.", leap.onRelease, false) {
                    TickTimers.updateLeap { it.copy(onRelease = !it.onRelease) }
                }
            } else {
                toggleRow(draw, rowY, "Leap announce", "Send the template after you teleport. Own announce is ignored.", leap.leapAnnounce, true) {
                    TickTimers.updateLeap { it.copy(leapAnnounce = !it.leapAnnounce) }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            val annId = "leap:announce"
            actionRow(draw, rowY, "Announce", "{class} or {name}. Others can use this as a trigger.", last = true) { cy ->
                draw.field(annId, field(annId, leap.announceTemplate), LeapChatPatterns.DEFAULT_ANNOUNCE, rightX(220), cy, 220, focused == annId)
            }
        }
        y = section(draw, y + SECTION_GAP, "Only in boss fight", "Keep Spirit Leap quiet outside dungeon boss fights.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                toggleRow(draw, rowY, "Cards", "Trigger cards fire only during a dungeon boss fight. Test still works anywhere.", leap.bossOnlyTriggers, false) {
                    TickTimers.updateLeap { it.copy(bossOnlyTriggers = !it.bossOnlyTriggers) }
                }
            } else {
                toggleRow(draw, rowY, "Auto leap", "Restrict oriented left-click auto leap to dungeon boss fights.", leap.autoLeapBossOnly, true) {
                    TickTimers.updateLeap { it.copy(autoLeapBossOnly = !it.autoLeapBossOnly) }
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Center priority", "Only used when two equal-priority locks overlap.")
        y = grouped(draw, y, LeapOrientSettings.PRIORITY_STATES.size) { rowY, index ->
            val (state, title) = LeapOrientSettings.PRIORITY_STATES[index]
            val rule = leap.preferred(state)
            val kind = rule?.kind ?: LeapPreferKind.LOCATION
            val value = rule?.value.orEmpty()
            val last = index == LeapOrientSettings.PRIORITY_STATES.lastIndex
            actionRow(draw, rowY, title, if (kind == LeapPreferKind.CLASS) "Prefer that class." else "Prefer that ping location.", last) { cy ->
                var x = rightX(0)
                val tokens = if (kind == LeapPreferKind.CLASS) {
                    LeapDungeonClass.PLAYABLE.map { it.shortName.lowercase() }
                } else {
                    OrientSpot.PRIORITY_TOKENS
                }
                tokens.asReversed().forEach { token ->
                    val selected = if (kind == LeapPreferKind.CLASS) {
                        LeapDungeonClass.fromToken(value) == LeapDungeonClass.fromToken(token)
                    } else {
                        OrientSpot.sameToken(value, token)
                    }
                    x -= draw.measure(token, draw.buttonFont) + 24
                    draw.chip(token, x, cy, selected) {
                        TickTimers.updateLeap { it.withPriority(state, kind, token) }
                    }
                    x -= 6
                }
                x -= draw.measure(kind.label(), draw.buttonFont) + 24
                draw.chip(kind.label(), x, cy, true) {
                    val nextKind = kind.next()
                    val nextValue = if (nextKind == LeapPreferKind.CLASS) "healer" else "sss"
                    TickTimers.updateLeap { it.withPriority(state, nextKind, nextValue) }
                }
            }
        }
        y = actionBar(draw, y, "Extra leaped messages") {
            draw.button("Add", rightX(48), it, ButtonStyle.PRIMARY) {
                TickTimers.updateLeap { it.copy(leapedMessages = it.leapedMessages + "[icantpy] leaped to {name}") }
            }
        }
        if (leap.leapedMessages.isEmpty()) {
            y = note(draw, y, "Built-in: [icantpy] leaped to {class}/{name} and Leaped to {name}!")
        }
        leap.leapedMessages.forEachIndexed { index, pattern ->
            y = grouped(draw, y, 1) { rowY, _ ->
                val fieldId = "leap:lp:$index"
                actionRow(draw, rowY, "Pattern", "Match party chat. Use {class} or {name}.", last = true) { cy ->
                    val delW = draw.button("Del", rightX(44), cy, ButtonStyle.DANGER) {
                        TickTimers.updateLeap { current ->
                            current.copy(leapedMessages = current.leapedMessages.filterIndexed { i, _ -> i != index })
                        }
                        fields.remove(fieldId)
                    }
                    draw.field(fieldId, field(fieldId, pattern), "{class}", rightX(44) - delW - 12 - 160, cy, 160, focused == fieldId)
                }
            }
        }
        return y
    }

    private fun drawLeapLook(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        val helpY = pageHelp(draw, startY, "Card sorting, scale, and colors for the leap menu.")
        var y = helpY
        y = section(draw, y + SECTION_GAP, "Look", "Same knobs as Odin's Leap Menu.")
        y = grouped(draw, y, 4) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Sorting", leap.sortMode.label(), last = false) { cy ->
                    draw.button("Next", rightX(52), cy, ButtonStyle.PRIMARY) {
                        TickTimers.updateLeap { it.copy(sortMode = it.sortMode.next()) }
                    }
                }
                1 -> actionRow(draw, rowY, "Scale", "${leap.scale}x", last = false) { cy ->
                    var x = rightX(0)
                    listOf(0.8f, 1f, 1.2f, 1.5f).asReversed().forEach { value ->
                        val label = value.toString()
                        x -= draw.measure(label, draw.buttonFont) + 24
                        draw.chip(label, x, cy, leap.scale == value) {
                            TickTimers.updateLeap { it.copy(scale = value) }
                        }
                        x -= 6
                    }
                }
                2 -> toggleRow(draw, rowY, "Color style", "Fill boxes with class color instead of gray.", leap.colorStyle, false) {
                    TickTimers.updateLeap { it.copy(colorStyle = !it.colorStyle) }
                }
                else -> toggleRow(draw, rowY, "Class names only", "Hide player names on the cards.", leap.onlyClass, true) {
                    TickTimers.updateLeap { it.copy(onlyClass = !it.onlyClass) }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            val id = "leap:opacity"
            actionRow(draw, rowY, "Card opacity", "0 is invisible, 100 is solid. Used when cards overlap.", last = true) { cy ->
                var x = rightX(48)
                listOf(25, 50, 75, 100).asReversed().forEach { value ->
                    val label = "$value%"
                    x -= draw.measure(label, draw.buttonFont) + 24
                    draw.chip(label, x, cy, leap.menuOpacity == value) {
                        TickTimers.updateLeap { it.copy(menuOpacity = value) }
                    }
                    x -= 6
                }
                draw.field(id, field(id, leap.menuOpacity.toString()), "100", rightX(48), cy, 48, focused == id)
            }
        }
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                toggleRow(draw, rowY, "Lock HUD", "Show who / zone / reason on screen, including the latest door opener.", leap.showLockHud, false) {
                    TickTimers.updateLeap { it.copy(showLockHud = !it.showLockHud) }
                }
            } else {
                toggleRow(
                    draw,
                    rowY,
                    "Boss death notifier",
                    "Show the detected boss death for five seconds, before leap resolution.",
                    leap.showBossDeathNotifier,
                    true,
                ) {
                    TickTimers.updateLeap { it.copy(showBossDeathNotifier = !it.showBossDeathNotifier) }
                }
            }
        }
        return y
    }

    private fun drawLeapKeybinds(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        val helpY = pageHelp(draw, startY, "Keys that pick a leap target. Click Bind, then press a key.")
        var y = helpY
        y = section(draw, y + SECTION_GAP, "Keybinds", "Click Bind, then press a key. Esc clears.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                actionRow(draw, rowY, "Mode", leap.keybindMode.label(), last = false) { cy ->
                    draw.button("Next", rightX(52), cy, ButtonStyle.PRIMARY) {
                        TickTimers.updateLeap { it.copy(keybindMode = it.keybindMode.next()) }
                    }
                }
            } else {
                infoRow(draw, rowY, "Corners vs class", "Corners uses TL/TR/BL/BR. Class leaps to that dungeon class.", last = true)
            }
        }
        val bindRows = if (leap.keybindMode == LeapKeybindMode.CORNERS) {
            listOf(
                "topLeftKey" to "Top left",
                "topRightKey" to "Top right",
                "bottomLeftKey" to "Bottom left",
                "bottomRightKey" to "Bottom right",
            )
        } else {
            listOf(
                "archerKey" to "Archer",
                "berserkKey" to "Berserk",
                "healerKey" to "Healer",
                "mageKey" to "Mage",
                "tankKey" to "Tank",
            )
        }
        return grouped(draw, y, bindRows.size) { rowY, index ->
            val (id, label) = bindRows[index]
            bindRow(draw, rowY, label, id, leapKey(leap, id), index == bindRows.lastIndex)
        }
    }

    private fun drawLeapDebug(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Simulate the live party-chat and boss event pipeline.")
        if (!TickTimers.settings.debugMode && !LeapOrient.debugActive()) {
            return note(draw, helpY, "Turn on debug mode to simulate the live event pipeline.")
        }
        val leap = TickTimers.settings.leap
        var bossDropdown: Triple<Int, Int, Int>? = null
        var partyDropdown: Triple<Int, Int, Int>? = null
        var y = grouped(draw, startY, 4) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Game state", LeapGameState.currentId(), last = false) { cy ->
                    var x = rightX(0)
                    listOf("s1", "s2", "s3", "s4", "p2", "p4", "p5").asReversed().forEach { token ->
                        x -= draw.measure(token, draw.buttonFont) + 24
                        draw.chip(token, x, cy, LeapGameState.currentId().contains(token)) {
                            LeapGameState.simulate(token)
                            toast(LeapGameState.currentId())
                        }
                        x -= 6
                    }
                }
                1 -> actionRow(draw, rowY, "Last boss death", LeapBossDeathNotifier.status(), last = false) { }
                2 -> actionRow(draw, rowY, "Skip reason", LeapOrient.lastSkipReason(), last = false) { }
                else -> actionRow(draw, rowY, "Clear state", "Use live dungeon detection again.", last = true) { cy ->
                    draw.button("Live", rightX(52), cy, ButtonStyle.GHOST) {
                        LeapGameState.clearSimulate()
                        toast("state live")
                    }
                }
            }
        }
        y = grouped(draw, y, 2) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Boss events", "Chat line → eligibility → lock.", last = false) { cy ->
                    val options = listOf(
                        "Relic pickup" to "relic-pickup",
                        "Necron die" to "necron-die",
                        "Goldor die" to "goldor-die",
                        "Core" to "core",
                        "Goldor" to "goldor",
                        "Storm die" to "storm",
                        "Maxor die" to "maxor",
                    )
                    val selected = options.indexOfFirst { it.second == debugBossToken }.coerceAtLeast(0)
                    val label = options[selected].first
                    val runW = draw.button("Run", rightX(52), cy, ButtonStyle.PRIMARY) {
                        toast(LeapOrient.simulateBossToken(debugBossToken))
                    }
                    val selectW = draw.measure(label, draw.buttonFont) + 48
                    val selectX = rightX(52) - runW - 8 - selectW
                    draw.selectButton(label, selectX, cy, selectW, LEAP_ROW_CONTROL_HEIGHT) {
                        openDebugDropdown = if (openDebugDropdown == "boss") null else "boss"
                    }
                    bossDropdown = Triple(selectX, cy + 24, selectW)
                }
                else -> actionRow(draw, rowY, "Party / leaped", "Ping sender or leaped-to-sender custom triggers.", last = true) { cy ->
                    val options = listOf(
                        "Archer ping" to "arch",
                        "Bers ping" to "bers",
                        "Healer ping" to "heal",
                        "Mage ping" to "mage",
                        "Tank ping" to "tank",
                        "Leaped me" to "leaped",
                        "Sender scene" to "sender",
                    )
                    val selected = options.indexOfFirst { it.second == debugPartyToken }.coerceAtLeast(0)
                    val label = options[selected].first
                    val runW = draw.button("Run", rightX(52), cy, ButtonStyle.PRIMARY) {
                        toast(
                            when (debugPartyToken) {
                                "leaped" -> LeapOrient.simulateLeapedTo("me")
                                "sender" -> LeapOrient.handleCommand("leaporient scene sender") ?: "scene failed"
                                else -> LeapOrient.simulatePartyPing(debugPartyToken, "ee2")
                            },
                        )
                    }
                    val selectW = draw.measure(label, draw.buttonFont) + 48
                    val selectX = rightX(52) - runW - 8 - selectW
                    draw.selectButton(label, selectX, cy, selectW, LEAP_ROW_CONTROL_HEIGHT) {
                        openDebugDropdown = if (openDebugDropdown == "party") null else "party"
                    }
                    partyDropdown = Triple(selectX, cy + 24, selectW)
                }
            }
        }
        val clocks = leap.visibleTriggers(leap.selfClass).filter { it.event == LeapTriggerEvent.CLOCK && it.clock != null }
        if (clocks.isNotEmpty()) {
            y = grouped(draw, y, 1) { rowY, _ ->
                actionRow(draw, rowY, "Clock crossing", "Previous snapshot then threshold. Does not call resolveArm directly.", last = true) { cy ->
                    var x = rightX(0)
                    clocks.take(4).asReversed().forEach { trigger ->
                        val label = trigger.clock?.secondsInput() ?: "?"
                        x -= draw.measure(label, draw.buttonFont) + 24
                        draw.chip(label, x, cy, false) {
                            toast(LeapOrient.simulateClockCrossing(trigger))
                        }
                        x -= 6
                    }
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Chat diagnostics", "Control the live party-chat trigger lines shown on the debug HUD.")
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(
                    draw,
                    rowY,
                    "Chat debug HUD",
                    "Show the recent chat trigger history below the state.",
                    leap.showChatDebugHud,
                    false,
                ) {
                    TickTimers.updateLeap { it.copy(showChatDebugHud = !it.showChatDebugHud) }
                }
                1 -> toggleRow(
                    draw,
                    rowY,
                    "Trigger chat messages",
                    "Print LOCK and optional SKIP diagnostics into Minecraft chat.",
                    leap.showChatDebugMessages,
                    false,
                ) {
                    TickTimers.updateLeap { it.copy(showChatDebugMessages = !it.showChatDebugMessages) }
                }
                else -> toggleRow(
                    draw,
                    rowY,
                    "Show skipped chats",
                    "Include messages that did not arm a lock in enabled diagnostics.",
                    leap.showChatDebugSkips,
                    true,
                ) {
                    TickTimers.updateLeap { it.copy(showChatDebugSkips = !it.showChatDebugSkips) }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Preview menu", "Opens the debug Spirit Leap overlay.", last = true) { cy ->
                val itemW = draw.button("Item", rightX(52), cy, ButtonStyle.GHOST) {
                    toast(LeapOrient.givePreviewItem())
                }
                draw.button("Open", rightX(52) - itemW - 8, cy, ButtonStyle.PRIMARY) {
                    toast(LeapOrient.openPreview())
                }
            }
        }
        if (openDebugDropdown == "boss") {
            bossDropdown?.let { (x, anchorY, width) ->
                val options = listOf("Relic pickup", "Necron die", "Goldor die", "Core", "Goldor", "Storm die", "Maxor die")
                val selected = listOf("relic-pickup", "necron-die", "goldor-die", "core", "goldor", "storm", "maxor")
                    .indexOf(debugBossToken)
                    .coerceAtLeast(0)
                draw.dropdownMenu(x, anchorY, width, options, selected) { index ->
                    debugBossToken = listOf("relic-pickup", "necron-die", "goldor-die", "core", "goldor", "storm", "maxor")[index]
                    openDebugDropdown = null
                }
            }
        } else if (openDebugDropdown == "party") {
            partyDropdown?.let { (x, anchorY, width) ->
                val options = listOf("Archer ping", "Bers ping", "Healer ping", "Mage ping", "Tank ping", "Leaped me", "Sender scene")
                val selected = listOf("arch", "bers", "heal", "mage", "tank", "leaped", "sender")
                    .indexOf(debugPartyToken)
                    .coerceAtLeast(0)
                draw.dropdownMenu(x, anchorY, width, options, selected) { index ->
                    debugPartyToken = listOf("arch", "bers", "heal", "mage", "tank", "leaped", "sender")[index]
                    openDebugDropdown = null
                }
            }
        }
        y = drawLocationBlock(draw, y + SECTION_GAP)
        y = drawFireBlock(draw, y + SECTION_GAP)
        y = drawRecentEventsBlock(draw, y + SECTION_GAP)
        return y
    }

    /** Live island, floor and tab counts as read by the location service. */
    private fun drawLocationBlock(draw: MenuDraw, startY: Int): Int {
        val loc = SkyblockLocation.snapshot
        val stats = loc.stats
        var y = section(draw, startY, "Location", "Tab list and sidebar, sampled every half second. No packets are sent.")
        y = grouped(draw, y, 4) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Island", loc.islandLabel.ifBlank { "unknown" }, last = false) { }
                1 -> actionRow(
                    draw,
                    rowY,
                    "Raw area / subarea",
                    "${loc.rawArea.ifBlank { "-" }} / ${loc.subarea.ifBlank { "-" }}",
                    last = false,
                ) { }
                2 -> actionRow(
                    draw,
                    rowY,
                    "Floor / in dungeon",
                    "${loc.floor.name.takeIf { it != "NONE" } ?: "-"} / ${loc.inDungeon} · SkyBlock ${loc.onSkyblock}",
                    last = false,
                ) { }
                else -> actionRow(
                    draw,
                    rowY,
                    "Phase / tab stats",
                    "P-phase ${LeapGameState.currentId()} · secrets ${stats.secrets ?: "-"} · crypts ${stats.crypts ?: "-"} · " +
                        "deaths ${stats.deaths ?: "-"} · puzzles ${stats.puzzles ?: "-"} · cleared ${stats.cleared?.let { "$it%" } ?: "-"}",
                    last = true,
                ) { }
            }
        }
        return y
    }

    /** Fires one trigger event, either through normal card matching or to every card for that event. */
    private fun drawFireBlock(draw: MenuDraw, startY: Int): Int {
        val events = LeapTriggerEvent.entries
        val headers = leapEventHeaders(events)
        val labels = events.map { it.label() }
        var y = section(draw, startY, "Fire a trigger", "Runs the event as if the game produced it. Value is the captured name, color, island or class.")
        y = grouped(draw, y, 2) { rowY, index ->
            when (index) {
                0 -> actionRow(draw, rowY, "Event and value", "Value is optional; the event label is used when blank.", last = false) { cy ->
                    val shown = debugFireEvent.label()
                    val selectW = draw.measure(shown, draw.buttonFont).coerceAtLeast(120) + 48
                    val valueW = 140
                    val selectX = rightX(0) - selectW - 8 - valueW
                    draw.field(
                        "debug:fire:value",
                        field("debug:fire:value"),
                        "value",
                        selectX + selectW + 8,
                        cy,
                        valueW,
                        focused == "debug:fire:value",
                        LEAP_ROW_CONTROL_HEIGHT,
                    )
                    leapSelect(
                        draw,
                        "debug-fire",
                        shown,
                        labels,
                        events.indexOf(debugFireEvent),
                        LayoutRect(selectX, cy, selectW, LEAP_ROW_CONTROL_HEIGHT),
                        headers,
                    ) { choice -> debugFireEvent = events[choice] }
                }
                else -> actionRow(draw, rowY, "Fire", "Honors card filters, or ignores match text for every card.", last = true) { cy ->
                    val value = field("debug:fire:value")
                    val all = draw.button("Ignore filters", rightX(120), cy, ButtonStyle.GHOST) {
                        toast(LeapOrient.fireDebugEvent(debugFireEvent, value, honorFilters = false))
                    }
                    draw.button("Fire", rightX(120) - all - 8, cy, ButtonStyle.PRIMARY) {
                        toast(LeapOrient.fireDebugEvent(debugFireEvent, value, honorFilters = true))
                    }
                }
            }
        }
        return y
    }

    /** Newest-first history of detected events, recorded whether or not a card matched. */
    private fun drawRecentEventsBlock(draw: MenuDraw, startY: Int): Int {
        val recent = LeapEventLog.newestFirst()
        var y = section(draw, startY, "Recent events", "Last ${LeapEventLog.newestFirst().size.coerceAtMost(30)} detected events, newest first.")
        if (recent.isEmpty()) return note(draw, y, "No events detected yet.")
        y = grouped(draw, y, recent.size + 1) { rowY, index ->
            if (index == 0) {
                actionRow(draw, rowY, "Log", "${recent.size} shown", last = false) { cy ->
                    draw.button("Clear", rightX(52), cy, ButtonStyle.GHOST) {
                        LeapEventLog.clear()
                        toast("event log cleared")
                    }
                }
            } else {
                val entry = recent[index - 1]
                val time = "%02d:%02d".format((entry.timeMs / 60_000L) % 60, (entry.timeMs / 1000L) % 60)
                val detail = "${entry.value} · ${entry.source} · ${entry.cardsFired} card(s)"
                actionRow(draw, rowY, "$time  ${entry.event.label()}", detail, last = index == recent.size) { }
            }
        }
        return y
    }

    private fun bindRow(draw: MenuDraw, y: Int, title: String, bindId: String, code: Int, last: Boolean): Int =
        actionRow(draw, y, title, if (capturingBind == bindId) "Press a key…" else keyLabel(code), last) { cy ->
            draw.button("Bind", rightX(52), cy, ButtonStyle.PRIMARY) {
                capturingBind = bindId
                focused = null
                toast("press a key")
            }
        }

    private fun leapCardKey(card: LeapTriggerCard): String =
        card.triggers.map { it.presetId ?: it.id }.sorted().joinToString("|")

    /** Per-card state shared by the row controls while one trigger card is drawn. */
    private class LeapCardEdit(
        val draw: MenuDraw,
        val card: LeapTriggerCard,
        val latest: () -> LeapOrientTrigger,
        val save: (LeapOrientTrigger) -> Unit,
    ) {
        val id: String get() = card.trigger.id
    }

    private fun leapTriggerBlock(draw: MenuDraw, startY: Int, triggerCard: LeapTriggerCard): Int {
        val trigger = triggerCard.trigger
        fun latest() = TickTimers.settings.leap.triggers.firstOrNull { it.id == trigger.id } ?: trigger
        fun save(next: LeapOrientTrigger) {
            TickTimers.updateLeap { current ->
                if (trigger.isPreset) LeapTriggerCards.applyShared(current, triggerCard, next)
                else current.upsertTrigger(next)
            }
        }
        val left = innerLeft()
        val width = innerWidth()
        val expanded = leapCardKey(triggerCard) in ConfigUiSession.leapExpandedCards
        val rows = LeapTriggerLayout.rows(leapCardShape(triggerCard))
        val cardH = LeapTriggerLayout.cardHeight(expanded, rows.size)
        if (trigger.id == pendingRevealId) revealLeapCard(startY)
        card(draw, left, startY, width, cardH)
        val edit = LeapCardEdit(draw, triggerCard, ::latest, ::save)
        leapCardHeader(edit, startY, cardH, expanded)
        if (expanded) {
            draw.text(
                draw.fit(leapHelpText(trigger), width - 2 * LeapTriggerLayout.SIDE_PAD, draw.microFont),
                left + LeapTriggerLayout.SIDE_PAD,
                startY + LeapTriggerLayout.HELP_TOP,
                draw.palette.tertiary,
                draw.microFont,
            )
            rows.forEachIndexed { index, row ->
                val rect = LeapTriggerLayout.rowRect(left, startY, width, index)
                drawLeapRow(edit, row, if (row.indented) LeapTriggerLayout.indentedRect(rect) else rect)
            }
        }
        return startY + cardH + LeapTriggerLayout.CARD_GAP
    }

    private fun leapCardShape(card: LeapTriggerCard): LeapCardShape {
        val trigger = card.trigger
        val chain = trigger.event == LeapTriggerEvent.LEAP_CHAIN
        return LeapCardShape(
            clock = trigger.event == LeapTriggerEvent.CLOCK,
            health = trigger.event == LeapTriggerEvent.HEALTH_LOW,
            threshold = trigger.event.usesThreshold(),
            chat = trigger.event == LeapTriggerEvent.CHAT_MESSAGE,
            chatFromPlayer = trigger.chatSender == ChatSender.PLAYER,
            valueMatch = trigger.event in LeapOrientEngine.VALUE_FILTERED_EVENTS,
            namedMatch = trigger.event in LeapOrientEngine.NAME_FILTERED_EVENTS,
            colorMatch = trigger.event in LeapOrientEngine.COLOR_FILTERED_EVENTS,
            leaped =trigger.event == LeapTriggerEvent.LEAPED_TO || trigger.event == LeapTriggerEvent.SELF_LEAP || chain,
            chain = chain,
            stepKinds = trigger.steps.map { it.kind },
            sortArmedSteps = trigger.steps.withIndex()
                .filter { (_, step) -> LeapAutoRouter.isArmedSort(step) }
                .map { (index, _) -> index }
                .toSet(),
            groupedPreset = trigger.isPreset && card.triggers.size > 1,
            centerTarget = trigger.target,
            timedHold = trigger.holdMode == LeapHoldMode.TIMED,
            timedFollowSteps = trigger.steps.withIndex()
                .filter { (_, step) -> step.kind == LeapAutoKind.FOLLOWUP_LEAP && step.followHoldMode == LeapHoldMode.TIMED }
                .map { (index, _) -> index }
                .toSet(),
            followTargets = trigger.steps.withIndex()
                .filter { (_, step) -> step.kind == LeapAutoKind.FOLLOWUP_LEAP }
                .associate { (index, step) -> index to step.followTarget },
            markPlayerSteps = trigger.steps.withIndex()
                .filter { (_, step) -> step.kind == LeapAutoKind.MARK_PLAYER && step.markTargetsPlayer() }
                .map { (index, _) -> index }
                .toSet(),
            markArrowSteps = trigger.steps.withIndex()
                .filter { (_, step) -> step.kind == LeapAutoKind.MARK_PLAYER && LeapMarkRouter.styleOf(step) == MarkerStyle.ARROW }
                .map { (index, _) -> index }
                .toSet(),
            stepBranches = trigger.steps.withIndex().associate { (index, step) -> index to step.branch },
            stepTermCounts = trigger.steps.withIndex().associate { (index, step) -> index to step.conditions.size },
        )
    }

    /** Scrolls a freshly added card to the top of the list. [cardY] is its drawn screen y. */
    private fun revealLeapCard(cardY: Int) {
        pendingRevealId = null
        scroll = LeapTriggerLayout.revealScroll(cardY - metrics.viewTop + scroll)
    }

    private fun leapCardHeader(edit: LeapCardEdit, y: Int, cardH: Int, expanded: Boolean) {
        val draw = edit.draw
        val trigger = edit.card.trigger
        val left = innerLeft()
        val deleteWidth = draw.measure("Delete", draw.buttonFont) + 28
        val offWidth = if (trigger.isPreset) draw.measure("Off", draw.buttonFont) + 28 else 0
        val testWidth = draw.measure("Test", draw.buttonFont) + 28
        val header = LeapTriggerLayout.header(left, y, innerWidth(), deleteWidth, offWidth, testWidth)
        val clickHeight = if (expanded) LeapTriggerLayout.HEADER_TOP + LeapTriggerLayout.HEADER_HEIGHT else cardH
        draw.clickable(left, y, header.toggle.x - left - 8, clickHeight) {
            toggleLeapCard(edit.card, expanded)
        }
        draw.chevron(
            header.chevron.x + header.chevron.w / 2,
            header.chevron.y + header.chevron.h / 2,
            LEAP_CHEVRON_SIZE,
            expanded,
            draw.palette.tertiary,
        )
        val title = LeapTriggerLayout.plainText(if (expanded) trigger.summary(statsPresetLabels()) else trigger.title())
        draw.text(draw.fit(title, header.titleWidth, draw.smallFont), header.titleX, y + 14, draw.palette.text, draw.smallFont)
        if (!expanded) {
            val subtitleWidth = innerWidth() - (header.titleX - left) - LeapTriggerLayout.SIDE_PAD
            draw.text(
                draw.fit(LeapTriggerLayout.plainText(trigger.subtitle(statsPresetLabels())), subtitleWidth, draw.microFont),
                header.titleX,
                y + 38,
                draw.palette.secondary,
                draw.microFont,
            )
        }
        draw.toggle(header.toggle.x, header.toggle.y, trigger.enabled) {
            TickTimers.updateLeap { LeapTriggerCards.setEnabled(it, edit.card, !edit.latest().enabled) }
        }
        header.off?.let { off ->
            draw.button("Off", off.x, off.y, ButtonStyle.GHOST) {
                TickTimers.updateLeap { LeapTriggerCards.setEnabled(it, edit.card, false) }
            }
        }
        header.test?.let { test ->
            draw.button("Test", test.x, test.y, ButtonStyle.GHOST) {
                // Same close path as Esc; the auto queue waits for the screen to clear before the steps start.
                onClose()
                LeapOrient.testCard(edit.card.triggers.map { member ->
                    TickTimers.settings.leap.triggers.firstOrNull { it.id == member.id } ?: member
                })
            }
        }
        draw.button("Delete", header.delete.x, header.delete.y, ButtonStyle.DANGER) {
            TickTimers.updateLeap {
                if (trigger.isPreset) LeapTriggerCards.deletePresets(it, edit.card) else it.removeTrigger(trigger.id)
            }
        }
    }

    private fun toggleLeapCard(card: LeapTriggerCard, expanded: Boolean) {
        val key = leapCardKey(card)
        if (expanded) ConfigUiSession.leapExpandedCards.remove(key) else ConfigUiSession.leapExpandedCards.add(key)
    }

    private fun drawLeapRow(edit: LeapCardEdit, slot: LeapCardSlot, rect: LayoutRect) {
        val draw = edit.draw
        val label = LeapTriggerLayout.labelRect(rect)
        val labelColor = if (slot.row == LeapCardRow.CHAT_TEXT && chatRegexInvalid(edit.latest())) {
            draw.palette.danger
        } else {
            draw.palette.secondary
        }
        draw.text(
            draw.fit(leapRowLabel(edit, slot), label.w, draw.smallFont),
            label.x,
            label.y + (label.h - draw.lineHeight(draw.smallFont)) / 2,
            labelColor,
            draw.smallFont,
        )
        val control = LeapTriggerLayout.controlRect(rect)
        when (slot.row) {
            LeapCardRow.TRIGGER, LeapCardRow.MATCH, LeapCardRow.CHAIN_DELAY, LeapCardRow.HOLD_MODE, LeapCardRow.HOLD_DURATION,
            LeapCardRow.HOLD_LOCK, LeapCardRow.HEALTH_BELOW,
            LeapCardRow.THRESHOLD ->
                drawTimingRow(edit, slot.row, control)
            LeapCardRow.CHAT_SENDER, LeapCardRow.CHAT_PLAYER, LeapCardRow.CHAT_CHANNEL, LeapCardRow.CHAT_MODE,
            LeapCardRow.CHAT_TEXT, LeapCardRow.CHAT_COOLDOWN -> drawChatRow(edit, slot.row, control)
            LeapCardRow.STEP -> drawStepRow(edit, slot.step, control)
            LeapCardRow.ADD_STEP -> drawAddStepRow(edit, control)
            LeapCardRow.SORT_ARMED, LeapCardRow.SORT_LOCK, LeapCardRow.SORT_REOPEN, LeapCardRow.SORT_ARM_TIMEOUT ->
                drawSortOptionRow(edit, slot.row, slot.step, control)
            LeapCardRow.MARK_BY, LeapCardRow.MARK_TARGET_CLASS, LeapCardRow.MARK_TARGET_PLAYER, LeapCardRow.MARK_STYLE,
            LeapCardRow.MARK_COLOR, LeapCardRow.MARK_FOR, LeapCardRow.MARK_SHOW_NAME, LeapCardRow.MARK_OPACITY,
            LeapCardRow.MARK_BRIGHTNESS,
            LeapCardRow.MARK_SIZE, LeapCardRow.MARK_HEIGHT, LeapCardRow.MARK_PULSE,
            LeapCardRow.MARK_THROUGH, LeapCardRow.MARK_SHAPE, LeapCardRow.MARK_SPIN, LeapCardRow.MARK_BOB,
            LeapCardRow.MARK_PLAYER_GLOW, LeapCardRow.MARK_MARKER_GLOW ->
                drawMarkRow(edit, slot.row, slot.step, control)
            LeapCardRow.FOLLOW_CENTER, LeapCardRow.FOLLOW_CLASS, LeapCardRow.FOLLOW_PLAYER, LeapCardRow.FOLLOW_HOLD_MODE,
            LeapCardRow.FOLLOW_HOLD_DURATION, LeapCardRow.FOLLOW_HOLD_LOCK, LeapCardRow.FOLLOW_WITHIN ->
                drawFollowRow(edit, slot.row, slot.step, control)
            LeapCardRow.TIMER, LeapCardRow.SECONDS, LeapCardRow.METRIC, LeapCardRow.CLOCK_WHEN ->
                drawClockRow(edit, slot.row, control)
            LeapCardRow.STEP_BRANCH, LeapCardRow.STEP_COND, LeapCardRow.STEP_COND_ADD, LeapCardRow.STEP_REQUIRES ->
                drawBranchRow(edit, slot, control)
            else -> drawFilterRow(edit, slot.row, control)
        }
    }

    /** Action rows are numbered in run order; the add row is labelled only when the card has no actions yet. */
    private fun leapRowLabel(edit: LeapCardEdit, slot: LeapCardSlot): String = when (slot.row) {
        LeapCardRow.STEP -> if (edit.latest().steps.getOrNull(slot.step)?.kind == LeapAutoKind.FOLLOWUP_LEAP) {
            "Then (after leaping)"
        } else {
            "${slot.step + 1}."
        }
        LeapCardRow.ADD_STEP -> if (slot.step == 0) slot.row.label else ""
        LeapCardRow.STEP_COND -> if (slot.term == 0) {
            edit.latest().steps.getOrNull(slot.step)?.branch?.label ?: "If"
        } else {
            ""
        }
        else -> slot.row.label
    }

    private fun drawTimingRow(edit: LeapCardEdit, row: LeapCardRow, c: LayoutRect) {
        val trigger = edit.latest()
        when (row) {
            LeapCardRow.TRIGGER -> {
                val events = LeapTriggerEvent.entries
                leapSelectRow(edit, row, c, events.map { it.label() }, events.indexOf(trigger.event), leapEventHeaders(events)) { index ->
                    edit.save(syncTriggerEvent(edit.latest(), events[index]))
                }
            }
            LeapCardRow.MATCH -> drawMatchRow(edit, c)
            LeapCardRow.HEALTH_BELOW -> {
                val levels = (LeapHealthHysteresis.MIN_PERCENT..LeapHealthHysteresis.MAX_PERCENT step 10).toList()
                leapSelectRow(edit, row, c, levels.map { "$it%" }, levels.indexOf(trigger.healthBelowPercent).coerceAtLeast(0)) { index ->
                    edit.save(edit.latest().copy(healthBelowPercent = levels[index]))
                }
            }
            LeapCardRow.THRESHOLD -> {
                val levels = LeapTriggerEventMenu.thresholdLevels(trigger.event)
                val selected = levels.indexOf(trigger.threshold).coerceAtLeast(0)
                val options = levels.map { LeapTriggerEventMenu.thresholdLabel(trigger.event, it) }
                leapSelectRow(edit, row, c, options, selected) { index ->
                    edit.save(edit.latest().copy(threshold = levels[index]))
                }
            }
            LeapCardRow.CHAIN_DELAY -> leapField(edit, "lt:${edit.id}:delay", trigger.chainDelaySeconds.toString(), "0", c)
            LeapCardRow.HOLD_MODE -> {
                val modes = LeapHoldMode.entries
                leapSelectRow(edit, row, c, modes.map { it.label() }, modes.indexOf(trigger.holdMode)) { index ->
                    edit.save(edit.latest().copy(holdMode = modes[index]))
                }
            }
            LeapCardRow.HOLD_DURATION -> drawHoldDurationRow(edit, c)
            else -> drawHoldLockRow(edit, c)
        }
    }

    /** "Default (10s)" first, then the fixed choices. A saved value outside the choices is listed too. */
    private fun drawHoldDurationRow(edit: LeapCardEdit, c: LayoutRect) {
        val trigger = edit.latest()
        val global = TickTimers.settings.leap.durationSeconds
        val choices = (LeapHoldSpec.CHOICES_SECONDS + trigger.holdSeconds).filter { it > 0 }.distinct().sorted()
        val options = listOf("Default (${global}s)") + choices.map { "${it}s" }
        val selected = if (trigger.holdSeconds <= 0) 0 else choices.indexOf(trigger.holdSeconds) + 1
        leapSelectRow(edit, LeapCardRow.HOLD_DURATION, c, options, selected) { index ->
            val seconds = if (index == 0) 0 else choices[index - 1]
            edit.save(edit.latest().copy(holdSeconds = seconds, chainDurationSeconds = 0))
        }
    }

    /** Toggle with a caption that explains what the lock does. */
    private fun drawHoldLockRow(edit: LeapCardEdit, c: LayoutRect) {
        val draw = edit.draw
        val on = edit.latest().holdLocked
        val caption = if (on) "Other triggers can't replace this target while it's active" else "Off: other triggers can replace it"
        val toggleY = c.y + (c.h - TOGGLE_HEIGHT) / 2
        draw.toggle(c.x, toggleY, on) { edit.save(edit.latest().let { it.copy(holdLocked = !it.holdLocked) }) }
        draw.text(
            draw.fit(caption, c.w - TOGGLE_WIDTH - 8, draw.smallFont),
            c.x + TOGGLE_WIDTH + 8,
            c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
            draw.palette.secondary,
            draw.smallFont,
        )
    }

    /** A regex match text that does not compile. Only checked for the Regex mode. */
    private fun chatRegexInvalid(trigger: LeapOrientTrigger): Boolean =
        trigger.chatMode == ChatTextMode.REGEX && !ChatMessageMatch.validRegex(trigger.chatText)

    /** Cooldown label such as "0.5s" or "2s". */
    private fun chatCooldownLabel(ms: Int): String =
        if (ms % 1000 == 0) "${ms / 1000}s" else "${ms / 1000.0}s"

    /** Rows of a CHAT_MESSAGE card: sender, optional player name, channel, match mode, text and cooldown. */
    private fun drawChatRow(edit: LeapCardEdit, row: LeapCardRow, c: LayoutRect) {
        val trigger = edit.latest()
        when (row) {
            LeapCardRow.CHAT_SENDER -> {
                val senders = ChatSender.entries
                leapSelectRow(edit, row, c, senders.map { it.label }, senders.indexOf(trigger.chatSender)) { index ->
                    edit.save(edit.latest().copy(chatSender = senders[index]))
                }
            }
            LeapCardRow.CHAT_PLAYER -> leapField(edit, "lt:${edit.id}:chatPlayer", trigger.chatPlayer, "player name", c)
            LeapCardRow.CHAT_CHANNEL -> {
                val channels = ChatChannel.entries
                leapSelectRow(edit, row, c, channels.map { it.label }, channels.indexOf(trigger.chatChannel)) { index ->
                    edit.save(edit.latest().copy(chatChannel = channels[index]))
                }
            }
            LeapCardRow.CHAT_MODE -> {
                val modes = ChatTextMode.entries
                leapSelectRow(edit, row, c, modes.map { it.label }, modes.indexOf(trigger.chatMode)) { index ->
                    edit.save(edit.latest().copy(chatMode = modes[index]))
                }
            }
            LeapCardRow.CHAT_TEXT -> {
                val invalid = chatRegexInvalid(trigger)
                val noteWidth = if (invalid) LeapTriggerLayout.CHAT_NOTE_WIDTH else 0
                val fieldRect = LayoutRect(c.x, c.y, c.w - noteWidth, c.h)
                leapField(edit, "lt:${edit.id}:chatText", trigger.chatText, "e.g. abc", fieldRect)
                if (invalid) {
                    edit.draw.text(
                        edit.draw.fit("Invalid regex", noteWidth - 8, edit.draw.smallFont),
                        fieldRect.right + 8,
                        c.y + (c.h - edit.draw.lineHeight(edit.draw.smallFont)) / 2,
                        edit.draw.palette.danger,
                        edit.draw.smallFont,
                    )
                }
            }
            else -> {
                val choices = LeapChatCooldown.CHOICES_MS
                val nearest = choices.indices.minBy { kotlin.math.abs(choices[it] - trigger.chatCooldownMs) }
                leapSelectRow(edit, row, c, choices.map { chatCooldownLabel(it) }, nearest) { index ->
                    edit.save(edit.latest().copy(chatCooldownMs = choices[index]))
                }
            }
        }
    }

    private fun drawClockRow(edit: LeapCardEdit, row: LeapCardRow, c: LayoutRect) {
        val spec = edit.latest().clock ?: ClockSpec.stormElapsed(35.0)
        when (row) {
            LeapCardRow.TIMER -> leapSelectRow(edit, row, c, TimerClock.entries.map { it.label() }, TimerClock.entries.indexOf(spec.clock)) { index ->
                val seconds = TimerTrigger.parseSeconds(spec.secondsInput()) ?: 35.0
                edit.save(edit.latest().copy(clock = ClockSpec.forSeconds(TimerClock.entries[index], seconds)))
            }
            LeapCardRow.SECONDS -> leapField(edit, "lt:${edit.id}:secs", spec.secondsInput(), "seconds", c)
            LeapCardRow.METRIC -> leapSelectRow(edit, row, c, ClockMetric.entries.map { it.label() }, ClockMetric.entries.indexOf(spec.metric)) { index ->
                edit.save(edit.latest().copy(clock = spec.copy(metric = ClockMetric.entries[index])))
            }
            else -> leapSelectRow(edit, row, c, ClockCrossing.entries.map { it.label() }, ClockCrossing.entries.indexOf(spec.crossing)) { index ->
                edit.save(edit.latest().copy(clock = spec.copy(crossing = ClockCrossing.entries[index])))
            }
        }
    }

    /** One ordered action: kind picker, value picker, then move up / move down / remove icon buttons. */
    private fun drawStepRow(edit: LeapCardEdit, index: Int, control: LayoutRect) {
        val step = edit.latest().steps.getOrNull(index) ?: return
        val count = edit.latest().steps.size
        val parts = LeapTriggerLayout.stepControls(control)
        val draw = edit.draw
        draw.selectButton(step.kind.label, parts.kind.x, parts.kind.y, parts.kind.w, parts.kind.h) {
            val next = LeapAutoStep.nextKind(step.kind, index, edit.latest().steps)
            editStep(edit, index) { it.withKind(next, firstSortPresetName(), firstStatsPresetId()) }
        }
        drawStepValue(edit, index, step, parts.value)
        draw.stepIconButton(parts.up, StepGlyph.UP, enabled = index > 0) {
            edit.save(edit.latest().withStepMoved(index, index - 1))
        }
        draw.stepIconButton(parts.down, StepGlyph.DOWN, enabled = index < count - 1) {
            edit.save(edit.latest().withStepMoved(index, index + 1))
        }
        draw.stepIconButton(parts.remove, StepGlyph.REMOVE, enabled = true) {
            edit.save(edit.latest().withStepRemoved(index))
        }
    }

    private fun drawAddStepRow(edit: LeapCardEdit, c: LayoutRect) {
        val trigger = edit.latest()
        if (trigger.steps.isEmpty()) {
            edit.draw.text(
                "No actions yet",
                c.x,
                c.y + (c.h - edit.draw.lineHeight(edit.draw.smallFont)) / 2,
                edit.draw.palette.tertiary,
                edit.draw.smallFont,
            )
        }
        if (trigger.steps.size >= LeapAutoStep.MAX_STEPS) return
        val button = LayoutRect(c.x + c.w - ADD_ACTION_BUTTON_WIDTH, c.y, ADD_ACTION_BUTTON_WIDTH, c.h)
        edit.draw.inlineButton("+ Add action", button) {
            edit.save(edit.latest().withStepAdded(LeapAutoStep.newStep(firstSortPresetName())))
        }
    }

    /**
     * Indented options under a sort step. The armed row is a toggle with its caption; the lock row is a toggle
     * too; reopen-block and arm-expiry are cycle buttons. Rects come from [LeapTriggerLayout] like every other row.
     */
    private fun drawSortOptionRow(edit: LeapCardEdit, row: LeapCardRow, index: Int, c: LayoutRect) {
        val step = edit.latest().steps.getOrNull(index) ?: return
        val draw = edit.draw
        when (row) {
            LeapCardRow.SORT_ARMED -> drawSortToggle(edit, index, step.sortArmed, c, "Wait until I open inventory") {
                it.copy(sortArmed = !it.sortArmed)
            }
            LeapCardRow.SORT_LOCK -> drawSortToggle(edit, index, step.sortLockUntilDone, c, "Keep inventory open until sorted") {
                it.copy(sortLockUntilDone = !it.sortLockUntilDone)
            }
            LeapCardRow.SORT_REOPEN -> {
                val choices = LeapAutoStep.SORT_REOPEN_CHOICES_MS
                val current = choices.indexOf(step.sortReopenBlockMs)
                draw.selectButton(LeapAutoStep.reopenBlockLabel(step.sortReopenBlockMs), c.x, c.y, c.w, c.h) {
                    val next = choices[(current + 1).mod(choices.size)]
                    editStep(edit, index) { it.copy(sortReopenBlockMs = next) }
                }
            }
            else -> {
                val choices = LeapAutoStep.SORT_ARM_TIMEOUT_CHOICES_MS
                val current = choices.indexOf(step.sortArmTimeoutMs)
                draw.selectButton(LeapAutoStep.armTimeoutLabel(step.sortArmTimeoutMs), c.x, c.y, c.w, c.h) {
                    val next = choices[(current + 1).mod(choices.size)]
                    editStep(edit, index) { it.copy(sortArmTimeoutMs = next) }
                }
            }
        }
    }

    private fun drawSortToggle(
        edit: LeapCardEdit,
        index: Int,
        on: Boolean,
        c: LayoutRect,
        caption: String,
        change: (LeapAutoStep) -> LeapAutoStep,
    ) {
        val draw = edit.draw
        val toggleY = c.y + (c.h - TOGGLE_HEIGHT) / 2
        draw.toggle(c.x, toggleY, on) { editStep(edit, index, change) }
        draw.text(
            draw.fit(caption, c.w - TOGGLE_WIDTH - 8, draw.smallFont),
            c.x + TOGGLE_WIDTH + 8,
            c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
            draw.palette.secondary,
            draw.smallFont,
        )
    }

    /**
     * Rows every action has. Run picks the branch role (Always, If, Else if, Else); each condition term is one row of
     * [join][is / NOT][fact][value][remove]; Requires waits for a leap to a class before the row runs.
     */
    private fun drawBranchRow(edit: LeapCardEdit, slot: LeapCardSlot, c: LayoutRect) {
        val index = slot.step
        val step = edit.latest().steps.getOrNull(index) ?: return
        when (slot.row) {
            LeapCardRow.STEP_BRANCH -> {
                val branches = LeapConditions.allowedBranches(edit.latest().steps, index)
                val options = branches.map { branchLabel(it) }
                followSelect(edit, slot.row, index, c, options, branches.indexOf(step.branch).coerceAtLeast(0)) { i ->
                    editStep(edit, index) { current ->
                        val seeded = if (branches[i].hasCondition && current.conditions.isEmpty()) {
                            listOf(newTerm(LeapFact.LOADOUT))
                        } else {
                            current.conditions
                        }
                        current.copy(branch = branches[i], conditions = seeded)
                    }
                }
            }
            LeapCardRow.STEP_COND -> drawCondTerm(edit, index, slot.term, step, c)
            LeapCardRow.STEP_COND_ADD -> {
                val button = LayoutRect(c.x, c.y, minOf(c.w, ADD_ACTION_BUTTON_WIDTH + 20), c.h)
                edit.draw.inlineButton("+ Add condition", button) {
                    val fact = if (step.conditions.isEmpty()) LeapFact.LOADOUT else LeapFact.MY_CLASS
                    editStep(edit, index) { it.copy(conditions = it.conditions + newTerm(fact)) }
                }
            }
            else -> {
                val labels = listOf("None") + LeapDungeonClass.PLAYABLE.map { "After leap to ${it.displayName}" }
                followSelect(edit, slot.row, index, c, labels, optionalClassIndex(step.requires)) { i ->
                    editStep(edit, index) { it.copy(requires = optionalClassAt(i)) }
                }
            }
        }
    }

    private fun branchLabel(branch: LeapBranch): String = when (branch) {
        LeapBranch.ALWAYS -> "Always"
        LeapBranch.IF -> "If (condition)"
        LeapBranch.ELSE_IF -> "Else if (condition)"
        LeapBranch.ELSE -> "Else"
    }

    private fun newTerm(fact: LeapFact): LeapTerm = LeapTerm(fact, LeapConditions.defaultValue(fact))

    private fun drawCondTerm(edit: LeapCardEdit, index: Int, termIndex: Int, step: LeapAutoStep, c: LayoutRect) {
        val term = step.conditions.getOrNull(termIndex) ?: return
        val draw = edit.draw
        val parts = LeapTriggerLayout.condControls(c, hasJoin = termIndex > 0)
        fun editTerm(change: (LeapTerm) -> LeapTerm) = editStep(edit, index) { current ->
            current.copy(conditions = current.conditions.mapIndexed { i, t -> if (i == termIndex) change(t) else t })
        }
        parts.join?.let { rect ->
            draw.selectButton(term.join.label, rect.x, rect.y, rect.w, rect.h) { editTerm { it.copy(join = it.join.other()) } }
        }
        draw.selectButton(if (term.negate) "NOT" else "is", parts.not.x, parts.not.y, parts.not.w, parts.not.h) {
            editTerm { it.copy(negate = !it.negate) }
        }
        val facts = LeapFact.entries
        leapSelect(
            draw, "leap-select:${edit.id}:cond-fact:$index:$termIndex", term.fact.label, facts.map { it.label },
            facts.indexOf(term.fact), parts.fact, emptyMap(),
        ) { i -> editTerm { it.copy(fact = facts[i], value = LeapConditions.defaultValue(facts[i])) } }
        val choices = LeapConditions.choices(term.fact)
        val selected = choices.indexOfFirst { it.first.equals(term.value, ignoreCase = true) }
        leapSelect(
            draw, "leap-select:${edit.id}:cond-value:$index:$termIndex", LeapConditions.valueLabel(term.fact, term.value),
            choices.map { it.second }, selected.coerceAtLeast(0), parts.value, emptyMap(),
        ) { i -> editTerm { it.copy(value = choices[i].first) } }
        draw.stepIconButton(parts.remove, StepGlyph.REMOVE, enabled = true) {
            editStep(edit, index) { current ->
                val left = current.conditions.filterIndexed { i, _ -> i != termIndex }
                current.copy(conditions = left, branch = if (left.isEmpty() && current.branch.hasCondition) LeapBranch.ALWAYS else current.branch)
            }
        }
    }

    private fun drawStepValue(edit: LeapCardEdit, index: Int, step: LeapAutoStep, c: LayoutRect) {
        when (step.kind) {
            LeapAutoKind.SORT_INVENTORY -> drawSortPresetRow(edit, index, step, c)
            LeapAutoKind.EQUIP_STATS -> drawStatsPresetRow(edit, index, step, c)
            LeapAutoKind.SWITCH_LOADOUT -> drawLoadoutRow(edit, index, step, c)
            LeapAutoKind.MARK_PLAYER -> leapReadOnly(edit.draw, c, LeapMarkRouter.summary(step))
            // The leap settings are drawn as their own rows underneath, so this row has no value control.
            LeapAutoKind.LEAP_TARGET, LeapAutoKind.FOLLOWUP_LEAP -> Unit
        }
    }

    /**
     * Indented options under a mark step. Each row writes only its own step. Color is a swatch plus a cycle
     * button, For cycles through [LeapMarkRouter.SECONDS_CHOICES], and Show name is a toggle. Labels are not
     * drawn in the world yet, so the toggle says so.
     */
    private fun drawMarkRow(edit: LeapCardEdit, row: LeapCardRow, index: Int, c: LayoutRect) {
        val step = edit.latest().steps.getOrNull(index) ?: return
        val draw = edit.draw
        when (row) {
            LeapCardRow.MARK_BY -> {
                val options = listOf("Class", "Player")
                val selected = if (step.markTargetsPlayer()) 1 else 0
                followSelect(edit, row, index, c, options, selected) { i ->
                    val by = if (i == 1) LeapMarkRouter.BY_PLAYER else LeapMarkRouter.BY_CLASS
                    editStep(edit, index) { it.copy(markBy = by) }
                }
            }
            LeapCardRow.MARK_TARGET_CLASS -> {
                val classes = LeapDungeonClass.PLAYABLE
                val selected = classes.indexOf(LeapDungeonClass.fromToken(step.markClass)).coerceAtLeast(0)
                followSelect(edit, row, index, c, classes.map { it.displayName }, selected) { i ->
                    editStep(edit, index) { it.copy(markClass = classes[i].name) }
                }
            }
            LeapCardRow.MARK_TARGET_PLAYER ->
                leapField(edit, "lt:${edit.id}:mark:$index:player", step.markPlayer, "player", c)
            LeapCardRow.MARK_STYLE -> {
                val styles = MarkerStyle.entries
                val options = styles.map { style -> style.name.lowercase().replaceFirstChar { it.uppercase() } }
                followSelect(edit, row, index, c, options, styles.indexOf(LeapMarkRouter.styleOf(step))) { i ->
                    editStep(edit, index) { it.copy(markStyle = styles[i].name) }
                }
            }
            LeapCardRow.MARK_COLOR -> drawMarkColor(edit, index, step, c)
            LeapCardRow.MARK_FOR -> {
                val choices = LeapMarkRouter.SECONDS_CHOICES
                val current = choices.indexOf(step.markSeconds)
                draw.selectButton(LeapMarkRouter.secondsLabel(step.markSeconds), c.x, c.y, c.w, c.h) {
                    val next = choices[(current + 1).mod(choices.size)]
                    editStep(edit, index) { it.copy(markSeconds = next) }
                }
            }
            LeapCardRow.MARK_SHOW_NAME -> {
                val caption = if (step.markShowName) "On" else "Off"
                val toggleY = c.y + (c.h - TOGGLE_HEIGHT) / 2
                draw.toggle(c.x, toggleY, step.markShowName) {
                    editStep(edit, index) { it.copy(markShowName = !it.markShowName) }
                }
                draw.text(
                    draw.fit(caption, c.w - TOGGLE_WIDTH - 8, draw.smallFont),
                    c.x + TOGGLE_WIDTH + 8,
                    c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
                    draw.palette.secondary,
                    draw.smallFont,
                )
            }
            LeapCardRow.MARK_OPACITY -> cycleMarkRow(edit, index, c, MarkerLook.opacityLabel(step.markOpacity)) {
                editStep(edit, index) { it.copy(markOpacity = MarkerLook.cycle(MarkerLook.OPACITY_CHOICES, it.markOpacity)) }
            }
            LeapCardRow.MARK_BRIGHTNESS -> cycleMarkRow(edit, index, c, MarkerLook.brightnessLabel(step.markBrightness)) {
                editStep(edit, index) { it.copy(markBrightness = MarkerLook.cycle(MarkerLook.BRIGHTNESS_CHOICES, it.markBrightness)) }
            }
            LeapCardRow.MARK_SIZE -> cycleMarkRow(edit, index, c, MarkerLook.sizeLabel(step.markSize)) {
                editStep(edit, index) { it.copy(markSize = MarkerLook.cycle(MarkerLook.SIZE_CHOICES, it.markSize)) }
            }
            LeapCardRow.MARK_HEIGHT -> cycleMarkRow(edit, index, c, MarkerLook.heightLabel(step.markHeight)) {
                editStep(edit, index) { it.copy(markHeight = MarkerLook.cycle(MarkerLook.HEIGHT_CHOICES, it.markHeight)) }
            }
            LeapCardRow.MARK_PULSE -> markToggleRow(edit, index, c, step.markPulse, "On", "Off") {
                editStep(edit, index) { it.copy(markPulse = !it.markPulse) }
            }
            LeapCardRow.MARK_SHAPE -> cycleMarkRow(edit, index, c, MarkerShapes.label(step.markArrowShape)) {
                editStep(edit, index) { it.copy(markArrowShape = MarkerShapes.next(it.markArrowShape)) }
            }
            LeapCardRow.MARK_SPIN -> markToggleRow(edit, index, c, step.markSpin, "On", "Off") {
                editStep(edit, index) { it.copy(markSpin = !it.markSpin) }
            }
            LeapCardRow.MARK_BOB -> markToggleRow(edit, index, c, step.markBob, "On", "Off") {
                editStep(edit, index) { it.copy(markBob = !it.markBob) }
            }
            LeapCardRow.MARK_PLAYER_GLOW -> markToggleRow(edit, index, c, step.markPlayerGlow, "On (vanilla outline, team color)", "Off") {
                editStep(edit, index) { it.copy(markPlayerGlow = !it.markPlayerGlow) }
            }
            LeapCardRow.MARK_MARKER_GLOW -> markToggleRow(edit, index, c, step.markMarkerGlow, "On (bright core and halo)", "Off") {
                editStep(edit, index) { it.copy(markMarkerGlow = !it.markMarkerGlow) }
            }
            LeapCardRow.MARK_THROUGH -> markToggleRow(edit, index, c, step.markThroughWalls, "On", "Off") {
                editStep(edit, index) { it.copy(markThroughWalls = !it.markThroughWalls) }
            }
            else -> Unit
        }
    }

    /** A value button that cycles on press. */
    private fun cycleMarkRow(edit: LeapCardEdit, index: Int, c: LayoutRect, label: String, onPress: () -> Unit) {
        edit.draw.selectButton(label, c.x, c.y, c.w, c.h, onPress)
    }

    /** A toggle with a caption beside it, the same layout as Show name. */
    private fun markToggleRow(
        edit: LeapCardEdit,
        index: Int,
        c: LayoutRect,
        on: Boolean,
        onCaption: String,
        offCaption: String,
        onPress: () -> Unit,
    ) {
        val draw = edit.draw
        val toggleY = c.y + (c.h - TOGGLE_HEIGHT) / 2
        draw.toggle(c.x, toggleY, on, onPress)
        draw.text(
            draw.fit(if (on) onCaption else offCaption, c.w - TOGGLE_WIDTH - 8, draw.smallFont),
            c.x + TOGGLE_WIDTH + 8,
            c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
            draw.palette.secondary,
            draw.smallFont,
        )
    }

    /** Swatch and name of the marker color; both advance to the next palette entry when pressed. */
    private fun drawMarkColor(edit: LeapCardEdit, index: Int, step: LeapAutoStep, c: LayoutRect) {
        val choices = LeapMarkRouter.COLOR_CHOICES
        val current = choices.indexOfFirst { it.second == step.markColor }
        val cycle: () -> Unit = {
            val next = choices[(current + 1).mod(choices.size)].second
            editStep(edit, index) { it.copy(markColor = next) }
        }
        val swatchSize = 16
        edit.draw.swatch(c.x, c.y + (c.h - swatchSize) / 2, step.markColor and 0xFFFFFF, false, cycle)
        val textX = c.x + swatchSize + 8
        edit.draw.selectButton(LeapMarkRouter.colorName(step.markColor), textX, c.y, c.w - (textX - c.x), c.h, cycle)
    }

    /**
     * Indented leap settings of one follow-up step. Rects come from [LeapTriggerLayout]; the step's own fields
     * are edited, so each row writes only its step.
     */
    private fun drawFollowRow(edit: LeapCardEdit, row: LeapCardRow, index: Int, c: LayoutRect) {
        val step = edit.latest().steps.getOrNull(index) ?: return
        val draw = edit.draw
        when (row) {
            LeapCardRow.FOLLOW_CENTER -> {
                val targets = LeapCenterTarget.entries
                followSelect(edit, row, index, c, targets.map { it.label() }, targets.indexOf(step.followTarget)) { i ->
                    editStep(edit, index) { it.copy(followTarget = targets[i]) }
                }
            }
            LeapCardRow.FOLLOW_CLASS -> {
                val classes = LeapDungeonClass.PLAYABLE
                val selected = classes.indexOf(LeapDungeonClass.fromToken(step.followTargetValue)).coerceAtLeast(0)
                followSelect(edit, row, index, c, classes.map { it.displayName }, selected) { i ->
                    editStep(edit, index) { it.copy(followTargetValue = classes[i].name.lowercase()) }
                }
            }
            LeapCardRow.FOLLOW_PLAYER -> leapField(edit, "lt:${edit.id}:follow:$index:name", step.followTargetValue, "player", c)
            LeapCardRow.FOLLOW_HOLD_MODE -> {
                val modes = LeapHoldMode.entries
                followSelect(edit, row, index, c, modes.map { it.label() }, modes.indexOf(step.followHoldMode)) { i ->
                    editStep(edit, index) { it.copy(followHoldMode = modes[i]) }
                }
            }
            LeapCardRow.FOLLOW_HOLD_DURATION -> {
                val global = TickTimers.settings.leap.durationSeconds
                val choices = (LeapHoldSpec.CHOICES_SECONDS + step.followHoldSeconds).filter { it > 0 }.distinct().sorted()
                val options = listOf("Default (${global}s)") + choices.map { "${it}s" }
                val selected = if (step.followHoldSeconds <= 0) 0 else choices.indexOf(step.followHoldSeconds) + 1
                followSelect(edit, row, index, c, options, selected) { i ->
                    val seconds = if (i == 0) 0 else choices[i - 1]
                    editStep(edit, index) { it.copy(followHoldSeconds = seconds) }
                }
            }
            LeapCardRow.FOLLOW_HOLD_LOCK -> {
                val on = step.followHoldLocked
                val caption = if (on) "Other triggers can't replace this target while it's active" else "Off: other triggers can replace it"
                val toggleY = c.y + (c.h - TOGGLE_HEIGHT) / 2
                draw.toggle(c.x, toggleY, on) { editStep(edit, index) { it.copy(followHoldLocked = !it.followHoldLocked) } }
                draw.text(
                    draw.fit(caption, c.w - TOGGLE_WIDTH - 8, draw.smallFont),
                    c.x + TOGGLE_WIDTH + 8,
                    c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
                    draw.palette.secondary,
                    draw.smallFont,
                )
            }
            else -> {
                val choices = LeapAutoStep.FOLLOW_WITHIN_CHOICES_S
                val selected = choices.indexOf(step.followWithinSeconds).coerceAtLeast(0)
                followSelect(edit, row, index, c, choices.map { LeapAutoStep.followWithinLabel(it) }, selected) { i ->
                    editStep(edit, index) { it.copy(followWithinSeconds = choices[i]) }
                }
            }
        }
    }

    private fun followSelect(
        edit: LeapCardEdit,
        row: LeapCardRow,
        index: Int,
        c: LayoutRect,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        val shown = options.getOrElse(selected) { "" }
        leapSelect(edit.draw, "leap-select:${edit.id}:${row.name}:$index", shown, options, selected, c, emptyMap(), onSelect)
    }

    private fun editStep(edit: LeapCardEdit, index: Int, transform: (LeapAutoStep) -> LeapAutoStep) {
        edit.save(edit.latest().withStepChanged(index, transform))
    }

    private fun firstSortPresetName(): String = InvSortController.settings.presets.firstOrNull()?.name.orEmpty()

    private fun firstStatsPresetId(): String = TickTimers.settings.statsArmor.presets().firstOrNull()?.id.orEmpty()

    private fun drawSortPresetRow(edit: LeapCardEdit, index: Int, step: LeapAutoStep, c: LayoutRect) {
        val names = InvSortController.settings.presets.map { it.name }
        val storedIndex = names.indexOfFirst { it.equals(step.sortPreset, ignoreCase = true) }
        val shown = when {
            names.isEmpty() -> "(no presets)"
            step.sortPreset.isBlank() -> "choose"
            storedIndex < 0 -> "${step.sortPreset} (missing)"
            else -> names[storedIndex]
        }
        edit.draw.selectButton(shown, c.x, c.y, c.w, c.h) {
            if (names.isEmpty()) return@selectButton
            editStep(edit, index) { it.copy(sortPreset = names[(storedIndex + 1).mod(names.size)]) }
        }
    }

    private fun statsPresetLabels(): Map<String, String> = StatsArmor.presetLabels()

    private fun drawStatsPresetRow(edit: LeapCardEdit, index: Int, step: LeapAutoStep, c: LayoutRect) {
        val presets = TickTimers.settings.statsArmor.presets()
        val storedIndex = presets.indexOfFirst { it.id == step.statsPreset }
        val shown = when {
            presets.isEmpty() -> "(no presets)"
            step.statsPreset.isBlank() -> "choose"
            storedIndex < 0 -> "(missing)"
            else -> StatsArmor.presetLabel(presets[storedIndex])
        }
        edit.draw.selectButton(shown, c.x, c.y, c.w, c.h) {
            if (presets.isEmpty()) return@selectButton
            val next = presets[(storedIndex + 1).mod(presets.size)]
            editStep(edit, index) { it.copy(statsPreset = next.id) }
        }
    }

    private fun drawLoadoutRow(edit: LeapCardEdit, index: Int, step: LeapAutoStep, c: LayoutRect) {
        val stored = step.loadout
        val shown = if (stored in 1..LoadoutPlan.MAX_LOADOUT) "Loadout $stored" else "choose"
        edit.draw.selectButton(shown, c.x, c.y, c.w, c.h) {
            val next = if (stored in 1 until LoadoutPlan.MAX_LOADOUT) stored + 1 else 1
            editStep(edit, index) { it.copy(loadout = next) }
        }
    }

    private fun drawMatchRow(edit: LeapCardEdit, c: LayoutRect) {
        val trigger = edit.latest()
        val chain = trigger.event == LeapTriggerEvent.LEAP_CHAIN
        val tokens = (if (chain) listOf("any") else listOf("self", "any")) +
            LeapDungeonClass.PLAYABLE.map { it.shortName.lowercase() }
        val labels = tokens.map { token ->
            when (token) {
                "self" -> "Me: I leaped"
                "any" -> "Anyone"
                else -> "${LeapDungeonClass.fromToken(token)?.displayName ?: token} leaped"
            }
        }
        val selected = tokens.indexOfFirst { it.equals(trigger.match, ignoreCase = true) }.coerceAtLeast(0)
        leapSelectRow(edit, LeapCardRow.MATCH, c, labels, selected) { index ->
            edit.save(edit.latest().copy(match = tokens[index]))
        }
    }

    private fun drawFilterRow(edit: LeapCardEdit, row: LeapCardRow, c: LayoutRect) {
        val trigger = edit.latest()
        val classes = LeapDungeonClass.PLAYABLE
        val optionalClassLabels = listOf("None") + classes.map { it.displayName }
        when (row) {
            LeapCardRow.ONLY_WHEN -> leapSelectRow(edit, row, c, listOf("Any class") + classes.map { it.displayName }, optionalClassIndex(trigger.forClass)) { index ->
                edit.save(edit.latest().copy(forClass = optionalClassAt(index)))
            }
            LeapCardRow.FOR_PRESET -> leapReadOnly(edit.draw, c, edit.card.actorLabel())
            LeapCardRow.CENTER_ON -> leapSelectRow(edit, row, c, LeapCenterTarget.entries.map { it.label() }, LeapCenterTarget.entries.indexOf(trigger.target)) { index ->
                edit.save(edit.latest().copy(target = LeapCenterTarget.entries[index]))
            }
            LeapCardRow.CLASS -> leapSelectRow(edit, row, c, classes.map { it.displayName }, classes.indexOf(LeapDungeonClass.fromToken(trigger.targetValue)).coerceAtLeast(0)) { index ->
                edit.save(edit.latest().copy(targetValue = classes[index].name.lowercase()))
            }
            LeapCardRow.REQUIRES -> leapSelectRow(edit, row, c, optionalClassLabels, optionalClassIndex(trigger.requiresTargetClass)) { index ->
                edit.save(edit.latest().copy(requiresTargetClass = optionalClassAt(index)))
            }
            LeapCardRow.PHASE -> leapSelectRow(edit, row, c, LEAP_PHASE_LABELS, LEAP_PHASE_CHOICES.indexOf(trigger.phase).coerceAtLeast(0)) { index ->
                edit.save(edit.latest().copy(phase = LEAP_PHASE_CHOICES[index]))
            }
            LeapCardRow.FLOOR -> leapSelectRow(edit, row, c, LEAP_FLOOR_LABELS, LEAP_FLOOR_CHOICES.indexOf(trigger.floor).coerceAtLeast(0)) { index ->
                edit.save(edit.latest().copy(floor = LEAP_FLOOR_CHOICES[index]))
            }
            LeapCardRow.ROUTE -> leapSelectRow(edit, row, c, LEAP_ROUTE_LABELS, LEAP_ROUTE_CHOICES.indexOf(trigger.route).coerceAtLeast(0)) { index ->
                edit.save(edit.latest().copy(route = LEAP_ROUTE_CHOICES[index]))
            }
            LeapCardRow.SECTION -> leapSelectRow(edit, row, c, LEAP_SECTION_LABELS, trigger.section.coerceIn(0, 4)) { index ->
                edit.save(edit.latest().copy(section = index))
            }
            LeapCardRow.BOSS_ONLY -> leapSelectRow(edit, row, c, listOf("Any time", "Only in boss fight"), if (trigger.bossOnly) 1 else 0) { index ->
                edit.save(edit.latest().copy(bossOnly = index == 1))
            }
            LeapCardRow.TARGET_NAME -> leapField(edit, "lt:${edit.id}:name", trigger.targetValue, "player", c)
            LeapCardRow.TARGET_INFO -> leapReadOnly(edit.draw, c, "Uses the player captured by the event.")
            LeapCardRow.ZONE -> leapField(edit, "lt:${edit.id}:zone", trigger.zone, "SS", c)
            LeapCardRow.REASON -> leapField(edit, "lt:${edit.id}:reason", trigger.reason, "reason", c)
            LeapCardRow.PLAYER -> leapField(
                edit,
                "lt:${edit.id}:match",
                trigger.match.takeUnless { it.equals("any", ignoreCase = true) }.orEmpty(),
                "anyone",
                c,
            )
            LeapCardRow.COLOR -> leapField(
                edit,
                "lt:${edit.id}:match",
                trigger.match.takeUnless { it.equals("any", ignoreCase = true) }.orEmpty(),
                "any color",
                c,
            )
            LeapCardRow.VALUE -> leapField(
                edit,
                "lt:${edit.id}:match",
                trigger.match.takeUnless { it.equals("any", ignoreCase = true) }.orEmpty(),
                valuePlaceholder(trigger.event),
                c,
            )
            else -> Unit
        }
    }

    private fun leapSelectRow(
        edit: LeapCardEdit,
        row: LeapCardRow,
        c: LayoutRect,
        options: List<String>,
        selected: Int,
        headers: Map<Int, String> = emptyMap(),
        onSelect: (Int) -> Unit,
    ) {
        val shown = options.getOrElse(selected) { "" }
        leapSelect(edit.draw, "leap-select:${edit.id}:${row.name}", shown, options, selected, c, headers, onSelect)
    }

    private fun valuePlaceholder(event: LeapTriggerEvent): String = when (event) {
        LeapTriggerEvent.ISLAND_ENTERED, LeapTriggerEvent.ISLAND_LEFT -> "any island"
        LeapTriggerEvent.CLASS_SELECTED -> "any class"
        LeapTriggerEvent.BLESSING -> "any blessing"
        else -> "any"
    }

    private fun leapField(edit: LeapCardEdit, id: String, value: String, placeholder: String, c: LayoutRect) {
        edit.draw.field(id, field(id, value), placeholder, c.x, c.y, c.w, focused == id, c.h)
    }

    private fun leapReadOnly(draw: MenuDraw, c: LayoutRect, text: String) {
        draw.text(
            draw.fit(text, c.w, draw.smallFont),
            c.x,
            c.y + (c.h - draw.lineHeight(draw.smallFont)) / 2,
            draw.palette.secondary,
            draw.smallFont,
        )
    }

    private fun optionalClassIndex(clazz: LeapDungeonClass): Int =
        if (clazz.isReal()) LeapDungeonClass.PLAYABLE.indexOf(clazz) + 1 else 0

    private fun optionalClassAt(index: Int): LeapDungeonClass =
        if (index == 0) LeapDungeonClass.EMPTY else LeapDungeonClass.PLAYABLE[index - 1]

    private fun addLeapTrigger(trigger: LeapOrientTrigger) {
        TickTimers.updateLeap { it.upsertTrigger(trigger) }
        ConfigUiSession.leapExpandedCards.add(trigger.id)
        pendingRevealId = trigger.id
    }

    private fun syncTriggerEvent(trigger: LeapOrientTrigger, event: LeapTriggerEvent): LeapOrientTrigger {
        val leaped = event == LeapTriggerEvent.LEAPED_TO ||
            event == LeapTriggerEvent.SELF_LEAP ||
            event == LeapTriggerEvent.LEAP_CHAIN
        return trigger.copy(
            event = event,
            whenState = event.nativeState() ?: "any",
            phase = event.nativePhase() ?: LeapPhase.ANY,
            match = when (event) {
                LeapTriggerEvent.LEAPED_TO -> "self"
                LeapTriggerEvent.LEAP_CHAIN -> if (trigger.event == LeapTriggerEvent.LEAP_CHAIN) trigger.match else "any"
                else -> if (leaped) trigger.match else "any"
            },
            clock = if (event == LeapTriggerEvent.CLOCK) trigger.clock ?: ClockSpec.stormElapsed(35.0) else null,
            chainDelaySeconds = if (event == LeapTriggerEvent.LEAP_CHAIN) trigger.chainDelaySeconds else 0,
            chainDurationSeconds = if (event == LeapTriggerEvent.LEAP_CHAIN) trigger.chainDurationSeconds else 0,
        )
    }

    private fun leapKey(leap: LeapOrientSettings, id: String): Int = when (id) {
        "topLeftKey" -> leap.topLeftKey
        "topRightKey" -> leap.topRightKey
        "bottomLeftKey" -> leap.bottomLeftKey
        "bottomRightKey" -> leap.bottomRightKey
        "archerKey" -> leap.archerKey
        "berserkKey" -> leap.berserkKey
        "healerKey" -> leap.healerKey
        "mageKey" -> leap.mageKey
        "tankKey" -> leap.tankKey
        else -> LeapOrientSettings.UNBOUND
    }

    private fun applyBind(id: String, code: Int) {
        if (id == "fishKey") {
            val key = if (code <= 0) FishingMacroConfig.UNBOUND else code
            FishingMacro.edit { it.copy(key = key) }
            return
        }
        if (id.startsWith("petKey:")) {
            val index = id.removePrefix("petKey:").toIntOrNull() ?: return
            val key = if (code <= 0) PetSwapConfig.UNBOUND else code
            PetSwap.edit { config -> config.withPet(index) { it.copy(key = key) } }
            return
        }
        if (id == "freelookKey") {
            Freelook.setKey(code)
            return
        }
        if (id == "zoomKey") {
            Visuals.edit { it.copy(zoomKey = code) }
            return
        }
        if (id == "aimKey") {
            BossAim.edit { it.copy(key = code) }
            return
        }
        if (id == "aimLockKey") {
            BossAim.edit { it.copy(lockKey = code) }
            return
        }
        if (id == "dropUltimateStackKey") {
            TickTimers.updateDropUltimate { it.copy(stackKey = code) }
            return
        }
        if (id == "slayerCarryPlusKey") {
            TickTimers.updateSlayer { it.copy(plusKey = code) }
            return
        }
        if (id == "slayerCarryMinusKey") {
            TickTimers.updateSlayer { it.copy(minusKey = code) }
            return
        }
        if (id == "slayerCarryNextKey") {
            TickTimers.updateSlayer { it.copy(cycleNextKey = code) }
            return
        }
        if (id == "slayerCarryPrevKey") {
            TickTimers.updateSlayer { it.copy(cyclePrevKey = code) }
            return
        }
        TickTimers.updateLeap { leap ->
            when (id) {
                "topLeftKey" -> leap.copy(topLeftKey = code)
                "topRightKey" -> leap.copy(topRightKey = code)
                "bottomLeftKey" -> leap.copy(bottomLeftKey = code)
                "bottomRightKey" -> leap.copy(bottomRightKey = code)
                "archerKey" -> leap.copy(archerKey = code)
                "berserkKey" -> leap.copy(berserkKey = code)
                "healerKey" -> leap.copy(healerKey = code)
                "mageKey" -> leap.copy(mageKey = code)
                "tankKey" -> leap.copy(tankKey = code)
                else -> leap
            }
        }
    }

    private fun keyLabel(code: Int): String {
        if (code == LeapOrientSettings.UNBOUND || code == FreelookConfig.UNBOUND) return "None"
        return icantpyKeyLabel(code)
    }

    private fun drawCamera(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Rotate the camera without turning your character.")
        var y = section(
            draw,
            helpY,
            "Freelook",
            "Camera only. Switches to third person while active, then restores your view. Movement, hits, and look packets keep your original facing. Bind the key in Minecraft Controls (icantpy category).",
        )
        y = grouped(draw, y, 2) { rowY, index ->
            when (index) {
                0 -> toggleRow(
                    draw,
                    rowY,
                    "Enabled",
                    "Mouse turns the camera instead of the player while freelook is on.",
                    Freelook.enabled(),
                    last = false,
                ) { Freelook.setEnabled(!Freelook.enabled()) }
                else -> actionRow(
                    draw,
                    rowY,
                    "Mode",
                    Freelook.config.mode.label,
                    last = true,
                ) { cy ->
                    draw.button(Freelook.config.mode.label, rightX(110), cy, ButtonStyle.PRIMARY) {
                        Freelook.cycleMode()
                    }
                }
            }
        }
        return note(
            draw,
            y,
            "Options → Controls → icantpy → Freelook. Hold: look while the key is down. Toggle: tap on, tap off. Hold or toggle: tap latches, a longer press peeks until you release.",
        )
    }

    /** One on/off setting on a Visuals subtab. [read] and [write] take the live config, so clicks never use a stale copy. */
    private class VisualToggle(
        val title: String,
        val subtitle: String,
        val read: (VisualsConfig) -> Boolean,
        val write: (VisualsConfig, Boolean) -> VisualsConfig,
    )

    /** One numeric setting changed by a - / + pair, clamped to [min]..[max]. */
    private class VisualStep(
        val title: String,
        val step: Float,
        val min: Float,
        val max: Float,
        val read: (VisualsConfig) -> Float,
        val write: (VisualsConfig, Float) -> VisualsConfig,
    )

    private fun visualToggles(draw: MenuDraw, startY: Int, toggles: List<VisualToggle>): Int {
        val config = Visuals.config()
        var y = startY
        toggles.forEachIndexed { index, toggle ->
            y = toggleRow(draw, y, toggle.title, toggle.subtitle, toggle.read(config), index == toggles.lastIndex) {
                Visuals.edit { current -> toggle.write(current, !toggle.read(current)) }
            }
        }
        return y + 8
    }

    private fun visualSteps(draw: MenuDraw, startY: Int, steps: List<VisualStep>): Int {
        var y = startY
        steps.forEach { step ->
            val value = step.read(Visuals.config())
            y = actionRow(draw, y, step.title, "", false) { cy ->
                val plusX = rightX(32)
                val minusX = plusX - 6 - 32
                draw.button("-", minusX, cy, ButtonStyle.GHOST) {
                    Visuals.edit { c -> step.write(c, stepValue(step.read(c), -step.step, step.min, step.max)) }
                }
                draw.button("+", plusX, cy, ButtonStyle.GHOST) {
                    Visuals.edit { c -> step.write(c, stepValue(step.read(c), step.step, step.min, step.max)) }
                }
                val text = formatValue(value)
                draw.text(text, minusX - 14 - draw.measure(text, draw.bodyFont), cy + 6, draw.palette.text, draw.bodyFont)
            }
        }
        return y + 8
    }

    /** A row of chips; [selected] is highlighted, -1 for none. */
    private fun visualChipRow(
        draw: MenuDraw,
        y: Int,
        title: String,
        subtitle: String,
        labels: List<String>,
        selected: Int,
        last: Boolean,
        onPick: (Int) -> Unit,
    ): Int = actionRow(draw, y, title, subtitle, last) { cy ->
        var x = rightX(0)
        labels.indices.reversed().forEach { index ->
            x -= draw.measure(labels[index], draw.buttonFont) + 24
            draw.chip(labels[index], x, cy, index == selected) { onPick(index) }
            x -= 6
        }
    }

    /**
     * A row with a slider from [min] to [max] in [step]s, showing [format] of the value to its left. [onChange] runs
     * only when the snapped value changes, so dragging does not save on every mouse move.
     */
    private fun sliderRow(
        draw: MenuDraw,
        y: Int,
        title: String,
        subtitle: String,
        value: Float,
        min: Float,
        max: Float,
        step: Float,
        last: Boolean,
        format: (Float) -> String,
        onChange: (Float) -> Unit,
    ): Int = actionRow(draw, y, title, subtitle, last) { cy ->
        val x = rightX(SLIDER_WIDTH)
        val label = format(value)
        draw.text(label, x - 12 - draw.measure(label, draw.buttonFont), cy + 6, draw.palette.secondary, draw.buttonFont)
        draw.slider(x, cy, SLIDER_WIDTH, (value - min) / (max - min)) { f ->
            val snapped = Math.round((min + f * (max - min) - min) / step) * step + min
            val v = Math.round(snapped.coerceIn(min, max) * 100f) / 100f
            if (v != sliderLast) {
                sliderLast = v
                onChange(v)
            }
        }
    }

    private fun stepValue(current: Float, delta: Float, min: Float, max: Float): Float =
        Math.round((current + delta).coerceIn(min, max) * 100f) / 100f

    private fun formatValue(value: Float): String =
        if (value % 1f == 0f) "%.0f".format(value) else "%.2f".format(value)

    /** Index of the option closest to [value], or -1 when [options] is empty. */
    private fun nearestIndex(options: List<Float>, value: Float): Int =
        options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: -1

    private fun swingLabel(mode: SwingMode): String = when (mode) {
        SwingMode.VANILLA -> "Vanilla"
        SwingMode.PUSH -> "Push"
        SwingMode.STAB -> "Stab"
        SwingMode.SPIN -> "Spin"
        SwingMode.PUNCH -> "Punch"
        SwingMode.SWING_DOWN -> "Swing down"
    }

    private fun resetViewmodel(c: VisualsConfig): VisualsConfig {
        val d = VisualsConfig()
        return c.copy(
            offsetX = d.offsetX,
            offsetY = d.offsetY,
            offsetZ = d.offsetZ,
            rotYaw = d.rotYaw,
            rotPitch = d.rotPitch,
            rotRoll = d.rotRoll,
            scale = d.scale,
            swingMode = d.swingMode,
            swingSpeed = d.swingSpeed,
            noEquipReset = d.noEquipReset,
        )
    }

    private fun drawVisualsCleanup(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Hide overlays, camera shake and particles you find distracting. Everything is vanilla until you turn it on.")
        val rowsY = section(draw, helpY, "Render cleanup", "Each switch removes one vanilla effect. Changes apply immediately and are saved.")
        return visualToggles(
            draw,
            rowsY,
            listOf(
                VisualToggle("No fire overlay", "Hides the fire covering your screen while burning.", { it.noFireOverlay }) { c, v -> c.copy(noFireOverlay = v) },
                VisualToggle("No water overlay", "Hides the blue tint while underwater.", { it.noWaterOverlay }) { c, v -> c.copy(noWaterOverlay = v) },
                VisualToggle("No in-wall overlay", "Hides the overlay drawn when your head is inside a block.", { it.noInWallOverlay }) { c, v -> c.copy(noInWallOverlay = v) },
                VisualToggle("No hurt camera", "Stops the camera tilt when you take damage.", { it.noHurtCamera }) { c, v -> c.copy(noHurtCamera = v) },
                VisualToggle("No vignette", "Removes the dark edges on screen.", { it.noVignette }) { c, v -> c.copy(noVignette = v) },
                VisualToggle("No block break particles", "Hides the debris when blocks break.", { it.noBlockBreakParticles }) { c, v -> c.copy(noBlockBreakParticles = v) },
                VisualToggle("No explosion particles", "Hides explosion smoke and flash particles.", { it.noExplosionParticles }) { c, v -> c.copy(noExplosionParticles = v) },
                VisualToggle("No lightning", "Hides lightning bolts in the world.", { it.noLightning }) { c, v -> c.copy(noLightning = v) },
            ),
        )
    }

    private fun drawVisualsWorld(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Lighting, time of day and fog. These only change how your client draws the world.")
        var y = section(draw, helpY, "Light & time", "Fullbright lifts the darkness. Time of day pins the client clock; the server time is unchanged.")
        y = visualToggles(
            draw,
            y,
            listOf(
                VisualToggle("Fullbright", "Full brightness everywhere, as if gamma were maxed.", { it.fullbright }) { cfg, v -> cfg.copy(fullbright = v) },
                VisualToggle("Change time of day", "Pins the client clock to the time chosen below.", { it.timeChanger }) { cfg, v -> cfg.copy(timeChanger = v) },
            ),
        )
        val times = listOf(0f, 6000f, 12000f, 18000f)
        y = visualChipRow(
            draw, y, "Time of day", "Picking a time also turns the time changer on.",
            listOf("Sunrise", "Noon", "Sunset", "Midnight"),
            if (c.timeChanger) nearestIndex(times, c.dayTime.toFloat()) else -1,
            true,
        ) { index -> Visuals.edit { it.copy(dayTime = times[index].toLong(), timeChanger = true) } }
        y += 8
        y = section(draw, y, "Fog", "Fog distance scales how far the fog starts and ends. 1x is vanilla.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("No fog", "Removes fog entirely.", { it.noFog }) { cfg, v -> cfg.copy(noFog = v) }),
        )
        val fogScales = listOf(0.5f, 1f, 2f, 4f)
        y = visualChipRow(
            draw, y, "Fog distance", "Multiplier on the vanilla fog range.",
            listOf("0.5x", "1x", "2x", "4x"),
            nearestIndex(fogScales, c.fogDistance),
            true,
        ) { index -> Visuals.edit { it.copy(fogDistance = fogScales[index]) } }
        return y + 8
    }

    private fun drawVisualsCamera(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Third-person distance, zoom and aspect ratio.")
        var y = section(draw, helpY, "Distance", "Third-person camera distance in blocks.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Custom distance", "Use the distance below instead of vanilla.", { it.customCameraDistance }) { cfg, v -> cfg.copy(customCameraDistance = v) }),
        )
        val distances = listOf(2f, 4f, 6f, 8f, 12f)
        y = visualChipRow(
            draw, y, "Camera distance", "Picking a distance also turns custom distance on.",
            listOf("2", "4", "6", "8", "12"),
            if (c.customCameraDistance) nearestIndex(distances, c.cameraDistance) else -1,
            true,
        ) { index -> Visuals.edit { it.copy(cameraDistance = distances[index], customCameraDistance = true) } }
        y += 8
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Through walls", "Stops the third-person camera from pulling in against blocks.", { it.cameraThroughWalls }) { cfg, v -> cfg.copy(cameraThroughWalls = v) }),
        )
        y = section(draw, y, "Zoom", "Hold the zoom key to narrow the field of view. Unbound means zoom is off.")
        y = bindRow(draw, y, "Zoom key", "zoomKey", c.zoomKey, false)
        val zoomFactors = listOf(2f, 3f, 4f, 6f, 10f)
        y = visualChipRow(
            draw, y, "Zoom factor", "The field of view is divided by this while zoomed.",
            listOf("2x", "3x", "4x", "6x", "10x"),
            nearestIndex(zoomFactors, c.zoomFactor),
            false,
        ) { index -> Visuals.edit { it.copy(zoomFactor = zoomFactors[index]) } }
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Smooth zoom", "Eases the field of view in and out instead of snapping.", { it.zoomSmooth }) { cfg, v -> cfg.copy(zoomSmooth = v) }),
        )
        y = section(draw, y, "Aspect ratio", "Projects the view at a fixed width to height ratio.")
        val aspects = listOf(4f / 3f, 1.6f, 16f / 9f, 21f / 9f)
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Custom aspect", "Use the ratio below instead of the window's.", { it.customAspect }) { cfg, v -> cfg.copy(customAspect = v) }),
        )
        y = visualChipRow(
            draw, y, "Ratio", "Picking a ratio also turns custom aspect on.",
            listOf("4:3", "16:10", "16:9", "21:9"),
            if (c.customAspect) nearestIndex(aspects, c.aspectRatio) else -1,
            true,
        ) { index -> Visuals.edit { it.copy(aspectRatio = aspects[index], customAspect = true) } }
        return y + 8
    }

    private fun drawVisualsViewmodel(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Move, tilt and scale the held item, and change the swing. Off keeps the vanilla hand.")
        var y = section(draw, helpY, "Viewmodel", "Off keeps the vanilla hand, swing and equip animation, whatever is set below.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Enabled", "Applies the viewmodel settings on this page.", { it.viewmodelEnabled }) { cfg, v -> cfg.copy(viewmodelEnabled = v) }),
        )
        y = section(draw, y, "Position", "Offsets in blocks.")
        y = visualSteps(
            draw,
            y,
            listOf(
                VisualStep("Offset X", 0.05f, -2f, 2f, { it.offsetX }) { cfg, v -> cfg.copy(offsetX = v) },
                VisualStep("Offset Y", 0.05f, -2f, 2f, { it.offsetY }) { cfg, v -> cfg.copy(offsetY = v) },
                VisualStep("Offset Z", 0.05f, -2f, 2f, { it.offsetZ }) { cfg, v -> cfg.copy(offsetZ = v) },
            ),
        )
        y = section(draw, y, "Rotation", "Degrees.")
        y = visualSteps(
            draw,
            y,
            listOf(
                VisualStep("Yaw", 5f, -180f, 180f, { it.rotYaw }) { cfg, v -> cfg.copy(rotYaw = v) },
                VisualStep("Pitch", 5f, -180f, 180f, { it.rotPitch }) { cfg, v -> cfg.copy(rotPitch = v) },
                VisualStep("Roll", 5f, -180f, 180f, { it.rotRoll }) { cfg, v -> cfg.copy(rotRoll = v) },
            ),
        )
        y = section(draw, y, "Scale", "Size of the held item. 1 is vanilla.")
        y = visualSteps(
            draw,
            y,
            listOf(VisualStep("Scale", 0.05f, 0.1f, 2f, { it.scale }) { cfg, v -> cfg.copy(scale = v) }),
        )
        y = section(draw, y, "Swing", "Swing style and speed. Vanilla keeps the game's swing.")
        val modes = SwingMode.entries
        y = actionRow(draw, y, "Swing mode", "Click to cycle through the swing styles.", false) { cy ->
            draw.button(swingLabel(c.swingMode), rightX(120), cy, ButtonStyle.PRIMARY) {
                Visuals.edit { cfg -> cfg.copy(swingMode = modes[(cfg.swingMode.ordinal + 1) % modes.size]) }
            }
        }
        val speeds = listOf(0.5f, 1f, 1.5f, 2f)
        y = visualChipRow(
            draw, y, "Swing speed", "Higher is faster.",
            listOf("0.5x", "1x", "1.5x", "2x"),
            nearestIndex(speeds, c.swingSpeed),
            false,
        ) { index -> Visuals.edit { it.copy(swingSpeed = speeds[index]) } }
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("No equip reset", "Skips the equip animation when you switch items.", { it.noEquipReset }) { cfg, v -> cfg.copy(noEquipReset = v) }),
        )
        y = actionRow(draw, y, "Reset viewmodel", "Restores offsets, rotation, scale and swing to defaults.", true) { cy ->
            draw.button("Reset", rightX(90), cy, ButtonStyle.GHOST) {
                Visuals.edit { resetViewmodel(it) }
            }
        }
        return y + 8
    }

    private val visualColorNames = listOf("Pink", "Cyan", "Purple", "Red", "Gold", "Green", "White")
    private val visualColorValues = listOf(0xFF7AF0, 0x6AD5FF, 0xA070FF, 0xFF4D4D, 0xFFC94D, 0x5DFF8A, 0xFFFFFF)

    /** A chip per preset color; the chip matching [color] is highlighted. */
    private fun visualColorRow(draw: MenuDraw, y: Int, title: String, subtitle: String, color: Int, last: Boolean, onPick: (Int) -> Unit): Int =
        visualChipRow(draw, y, title, subtitle, visualColorNames, visualColorValues.indexOf(color), last) { index ->
            onPick(visualColorValues[index])
        }

    /** A row with a button showing [label]; each click runs [onCycle], which advances the value. */
    private fun visualCycleRow(draw: MenuDraw, y: Int, title: String, subtitle: String, label: String, last: Boolean, onCycle: () -> Unit): Int =
        actionRow(draw, y, title, subtitle, last) { cy ->
            draw.button(label, rightX(120), cy, ButtonStyle.PRIMARY) { onCycle() }
        }

    private fun drawVisualsHand(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Recolors the first-person hand and held item. Off keeps the vanilla colors.")
        var y = section(draw, helpY, "Hand shader", "Pick a style, colors, speed and strength.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Hand shader", "Recolors your first-person hand and held item.", { it.handShader }) { cfg, v -> cfg.copy(handShader = v) }),
        )
        val modes = HandShaderMode.entries
        y = visualCycleRow(draw, y, "Shader mode", "Click to cycle through the shader styles.", c.handShaderMode.label, false) {
            Visuals.edit { cfg -> cfg.copy(handShaderMode = modes[(cfg.handShaderMode.ordinal + 1) % modes.size]) }
        }
        y = visualColorRow(draw, y, "Color 1", "Primary color.", c.handColor1, false) { color ->
            Visuals.edit { it.copy(handColor1 = color) }
        }
        y = visualColorRow(draw, y, "Color 2", "Second color for two-tone styles.", c.handColor2, false) { color ->
            Visuals.edit { it.copy(handColor2 = color) }
        }
        val speeds = listOf(0.5f, 1f, 2f, 3f)
        y = visualChipRow(
            draw, y, "Speed", "Animation speed.",
            listOf("0.5x", "1x", "2x", "3x"),
            nearestIndex(speeds, c.handShaderSpeed),
            false,
        ) { index -> Visuals.edit { it.copy(handShaderSpeed = speeds[index]) } }
        val opacities = listOf(0.25f, 0.5f, 0.85f, 1f)
        y = visualChipRow(
            draw, y, "Opacity", "How strongly the effect replaces the hand's colors.",
            listOf("25%", "50%", "85%", "100%"),
            nearestIndex(opacities, c.handShaderOpacity),
            true,
        ) { index -> Visuals.edit { it.copy(handShaderOpacity = opacities[index]) } }
        return y + 8
    }

    private fun drawVisualsEffects(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Client-only trail, hat and floating particles around you. Everything is off until you turn it on.")
        var y = section(draw, helpY, "Trail", "A movement trail behind you, drawn as a ribbon or particles.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Trail", "Draws a trail as you move.", { it.trail }) { cfg, v -> cfg.copy(trail = v) }),
        )
        val styles = TrailStyle.entries
        y = visualCycleRow(draw, y, "Trail style", "Click to cycle through the trail styles.", c.trailStyle.label, false) {
            Visuals.edit { cfg -> cfg.copy(trailStyle = styles[(cfg.trailStyle.ordinal + 1) % styles.size]) }
        }
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Trail rainbow", "Cycles the trail through the rainbow.", { it.trailRainbow }) { cfg, v -> cfg.copy(trailRainbow = v) }),
        )
        y = visualColorRow(draw, y, "Trail color", "Used when rainbow is off.", c.trailColor, false) { color ->
            Visuals.edit { it.copy(trailColor = color) }
        }
        y = visualChipRow(
            draw, y, "Trail length", "Number of recorded positions.",
            listOf("10", "20", "30", "60"),
            nearestIndex(listOf(10f, 20f, 30f, 60f), c.trailLength.toFloat()),
            false,
        ) { index -> Visuals.edit { it.copy(trailLength = listOf(10, 20, 30, 60)[index]) } }
        y = visualChipRow(
            draw, y, "Trail width", "Line thickness.",
            listOf("1", "2", "3", "5"),
            nearestIndex(listOf(1f, 2f, 3f, 5f), c.trailWidth),
            true,
        ) { index -> Visuals.edit { it.copy(trailWidth = listOf(1f, 2f, 3f, 5f)[index]) } }
        y += 8
        y = section(draw, y, "China hat", "A cone floating above your head.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("China hat", "Shows a cone above your head.", { it.chinaHat }) { cfg, v -> cfg.copy(chinaHat = v) }),
        )
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Hat rainbow", "Cycles the hat through the rainbow.", { it.chinaHatRainbow }) { cfg, v -> cfg.copy(chinaHatRainbow = v) }),
        )
        y = visualColorRow(draw, y, "Hat color", "Used when rainbow is off.", c.chinaHatColor, false) { color ->
            Visuals.edit { it.copy(chinaHatColor = color) }
        }
        y = visualChipRow(
            draw, y, "Hat size", "Cone radius in blocks.",
            listOf("0.5", "0.7", "1.0"),
            nearestIndex(listOf(0.5f, 0.7f, 1f), c.chinaHatSize),
            false,
        ) { index -> Visuals.edit { it.copy(chinaHatSize = listOf(0.5f, 0.7f, 1f)[index]) } }
        y = visualChipRow(
            draw, y, "Hat height", "Cone height in blocks.",
            listOf("0.2", "0.3", "0.5"),
            nearestIndex(listOf(0.2f, 0.3f, 0.5f), c.chinaHatHeight),
            true,
        ) { index -> Visuals.edit { it.copy(chinaHatHeight = listOf(0.2f, 0.3f, 0.5f)[index]) } }
        y += 8
        y = section(draw, y, "Ambient particles", "Floating particles spawned around you.")
        y = visualToggles(
            draw,
            y,
            listOf(VisualToggle("Ambient particles", "Spawns client-only particles around you.", { it.ambientParticles }) { cfg, v -> cfg.copy(ambientParticles = v) }),
        )
        val ambientStyles = AmbientStyle.entries
        y = visualCycleRow(draw, y, "Particle style", "Click to cycle through the particle styles.", c.ambientStyle.label, false) {
            Visuals.edit { cfg -> cfg.copy(ambientStyle = ambientStyles[(cfg.ambientStyle.ordinal + 1) % ambientStyles.size]) }
        }
        y = visualChipRow(
            draw, y, "Density", "Particles spawned per second.",
            listOf("2", "6", "12", "20"),
            nearestIndex(listOf(2f, 6f, 12f, 20f), c.ambientDensity.toFloat()),
            false,
        ) { index -> Visuals.edit { it.copy(ambientDensity = listOf(2, 6, 12, 20)[index]) } }
        y = visualChipRow(
            draw, y, "Radius", "Spawn radius around you in blocks.",
            listOf("2", "3", "5", "8"),
            nearestIndex(listOf(2f, 3f, 5f, 8f), c.ambientRadius),
            true,
        ) { index -> Visuals.edit { it.copy(ambientRadius = listOf(2f, 3f, 5f, 8f)[index]) } }
        return y + 8
    }

    private fun resetModel(c: VisualsConfig): VisualsConfig {
        val d = VisualsConfig()
        return c.copy(
            dinnerbone = d.dinnerbone,
            spinbot = d.spinbot,
            spinSpeed = d.spinSpeed,
            modelScaleX = d.modelScaleX,
            modelScaleY = d.modelScaleY,
            modelScaleZ = d.modelScaleZ,
        )
    }

    private fun drawVisualsModel(draw: MenuDraw, startY: Int): Int {
        val c = Visuals.config()
        val helpY = pageHelp(draw, startY, "Only your own model, seen in third person (F5).")
        var y = section(draw, helpY, "Model", "Flip, spin and resize your player model.")
        y = visualToggles(
            draw,
            y,
            listOf(
                VisualToggle("Upside down (Dinnerbone)", "Flips your model upside down.", { it.dinnerbone }) { cfg, v -> cfg.copy(dinnerbone = v) },
                VisualToggle("Spin", "Rotates your model every tick.", { it.spinbot }) { cfg, v -> cfg.copy(spinbot = v) },
            ),
        )
        y = visualChipRow(
            draw, y, "Spin speed", "Degrees per tick.",
            listOf("5", "10", "20", "40"),
            nearestIndex(listOf(5f, 10f, 20f, 40f), c.spinSpeed),
            false,
        ) { index -> Visuals.edit { it.copy(spinSpeed = listOf(5f, 10f, 20f, 40f)[index]) } }
        y = section(draw, y, "Scale", "Size per axis. 1 is vanilla.")
        y = visualSteps(
            draw,
            y,
            listOf(
                VisualStep("Scale X", 0.05f, 0.25f, 2f, { it.modelScaleX }) { cfg, v -> cfg.copy(modelScaleX = v) },
                VisualStep("Scale Y", 0.05f, 0.25f, 2f, { it.modelScaleY }) { cfg, v -> cfg.copy(modelScaleY = v) },
                VisualStep("Scale Z", 0.05f, 0.25f, 2f, { it.modelScaleZ }) { cfg, v -> cfg.copy(modelScaleZ = v) },
            ),
        )
        y = actionRow(draw, y, "Reset model", "Restores the flip, spin and scale to defaults.", true) { cy ->
            draw.button("Reset", rightX(90), cy, ButtonStyle.GHOST) {
                Visuals.edit { resetModel(it) }
            }
        }
        return y + 8
    }

    private fun drawPerformance(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Smoother frames and lower video cost.")
        var y = section(
            draw,
            helpY,
            "Performance",
            "Motion blur blends the previous frame into the current one. The max-FPS preset lowers the video settings that cost the most frames.",
        )
        val cullingOn = Culling.config.enabled
        y = grouped(draw, y, 7) { rowY, index ->
            when (index) {
                0 -> toggleRow(
                    draw,
                    rowY,
                    "Motion blur",
                    "Blends the last frame in. One texture copy per frame, no Fast Render trade-off.",
                    MotionBlur.config.enabled,
                    last = false,
                ) { MotionBlur.setEnabled(!MotionBlur.config.enabled) }
                1 -> actionRow(draw, rowY, "Blur strength", "${(MotionBlur.config.strength * 100).toInt()}%", last = false) { cy ->
                    var x = rightX(0)
                    MotionBlurConfig.STRENGTH_PRESETS.asReversed().forEach { value ->
                        val label = "${(value * 100).toInt()}%"
                        x -= draw.measure(label, draw.buttonFont) + 24
                        draw.chip(label, x, cy, kotlin.math.abs(MotionBlur.config.strength - value) < 0.001f) {
                            MotionBlur.setStrength(value)
                        }
                        x -= 6
                    }
                }
                2 -> actionRow(
                    draw,
                    rowY,
                    "Max FPS preset",
                    "Lower video settings that cost the most FPS, keep it looking decent. Your old settings are saved.",
                    last = false,
                ) { cy ->
                    val applyX = rightX(90)
                    if (PerformancePreset.hasBackup) {
                        draw.button("Restore", applyX - 6 - 80, cy, ButtonStyle.GHOST) {
                            toast(PerformancePreset.restore())
                        }
                    }
                    draw.button("Apply", applyX, cy, ButtonStyle.PRIMARY) {
                        toast(PerformancePreset.apply())
                    }
                }
                3 -> actionRow(
                    draw,
                    rowY,
                    "Frame generation",
                    "Skips every other world render and turns the last frame to match your mouse. Smoother looking around; fast strafing can wobble at the edges.",
                    last = false,
                ) { cy ->
                    var x = rightX(0)
                    FrameGenConfig.MODES.asReversed().forEach { mode ->
                        val label = frameGenLabel(mode)
                        x -= draw.measure(label, draw.buttonFont) + 24
                        draw.chip(label, x, cy, FrameGen.config.generatedPerReal == mode) {
                            FrameGen.setGeneratedPerReal(mode)
                        }
                        x -= 6
                    }
                }
                4 -> toggleRow(
                    draw,
                    rowY,
                    "Adaptive frame gen",
                    "Only generates frames when your real FPS is below your monitor's refresh rate.",
                    FrameGen.config.adaptive,
                    last = false,
                ) { FrameGen.setAdaptive(!FrameGen.config.adaptive) }
                5 -> toggleRow(
                    draw,
                    rowY,
                    "Performance HUD",
                    "FPS, 1% lows, frame time, frame-gen split and culled counts.",
                    PerfHud.enabled,
                    last = false,
                ) { PerfHud.setEnabled(!PerfHud.enabled) }
                else -> toggleRow(
                    draw,
                    rowY,
                    "Smart culling",
                    "Skips mobs, chests, heads, name tags and particles you can't see or are far away.",
                    cullingOn,
                    last = !cullingOn,
                ) { Culling.setEnabled(!Culling.config.enabled) }
            }
        }
        if (cullingOn) {
            y = grouped(draw, y, 4) { rowY, index ->
                val last = index == 3
                when (index) {
                    0 -> toggleRow(draw, rowY, "Hidden & far entities", "Skips mobs and players that are out of view or far away.", Culling.config.entities, last = false) {
                        Culling.setEntities(!Culling.config.entities)
                    }
                    1 -> toggleRow(draw, rowY, "Far block entities", "Skips chests, heads and signs that are far away.", Culling.config.blockEntities, last = false) {
                        Culling.setBlockEntities(!Culling.config.blockEntities)
                    }
                    2 -> toggleRow(draw, rowY, "Far particles", "Skips particles that are far away.", Culling.config.particles, last = false) {
                        Culling.setParticles(!Culling.config.particles)
                    }
                    else -> toggleRow(draw, rowY, "Far name tags", "Skips name tags that are far away.", Culling.config.nameTags, last = last) {
                        Culling.setNameTags(!Culling.config.nameTags)
                    }
                }
            }
        }
        return y
    }

    private fun frameGenLabel(mode: Int): String = when (mode) {
        0 -> "Off"
        1 -> "2×"
        2 -> "3×"
        else -> "$mode×"
    }

    private fun drawCombat(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Press Q while holding a weapon to cast your ultimate.")
        val settings = TickTimers.settings.dropUltimate
        var y = section(
            draw,
            helpY,
            "Class ability drop",
            "In dungeons, pressing your drop key first selects the ability slot so the class ultimate fires, then restores your held slot. The held item is not thrown.",
        )
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw,
                rowY,
                "Enabled",
                "Uses your normal drop key (default Q). Dungeon-only.",
                settings.enabled,
                last = true,
            ) { TickTimers.updateDropUltimate { it.copy(enabled = !it.enabled) } }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Single ability slot", "Hotbar slot 1-9 for the plain Q ability.", last = true) { cy ->
                slotChips(draw, cy, settings.slot) { value ->
                    TickTimers.updateDropUltimate { it.copy(slot = value) }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Stack ability slot", "Hotbar slot 1-9 for the Ctrl+Q and stack-key ability.", last = true) { cy ->
                slotChips(draw, cy, settings.stackSlot) { value ->
                    TickTimers.updateDropUltimate { it.copy(stackSlot = value) }
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            bindRow(draw, rowY, "Stack ability key", "dropUltimateStackKey", settings.stackKey, last = true)
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw,
                rowY,
                "Close SkyBlock Menu",
                "If the ability slot opens the SkyBlock Menu, close it again.",
                settings.closeSkyblockMenu,
                last = true,
            ) { TickTimers.updateDropUltimate { it.copy(closeSkyblockMenu = !it.closeSkyblockMenu) } }
        }
        return y
    }

    private fun slotChips(draw: MenuDraw, cy: Int, selected: Int, onSelect: (Int) -> Unit) {
        var x = rightX(0)
        (DropUltimateSettings.MAX_SLOT downTo DropUltimateSettings.MIN_SLOT).forEach { value ->
            val label = value.toString()
            x -= draw.measure(label, draw.buttonFont) + 24
            draw.chip(label, x, cy, selected == value) { onSelect(value) }
            x -= 6
        }
    }

    private fun drawCarriesCounter(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings.slayer
        val helpY = pageHelp(draw, startY, "Counts boss kills for carried players. Auto count adds 1 when their boss dies near you.")
        var y = section(
            draw,
            helpY,
            "Slayer carrier",
            "Track a count per carried player. Auto-counts when that player's slayer boss dies near you.",
        )
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Enabled", "Turn the carry counter on.", settings.enabled, last = false) {
                    TickTimers.updateSlayer { it.copy(enabled = !it.enabled) }
                }
                1 -> toggleRow(
                    draw,
                    rowY,
                    "Auto count",
                    "Add 1 when a carried player's boss is killed near you.",
                    settings.autoCount,
                    last = false,
                ) { TickTimers.updateSlayer { it.copy(autoCount = !it.autoCount) } }
                else -> toggleRow(
                    draw,
                    rowY,
                    "Notify on target",
                    "Show an alert when a player's count reaches their target.",
                    settings.notifyOnTarget,
                    last = true,
                ) { TickTimers.updateSlayer { it.copy(notifyOnTarget = !it.notifyOnTarget) } }
            }
        }
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                toggleRow(
                    draw,
                    rowY,
                    "Show HUD",
                    "Draw every player's progress on screen. Position and style are on the HUD canvas.",
                    TickTimers.settings.carryHud,
                    last = false,
                ) { TickTimers.toggle("carryHud") }
            } else {
                actionRow(draw, rowY, "HUD style", "Drag to move, corners to scale, plus font and color.", last = true) { cy ->
                    draw.button("Edit HUD", rightX(72), cy, ButtonStyle.PRIMARY) {
                        IcantpyGui.openHudEditor(TimerHud.CARRY)
                    }
                }
            }
        }
        return y
    }

    private fun drawCarriesKeys(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings.slayer
        val helpY = pageHelp(draw, startY, "Keys change the selected player's count without the mouse.")
        var y = helpY
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                bindRow(draw, rowY, "+1 key", "slayerCarryPlusKey", settings.plusKey, last = false)
            } else {
                bindRow(draw, rowY, "-1 key", "slayerCarryMinusKey", settings.minusKey, last = true)
            }
        }
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                bindRow(draw, rowY, "Next player key", "slayerCarryNextKey", settings.cycleNextKey, last = false)
            } else {
                bindRow(draw, rowY, "Previous player key", "slayerCarryPrevKey", settings.cyclePrevKey, last = true)
            }
        }
        return y
    }

    private fun drawBossAim(draw: MenuDraw, startY: Int): Int {
        val c = BossAim.config()
        val warnY = note(draw, startY, "Turns your camera for you. Aim assistance can be against server rules — use at your own risk.")
        val helpY = pageHelp(draw, warnY, "Smoothly turns your camera toward your own slayer boss with curved, never-instant motion.")
        var y = infoRow(draw, helpY, "Status", if (BossAim.engaged()) "Aiming" else "Idle", last = false)
        y += 8
        y = drawAimActivation(draw, y, c)
        y += 8
        y = drawAimLazy(draw, y, c)
        y += 8
        y = drawAimMotion(draw, y, c)
        y += 8
        y = drawAimTargeting(draw, y, c, AimPageKind.SLAYER)
        y += 8
        y = section(
            draw, y, "S-tap",
            "While aiming and attacking, tap S after each hit. Never backs into a pit or fire, and waits out Quazii's beam.",
        )
        y = toggleRow(draw, y, "S-tap", "Tap S while attacking the aimed target.", c.strafe, last = false) {
            BossAim.edit { it.copy(strafe = !it.strafe) }
        }
        y = sliderRow(
            draw, y, "Tap length", "How long each tap holds S, in ticks (20 ticks = 1 second).",
            c.strafeTapTicks.toFloat(), AimConfig.MIN_STRAFE_TAP_TICKS.toFloat(), AimConfig.MAX_STRAFE_TAP_TICKS.toFloat(), 1f,
            last = false, format = { "${it.toInt()} t" },
        ) { v -> BossAim.edit { it.copy(strafeTapTicks = v.toInt()) } }
        y = note(
            draw, y,
            "Presses your back key, which is macro-like. Pits are only known while the fire pit helper is on; holding S yourself takes over.",
        )
        y += 8
        y = section(
            draw, y, "Lock-on (debug)",
            "Press while looking at any mob to lock on; aims until it dies or you press again. Escape unbinds.",
        )
        y = bindRow(draw, y, "Lock-on key", "aimLockKey", c.lockKey, last = false)
        y += 8
        y = section(draw, y, "Extra targets", "Name fragments matched against mob and hologram names, for other bosses.")
        y = actionRow(draw, y, "Extra targets", "Editing comes later.", last = true) { cy ->
            val clearX = rightX(72)
            draw.button("Clear", clearX, cy, ButtonStyle.GHOST) {
                BossAim.edit { it.copy(extraTargets = emptyList()) }
            }
            val listX = innerLeft() + 180
            val raw = if (c.extraTargets.isEmpty()) "none" else c.extraTargets.joinToString(", ")
            val text = draw.fit(raw, clearX - 14 - listX, draw.smallFont)
            draw.text(text, listX, cy + 6, draw.palette.secondary, draw.smallFont)
        }
        return y
    }


    /** Which feature an aim settings block is drawn for; blood aim has no hitbox, so it skips the hitbox rows. */
    private enum class AimPageKind { SLAYER, BLOOD, LIVID }

    private fun drawAimActivation(draw: MenuDraw, startY: Int, c: AimConfig): Int {
        var y = startY
        y = section(draw, y, "Activation", "Hold left click, hold a key, or toggle a key to start aiming.")
        val activations = AimActivation.entries
        y = visualChipRow(
            draw, y, "Activation", "What starts aiming at the boss.",
            activations.map { it.label }, activations.indexOf(c.activation), last = false,
        ) { index -> BossAim.edit { it.copy(activation = activations[index]) } }
        y = section(draw, y, "Aim key", "Used by Hold key and Toggle key. Escape unbinds.")
        y = bindRow(draw, y, "Aim key", "aimKey", c.key, last = false)
        return y
    }

    private fun drawAimLazy(draw: MenuDraw, startY: Int, c: AimConfig): Int {
        var y = startY
        y = section(draw, y, "Lazy aim", "Aim at a drifting point inside the hitbox instead of locking onto its center.")
        y = toggleRow(draw, y, "Lazy aim", "Drift around inside the hitbox.", c.lazyAim, last = false) {
            BossAim.edit { it.copy(lazyAim = !it.lazyAim) }
        }
        y = toggleRow(
            draw, y, "Dead zone", "Don't correct while the crosshair is already on the target.", c.lazyDeadzone,
            last = false,
        ) { BossAim.edit { it.copy(lazyDeadzone = !it.lazyDeadzone) } }
        y = sliderRow(
            draw, y, "Drift", "How far from the aim point the lazy point may wander, as a share of the hitbox.",
            c.lazyStrength, 0f, 1f, 0.05f, last = false, format = { "${(it * 100).toInt()}%" },
        ) { v -> BossAim.edit { it.copy(lazyStrength = v) } }
        return y
    }

    private fun drawAimMotion(draw: MenuDraw, startY: Int, c: AimConfig): Int {
        var y = startY
        y = section(draw, y, "Motion", "How fast the camera turns and how long one turn may take.")
        y = sliderRow(
            draw, y, "Speed", "Maximum turn speed in degrees per second.",
            c.speedDegPerSec, AimConfig.MIN_SPEED_DEG_PER_SEC, AimConfig.MAX_SPEED_DEG_PER_SEC, 10f,
            last = false, format = { "${it.toInt()} °/s" },
        ) { v -> BossAim.edit { it.copy(speedDegPerSec = v) } }
        y = sliderRow(
            draw, y, "Min duration", "Shortest time a turn takes, in milliseconds.",
            c.minDurationMs.toFloat(), AimConfig.MIN_MIN_DURATION_MS.toFloat(), AimConfig.MAX_MIN_DURATION_MS.toFloat(), 10f,
            last = false, format = { "${it.toInt()} ms" },
        ) { f ->
            val v = f.toInt()
            BossAim.edit { it.copy(minDurationMs = v, maxDurationMs = maxOf(it.maxDurationMs, v)) }
        }
        y = sliderRow(
            draw, y, "Max duration", "Longest time a turn may take, in milliseconds.",
            c.maxDurationMs.toFloat(), AimConfig.MIN_MAX_DURATION_MS.toFloat(), AimConfig.MAX_MAX_DURATION_MS.toFloat(), 50f,
            last = false, format = { "${it.toInt()} ms" },
        ) { f ->
            val v = f.toInt()
            BossAim.edit { it.copy(maxDurationMs = v, minDurationMs = minOf(it.minDurationMs, v)) }
        }
        y = sliderRow(
            draw, y, "Curve", "Bends the path away from a straight line. 0 is straight.",
            c.curve, AimConfig.MIN_CURVE, AimConfig.MAX_CURVE, 0.05f, last = false, format = { "%.2f".format(it) },
        ) { v -> BossAim.edit { it.copy(curve = v) } }
        val easings = AimEasing.entries
        y = actionRow(draw, y, "Easing", "Shape of the turn. Click to cycle.", last = false) { cy ->
            draw.button(c.easing.label, rightX(140), cy, ButtonStyle.GHOST) {
                val next = easings[(easings.indexOf(BossAim.config().easing) + 1) % easings.size]
                BossAim.edit { it.copy(easing = next) }
            }
        }
        return y
    }

    private fun drawAimTargeting(draw: MenuDraw, startY: Int, c: AimConfig, kind: AimPageKind): Int {
        var y = section(draw, startY, "Targeting", "What counts as a target and how it is shown.")
        if (kind != AimPageKind.BLOOD) {
            val points = AimPoint.entries
            y = visualChipRow(
                draw, y, "Aim point", "Where on the target the camera settles.",
                points.map { it.label }, points.indexOf(c.aimPoint), last = false,
            ) { index -> BossAim.edit { it.copy(aimPoint = points[index]) } }
            y = sliderRow(
                draw, y, "Range", "Targets farther than this, in blocks, are ignored.",
                c.maxRange, AimConfig.MIN_MAX_RANGE, AimConfig.MAX_MAX_RANGE, 1f, last = false, format = { "${it.toInt()} blocks" },
            ) { v -> BossAim.edit { it.copy(maxRange = v) } }
        }
        y = sliderRow(
            draw, y, "FOV", "Targets outside this cone around the crosshair are ignored.",
            c.fovLimit, AimConfig.MIN_FOV_LIMIT, AimConfig.MAX_FOV_LIMIT, 5f, last = false, format = { "${it.toInt()}°" },
        ) { v -> BossAim.edit { it.copy(fovLimit = v) } }
        if (kind == AimPageKind.SLAYER) {
            y = toggleRow(draw, y, "Own slayer boss", "Aim at your own slayer boss.", c.ownSlayerBoss, last = false) {
                BossAim.edit { it.copy(ownSlayerBoss = !it.ownSlayerBoss) }
            }
        }
        if (kind != AimPageKind.BLOOD) {
            y = toggleRow(draw, y, "Require line of sight", "Only aim at targets you can see.", c.requireLineOfSight, last = false) {
                BossAim.edit { it.copy(requireLineOfSight = !it.requireLineOfSight) }
            }
        }
        y = toggleRow(draw, y, "Highlight target", "Mark the target that is being aimed at.", c.highlightTarget, last = false) {
            BossAim.edit { it.copy(highlightTarget = !it.highlightTarget) }
        }
        return y
    }

    private fun drawBloodHelper(draw: MenuDraw, startY: Int): Int {
        val c = BossAim.config()
        val warnY = note(draw, startY, "Turns your camera for you. Aim assistance can be against server rules — use at your own risk.")
        val helpY = pageHelp(draw, warnY, "Turns your camera toward the box where each blood mob is predicted to drop, like the Odin and NoammAddons blood camp boxes. Works in the blood room only.")
        var y = infoRow(draw, helpY, "Status", if (BossAim.engaged()) "Aiming" else "Idle", last = false)
        y = note(draw, y, "The aim settings below are shared with Aim Assist and Livid Helper. Prediction needs a moment of the mob moving before its box is trusted.")
        y += 8
        y = drawAimActivation(draw, y, c)
        y += 8
        y = drawAimMotion(draw, y, c)
        y += 8
        y = drawAimTargeting(draw, y, c, AimPageKind.BLOOD)
        return y
    }

    private fun drawLividHelper(draw: MenuDraw, startY: Int): Int {
        val c = BossAim.config()
        val warnY = note(draw, startY, "Turns your camera for you. Aim assistance can be against server rules — use at your own risk.")
        val helpY = pageHelp(draw, warnY, "Turns your camera toward the real Livid in the F5 and M5 boss room, found from the colour of the ceiling wool.")
        var y = infoRow(draw, helpY, "Status", if (BossAim.engaged()) "Aiming" else "Idle", last = false)
        y = note(draw, y, "The aim settings below are shared with Aim Assist and Blood Helper.")
        y += 8
        y = drawAimActivation(draw, y, c)
        y += 8
        y = drawAimLazy(draw, y, c)
        y += 8
        y = drawAimMotion(draw, y, c)
        y += 8
        y = drawAimTargeting(draw, y, c, AimPageKind.LIVID)
        return y
    }

    private fun drawBlazeFire(draw: MenuDraw, startY: Int): Int {
        val c = FirePit.config()
        val helpY = pageHelp(draw, startY, "Watches the blocks around you during the Inferno Demonlord fight and marks the pit columns.")
        var y = section(
            draw,
            helpY,
            "Blaze fire pits",
            "Notify warns when you stand in a pit column. Walk presses movement keys to take you out.",
        )
        val modes = EscapeMode.entries
        y = visualChipRow(
            draw, y, "Mode", "How the helper reacts inside a pit.",
            listOf("Notify", "Walk"), modes.indexOf(c.mode), last = false,
        ) { index -> FirePit.edit { it.copy(mode = modes[index]) } }
        if (c.mode == EscapeMode.WALK) {
            y = note(
                draw,
                y,
                "Presses your movement keys for you — this is macro-like and can be against server rules. Pressing any movement key cancels it.",
            )
        }
        y = toggleRow(draw, y, "Only in boss fight", "Run the helper only while the Inferno Demonlord is nearby.", c.onlyInBossFight, last = false) {
            FirePit.edit { it.copy(onlyInBossFight = !it.onlyInBossFight) }
        }
        y = toggleRow(draw, y, "Sound", "Play a sound when you enter a pit.", c.sound, last = false) {
            FirePit.edit { it.copy(sound = !it.sound) }
        }
        y = sliderRow(
            draw, y, "Scan radius", "Columns scanned around you, in blocks.",
            c.scanRadius.toFloat(), FirePitConfig.MIN_SCAN_RADIUS.toFloat(), FirePitConfig.MAX_SCAN_RADIUS.toFloat(), 1f,
            last = false, format = { "${it.toInt()} blocks" },
        ) { v -> FirePit.edit { it.copy(scanRadius = v.toInt()) } }
        y = sliderRow(
            draw, y, "Margin", "Edge distance that still counts as inside the pit, in blocks.",
            c.margin, FirePitConfig.MIN_MARGIN, FirePitConfig.MAX_MARGIN, 0.1f, last = false, format = { "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(margin = v) } }
        y = sliderRow(
            draw, y, "Fire avoid radius", "Blocks kept clear around every fire block; escape spots stay this far away.",
            c.fireRadius, FirePitConfig.MIN_FIRE_RADIUS, FirePitConfig.MAX_FIRE_RADIUS, 0.5f, last = false,
            format = { if (it == 0f) "Off" else "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(fireRadius = v) } }
        val phases = PitPhase.entries
        y = visualChipRow(
            draw, y, "Pit detects from",
            "Pits turn red, yellow, then black. Detection starts at this colour; later means less warning.",
            phases.map { it.label }, phases.indexOf(c.pitPhase), last = false,
        ) { index -> FirePit.edit { it.copy(pitPhase = phases[index]) } }
        y = sliderRow(
            draw, y, "Pit reaction delay", "A pit must be seen this long before it counts.",
            c.pitDelayMs.toFloat(), 0f, FirePitConfig.MAX_DETECT_DELAY_MS.toFloat(), 50f, last = false,
            format = { if (it == 0f) "Instant" else "${it.toInt()} ms" },
        ) { v -> FirePit.edit { it.copy(pitDelayMs = v.toInt()) } }
        y = sliderRow(
            draw, y, "Fire reaction delay", "Ground fire must be seen this long before it counts.",
            c.fireDelayMs.toFloat(), 0f, FirePitConfig.MAX_DETECT_DELAY_MS.toFloat(), 50f, last = false,
            format = { if (it == 0f) "Instant" else "${it.toInt()} ms" },
        ) { v -> FirePit.edit { it.copy(fireDelayMs = v.toInt()) } }
        y = toggleRow(
            draw, y, "Air strafe",
            "Airborne with a pit, fire or Quazii zone under your landing spot: steers you sideways in the air. Moves you for you, so it is macro-like. Client movement only.",
            c.airAvoid, last = false,
        ) { FirePit.edit { it.copy(airAvoid = !it.airAvoid) } }
        y = toggleRow(
            draw, y, "Hard avoid",
            "Fire (and its avoid radius) acts like a wall you can't walk into. Client movement only; needs loader 1.0.3.43.",
            c.hardAvoid, last = false,
        ) { FirePit.edit { it.copy(hardAvoid = !it.hardAvoid) } }
        y = sliderRow(
            draw, y, "Max escape distance", "Longest escape path considered, in blocks.",
            c.maxEscapeDistance, FirePitConfig.MIN_MAX_ESCAPE_DISTANCE, FirePitConfig.MAX_MAX_ESCAPE_DISTANCE, 0.5f,
            last = false, format = { "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(maxEscapeDistance = v) } }
        y = sliderRow(
            draw, y, "Walk timeout", "How long a walk-out may run before it stops.",
            c.walkTimeoutMs.toFloat(), FirePitConfig.MIN_WALK_TIMEOUT_MS.toFloat(), FirePitConfig.MAX_WALK_TIMEOUT_MS.toFloat(), 100f,
            last = false, format = { "%.1fs".format(it / 1000f) },
        ) { v -> FirePit.edit { it.copy(walkTimeoutMs = v.toInt()) } }
        y = note(
            draw, y,
            "Priority (0-10, higher wins): when hazards compete, the higher one gets the action bar line, and standing in one that outranks the beam stops the beam jump. Ties go to the beam.",
        )
        val priorityFormat = { v: Float -> v.toInt().toString() }
        y = sliderRow(
            draw, y, "Priority: Quazii beam", "How urgent the beam is.",
            c.priorityBeam.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityBeam = v.toInt()) } }
        y = sliderRow(
            draw, y, "Priority: ground fire", "Fire already on the ground.",
            c.priorityFire.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityFire = v.toInt()) } }
        y = sliderRow(
            draw, y, "Priority: red pit", "Pit in its first (red) phase.",
            c.priorityPitRed.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityPitRed = v.toInt()) } }
        y = sliderRow(
            draw, y, "Priority: yellow pit", "Pit in its second (yellow) phase.",
            c.priorityPitYellow.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityPitYellow = v.toInt()) } }
        y = sliderRow(
            draw, y, "Priority: black pit", "Pit in its last (black) phase, just before the fire.",
            c.priorityPitBlack.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityPitBlack = v.toInt()) } }
        y = sliderRow(
            draw, y, "Priority: Quazii zone", "The step-back zone around Quazii's feet.",
            c.priorityQuaziiZone.toFloat(), 0f, FirePitConfig.MAX_PRIORITY.toFloat(), 1f, last = false, format = priorityFormat,
        ) { v -> FirePit.edit { it.copy(priorityQuaziiZone = v.toInt()) } }
        y = actionRow(draw, y, "Pit blocks", "Block ids that mark a pit. Reset restores the defaults.", last = false) { cy ->
            val resetX = rightX(72)
            draw.button("Reset", resetX, cy, ButtonStyle.GHOST) {
                FirePit.edit { it.copy(pitBlocks = FirePitConfig().pitBlocks) }
            }
            val listX = innerLeft() + 180
            val text = draw.fit(c.pitBlocks.joinToString(", "), resetX - 14 - listX, draw.smallFont)
            draw.text(text, listX, cy + 6, draw.palette.secondary, draw.smallFont)
        }
        y = toggleRow(draw, y, "Debug log", "Writes block changes around you to the log.", c.debugLog, last = false) {
            FirePit.edit { it.copy(debugLog = !it.debugLog) }
        }
        y = note(draw, y, "Logs block changes around you to latest.log during the fight, to find which block marks a pit.")
        return y
    }

    private fun drawBlazeQuazii(draw: MenuDraw, startY: Int): Int {
        val c = FirePit.config()
        val helpY = pageHelp(draw, startY, "Quazii's beam and body. Needs the Blaze switch on.")
        var y = section(
            draw, helpY, "Quazii beam",
            "Quazii's beam spins around its body and heals it 20% per hit. Works without the pit helper.",
        )
        val beamModes = BeamDodge.entries
        y = visualChipRow(
            draw, y, "Mode",
            "Warn counts down to the next sweep. Jump also jumps over it in place and lets go of movement until you land.",
            beamModes.map { it.label }, beamModes.indexOf(c.beamDodge), last = false,
        ) { index -> FirePit.edit { it.copy(beamDodge = beamModes[index]) } }
        y = sliderRow(
            draw, y, "Jump below health", "Only jump while your health is under this; at 100% it jumps every sweep.",
            c.beamDodgeBelowHp.toFloat(), FirePitConfig.MIN_BEAM_DODGE_BELOW_HP.toFloat(),
            FirePitConfig.MAX_BEAM_DODGE_BELOW_HP.toFloat(), 5f, last = false,
            format = { if (it >= FirePitConfig.MAX_BEAM_DODGE_BELOW_HP) "Always" else "${it.toInt()}%" },
        ) { v -> FirePit.edit { it.copy(beamDodgeBelowHp = v.toInt()) } }
        y = sliderRow(
            draw, y, "Jump earlier", "Ticks earlier than the measured timing; raise it if you still get hit on the way up.",
            c.beamJumpEarlyTicks.toFloat(), 0f, FirePitConfig.MAX_BEAM_JUMP_EARLY_TICKS.toFloat(), 1f, last = false,
            format = { if (it == 0f) "Off" else "${it.toInt()} t" },
        ) { v -> FirePit.edit { it.copy(beamJumpEarlyTicks = v.toInt()) } }
        y = toggleRow(
            draw, y, "Stay out of Quazii",
            "Quazii's hitbox acts like a wall and you get eased out if it walks into you; the beam hits repeatedly inside it.",
            c.avoidQuaziiBody, last = false,
        ) { FirePit.edit { it.copy(avoidQuaziiBody = !it.avoidQuaziiBody) } }
        y = toggleRow(
            draw, y, "Step back from Quazii",
            "Treats the block Quazii stands on like a fire pit so you are told (or walked) to step back. Needs the fire pit helper on.",
            c.quaziiStand, last = false,
        ) { FirePit.edit { it.copy(quaziiStand = !it.quaziiStand) } }
        y = sliderRow(
            draw, y, "Quazii radius", "Blocks around his feet that count as a pit.",
            c.quaziiStandRadius, FirePitConfig.MIN_QUAZII_STAND_RADIUS, FirePitConfig.MAX_QUAZII_STAND_RADIUS, 0.5f,
            last = false, format = { "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(quaziiStandRadius = v) } }
        y = note(draw, y, "Presses your jump key for you, which is macro-like. Debug log writes one line per sweep.")
        return y
    }

    private fun drawBlazeMobs(draw: MenuDraw, startY: Int): Int {
        val c = FirePit.config()
        val helpY = pageHelp(draw, startY, "Typhoeus and his fire. Both are treated like a fire pit so you are told (or walked) to step away. Needs the Blaze switch on.")
        var y = section(draw, helpY, "Fire pillars", "The countdown pillar (\"2s 8 hits\") that burns everything around it.")
        y = toggleRow(draw, y, "Avoid fire pillars", "Keep clear of the ground around every fire pillar.", c.avoidPillars, last = false) {
            FirePit.edit { it.copy(avoidPillars = !it.avoidPillars) }
        }
        y = sliderRow(
            draw, y, "Pillar radius", "Blocks around a pillar that count as a pit.",
            c.pillarRadius, FirePitConfig.MIN_QUAZII_STAND_RADIUS, FirePitConfig.MAX_QUAZII_STAND_RADIUS, 0.5f,
            last = false, format = { "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(pillarRadius = v) } }
        y += 8
        y = section(draw, y, "Typhoeus and other mobs", "Name tags are matched ignoring case, colour codes and circled letters.")
        y = toggleRow(
            draw, y, "Avoid Typhoeus",
            "Treats the ground around the mobs named below (Typhoeus by default) as a pit so you step back.",
            c.avoidMobs, last = false,
        ) { FirePit.edit { it.copy(avoidMobs = !it.avoidMobs) } }
        val avoidId = "fp:avoidNames"
        y = actionRow(draw, y, "Mob names", "Comma separated name parts, matched against name tags.", last = false) { cy ->
            draw.field(avoidId, field(avoidId, c.avoidMobNames), "Typhoeus", rightX(200), cy, 200, focused == avoidId)
        }
        y = sliderRow(
            draw, y, "Mob radius", "Blocks around each matching mob's feet that count as a pit.",
            c.avoidMobRadius, FirePitConfig.MIN_QUAZII_STAND_RADIUS, FirePitConfig.MAX_QUAZII_STAND_RADIUS, 0.5f,
            last = false, format = { "%.1f".format(it) },
        ) { v -> FirePit.edit { it.copy(avoidMobRadius = v) } }
        return y
    }

    private fun drawCarriesPlayers(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings.slayer
        val helpY = pageHelp(draw, startY, "Add carried players and set their target counts.")
        var y = section(draw, helpY, "Players", "Auto counts only for these names (case-insensitive).")
        y = actionBar(draw, y, "Add a player") { cy ->
            val nameId = "carry:newName"
            val targetId = "carry:newTarget"
            val addedW = draw.button("Add", rightX(48), cy, ButtonStyle.PRIMARY) {
                val name = field(nameId).trim()
                if (name.isNotEmpty()) {
                    val target = field(targetId).trim().toIntOrNull() ?: 0
                    TickTimers.updateSlayer { it.addPlayer(name, target) }
                    fields[nameId] = ""
                    fields[targetId] = ""
                    toast("added $name")
                }
            }
            val nameW = 120
            val targetW = 44
            val nameX = rightX(48) - addedW - 12 - nameW
            draw.field(targetId, field(targetId), "target", nameX - targetW - 10, cy, targetW, focused == targetId)
            draw.field(nameId, field(nameId), "PlayerName", nameX, cy, nameW, focused == nameId)
        }
        if (settings.players.isEmpty()) {
            y = note(draw, y, "None yet. /icantpy carry add Name1, Name2 20")
        } else {
            y = grouped(draw, y, settings.players.size) { rowY, index ->
                val player = settings.players[index]
                val isSelected = player === settings.selectedPlayer()
                draw.clickable(innerLeft(), rowY, innerWidth(), metrics.row) {
                    TickTimers.updateSlayer { it.select(player.name) }
                }
                actionRow(
                    draw,
                    rowY,
                    player.name,
                    "${player.progress()}${if (isSelected) " <" else ""}",
                    index == settings.players.lastIndex,
                ) { cy ->
                    val removeW = draw.button("x", rightX(28), cy, ButtonStyle.DANGER) {
                        TickTimers.updateSlayer { it.removePlayer(player.name) }
                    }
                    val plusW = draw.button("+", rightX(28) - removeW - 6, cy, ButtonStyle.PRIMARY) {
                        SlayerCarryCounter.adjustPlayer(player.name, 1)
                    }
                    val minusW = draw.button("-", rightX(28) - removeW - plusW - 12, cy, ButtonStyle.GHOST) {
                        SlayerCarryCounter.adjustPlayer(player.name, -1)
                    }
                    val targetId = "carry:target:${player.name}"
                    if (focused != targetId) fields[targetId] = player.target.toString()
                    val targetW = 46
                    val targetX = rightX(28) - removeW - plusW - minusW - 12 - 14 - targetW
                    draw.field(targetId, field(targetId, player.target.toString()), "20", targetX, cy, targetW, focused == targetId)
                }
            }
        }
        val selected = settings.selectedPlayer()
        if (selected != null) {
            y = section(draw, y + SECTION_GAP, "Selected: ${selected.name}", "Keys and /icantpy carry +1/-1 change this player.")
            y = grouped(draw, y, 2) { rowY, index ->
                if (index == 0) {
                    actionRow(draw, rowY, "Count", "Current carries for this player.", last = false) { cy ->
                        val countId = "carry:count"
                        if (focused != countId) fields[countId] = selected.count.toString()
                        val minusW = draw.button("-1", rightX(38), cy, ButtonStyle.GHOST) {
                            SlayerCarryCounter.adjustSelected(-1)
                        }
                        val plusW = draw.button("+1", rightX(38) - minusW - 6, cy, ButtonStyle.PRIMARY) {
                            SlayerCarryCounter.adjustSelected(1)
                        }
                        draw.field(
                            countId,
                            field(countId, selected.count.toString()),
                            "0",
                            rightX(38) - minusW - plusW - 18 - 76,
                            cy,
                            76,
                            focused == countId,
                        )
                    }
                } else {
                    actionRow(draw, rowY, "Target", "0 disables the target and the completion alert.", last = true) { cy ->
                        val targetId = "carry:target"
                        if (focused != targetId) fields[targetId] = selected.target.toString()
                        draw.field(targetId, field(targetId, selected.target.toString()), "0", rightX(76), cy, 76, focused == targetId)
                    }
                }
            }
        }
        return grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Reset", "Reset the selected count, all counts, or the player list.", last = true) { cy ->
                val clearW = draw.button("Clear players", rightX(126), cy, ButtonStyle.DANGER) {
                    TickTimers.updateSlayer { it.clearPlayers() }
                }
                val allW = draw.button("All 0", rightX(126) - clearW - 8, cy, ButtonStyle.GHOST) {
                    TickTimers.updateSlayer { current -> current.copy(players = current.players.map { it.withCount(0) }) }
                }
                draw.button("Selected 0", rightX(126) - clearW - allW - 16, cy, ButtonStyle.GHOST) {
                    val player = TickTimers.settings.slayer.selectedPlayer()
                    if (player != null) {
                        TickTimers.updateSlayer { it.updatePlayer(player.name) { current -> current.withCount(0) } }
                    }
                }
            }
        }
    }

    private fun drawAppearanceTheme(draw: MenuDraw, startY: Int): Int {
        val look = TickTimers.settings.look
        val helpY = pageHelp(draw, startY, "Colors for the window and the accent highlight.")
        var y = helpY
        y = section(draw, y + SECTION_GAP, "Theme", "Colors for the panel. Accent below can override the highlight.")
        y = pickRow(draw, y, 64) { x, w, h, index ->
            val id = GuiThemeId.entries[index]
            draw.pickCard(id.label(), id.caption(), x, y, w, h, look.theme == id) {
                TickTimers.updateLook { it.copy(theme = id) }
            }
        }
        y = section(draw, y + SECTION_GAP, "Accent", "Leave hex empty to use the theme color. Live while you type.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                val hexId = "look:accent"
                val hex = field(hexId, look.accentHex.removePrefix("#"))
                actionRow(draw, rowY, "Hex", "RRGGBB override.", last = false) { cy ->
                    val clearW = draw.button("Theme", rightX(58), cy, ButtonStyle.GHOST) {
                        fields[hexId] = ""
                        TickTimers.updateLook { it.copy(accentHex = "") }
                    }
                    draw.field(hexId, hex, "7CFFC5", rightX(58) - clearW - 96, cy, 88, focused == hexId)
                }
            } else {
                val swatchY = rowY + 10
                ACCENT_PRESETS.forEachIndexed { colorIndex, rgb ->
                    val selected = look.resolvedAccent() == rgb
                    draw.swatch(innerLeft() + 14 + colorIndex * 22, swatchY, rgb, selected) {
                        val hex = HexColor.format(rgb).removePrefix("#")
                        fields["look:accent"] = hex
                        TickTimers.updateLook { it.copy(accentHex = hex) }
                    }
                }
                rowY + metrics.row
            }
        }
        return y
    }

    private fun drawAppearanceTextShape(draw: MenuDraw, startY: Int): Int {
        val look = TickTimers.settings.look
        val helpY = pageHelp(draw, startY, "Font, corner radius, and control rounding.")
        var y = helpY
        y = section(draw, y + SECTION_GAP, "Typeface", "System fonts. Pick one that stays sharp at your GUI scale.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                actionRow(draw, rowY, look.font.label(), "Previous / next family.", last = false) { cy ->
                    val nextW = draw.button("Next", rightX(52), cy, ButtonStyle.PRIMARY) {
                        TickTimers.updateLook { it.copy(font = it.font.next()) }
                    }
                    draw.button("Prev", rightX(52) - nextW - 8, cy, ButtonStyle.GHOST) {
                        TickTimers.updateLook { it.copy(font = it.font.previous()) }
                    }
                }
            } else {
                infoRow(draw, rowY, "Preview", "Storm Pad  12.4s", last = true)
            }
        }
        y = section(draw, y + SECTION_GAP, "Corners", "0 is square. Default is 2.")
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Radius", "Applied to the window and cards.", last = true) { cy ->
                var x = rightX(0)
                GuiLookSettings.RADIUS_PRESETS.asReversed().forEach { value ->
                    val label = value.toString()
                    x -= draw.measure(label, draw.buttonFont) + 24
                    draw.chip(label, x, cy, look.radius == value) {
                        TickTimers.updateLook { it.withRadius(value) }
                    }
                    x -= 6
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Controls", "Buttons, toggles, fields, chips, and swatches can be rounded independently.")
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Radius", "0 is square. Higher values make interactive controls rounder.", last = true) { cy ->
                var x = rightX(0)
                GuiLookSettings.RADIUS_PRESETS.asReversed().forEach { value ->
                    val label = value.toString()
                    x -= draw.measure(label, draw.buttonFont) + 24
                    draw.chip(label, x, cy, look.controlRadius == value) {
                        TickTimers.updateLook { it.withControlRadius(value) }
                    }
                    x -= 6
                }
            }
        }
        return y
    }

    private fun drawAppearanceAdvanced(draw: MenuDraw, startY: Int): Int {
        val look = TickTimers.settings.look
        val helpY = pageHelp(draw, startY, "Reload the payload, world blur, and reset the look.")
        var y = section(draw, helpY, "Payload", "Same as /icantpy reload. Closes this menu first.")
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Reload", "Fetch and load the current payload without restarting the game.", last = true) { cy ->
                draw.button("Reload", rightX(64), cy, ButtonStyle.PRIMARY) {
                    IcantpyGui.requestPayloadReload()
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Window", "How see-through the glass themes are.")
        y = sliderRow(
            draw, y, "Opacity", "Window opacity for the glass themes.",
            look.opacity.toFloat(), GuiLookSettings.MIN_OPACITY.toFloat(), GuiLookSettings.MAX_OPACITY.toFloat(), 1f,
            last = false, format = { "${it.toInt()}%" },
        ) { v -> TickTimers.updateLook { it.withOpacity(v.toInt()) } }
        y = section(draw, y + SECTION_GAP, "World", "Vanilla menu blur behind the panel. Off keeps the game sharp.")
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw, rowY, "Background blur",
                "Minecraft's screen blur. The panel itself renders at framebuffer scale either way.",
                look.backgroundBlur,
                last = true,
            ) { TickTimers.updateLook { it.copy(backgroundBlur = !it.backgroundBlur) } }
        }
        return grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Reset look", "Glass dark, Segoe, radius 12, blur on, opacity 62%.", last = true) { cy ->
                draw.button("Reset", rightX(56), cy, ButtonStyle.DANGER) {
                    fields.remove("look:accent")
                    TickTimers.updateLook { GuiLookSettings() }
                    toast("look reset")
                }
            }
        }
    }

    private fun pickRow(draw: MenuDraw, y: Int, cardH: Int, paint: (Int, Int, Int, Int) -> Unit): Int {
        val gap = 8
        val cardW = (innerWidth() - gap * 2) / 3
        repeat(3) { index ->
            val x = innerLeft() + index * (cardW + gap)
            paint(x, cardW, cardH, index)
        }
        return y + cardH + 12
    }

    private fun drawClocksDisplay(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings
        val helpY = pageHelp(draw, startY, "Number format and the HUD editor for dungeon clocks.")
        var y = section(draw, helpY, "Readout", "How the numbers look on your HUD.")
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(
                    draw, rowY, "Display in ticks",
                    "Show remaining time as ticks instead of seconds.",
                    settings.displayInTicks,
                    last = false,
                ) { TickTimers.toggle("displayInTicks") }
                1 -> toggleRow(
                    draw, rowY, "Unit suffix",
                    "Append s or t so the unit is obvious.",
                    settings.showSuffix,
                    last = false,
                ) { TickTimers.toggle("showSuffix") }
                else -> toggleRow(
                    draw, rowY, "Show prefix",
                    "Label each HUD with Pad, Tick, Necron, and so on.",
                    settings.showPrefix,
                    last = true,
                ) { TickTimers.toggle("showPrefix") }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "HUD editor", "Drag corners to scale. Style is on the right.", last = true) { cy ->
                draw.button("Edit HUD", rightX(72), cy, ButtonStyle.PRIMARY) {
                    IcantpyGui.openHudEditor(HUD_TOP.firstOrNull() ?: TimerHud.PAD)
                }
            }
        }
        return y
    }

    private fun drawClocksMechanics(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings
        val helpY = pageHelp(draw, startY, "Storm, devices, and other mechanic timers. Each row toggles its HUD.")
        var y = section(draw, helpY, "Mechanic timers", "F7/M7 mechanics plus the shared F1-F7 timer panel.")
        y = grouped(draw, y, HUD_TOP.size) { rowY, index ->
            val hud = HUD_TOP[index]
            hudRow(draw, rowY, hud, settings.enabled(hud), index == HUD_TOP.lastIndex)
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw, rowY, "Start timer",
                "Countdown after Storm dies until devices can be done.",
                settings.startTimer,
                last = true,
            ) { TickTimers.toggle("startTimer") }
        }
        y = grouped(draw, y, HUD_BOTTOM.size) { rowY, index ->
            val hud = HUD_BOTTOM[index]
            hudRow(draw, rowY, hud, settings.enabled(hud), false)
        }
        return y
    }

    private fun drawClocksDungeon(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings
        val helpY = pageHelp(draw, startY, "Phase splits for F1-F7 in one HUD for the active floor.")
        var y = section(draw, helpY, "Dungeon timers · F1-F7", "The active floor's phase splits appear together in one movable HUD.")
        y = grouped(draw, y, DUNGEON_TIMER_LABELS.size) { rowY, index ->
            infoRow(draw, rowY, DUNGEON_TIMER_LABELS[index], "Available as an Alert clock and in the F1-F7 HUD.", index == DUNGEON_TIMER_LABELS.lastIndex)
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            hudRow(draw, rowY, TimerHud.DUNGEON, settings.enabled(TimerHud.DUNGEON), last = true)
        }
        return y
    }

    private fun drawClocksDebug(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings
        val helpY = pageHelp(draw, startY, "Test clocks that tick without a server ping.")
        val y = section(draw, helpY, "Debug", "Same clocks as Hypixel, ticking without ping.")
        return grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw, rowY, "Debug mode",
                "/icantpy debug storm   py   goldor   necron",
                settings.debugMode,
                last = true,
            ) { TickTimers.toggle("debugMode") }
        }
    }

    private fun drawNotifications(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Sound and on-screen warnings for critical timings.")
        val settings = TickTimers.settings
        var y = section(
            draw,
            helpY,
            "Alerts",
            "Time is seconds on the clock. Use -1 so it fires one second after that clock touches 0.",
        )
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Position & style", "Corners scale. Edges set wrap width. Colors on the right.", last = true) { cy ->
                draw.button("Edit HUD", rightX(72), cy, ButtonStyle.PRIMARY) {
                    IcantpyGui.openHudEditor(TimerHud.NOTIFICATION)
                }
            }
        }
        val hud = settings.hud
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(
                draw, rowY, AlertSounds.label(hud.alertSound),
                "Default cue. Drop .wav files in the sounds folder.",
                last = true,
            ) { cy ->
                val folderW = draw.button("Folder", rightX(62), cy, ButtonStyle.GHOST) {
                    toast(AlertSounds.openFolder())
                }
                val playW = draw.button("Play", rightX(62) - folderW - 8, cy, ButtonStyle.GHOST) {
                    AlertSounds.ensureDirectory()
                    AlertSounds.play(hud.alertSound.ifBlank { "pling" })
                }
                draw.button("Next", rightX(62) - folderW - playW - 16, cy, ButtonStyle.PRIMARY) {
                    AlertSounds.ensureDirectory()
                    TickTimers.updateHud { it.copy(alertSound = AlertSounds.next(it.alertSound)) }
                }
            }
        }
        y = actionBar(draw, y, "Triggers") {
            draw.button("Add", rightX(48), it, ButtonStyle.PRIMARY) { TickTimers.addTrigger() }
        }
        if (settings.triggers.isEmpty()) {
            y = note(draw, y, "None yet. Add one, pick the clock, set seconds, and an optional message.")
        }
        settings.triggers.forEach { trigger ->
            y = triggerBlock(draw, y, trigger)
        }
        return y
    }

    private fun drawWaypoints(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Walk-on trigger areas that send a command when you step in.")
        val settings = TickTimers.settings
        var y = section(draw, helpY, "Waypoints", "Boxes in the world. Walk onto one to send its command.")
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                toggleRow(
                    draw, rowY, "Enabled",
                    "Walk onto a box to send its command.",
                    settings.waypointsEnabled,
                    last = false,
                ) { TickTimers.toggle("waypointsEnabled") }
            } else {
                toggleRow(
                    draw, rowY, "Show boxes",
                    "Draw the waypoint so you can see where it sits.",
                    settings.showWaypoints,
                    last = true,
                ) { TickTimers.toggle("showWaypoints") }
            }
        }
        y = actionBar(draw, y, "Place at your feet or the block you look at") { cy ->
            val look = draw.button("Look", rightX(52), cy, ButtonStyle.PRIMARY) {
                CommandWaypoints.tell(CommandWaypoints.addAtLook())
            }
            draw.button("Feet", rightX(52) - look - 8, cy, ButtonStyle.GHOST) {
                CommandWaypoints.tell(CommandWaypoints.addAtFeet())
            }
        }
        if (settings.waypoints.isEmpty()) {
            y = note(draw, y, "None yet. /icantpy wp add /pc hello   or   /icantpy wp look /pc hello.")
        }
        settings.waypoints.forEach { waypoint ->
            y = waypointBlock(draw, y, waypoint)
        }
        return y
    }

    private fun drawMorph(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Replace your player model on this client only.")
        var y = section(
            draw,
            helpY,
            "Player morph",
            "Local model swap. Camera height is optional and needs the updated loader.",
        )
        val morphId = PlayerDisguise.entityId()
        val morphStatus = when {
            !PlayerDisguise.enabled() -> "Disabled" + (morphId?.let { " (saved: $it)" }.orEmpty())
            else -> morphId ?: "Off"
        }
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> infoRow(draw, rowY, "Current morph", morphStatus, last = false)
                1 -> toggleRow(
                    draw,
                    rowY,
                    "Follow morph eye height",
                    "Mob eye height. Choose crosshair or look-at alignment below; not Hypixel-approved.",
                    PlayerDisguise.followEyeHeight(),
                    false,
                ) {
                    PlayerDisguise.setFollowEyeHeight(!PlayerDisguise.followEyeHeight())
                }
                else -> actionRow(draw, rowY, "Camera alignment",
                    "Crosshair: moving crosshair. Look-at: fixed centered crosshair, no block-distance twitch.", last = true) { cy ->
                    val mode = PlayerDisguise.cameraMode()
                    draw.button(mode.label, rightX(154), cy, ButtonStyle.GHOST) {
                        PlayerDisguise.setCameraMode(if (mode == MorphCameraMode.LOOK_AT)
                            MorphCameraMode.SHIFTED_CROSSHAIR else MorphCameraMode.LOOK_AT)
                    }
                }
            }
        }
        return note(draw, y, "Use /icantpy morph camera mode crosshair|look to switch alignment. /icantpy morph camera on|off controls eye height.")
    }

    private fun drawItemSharing(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Share your worn cosmetics with other icantpy users.")
        var y = section(
            draw,
            helpY,
            "Shared cosmetics",
            "Other icantpy users can see your worn dyes, names, and models when sharing is on. Morphs publish even with sharing off.",
        )
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw,
                rowY,
                "Share with other icantpy users",
                "Click this row, or /icantpy share on. Worn SkyBlock items only; no item UUID.",
                CustomRename.shareWithOthers(),
                true,
            ) {
                CustomRename.setShareWithOthers(!CustomRename.shareWithOthers())
            }
        }
        return y
    }

    private fun drawItemCustomize(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Rename or restyle the item in your main hand. Only this client sees it.")
        var y = helpY
        y = section(
            draw,
            y + SECTION_GAP,
            "Item rename",
            "Client-only, matched by Hypixel UUID, SkyBlock id, or name. Hold the item in your main hand.",
        )
        val stack = CustomRename.heldItem()
        if (stack == null || stack.isEmpty) {
            renamePreview = null
            return note(draw, y, "Hold an item in your main hand. Names never change server-side.")
        }
        val inspect = CustomRename.inspect(stack)
        val identity = inspect.identity
        y = grouped(draw, y, 3) { rowY, index ->
            when (index) {
                0 -> infoRow(draw, rowY, "Item UUID", identity?.uuid ?: "No Hypixel UUID", last = false)
                1 -> infoRow(draw, rowY, "Original", inspect.originalName, last = false)
                else -> infoRow(draw, rowY, "Current", inspect.customName ?: inspect.originalName, last = true)
            }
        }
        if (identity == null) return y
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "NEU editor", "Open the full NEU-style item customizer for the held item.", last = true) { cy ->
                draw.button("Open editor", rightX(110), cy, ButtonStyle.PRIMARY) { IcantpyGui.showItemCustomize() }
            }
        }
        if (renameItem != identity.uuid) {
            renameItem = identity.uuid
            fields["r:name"] = inspect.customName.orEmpty()
            fields["r:glint"] = if (CustomRename.effectiveGlint(stack)) "on" else "off"
            fields["r:glintColor"] = CustomRename.glintColorText(stack)
            fields["r:leatherColor"] = CustomRename.leatherColorText(stack)
                ?: HexColor.format(DyedItemColor.LEATHER_COLOR)
        }
        val previewY = y
        val previewH = 62
        card(draw, innerLeft(), previewY, innerWidth(), previewH)
        draw.text("Preview", innerLeft() + 14, previewY + 12, draw.palette.text, draw.bodyFont)
        draw.text(
            "Rendered locally with the active name and appearance overrides.",
            innerLeft() + 14,
            previewY + 32,
            draw.palette.tertiary,
            draw.descFont,
        )
        renamePreview = RenamePreview(stack, innerLeft() + innerWidth() - 38, previewY + 20)
        y += previewH + 10
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Name", "Supports spaces, § formatting, and && color codes.", last = true) { cy ->
                val id = "r:name"
                draw.field(id, field(id), "My item", rightX(154), cy, 154, focused == id)
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Enchant glint", "Override the vanilla foil effect for this item.", last = true) { cy ->
                draw.toggle(rightX(40), cy + 1, CustomRename.effectiveGlint(stack)) {
                    val vanilla = CustomRename.vanillaGlint(stack)
                    CustomRename.setGlint(identity, !CustomRename.effectiveGlint(stack), vanilla)
                    fields["r:glint"] = if (CustomRename.effectiveGlint(stack)) "on" else "off"
                }
            }
        }
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Custom glint colour", "NEU-encoded speed:alpha:r:g:b; reset returns to the NEU-compatible purple default.", last = true) { cy ->
                val id = "r:glintColor"
                draw.field(id, field(id, CustomRename.DEFAULT_GLINT_ENCODED), CustomRename.DEFAULT_GLINT_ENCODED, rightX(126), cy, 126, focused == id)
                draw.swatch(
                    rightX(18),
                    cy + 3,
                    ItemCustomizeColorCodec.parseOrNull(field(id, CustomRename.DEFAULT_GLINT_ENCODED))?.rgb ?: 0x6419FF,
                    false,
                ) {
                    fields[id] = CustomRename.DEFAULT_GLINT_ENCODED
                    applyField(id, fields[id].orEmpty())
                }
                draw.button("Reset", rightX(18) - 58, cy, ButtonStyle.GHOST) {
                    fields[id] = CustomRename.DEFAULT_GLINT_ENCODED
                    CustomRename.setGlintColor(identity, null)
                }
            }
        }
        if (CustomRename.isLeatherDyeable(stack)) {
            y = grouped(draw, y, 1) { rowY, _ ->
                actionRow(draw, rowY, "Custom leather colour", "Recolors this leather armor piece locally; reset restores vanilla.", last = true) { cy ->
                    val id = "r:leatherColor"
                    val default = HexColor.format(DyedItemColor.LEATHER_COLOR)
                    draw.field(id, field(id, default), default, rightX(126), cy, 126, focused == id)
                    draw.swatch(rightX(18), cy + 3, HexColor.parse(field(id, default)).getOrNull() ?: DyedItemColor.LEATHER_COLOR, false) {
                        applyField(id, field(id, default))
                    }
                    draw.button("Reset", rightX(18) - 58, cy, ButtonStyle.GHOST) {
                        fields[id] = default
                        CustomRename.setLeatherColor(identity, null)
                    }
                }
            }
        }
        y = note(draw, y, "Name help: use && followed by a Minecraft color/format code, ** for ✪, and *1-*9 for circled digits. Dyes on other players use their published cosmetics, not yours.")
        return grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Apply / reset", "Only this client sees the renamed item and appearance changes.", last = true) { cy ->
                val clearW = draw.button("Clear", rightX(58), cy, ButtonStyle.DANGER) {
                    CustomRename.clear(identity)
                    fields["r:name"] = ""
                    fields["r:glintColor"] = CustomRename.DEFAULT_GLINT_ENCODED
                    fields["r:leatherColor"] = HexColor.format(DyedItemColor.LEATHER_COLOR)
                    toast("customization cleared")
                }
                draw.button("Apply", rightX(58) - clearW - 8, cy, ButtonStyle.PRIMARY) {
                    applyRenameField()
                    toast("customization saved")
                }
            }
        }
    }

    private fun applyRenameField() {
        val stack = CustomRename.heldItem() ?: return
        CustomRename.identity(stack)?.let { CustomRename.setName(it, field("r:name")) }
    }

    private fun hudRow(draw: MenuDraw, y: Int, hud: TimerHud, on: Boolean, last: Boolean): Int {
        if (hud == TimerHud.NOTIFICATION) return y
        return actionRow(draw, y, hud.title(), hud.subtitle(), last) { cy ->
            draw.toggle(rightX(40), cy + 1, on) { TickTimers.toggle(hud.toggleKey()) }
            draw.button("Edit", rightX(40) - 48, cy, ButtonStyle.GHOST) { IcantpyGui.openHudEditor(hud) }
        }
    }

    private fun triggerBlock(draw: MenuDraw, startY: Int, trigger: TimerTrigger): Int {
        fun latest() = TickTimers.settings.triggers.firstOrNull { it.id == trigger.id } ?: trigger
        val timeId = "t:${trigger.id}:time"
        val durId = "t:${trigger.id}:dur"
        val msgId = "t:${trigger.id}:msg"
        val whenHint = when {
            trigger.clock.countsUp() -> "elapsed"
            trigger.ticks < 0 -> "after 0"
            else -> "left"
        }
        val y = startY
        val cardH = 112
        val left = innerLeft()
        val width = innerWidth()
        val cycleEnd = left + 70
        val deleteX = left + width - 70
        val toggleX = left + width - 118
        var durX = toggleX - 48
        var timeX = durX - 60
        if (timeX < cycleEnd) {
            timeX = cycleEnd
            durX = (timeX + 60).coerceAtMost(toggleX - 48)
        }
        card(draw, left, y, width, cardH)
        draw.field(msgId, field(msgId, trigger.message), "message", left + 14, y + 10, width - 28, focused == msgId)
        draw.button("<", left + 14, y + 42, ButtonStyle.GHOST) {
            TickTimers.updateTrigger(latest().copy(clock = latest().clock.previous()))
        }
        draw.button(">", left + 42, y + 42, ButtonStyle.GHOST) {
            TickTimers.updateTrigger(latest().copy(clock = latest().clock.next()))
        }
        if (timeX >= cycleEnd + 10) {
            draw.text(
                draw.fit("${trigger.clock.label()} · $whenHint", timeX - left - 80, draw.smallFont),
                left + 72,
                y + 46,
                draw.palette.secondary,
                draw.smallFont,
            )
        }
        draw.field(timeId, field(timeId, trigger.secondsInput()), "-1", timeX, y + 42, 52, focused == timeId)
        draw.field(durId, field(durId, trigger.durationInput()), "2", durX, y + 42, 40, focused == durId)
        draw.toggle(toggleX, y + 42, trigger.enabled) {
            TickTimers.updateTrigger(latest().copy(enabled = !latest().enabled))
        }
        draw.button("Delete", deleteX, y + 42, ButtonStyle.DANGER) {
            TickTimers.removeTrigger(trigger.id)
        }
        val soundW = draw.button(
            draw.fit(AlertSounds.triggerLabel(trigger.sound), 90, draw.buttonFont),
            left + 14,
            y + 72,
            ButtonStyle.GHOST,
        ) {
            AlertSounds.ensureDirectory()
            TickTimers.updateTrigger(latest().copy(sound = AlertSounds.nextTrigger(latest().sound)))
        }
        draw.text("Sound", left + 22 + soundW, y + 76, draw.palette.secondary, draw.microFont)
        return y + cardH + 10
    }

    private fun waypointBlock(draw: MenuDraw, startY: Int, waypoint: CommandWaypoint): Int {
        fun latest() = TickTimers.settings.waypoints.firstOrNull { it.id == waypoint.id } ?: waypoint
        val cmdId = "w:${waypoint.id}:cmd"
        val nameId = "w:${waypoint.id}:name"
        val cardH = 96
        val y = startY
        val left = innerLeft()
        val width = innerWidth()
        card(draw, left, y, width, cardH)
        draw.text("${waypoint.x}  ${waypoint.y}  ${waypoint.z}", left + 14, y + 10, draw.palette.text, draw.bodyFont)
        val gtW = draw.measure(">", draw.buttonFont) + 20
        val ltW = draw.measure("<", draw.buttonFont) + 20
        val gtX = left + width - 14 - gtW
        val ltX = gtX - 80 - ltW
        draw.button("<", ltX, y + 8, ButtonStyle.GHOST) {
            val current = latest()
            CommandWaypoints.update(
                current.copy(
                    world = WaypointWorld.cycle(
                        current.world,
                        extras = listOf(current.world, SkyblockWorlds.current().key),
                        delta = -1,
                    ),
                ),
            )
        }
        draw.text(
            draw.fit(waypoint.worldKey().label(), 72, draw.smallFont),
            ltX + ltW + 4,
            y + 12,
            draw.palette.secondary,
            draw.smallFont,
        )
        draw.button(">", gtX, y + 8, ButtonStyle.GHOST) {
            val current = latest()
            CommandWaypoints.update(
                current.copy(
                    world = WaypointWorld.cycle(
                        current.world,
                        extras = listOf(current.world, SkyblockWorlds.current().key),
                        delta = 1,
                    ),
                ),
            )
        }
        draw.field(cmdId, field(cmdId, waypoint.command), "/pc hello", left + 14, y + 36, width - 28, focused == cmdId)
        draw.field(nameId, field(nameId, waypoint.name), "name", left + 14, y + 64, 110, focused == nameId)
        draw.text("Once", left + 132, y + 68, draw.palette.secondary, draw.microFont)
        draw.toggle(left + 162, y + 64, waypoint.once) {
            val current = latest()
            CommandWaypoints.update(current.copy(once = !current.once))
        }
        draw.toggle(left + width - 118, y + 64, waypoint.enabled) {
            val current = latest()
            CommandWaypoints.update(current.copy(enabled = !current.enabled))
        }
        draw.button("Delete", left + width - 70, y + 64, ButtonStyle.DANGER) {
            CommandWaypoints.remove(waypoint.id)
        }
        return y + cardH + 10
    }

    /** Opens [target] at its remembered subtab and scroll. HUD Layout opens the HUD editor instead. */
    private fun openPage(target: ConfigPage) {
        if (target.opensHudEditor) {
            IcantpyGui.openHudEditor(TimerHud.DUNGEON)
            return
        }
        if (target == page) {
            resetTransientInput()
            return
        }
        ConfigUiSession.select(target)
        page = target
        subtab = ConfigUiSession.subtabOf(target)
        scroll = ConfigUiSession.scrollOf(target, subtab)
        resetTransientInput()
    }

    private fun openFromSearch(target: ConfigPage) {
        fields[SEARCH_FIELD_ID] = ""
        openPage(target)
    }

    private fun selectSubtab(index: Int) {
        ConfigUiSession.selectSubtab(index)
        subtab = ConfigUiSession.subtabOf(page)
        scroll = ConfigUiSession.scrollOf(page, subtab)
        resetTransientInput()
    }

    private fun resetTransientInput() {
        focused = null
        openDebugDropdown = null
        leapDropdownSpec = null
    }

    private fun headerToggle(): HeaderToggle? {
        val module = page.primaryModuleId?.let(AetherModules::byId) ?: return null
        if (!AetherModules.hasMasterSwitch(module.id)) return null
        return HeaderToggle(moduleEnabled(module)) { toggleAetherModule(module) }
    }

    private fun drawOverview(draw: MenuDraw, startY: Int): Int {
        val columns = if (innerWidth() >= OVERVIEW_WIDE_MIN_WIDTH) 3 else 2
        val cardW = (innerWidth() - OVERVIEW_CARD_GAP * (columns - 1)) / columns
        var y = startY
        ConfigSection.entries.filter { it != ConfigSection.HOME }.forEach { group ->
            val pages = ConfigNav.overviewPages().filter { it.section == group }
            if (pages.isEmpty()) return@forEach
            draw.heading(group.label, innerLeft(), y)
            y += draw.lineHeight(draw.headingFont) + 10
            pages.chunked(columns).forEach { row ->
                row.forEachIndexed { index, target ->
                    overviewCard(draw, innerLeft() + index * (cardW + OVERVIEW_CARD_GAP), y, cardW, target)
                }
                y += OVERVIEW_CARD_HEIGHT + OVERVIEW_CARD_GAP
            }
            y += 6
        }
        return y
    }

    private fun overviewCard(draw: MenuDraw, x: Int, y: Int, w: Int, target: ConfigPage) {
        val h = OVERVIEW_CARD_HEIGHT
        val corner = draw.chrome.cardRadius
        draw.round(x, y, w, h, corner, draw.palette.group)
        if (draw.hovered(x, y, w, h)) draw.round(x, y, w, h, corner, draw.palette.rowHover)
        draw.outline(x, y, w, h, corner, draw.palette.line)
        // Registered before the toggle so the toggle wins clicks on its own area.
        draw.clickable(x, y, w, h) { openPage(target) }
        val module = target.primaryModuleId?.let(AetherModules::byId)
        val hasToggle = module != null && AetherModules.hasMasterSwitch(module.id)
        val titleW = w - 28 - if (hasToggle) OVERVIEW_TOGGLE_RESERVE else 0
        draw.text(draw.fit(target.title, titleW, draw.bodyFont), x + 14, y + 10, draw.palette.text, draw.bodyFont)
        draw.text(draw.fit(target.caption, w - 28, draw.descFont), x + 14, y + 32, draw.palette.tertiary, draw.descFont)
        if (module != null && hasToggle) {
            draw.toggle(x + w - TOGGLE_WIDTH - 12, y + 10, moduleEnabled(module)) { toggleAetherModule(module) }
        }
    }

    private fun drawInventorySort(draw: MenuDraw, startY: Int): Int {
        val helpY = pageHelp(draw, startY, "Sort your inventory into saved layouts with a keybind or command.")
        val blurb = AetherModules.byId(AetherModuleId.INVENTORY_SORT)?.hint.orEmpty()
        var y = section(draw, helpY, "Inventory sort", blurb)
        y = grouped(draw, y, 7) { rowY, index ->
            val sort = InvSortController.settings
            when (index) {
                0 -> toggleRow(draw, rowY, "Enabled", "Show the sort button and allow hotkey sorting.", sort.enabled, false) {
                    InvSortController.setEnabled(!InvSortController.settings.enabled)
                }
                1 -> toggleRow(draw, rowY, "Move cursor like a human", "Glide the mouse to each slot before clicking it.", sort.moveCursor, false) {
                    InvSortController.setMoveCursor(!InvSortController.settings.moveCursor)
                }
                2 -> toggleRow(draw, rowY, "Close inventory after manual sort", "Close the inventory after a sort started from a preset button finishes.", sort.closeWhenDone, false) {
                    InvSortController.setCloseWhenDone(!InvSortController.settings.closeWhenDone)
                }
                3 -> toggleRow(draw, rowY, "Instant sort", "Same moves, no delays — still waits for the server to confirm each click.", sort.instant, false) {
                    InvSortController.setInstant(!InvSortController.settings.instant)
                }
                4 -> actionRow(
                    draw,
                    rowY,
                    "Lag safety: ${safetyLabel(sort.safetyLevel)}",
                    "Strict waits longer for the server; use on laggy servers.",
                    last = false,
                ) { cy ->
                    val label = safetyLabel(sort.safetyLevel)
                    draw.button(label, rightX(draw.measure(label, draw.buttonFont) + 28), cy, ButtonStyle.PRIMARY) {
                        InvSortController.cycleSafetyLevel()
                    }
                }
                5 -> toggleRow(
                    draw,
                    rowY,
                    "Hotbar swap: one swap per press",
                    "Each press fixes one hotbar slot (1 input → 1 click).",
                    sort.swapOnePerPress,
                    false,
                ) {
                    InvSortController.setSwapOnePerPress(!InvSortController.settings.swapOnePerPress)
                }
                else -> toggleRow(
                    draw,
                    rowY,
                    "Lock input while auto actions run",
                    "Clicks and keys are ignored; press Esc twice quickly (within 600 ms) to cancel.",
                    sort.lockInput,
                    true,
                ) {
                    InvSortController.setLockInput(!InvSortController.settings.lockInput)
                }
            }
        }
        y = section(draw, y + SECTION_GAP, "Presets", "Presets are saved layouts. Each can have its own hotkey.")
        return grouped(draw, y, 1) { rowY, _ ->
            val label = "Open preset editor"
            actionRow(draw, rowY, "Preset editor", "Save, overwrite, rename, bind hotkeys, and clear inventory sort presets.", last = true) { cy ->
                draw.button(label, rightX(draw.measure(label, draw.buttonFont) + 28), cy, ButtonStyle.PRIMARY) {
                    McUi.setScreen(Minecraft.getInstance(), InvSortEditorScreen(this))
                }
            }
        }
    }

    private fun safetyLabel(level: CursorGuard.SafetyLevel): String = when (level) {
        CursorGuard.SafetyLevel.FAST -> "Fast"
        CursorGuard.SafetyLevel.BALANCED -> "Balanced"
        CursorGuard.SafetyLevel.STRICT -> "Strict"
    }

    private fun moduleEnabled(module: AetherModule): Boolean {
        aetherOverrides[module.id]?.let { return it }
        val settings = TickTimers.settings
        return when (module.id) {
            AetherModuleId.CLOCKS -> settings.dungeonHud
            AetherModuleId.MECHANICS -> listOf(TimerHud.PAD, TimerHud.LIGHTNING, TimerHud.PY, TimerHud.STORM_TICK, TimerHud.GOLDOR, TimerHud.NECRON).any(settings::enabled)
            AetherModuleId.ALERTS -> settings.padHud || settings.goldorHud
            AetherModuleId.LEAP -> settings.leap.enabled
            AetherModuleId.FREELOOK -> Freelook.enabled()
            AetherModuleId.BOXES -> settings.waypointsEnabled
            AetherModuleId.STATS -> settings.statsArmor.enabled
            AetherModuleId.COMBAT -> settings.dropUltimate.enabled
            AetherModuleId.CARRY -> settings.slayer.enabled
            AetherModuleId.FIRE_PITS -> FirePit.config().enabled
            AetherModuleId.BOSS_AIM -> BossAim.config().enabled
            AetherModuleId.BLOOD_HELPER -> BossAim.config().bloodAim
            AetherModuleId.LIVID_HELPER -> BossAim.config().lividAim
            AetherModuleId.CLICK_GUI -> true
            AetherModuleId.INVENTORY_SORT -> InvSortController.settings.enabled
            AetherModuleId.RENAME -> CustomRename.enabled()
            AetherModuleId.MORPH -> PlayerDisguise.enabled()
            AetherModuleId.SHARDS -> ShardChecklistController.enabled()
            AetherModuleId.SPLITS -> RunSplitsController.settings.enabled
            AetherModuleId.SCORE -> ScoreTracker.settings.enabled
            AetherModuleId.DRAGONS -> DragonController.settings().enabled
            AetherModuleId.TERMINALS -> TerminalOverlay.settings.enabled
            else -> module.defaultEnabled
        }
    }

    private fun toggleAetherModule(module: AetherModule) {
        val settings = TickTimers.settings
        when (module.id) {
            AetherModuleId.CLOCKS -> TickTimers.toggle("dungeonHud")
            AetherModuleId.MECHANICS -> {
                val huds = listOf(TimerHud.PAD, TimerHud.LIGHTNING, TimerHud.PY, TimerHud.STORM_TICK, TimerHud.GOLDOR, TimerHud.NECRON)
                val next = !huds.any(settings::enabled)
                huds.forEach { hud -> if (settings.enabled(hud) != next) TickTimers.toggle(hud.toggleKey()) }
            }
            AetherModuleId.ALERTS -> {
                val next = !(settings.padHud || settings.goldorHud)
                if (settings.padHud != next) TickTimers.toggle("padHud")
                if (settings.goldorHud != next) TickTimers.toggle("goldorHud")
            }
            AetherModuleId.LEAP -> TickTimers.updateLeap { it.copy(enabled = !it.enabled) }
            AetherModuleId.FREELOOK -> Freelook.setEnabled(!Freelook.enabled())
            AetherModuleId.BOXES -> TickTimers.toggle("waypointsEnabled")
            AetherModuleId.STATS -> TickTimers.updateStatsArmor { it.copy(enabled = !it.enabled) }
            AetherModuleId.COMBAT -> TickTimers.updateDropUltimate { it.copy(enabled = !it.enabled) }
            AetherModuleId.CARRY -> TickTimers.updateSlayer { it.copy(enabled = !it.enabled) }
            AetherModuleId.FIRE_PITS -> FirePit.edit { it.copy(enabled = !it.enabled) }
            AetherModuleId.BOSS_AIM -> BossAim.edit { it.copy(enabled = !it.enabled) }
            AetherModuleId.BLOOD_HELPER -> BossAim.edit { it.copy(bloodAim = !it.bloodAim) }
            AetherModuleId.LIVID_HELPER -> BossAim.edit { it.copy(lividAim = !it.lividAim) }
            AetherModuleId.INVENTORY_SORT -> InvSortController.setEnabled(!InvSortController.settings.enabled)
            AetherModuleId.RENAME -> CustomRename.setEnabled(!CustomRename.enabled())
            AetherModuleId.MORPH -> PlayerDisguise.setEnabled(!PlayerDisguise.enabled())
            AetherModuleId.SHARDS -> ShardChecklistController.setEnabled(!ShardChecklistController.enabled())
            AetherModuleId.SPLITS -> RunSplitsController.updateSettings { it.copy(enabled = !it.enabled) }
            AetherModuleId.SCORE -> ScoreTracker.updateSettings { it.copy(enabled = !it.enabled) }
            AetherModuleId.DRAGONS -> DragonController.updateSettings { it.copy(enabled = !it.enabled) }
            AetherModuleId.TERMINALS -> TerminalOverlay.updateSettings { it.copy(enabled = !it.enabled) }
            AetherModuleId.CLICK_GUI, AetherModuleId.HUD_EDIT -> {
                aetherOverrides[module.id] = !(aetherOverrides[module.id] ?: module.defaultEnabled)
            }
            else -> aetherOverrides[module.id] = !(aetherOverrides[module.id] ?: module.defaultEnabled)
        }
    }

    private fun descriptionVisible(key: String, hovered: Boolean): Boolean {
        if (!hovered) {
            if (descriptionHoverKey == key) descriptionHoverKey = null
            return false
        }
        val now = System.currentTimeMillis()
        if (descriptionHoverKey != key) {
            descriptionHoverKey = key
            descriptionHoverSince = now
            return false
        }
        return now - descriptionHoverSince >= 500L
    }

    /** Muted one-sentence intro at the top of a subtab. Returns the y where its content starts. */
    private fun pageHelp(draw: MenuDraw, y: Int, text: String): Int {
        var ty = y
        draw.wrap(text, innerWidth() - 24).forEach { line ->
            draw.text(line, innerLeft(), ty, draw.palette.secondary, draw.descFont)
            ty += draw.lineHeight(draw.descFont)
        }
        return ty + SECTION_GAP
    }

    private fun cardsOn(): Boolean = !TickTimers.settings.look.theme.palette(null).glass &&
        ConfigContents.at(page, subtab) in CARD_CONTENTS

    private fun closeCard(endY: Int) {
        val key = cardKey ?: return
        cardExtent[key] = (endY - cardTop - CARD_GAP).coerceAtLeast(0)
        cardKey = null
    }

    /** Opens a section card: background from last frame's height, an accent bar, the title and its blurb. */
    private fun cardSection(draw: MenuDraw, y: Int, title: String, blurb: String): Int {
        closeCard(y)
        val key = "${page.name}:$subtab:$title"
        cardKey = key
        cardTop = y
        cardRows = 0
        val height = cardExtent[key] ?: 0
        if (height > 0) card(draw, innerLeft(), y, innerWidth(), height)
        val headingH = draw.lineHeight(draw.headingFont)
        val textX = innerLeft() + CARD_PAD + 10
        draw.round(innerLeft() + CARD_PAD, y + CARD_PAD, 3, headingH, 1.5f, draw.palette.accent)
        draw.text(title.uppercase(), textX, y + CARD_PAD, draw.palette.accent, draw.headingFont)
        var ty = y + CARD_PAD + headingH + 4
        draw.wrap(blurb, innerWidth() - CARD_PAD * 2 - 10).forEach { line ->
            draw.text(line, textX, ty, draw.palette.secondary, draw.descFont)
            ty += draw.lineHeight(draw.descFont)
        }
        return ty + CARD_PAD - 2
    }

    private fun section(draw: MenuDraw, y: Int, title: String, blurb: String): Int {
        anchorsNext.add(title to (y + scroll - (contentOrigin + metrics.viewTop)))
        if (cardsOn()) return cardSection(draw, y, title, blurb)
        if (draw.palette.glass) return glassSection(draw, y, title, blurb)
        draw.heading(title, innerLeft(), y)
        val headingH = draw.lineHeight(draw.headingFont)
        val hovered = draw.hovered(innerLeft(), y, innerWidth(), headingH + 4)
        if (descriptionVisible("section:$title", hovered)) {
            draw.requestTooltip(blurb, innerWidth() - 28)
        }
        return y + headingH + 10
    }

    /** Flat section heading: small muted caps with a hairline, the blurb on hover. */
    private fun glassSection(draw: MenuDraw, y: Int, title: String, blurb: String): Int {
        val top = y + 6
        draw.heading(title, innerLeft(), top)
        val headingH = draw.lineHeight(draw.headingFont)
        draw.line(innerLeft() + draw.measure(title.uppercase(), draw.headingFont) + 10, top + headingH / 2, innerWidth() - draw.measure(title.uppercase(), draw.headingFont) - 10)
        if (descriptionVisible("section:$title", draw.hovered(innerLeft(), top, innerWidth(), headingH + 4))) {
            draw.requestTooltip(blurb, innerWidth() - 28)
        }
        return top + headingH + 8
    }

    private fun note(draw: MenuDraw, y: Int, text: String): Int {
        val lines = draw.wrap(text, innerWidth() - 24)
        val h = 18 + lines.size * draw.lineHeight(draw.descFont) + 12
        card(draw, innerLeft(), y, innerWidth(), h)
        var ty = y + 12
        lines.forEach { line ->
            draw.text(line, innerLeft() + 12, ty, draw.palette.secondary, draw.descFont)
            ty += draw.lineHeight(draw.descFont)
        }
        return y + h + 10
    }

    private fun grouped(draw: MenuDraw, y: Int, rows: Int, row: (Int, Int) -> Int): Int {
        var next = y
        repeat(rows) { index ->
            next = row(next, index)
        }
        return y + rows * metrics.row + 8
    }

    private fun card(draw: MenuDraw, x: Int, y: Int, w: Int, h: Int) {
        val corner = draw.chrome.cardRadius
        if (draw.chrome.layout != GuiLayoutId.RIBBON) {
            draw.round(x, y, w, h, corner, draw.palette.group)
        }
        if (!draw.palette.glass) draw.outline(x, y, w, h, corner, draw.palette.line)
    }

    private fun actionBar(draw: MenuDraw, y: Int, title: String, controls: (Int) -> Unit): Int {
        draw.text(draw.fit(title, innerWidth() - 140, draw.smallFont), innerLeft(), y + 4, draw.palette.secondary, draw.smallFont)
        controls(y)
        return y + 32
    }

    private fun infoRow(draw: MenuDraw, y: Int, title: String, value: String, last: Boolean): Int =
        actionRow(draw, y, title, value, last) { }

    private fun toggleRow(
        draw: MenuDraw,
        y: Int,
        title: String,
        subtitle: String,
        on: Boolean,
        last: Boolean,
        action: () -> Unit,
    ): Int {
        draw.clickable(innerLeft(), y, innerWidth(), metrics.row, action)
        return actionRow(draw, y, title, subtitle, last) { cy ->
            draw.toggle(rightX(40), cy + 5, on, action)
        }
    }

    private fun actionRow(
        draw: MenuDraw,
        y: Int,
        title: String,
        subtitle: String,
        last: Boolean,
        controls: (Int) -> Unit,
    ): Int {
        val left = innerLeft()
        val width = innerWidth()
        val row = metrics.row
        val cardHeight = row - 4
        val corner = draw.chrome.cardRadius
        if (draw.palette.glass) {
            if (draw.hovered(left, y, width, row)) draw.round(left - 4, y, width + 8, cardHeight, 8f, draw.palette.rowHover)
            draw.line(left, y + row - 2, width)
        } else if (cardKey != null) {
            if (cardRows > 0) draw.line(left + CARD_PAD, y - 2, width - CARD_PAD * 2)
            cardRows++
            if (draw.hovered(left, y, width, row)) draw.round(left + 4, y, width - 8, cardHeight, 6f, draw.palette.rowHover)
        } else {
            draw.round(left, y, width, cardHeight, corner, draw.palette.group)
            draw.outline(left, y, width, cardHeight, corner, draw.palette.line)
            if (draw.hovered(left, y, width, row)) {
                draw.round(left, y, width, cardHeight, corner, draw.palette.rowHover)
                draw.outline(left, y, width, cardHeight, corner, 0x28C9BBFF)
            }
        }
        val hovered = draw.hovered(left, y, width, row)
        draw.text(title, left + if (draw.palette.glass) 4 else 18, y + 8, draw.palette.text, draw.bodyFont)
        if (descriptionVisible("legacy:$title", hovered)) draw.requestTooltip(subtitle, width - 28)
        controls(y + ((cardHeight - 28) / 2).coerceAtLeast(2))
        return y + row
    }

    private fun innerLeft(): Int = metrics.innerLeft

    private fun innerWidth(): Int = metrics.innerWidth

    private fun rightX(controlWidth: Int): Int = metrics.rightX(controlWidth)

    private fun inWindow(mx: Int, my: Int): Boolean {
        val m = metrics
        return mx >= m.panelX && mx < m.panelX + m.panelW && my >= m.panelY && my < m.panelY + m.panelH
    }

    private fun field(id: String, default: String = ""): String = fields.getOrPut(id) { default }

    private fun applyFishingField(id: String, value: String) {
        val number = value.trim().toIntOrNull() ?: return
        val ms = number.coerceIn(0, FishingMacroConfig.MAX_DELAY_MS)
        FishingMacro.edit { c ->
            when (id) {
                "fish:reel:min" -> c.copy(reelMinMs = ms, reelMaxMs = maxOf(c.reelMaxMs, ms))
                "fish:reel:max" -> c.copy(reelMaxMs = ms, reelMinMs = minOf(c.reelMinMs, ms))
                "fish:cast:min" -> c.copy(castMinMs = ms, castMaxMs = maxOf(c.castMaxMs, ms))
                "fish:cast:max" -> c.copy(castMaxMs = ms, castMinMs = minOf(c.castMinMs, ms))
                "fish:quiet" -> c.copy(
                    noBiteSeconds = number.coerceIn(FishingMacroConfig.MIN_NO_BITE_SECONDS, FishingMacroConfig.MAX_NO_BITE_SECONDS),
                )
                else -> c
            }
        }
    }

    private fun applyField(id: String, value: String) {
        when {
            id == SEARCH_FIELD_ID -> scroll = 0
            id == "shards:search" -> {
                shardPage.search = value.take(200)
                fields[id] = shardPage.search
                scroll = 0
                ConfigUiSession.save(page, subtab, 0)
            }
            id.startsWith("carry:target:") -> value.trim().toIntOrNull()?.let { number ->
                val name = id.removePrefix("carry:target:")
                TickTimers.updateSlayer { it.updatePlayer(name) { player -> player.withTarget(number) } }
            }
            id == "carry:count" -> value.trim().toIntOrNull()?.let { number ->
                val player = TickTimers.settings.slayer.selectedPlayer() ?: return@let
                TickTimers.updateSlayer { it.updatePlayer(player.name) { current -> current.withCount(number) } }
            }
            id == "carry:target" -> value.trim().toIntOrNull()?.let { number ->
                val player = TickTimers.settings.slayer.selectedPlayer() ?: return@let
                TickTimers.updateSlayer { it.updatePlayer(player.name) { current -> current.withTarget(number) } }
            }
            id == "hud:width" -> value.toIntOrNull()?.let { width ->
                TickTimers.updateHud { it.withNotifyWidth(width) }
            }
            id == "look:accent" -> TickTimers.updateLook { it.copy(accentHex = value) }
            id == "leap:duration" -> value.toIntOrNull()?.coerceIn(1, 60)?.let { seconds ->
                TickTimers.updateLeap { it.copy(durationSeconds = seconds) }
            }
            id == "leap:gap" -> value.toIntOrNull()?.coerceIn(0, 64)?.let { gap ->
                TickTimers.updateLeap { it.copy(menuGap = gap) }
            }
            id == "leap:autoDelay" -> value.toIntOrNull()?.coerceIn(0, 2000)?.let { delay ->
                TickTimers.updateLeap { it.copy(autoLeapDelayMs = delay) }
            }
            id == "leap:opacity" -> value.toIntOrNull()?.coerceIn(0, 100)?.let { opacity ->
                TickTimers.updateLeap { it.copy(menuOpacity = opacity) }
            }
            id == "pet:delayMin" -> value.trim().toIntOrNull()?.coerceIn(0, PetSwapConfig.MAX_DELAY_MS)?.let { ms ->
                PetSwap.edit { it.copy(delayMinMs = ms, delayMaxMs = maxOf(it.delayMaxMs, ms)) }
            }
            id == "pet:delayMax" -> value.trim().toIntOrNull()?.coerceIn(0, PetSwapConfig.MAX_DELAY_MS)?.let { ms ->
                PetSwap.edit { it.copy(delayMaxMs = ms, delayMinMs = minOf(it.delayMinMs, ms)) }
            }
            id.startsWith("fish:") -> applyFishingField(id, value)
            id == "pet:castName" ->PetSwap.edit { it.copy(castPet = PetMenu.cleanTarget(value)) }
            id.startsWith("pet:name:") -> {
                val index = id.removePrefix("pet:name:").toIntOrNull() ?: return
                PetSwap.edit { config -> config.withPet(index) { it.copy(name = PetMenu.cleanTarget(value)) } }
            }
            id == "fp:avoidNames" ->FirePit.edit { it.copy(avoidMobNames = value.take(200)) }
            id == "leap:keyword" -> TickTimers.updateLeap { it.copy(keyword = value.ifBlank { "[icantpy]" }) }
            id == "leap:announce" -> TickTimers.updateLeap {
                it.copy(announceTemplate = value.ifBlank { LeapChatPatterns.DEFAULT_ANNOUNCE })
            }
            id.startsWith("leap:lp:") -> {
                val index = id.removePrefix("leap:lp:").toIntOrNull() ?: return
                TickTimers.updateLeap { current ->
                    if (index !in current.leapedMessages.indices) return@updateLeap current
                    current.copy(leapedMessages = current.leapedMessages.toMutableList().also { it[index] = value })
                }
            }
            id.startsWith("lt:") -> {
                val parts = id.split(':')
                if (parts.size == 5 && parts[2] == "mark" && parts[4] == "player") {
                    // lt:<card>:mark:<step>:player, the player a mark step names.
                    val index = parts[3].toIntOrNull() ?: return
                    val trigger = TickTimers.settings.leap.triggers.firstOrNull { it.id == parts[1] } ?: return
                    TickTimers.updateLeap {
                        it.upsertTrigger(trigger.withStepChanged(index) { step -> step.copy(markPlayer = value.trim()) })
                    }
                    return
                }
                if (parts.size == 5 && parts[2] == "follow") {
                    // lt:<card>:follow:<step>:name, the player of a follow-up step.
                    val index = parts[3].toIntOrNull() ?: return
                    val trigger = TickTimers.settings.leap.triggers.firstOrNull { it.id == parts[1] } ?: return
                    TickTimers.updateLeap {
                        it.upsertTrigger(trigger.withStepChanged(index) { step -> step.copy(followTargetValue = value.trim()) })
                    }
                    return
                }
                if (parts.size != 3) return
                val trigger = TickTimers.settings.leap.triggers.firstOrNull { it.id == parts[1] } ?: return
                when (parts[2]) {
                    "name" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(targetValue = value)) }
                    "zone" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(zone = value)) }
                    "reason" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(reason = value)) }
                    "match" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(match = value.trim().ifBlank { "any" })) }
                    "player" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(targetPlayer = value)) }
                    "chatPlayer" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(chatPlayer = value.trim())) }
                    "chatText" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(chatText = value)) }
                    "secs" -> TimerTrigger.parseSeconds(value)?.let { seconds ->
                        val current = trigger.clock ?: ClockSpec.stormElapsed(seconds)
                        TickTimers.updateLeap {
                            it.upsertTrigger(trigger.copy(clock = ClockSpec.forSeconds(current.clock, seconds)))
                        }
                    }
                    "delay" -> value.toIntOrNull()?.coerceAtLeast(0)?.let { seconds ->
                        TickTimers.updateLeap { it.upsertTrigger(trigger.copy(chainDelaySeconds = seconds)) }
                    }
                }
            }
            id == "r:name" -> Unit
            id == "r:glintColor" -> {
                ItemCustomizeColorCodec.parseOrNull(value)?.let { color ->
                    CustomRename.heldItem()?.let { stack ->
                        CustomRename.identity(stack)?.let { CustomRename.setGlintColor(it, color) }
                    }
                }
            }
            id == "r:leatherColor" -> {
                ItemCustomizeColorCodec.parseOrNull(value)?.let { color ->
                    CustomRename.heldItem()?.let { stack ->
                        CustomRename.identity(stack)?.let { CustomRename.setLeatherColor(it, color) }
                    }
                }
            }
            id.startsWith("t:") -> {
                val parts = id.split(':')
                if (parts.size != 3) return
                val trigger = TickTimers.settings.triggers.firstOrNull { it.id == parts[1] } ?: return
                when (parts[2]) {
                    "time" -> TimerTrigger.parseSeconds(value)?.let {
                        TickTimers.updateTrigger(trigger.copy(ticks = TimerTrigger.secondsToTicks(it)))
                    }
                    "dur" -> TimerTrigger.parseSeconds(value)?.let { seconds ->
                        TickTimers.updateTrigger(
                            trigger.copy(durationTicks = TimerTrigger.secondsToTicks(seconds).coerceAtLeast(1)),
                        )
                    }
                    "msg" -> TickTimers.updateTrigger(trigger.copy(message = value))
                }
            }
            id.startsWith("w:") -> {
                val parts = id.split(':')
                if (parts.size != 3) return
                val waypoint = TickTimers.settings.waypoints.firstOrNull { it.id == parts[1] } ?: return
                when (parts[2]) {
                    "cmd" -> CommandWaypoints.update(waypoint.copy(command = value))
                    "name" -> CommandWaypoints.update(waypoint.copy(name = value))
                }
            }
        }
    }

    private fun openRunHudEditor(target: RunHudEditorScreen.Target) {
        McUi.setScreen(Minecraft.getInstance(), RunHudEditorScreen(target))
    }

    private fun toast(message: String) {
        toast = message
        toastUntil = System.currentTimeMillis() + 1600
    }

    companion object {
        private const val SLIDER_WIDTH = 150
        private val HUD_TOP = listOf(TimerHud.NECRON, TimerHud.GOLDOR)
        private val HUD_BOTTOM = listOf(
            TimerHud.PAD,
            TimerHud.LIGHTNING,
            TimerHud.PY,
            TimerHud.STORM_TICK,
            TimerHud.SECRETS,
        )
        private val DUNGEON_TIMER_LABELS = listOf(
            "F1 · Undead / Bonzo / Boss",
            "F2 · First Phase / Second Phase / Boss",
            "F3 · Guardians / Human / Guardian / Boss",
            "F4 · Thorn / Boss",
            "F5 · Livid / Boss",
            "F6 · Terracottas / Giants / Sadan / Boss",
            "F7 · Maxor / Storm / Terminals / S1-S4 / Goldor / Necron / Boss",
        )
        private val ACCENT_PRESETS = listOf(
            0xFAFAFA, 0x7CFFC5, 0x3DFF8A, 0x1F5EFF, 0xFFB020, 0xFF5C8A, 0x5CE1FF, 0xFF7A40,
        )
    }
}

internal fun closeConfigUi() {
    IcantpyGui.closeIfOpen()
}

