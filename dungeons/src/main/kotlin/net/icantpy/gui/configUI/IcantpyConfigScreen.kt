package net.icantpy.gui.configUI

import net.icantpy.gui.GuiHit
import net.icantpy.gui.IcantpyGui
import net.icantpy.gui.McUi
import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.icantpy.gui.clampScroll
import net.icantpy.modules.impl.appearance.CustomRename
import net.icantpy.modules.impl.appearance.HexColor
import net.icantpy.modules.impl.appearance.ItemCustomizeColorCodec
import net.icantpy.modules.impl.dungeon.leaporient.ClockCrossing
import net.icantpy.modules.impl.dungeon.leaporient.ClockMetric
import net.icantpy.modules.impl.dungeon.leaporient.ClockSpec
import net.icantpy.modules.impl.dungeon.leaporient.LeapBossDeathNotifier
import net.icantpy.modules.impl.dungeon.leaporient.LeapCenterTarget
import net.icantpy.modules.impl.dungeon.leaporient.LeapChatPatterns
import net.icantpy.modules.impl.dungeon.leaporient.LeapDungeonClass
import net.icantpy.modules.impl.dungeon.leaporient.LeapFloor
import net.icantpy.modules.impl.dungeon.leaporient.LeapGameState
import net.icantpy.modules.impl.dungeon.leaporient.LeapKeybindMode
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrient
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrientSettings
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrientTrigger
import net.icantpy.modules.impl.dungeon.leaporient.LeapPhase
import net.icantpy.modules.impl.dungeon.leaporient.LeapPreferKind
import net.icantpy.modules.impl.dungeon.leaporient.LeapRoster
import net.icantpy.modules.impl.dungeon.leaporient.LeapStormRoute
import net.icantpy.modules.impl.dungeon.leaporient.LeapTriggerEvent
import net.icantpy.modules.impl.dungeon.leaporient.LeapTriggerCard
import net.icantpy.modules.impl.dungeon.leaporient.LeapTriggerCards
import net.icantpy.modules.impl.dungeon.leaporient.OrientSpot
import net.icantpy.modules.impl.stats.StatsArmor
import net.icantpy.modules.impl.timer.AlertSounds
import net.icantpy.modules.impl.timer.TickTimers
import net.icantpy.modules.impl.timer.TimerClock
import net.icantpy.modules.impl.timer.TimerHud
import net.icantpy.modules.impl.timer.TimerTrigger
import net.icantpy.modules.impl.waypoint.CommandWaypoint
import net.icantpy.modules.impl.waypoint.CommandWaypoints
import net.icantpy.modules.impl.waypoint.SkyblockWorlds
import net.icantpy.modules.impl.waypoint.WaypointWorld
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

class IcantpyConfigScreen : Screen(Component.literal("icantpy")) {
    private val surface = SkiaSurface()
    private var tab = ConfigUiSession.tab
    private var scroll = ConfigUiSession.scrollOf(tab)
    private var contentHeight = 0
    private var hits: List<GuiHit> = emptyList()
    private val fields = HashMap<String, String>()
    private var focused: String? = null
    private var toast: String? = null
    private var toastUntil = 0L
    private var renameItem: String? = null
    private var capturingBind: String? = null
    private var openDebugDropdown: String? = null
    private var leapDropdownSpec: LeapDropdownSpec? = null
    private var debugBossToken = "storm"
    private var debugPartyToken = "arch"
    private var renamePreview: RenamePreview? = null
    private var metrics = ConfigMetrics.compute(640, 360, GuiLookSettings())

    private data class RenamePreview(val stack: net.minecraft.world.item.ItemStack, val x: Int, val y: Int)

    private data class LeapDropdownSpec(
        val x: Int,
        val anchorY: Int,
        val width: Int,
        val options: List<String>,
        val selected: Int,
        val onSelect: (Int) -> Unit,
    )

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
        ConfigUiSession.save(tab, scroll)
        super.removed()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val look = TickTimers.settings.look
        val chrome = GuiChrome.of(look)
        metrics = ConfigMetrics.compute(width, height, look)
        val m = metrics
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(
                canvas,
                m.viewTop,
                m.viewBottom,
                m.contentLeft,
                m.panelX + m.panelW,
                mouseX,
                mouseY,
                chrome,
            )
            draw.paintConfigChrome(m, tab) { item ->
                if (tab != item) {
                    ConfigUiSession.save(tab, scroll)
                    tab = item
                    scroll = ConfigUiSession.scrollOf(item)
                    focused = null
                    openDebugDropdown = null
                }
            }
            canvas.save()
            canvas.clipRect(
                Rect.makeLTRB(
                    m.contentLeft.toFloat(),
                    m.viewTop.toFloat(),
                    (m.panelX + m.panelW - 8).toFloat(),
                    m.viewBottom.toFloat(),
                ),
            )
            var y = m.viewTop + 12 - scroll
            y = when (tab) {
                ConfigTab.TIMERS -> drawTimers(draw, y)
                ConfigTab.ALERTS -> drawNotifications(draw, y)
                ConfigTab.WAYPOINTS -> drawWaypoints(draw, y)
                ConfigTab.LEAP -> drawLeap(draw, y)
                ConfigTab.RENAME -> drawRename(draw, y)
                ConfigTab.LOOK -> drawLook(draw, y)
                ConfigTab.STATS -> drawStats(draw, y)
            }
            contentHeight = y + scroll - m.viewTop + 16
            canvas.restore()
            draw.unclipped {
                val message = toast
                if (message != null) {
                    if (System.currentTimeMillis() > toastUntil) {
                        toast = null
                    } else {
                        draw.text(message, m.innerLeft, m.panelY + m.panelH - 18, draw.palette.accent, draw.descFont)
                    }
                } else {
                    draw.text(
                        "Right Shift toggles this panel",
                        m.innerLeft,
                        m.panelY + m.panelH - 18,
                        draw.palette.tertiary,
                        draw.microFont,
                    )
                }
                draw.scrollbar(m.panelX + m.panelW - 10, m.viewTop + 8, m.viewHeight - 16, contentHeight, scroll)
            }
            hits = draw.hits
        }
        renamePreview?.let { preview ->
            if (preview.y in (m.viewTop - 24)..m.viewBottom) {
                graphics.item(preview.stack, preview.x, preview.y)
            }
        }
        if (contentHeight > 0) {
            scroll = clampScroll(scroll, contentHeight, m.viewHeight)
            ConfigUiSession.save(tab, scroll)
        }
    }

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
        val hit = hits.lastOrNull { it.rect.contains(mx, my) }
        if (hit == null) {
            focused = null
            openDebugDropdown = null
            return true
        }
        focused = hit.fieldId
        hit.action?.invoke()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        val m = metrics
        if (mx < m.contentLeft || mx >= m.panelX + m.panelW || my < m.viewTop || my >= m.viewBottom) {
            return true
        }
        scroll = clampScroll(scroll - (scrollY * 22).toInt(), contentHeight, m.viewHeight)
        openDebugDropdown = null
        ConfigUiSession.save(tab, scroll)
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
                    focused = null
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (id == "r:name") applyRenameField()
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
        val stats = TickTimers.settings.statsArmor
        var y = section(draw, startY, "Stats armor", "Use /icantpy stats or the altered-stats keybind for the fast armor menu.")
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
        y = section(draw, y, "Setup", "Open the altered menu, press SETUP, then capture items from your inventory.")
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
        return y
    }

    private fun drawLeap(draw: MenuDraw, startY: Int): Int {
        var y = section(draw, startY, "Spirit Leap", "Route triggers arm a local center card. The server inventory order stays unchanged.")
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Page", "Route sets class leaps. Menu is look and announce. Debug simulates the live pipeline.", last = true) { cy ->
                var x = rightX(0)
                LeapConfigPage.entries.asReversed().forEach { page ->
                    x -= draw.measure(page.label(), draw.buttonFont) + 24
                    draw.chip(page.label(), x, cy, ConfigUiSession.leapPage == page) {
                        ConfigUiSession.leapPage = page
                        focused = null
                    }
                    x -= 6
                }
            }
        }
        return when (ConfigUiSession.leapPage) {
            LeapConfigPage.ROUTE -> drawLeapRoute(draw, y)
            LeapConfigPage.MENU -> drawLeapMenu(draw, y)
            LeapConfigPage.DEBUG -> drawLeapDebug(draw, y)
        }
    }

    private fun drawLeapRoute(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        var y = grouped(draw, startY, 3) { rowY, index ->
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
                TickTimers.updateLeap { it.upsertTrigger(LeapOrientTrigger.create(LeapTriggerEvent.LEAP_CHAIN, "berserk")) }
                toast("added chain trigger")
            }
            draw.button("Add", rightX(56) - chainW - 8, it, ButtonStyle.PRIMARY) {
                TickTimers.updateLeap { it.upsertTrigger(LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer")) }
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
        var viewAnchor: Triple<Int, Int, Int>? = null
        y = grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Preset view", "Browse rules without changing your dungeon class.", last = true) { cy ->
                val selectW = (draw.measure("$viewLabel ▾", draw.buttonFont) + 20).coerceAtMost(innerWidth() - 28)
                val selectX = rightX(selectW)
                draw.button("$viewLabel ▾", selectX, cy, ButtonStyle.GHOST) {
                    openDebugDropdown = if (openDebugDropdown == "leap-view") null else "leap-view"
                }
                val menuW = (viewLabels.maxOf { draw.measure(it, draw.buttonFont) } + 20)
                    .coerceAtMost(innerWidth() - 28)
                viewAnchor = Triple((selectX + selectW - menuW).coerceAtLeast(innerLeft() + 14), cy + 24, menuW)
            }
        }
        val groups = listOf("P1", "P2", "S1", "S2", "S3", "S4", "P4", "P5", "Other")
        leapDropdownSpec = null
        val visible = leap.visibleTriggers(viewClass)
        groups.forEach { group ->
            val rows = visible.filter { it.guiGroup() == group }
            if (rows.isEmpty()) return@forEach
            y = leapGroup(draw, y, group, rows)
        }
        val leftover = visible.filter { it.guiGroup() !in groups }
        if (leftover.isNotEmpty()) y = leapGroup(draw, y, "Other", leftover)
        if (openDebugDropdown == "leap-view") {
            viewAnchor?.let { (x, anchorY, width) ->
                draw.dropdownMenu(x, anchorY, width, viewLabels, viewOptions.indexOf(ConfigUiSession.leapViewClass)) { index ->
                    ConfigUiSession.leapViewClass = viewOptions[index]
                    openDebugDropdown = null
                }
            }
        }
        if (openDebugDropdown?.startsWith("leap-select:") == true) {
            leapDropdownSpec?.let { spec ->
                draw.dropdownMenu(spec.x, spec.anchorY, spec.width, spec.options, spec.selected) { index ->
                    spec.onSelect(index)
                    openDebugDropdown = null
                }
            }
        }
        return y
    }

    private fun leapGroup(draw: MenuDraw, startY: Int, group: String, rows: List<LeapOrientTrigger>): Int {
        val collapsed = group in ConfigUiSession.leapCollapsed
        var y = actionBar(draw, startY, group) {
            draw.button(if (collapsed) "Show" else "Hide", rightX(52), it, ButtonStyle.GHOST) {
                if (collapsed) ConfigUiSession.leapCollapsed.remove(group)
                else ConfigUiSession.leapCollapsed += group
            }
        }
        if (collapsed) return y
        LeapTriggerCards.group(rows).forEach { card ->
            y = leapTriggerBlock(draw, y, card)
        }
        return y
    }

    private fun leapSelect(
        draw: MenuDraw,
        key: String,
        label: String,
        options: List<String>,
        selected: Int,
        x: Int,
        y: Int,
        maxWidth: Int,
        onSelect: (Int) -> Unit,
    ): Int {
        if (options.isEmpty()) return 0
        val safeSelected = selected.coerceIn(options.indices)
        val buttonLabel = draw.fit("$label: ${options[safeSelected]} ▾", maxWidth - 20, draw.buttonFont)
        val buttonWidth = (draw.measure(buttonLabel, draw.buttonFont) + 20).coerceAtMost(maxWidth)
        draw.button(buttonLabel, x, y, ButtonStyle.GHOST) {
            openDebugDropdown = if (openDebugDropdown == key) null else key
        }
        if (openDebugDropdown == key) {
            val menuWidth = options.maxOf { draw.measure(it, draw.buttonFont) + 18 }
                .coerceAtMost(maxWidth)
            leapDropdownSpec = LeapDropdownSpec(
                x = (x + buttonWidth - menuWidth).coerceAtLeast(innerLeft() + 14),
                anchorY = y + 24,
                width = menuWidth,
                options = options,
                selected = safeSelected,
                onSelect = onSelect,
            )
        }
        return buttonWidth
    }

    private fun leapEventHelp(event: LeapTriggerEvent): String = when (event) {
        LeapTriggerEvent.BOSS_STORM_END -> "Storm dies and the party enters the post-Storm route."
        LeapTriggerEvent.BOSS_STORM_START -> "Maxor dies and the party moves into Storm."
        LeapTriggerEvent.BOSS_GOLDOR -> "Goldor's fight begins."
        LeapTriggerEvent.BOSS_CORE -> "The Goldor core opens."
        LeapTriggerEvent.BOSS_GOLDOR_DEATH -> "Goldor dies and the Necron phase begins."
        LeapTriggerEvent.BOSS_NECRON -> "Necron drops into the main fight."
        LeapTriggerEvent.BOSS_NECRON_DEATH -> "Necron dies and relics become available."
        LeapTriggerEvent.BOSS_P5 -> "The P5 relic phase is detected."
        LeapTriggerEvent.RELIC_PICKUP -> "You pick up a relic in P5."
        LeapTriggerEvent.LEAPED_TO -> "A party member announces that they leaped to someone."
        LeapTriggerEvent.SELF_LEAP -> "You successfully leap to a party member."
        LeapTriggerEvent.LEAP_CHAIN -> "A follow-up rule runs after your own leap."
        LeapTriggerEvent.CLOCK -> "A dungeon timer reaches the configured threshold."
    }

    private fun drawLeapMenu(draw: MenuDraw, startY: Int): Int {
        val leap = TickTimers.settings.leap
        var y = grouped(draw, startY, 3) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Enabled", "Master switch for the leap menu and orient center.", leap.enabled, false) {
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
        y = grouped(draw, y, 2) { rowY, index ->
            if (index == 0) {
                val durId = "leap:duration"
                actionRow(draw, rowY, "Lock seconds", "How long the center card stays armed.", last = false) { cy ->
                    draw.field(durId, field(durId, leap.durationSeconds.toString()), "10", rightX(48), cy, 48, focused == durId)
                }
            } else {
                val keyId = "leap:keyword"
                actionRow(draw, rowY, "Keyword", "Party chat must contain this, then at sss / ee2 / healer.", last = true) { cy ->
                    draw.field(keyId, field(keyId, leap.keyword), "[icantpy]", rightX(96), cy, 96, focused == keyId)
                }
            }
        }
        y = section(draw, y, "Look", "Same knobs as Odin's Leap Menu.")
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
        y = grouped(draw, y, 4) { rowY, index ->
            when (index) {
                0 -> toggleRow(draw, rowY, "Click on release", "Leap when you let go instead of on press.", leap.onRelease, false) {
                    TickTimers.updateLeap { it.copy(onRelease = !it.onRelease) }
                }
                1 -> toggleRow(draw, rowY, "Leap announce", "Send the template after you teleport. Own announce is ignored.", leap.leapAnnounce, false) {
                    TickTimers.updateLeap { it.copy(leapAnnounce = !it.leapAnnounce) }
                }
                2 -> toggleRow(draw, rowY, "Lock HUD", "Show who / zone / reason on screen.", leap.showLockHud, false) {
                    TickTimers.updateLeap { it.copy(showLockHud = !it.showLockHud) }
                }
                else -> toggleRow(
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
        y = grouped(draw, y, 1) { rowY, _ ->
            val annId = "leap:announce"
            actionRow(draw, rowY, "Announce", "{class} or {name}. Others can use this as a trigger.", last = true) { cy ->
                draw.field(annId, field(annId, leap.announceTemplate), LeapChatPatterns.DEFAULT_ANNOUNCE, rightX(220), cy, 220, focused == annId)
            }
        }
        y = section(draw, y, "Center priority", "Only used when two equal-priority locks overlap.")
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
        y = section(draw, y, "Keybinds", "Click Bind, then press a key. Esc clears.")
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
        if (!TickTimers.settings.debugMode && !LeapOrient.debugActive()) {
            return note(draw, startY, "Turn on debug mode to simulate the live event pipeline.")
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
                    val selectW = draw.measure("$label ▾", draw.buttonFont) + 20
                    val selectX = rightX(52) - runW - 8 - selectW
                    draw.button("$label ▾", selectX, cy, ButtonStyle.GHOST) {
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
                    val selectW = draw.measure("$label ▾", draw.buttonFont) + 20
                    val selectX = rightX(52) - runW - 8 - selectW
                    draw.button("$label ▾", selectX, cy, ButtonStyle.GHOST) {
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
        y = section(draw, y + 6, "Chat diagnostics", "Control the live party-chat trigger lines shown on the debug HUD.")
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

    private fun leapTriggerBlock(draw: MenuDraw, startY: Int, triggerCard: LeapTriggerCard): Int {
        val trigger = triggerCard.trigger
        fun latest() = TickTimers.settings.leap.triggers.firstOrNull { it.id == trigger.id } ?: trigger
        fun save(next: LeapOrientTrigger) {
            TickTimers.updateLeap { it.upsertTrigger(next) }
        }
        val clock = trigger.event == LeapTriggerEvent.CLOCK
        val chain = trigger.event == LeapTriggerEvent.LEAP_CHAIN
        val leaped = trigger.event == LeapTriggerEvent.LEAPED_TO ||
            trigger.event == LeapTriggerEvent.SELF_LEAP ||
            chain
        val custom = !trigger.isPreset
        val y = startY
        val left = innerLeft()
        val width = innerWidth()
        val cardKey = leapCardKey(triggerCard)
        val expanded = cardKey in ConfigUiSession.leapExpandedCards
        val cardH = leapTriggerCardHeight(trigger, expanded)
        card(draw, left, y, width, cardH)
        val deleteLabel = if (trigger.isPreset) "Off" else "Delete"
        val deleteW = draw.measure(deleteLabel, draw.buttonFont) + 20
        val deleteX = left + width - deleteW - 14
        val toggleX = deleteX - 50
        val titleX = left + 14
        val titleMax = (toggleX - titleX - 8).coerceAtLeast(40)
        val chevron = if (expanded) "▾ " else "▸ "
        draw.clickable(left, y, (toggleX - left - 8).coerceAtLeast(1), if (expanded) 52 else cardH) {
            if (expanded) ConfigUiSession.leapExpandedCards.remove(cardKey)
            else ConfigUiSession.leapExpandedCards.add(cardKey)
        }
        if (custom && expanded) {
            val chevronW = draw.measure(chevron, draw.microFont)
            draw.text(chevron.trimEnd(), left + 14, y + 14, draw.palette.tertiary, draw.microFont)
            val eventOptions = LeapTriggerEvent.entries.map { it.label() }
            leapSelect(
                draw,
                "leap-select:${trigger.id}:event",
                "Trigger",
                eventOptions,
                LeapTriggerEvent.entries.indexOf(trigger.event),
                left + 14 + chevronW,
                y + 10,
                (toggleX - left - 28 - chevronW).coerceAtLeast(140),
            ) { index -> save(syncTriggerEvent(latest(), LeapTriggerEvent.entries[index])) }
        } else {
            draw.text(
                draw.fit("$chevron${trigger.title()}", titleMax, draw.smallFont),
                titleX,
                y + 14,
                draw.palette.text,
                draw.smallFont,
            )
        }
        draw.toggle(toggleX, y + 10, trigger.enabled) {
            TickTimers.updateLeap { LeapTriggerCards.setEnabled(it, triggerCard, !latest().enabled) }
        }
        draw.button(deleteLabel, deleteX, y + 10, ButtonStyle.DANGER) {
            TickTimers.updateLeap {
                if (trigger.isPreset) LeapTriggerCards.setEnabled(it, triggerCard, false) else it.removeTrigger(trigger.id)
            }
        }
        draw.text(draw.fit(trigger.subtitle(), width - 40, draw.microFont), left + 14, y + 38, draw.palette.secondary, draw.microFont)
        if (!expanded) return y + cardH + 10
        if (!custom) {
            draw.text(draw.fit("For: ${triggerCard.actorLabel()}", width - 28, draw.microFont), left + 14, y + 60,
                draw.palette.tertiary, draw.microFont)
            draw.text(draw.fit(leapEventHelp(trigger.event), width - 28, draw.microFont), left + 14, y + 78,
                draw.palette.tertiary, draw.microFont)
            leapSelect(
                draw,
                "leap-select:${trigger.id}:requires",
                "Requires",
                listOf("None") + LeapDungeonClass.PLAYABLE.map { it.displayName },
                trigger.requiresTargetClass.takeIf { it.isReal() }
                    ?.let { LeapDungeonClass.PLAYABLE.indexOf(it) + 1 } ?: 0,
                left + 14,
                y + 98,
                width - 28,
            ) { index ->
                val required = if (index == 0) LeapDungeonClass.EMPTY else LeapDungeonClass.PLAYABLE[index - 1]
                save(latest().copy(requiresTargetClass = required))
            }
            return y + cardH + 10
        }
        draw.text(draw.fit(leapEventHelp(trigger.event), width - 28, draw.microFont), left + 14, y + 54,
            draw.palette.tertiary, draw.microFont)
        var row = y + 70
        if (clock) {
            val spec = latest().clock ?: ClockSpec.stormElapsed(35.0)
            val clockW = leapSelect(
                draw,
                "leap-select:${trigger.id}:clock",
                "Timer",
                TimerClock.entries.map { it.label() },
                TimerClock.entries.indexOf(spec.clock),
                left + 14,
                row,
                180,
            ) { index ->
                val seconds = TimerTrigger.parseSeconds(spec.secondsInput()) ?: 35.0
                save(latest().copy(clock = ClockSpec.forSeconds(TimerClock.entries[index], seconds)))
            }
            val secsId = "lt:${trigger.id}:secs"
            draw.field(secsId, field(secsId, spec.secondsInput()), "seconds", left + 20 + clockW, row, 66, focused == secsId)
            leapSelect(
                draw,
                "leap-select:${trigger.id}:metric",
                "Metric",
                ClockMetric.entries.map { it.label() },
                ClockMetric.entries.indexOf(spec.metric),
                left + 20 + clockW + 72,
                row,
                (width - clockW - 92).coerceAtLeast(150),
            ) { index -> save(latest().copy(clock = spec.copy(metric = ClockMetric.entries[index]))) }
            row += 28
            leapSelect(
                draw,
                "leap-select:${trigger.id}:crossing",
                "When",
                ClockCrossing.entries.map { it.label() },
                ClockCrossing.entries.indexOf(spec.crossing),
                left + 14,
                row,
                width - 28,
            ) { index -> save(latest().copy(clock = spec.copy(crossing = ClockCrossing.entries[index]))) }
            row += 28
        } else if (leaped) {
            val matchTokens = if (chain) {
                listOf("any").plus(LeapDungeonClass.PLAYABLE.map { it.shortName.lowercase() })
            } else {
                listOf("self", "any").plus(LeapDungeonClass.PLAYABLE.map { it.shortName.lowercase() })
            }
            val matchLabels = matchTokens.map { token ->
                when (token) {
                    "self" -> "Me: I leaped"
                    "any" -> "Anyone"
                    else -> "${LeapDungeonClass.fromToken(token)?.displayName ?: token} leaped"
                }
            }
            leapSelect(
                draw,
                "leap-select:${trigger.id}:match",
                "When",
                matchLabels,
                matchTokens.indexOfFirst { it.equals(trigger.match, ignoreCase = true) }.coerceAtLeast(0),
                left + 14,
                row,
                width - 28,
            ) { index -> save(latest().copy(match = matchTokens[index])) }
            row += 28
            if (chain) {
                val delayId = "lt:${trigger.id}:delay"
                val holdId = "lt:${trigger.id}:hold"
                draw.text("After leap", left + 14, row + 4, draw.palette.secondary, draw.microFont)
                draw.field(
                    delayId,
                    field(delayId, latest().chainDelaySeconds.toString()),
                    "0",
                    left + 92,
                    row,
                    44,
                    focused == delayId,
                )
                draw.text("s, hold", left + 146, row + 4, draw.palette.secondary, draw.microFont)
                draw.field(
                    holdId,
                    field(
                        holdId,
                        if (latest().chainDurationSeconds > 0) latest().chainDurationSeconds.toString() else "",
                    ),
                    "global",
                    left + 190,
                    row,
                    52,
                    focused == holdId,
                )
                draw.text("s", left + 248, row + 4, draw.palette.secondary, draw.microFont)
                row += 28
            }
        }
        val actorOptions = listOf("Any class") + LeapDungeonClass.PLAYABLE.map { it.displayName }
        leapSelect(
            draw,
            "leap-select:${trigger.id}:actor",
            "Only when",
            actorOptions,
            latest().forClass.takeIf { it.isReal() }?.let { LeapDungeonClass.PLAYABLE.indexOf(it) + 1 } ?: 0,
            left + 14,
            row,
            width - 28,
        ) { index ->
            val clazz = if (index == 0) LeapDungeonClass.EMPTY else LeapDungeonClass.PLAYABLE[index - 1]
            save(latest().copy(forClass = clazz))
        }
        row += 28
        draw.text("Active filters", left + 14, row + 4, draw.palette.secondary, draw.microFont)
        row += 22
        val half = ((width - 34) / 2).coerceAtLeast(140)
        val phaseOptions = listOf("Any phase", "P1", "P2", "P3", "P4", "P5")
        val phases = listOf(LeapPhase.ANY, LeapPhase.P1, LeapPhase.P2, LeapPhase.P3, LeapPhase.P4, LeapPhase.P5)
        leapSelect(draw, "leap-select:${trigger.id}:phase", "Phase", phaseOptions, phases.indexOf(latest().phase).coerceAtLeast(0), left + 14, row, half) { index ->
            save(latest().copy(phase = phases[index]))
        }
        val floors = listOf(LeapFloor.ANY, LeapFloor.F7, LeapFloor.M7)
        leapSelect(draw, "leap-select:${trigger.id}:floor", "Floor", listOf("Any floor", "F7", "M7"), floors.indexOf(latest().floor).coerceAtLeast(0), left + 20 + half, row, half) { index ->
            save(latest().copy(floor = floors[index]))
        }
        row += 28
        val routes = listOf(LeapStormRoute.ANY, LeapStormRoute.PY, LeapStormRoute.GY)
        leapSelect(draw, "leap-select:${trigger.id}:route", "Route", listOf("Any route", "PY", "GY"), routes.indexOf(latest().route).coerceAtLeast(0), left + 14, row, half) { index ->
            save(latest().copy(route = routes[index]))
        }
        val sections = listOf("Any section", "S1", "S2", "S3", "S4")
        leapSelect(draw, "leap-select:${trigger.id}:section", "Section", sections, latest().section.coerceIn(0, 4), left + 20 + half, row, half) { index ->
            save(latest().copy(section = index))
        }
        row += 28
        val target = latest().target
        val targetW = leapSelect(draw, "leap-select:${trigger.id}:target", "Center on", LeapCenterTarget.entries.map { it.label() }, LeapCenterTarget.entries.indexOf(target), left + 14, row, width - 28) { index ->
            save(latest().copy(target = LeapCenterTarget.entries[index]))
        }
        when (target) {
            LeapCenterTarget.CLASS -> {
                val classes = LeapDungeonClass.PLAYABLE
                leapSelect(draw, "leap-select:${trigger.id}:target-value", "Class", classes.map { it.displayName }, classes.indexOf(LeapDungeonClass.fromToken(latest().targetValue)).coerceAtLeast(0), left + 20 + targetW, row, (width - targetW - 34).coerceAtLeast(150)) { index ->
                    save(latest().copy(targetValue = classes[index].name.lowercase()))
                }
            }
            LeapCenterTarget.NAME -> {
                val nameId = "lt:${trigger.id}:name"
                draw.field(nameId, field(nameId, latest().targetValue), "player", left + 20 + targetW, row, 110, focused == nameId)
            }
            LeapCenterTarget.SENDER, LeapCenterTarget.LEAPED_TO, LeapCenterTarget.LEAPED_FROM -> {
                draw.text("Uses the player captured by the event.", left + 20 + targetW, row + 4, draw.palette.secondary, draw.microFont)
            }
        }
        row += 28
        val zoneId = "lt:${trigger.id}:zone"
        val reasonId = "lt:${trigger.id}:reason"
        leapSelect(
            draw,
            "leap-select:${trigger.id}:requires",
            "Requires prior leap",
            listOf("None") + LeapDungeonClass.PLAYABLE.map { it.displayName },
            latest().requiresTargetClass.takeIf { it.isReal() }
                ?.let { LeapDungeonClass.PLAYABLE.indexOf(it) + 1 } ?: 0,
            left + 14,
            row,
            width - 28,
        ) { index ->
            val required = if (index == 0) LeapDungeonClass.EMPTY else LeapDungeonClass.PLAYABLE[index - 1]
            save(latest().copy(requiresTargetClass = required))
        }
        row += 28
        val zoneW = 72
        val reasonW = (width - 108).coerceAtLeast(1)
        draw.field(zoneId, field(zoneId, latest().zone), "SS", left + 14, row, zoneW, focused == zoneId)
        draw.field(reasonId, field(reasonId, latest().reason), "reason", left + 94, row, reasonW, focused == reasonId)
        return y + cardH + 10
    }

    private fun leapTriggerCardHeight(trigger: LeapOrientTrigger, expanded: Boolean): Int {
        if (!expanded) return 58
        if (trigger.isPreset) return 148
        var height = 80 // header, subtitle, and event explanation
        height += if (trigger.event == LeapTriggerEvent.CLOCK) 56 else 28
        if (trigger.event == LeapTriggerEvent.LEAP_CHAIN) height += 28
        height += 28 // class restriction
        height += 78 // filter heading and two rows
        height += 28 // center target
        height += 28 // prerequisite
        height += 28 // zone and reason
        return height + 12
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
        if (code == LeapOrientSettings.UNBOUND) return "None"
        return GLFW.glfwGetKeyName(code, 0)?.uppercase() ?: "Key $code"
    }

    private fun drawLook(draw: MenuDraw, startY: Int): Int {
        val look = TickTimers.settings.look
        var y = section(draw, startY, "Layout", "Three shells. Same settings, different chrome.")
        y = pickRow(draw, y, 72) { x, w, h, index ->
            val id = GuiLayoutId.entries[index]
            draw.pickCard(id.label(), id.caption(), x, y, w, h, look.layout == id) {
                TickTimers.updateLook { it.copy(layout = id) }
            }
        }
        y = section(draw, y, "Theme", "Colors for the panel. Accent below can override the highlight.")
        y = pickRow(draw, y, 64) { x, w, h, index ->
            val id = GuiThemeId.entries[index]
            draw.pickCard(id.label(), id.caption(), x, y, w, h, look.theme == id) {
                TickTimers.updateLook { it.copy(theme = id) }
            }
        }
        y = section(draw, y, "Typeface", "System fonts. Pick one that stays sharp at your GUI scale.")
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
        y = section(draw, y, "Accent", "Leave hex empty to use the theme color. Live while you type.")
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
        y = section(draw, y, "Corners", "0 is square. Default is 2.")
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
        y = section(draw, y, "Controls", "Buttons, toggles, fields, chips, and swatches can be rounded independently.")
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
        y = section(draw, y, "World", "Vanilla menu blur behind the panel. Off keeps the game sharp.")
        y = grouped(draw, y, 1) { rowY, _ ->
            toggleRow(
                draw, rowY, "Background blur",
                "Minecraft's screen blur. The panel itself renders at framebuffer scale either way.",
                look.backgroundBlur,
                last = true,
            ) { TickTimers.updateLook { it.copy(backgroundBlur = !it.backgroundBlur) } }
        }
        return grouped(draw, y, 1) { rowY, _ ->
            actionRow(draw, rowY, "Reset look", "Rail, Midnight, Segoe, radius 2.", last = true) { cy ->
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

    private fun drawTimers(draw: MenuDraw, startY: Int): Int {
        val settings = TickTimers.settings
        var y = section(draw, startY, "Readout", "How the numbers look on your HUD.")
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
        y = section(draw, y + 6, "F7 clocks", "Turn each piece on, then drag it with Move.")
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
            hudRow(draw, rowY, hud, settings.enabled(hud), index == HUD_BOTTOM.lastIndex)
        }
        y = section(draw, y + 6, "Debug", "Same clocks as Hypixel, ticking without ping.")
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
        val settings = TickTimers.settings
        var y = section(
            draw,
            startY,
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
        val settings = TickTimers.settings
        var y = section(draw, startY, "Waypoints", "Boxes in the world. Walk onto one to send its command.")
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

    private fun drawRename(draw: MenuDraw, startY: Int): Int {
        var y = section(
            draw,
            startY,
            "Item rename",
            "Client-only, stored by Hypixel UUID. Hold the item in your main hand.",
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
        y = note(draw, y, "Name help: use && followed by a Minecraft color/format code, ** for ✪, and *1-*9 for circled digits. Changes are client-side and keyed to the Hypixel item UUID.")
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

    private fun section(draw: MenuDraw, y: Int, title: String, blurb: String): Int {
        draw.heading(title, innerLeft(), y)
        var next = y + draw.lineHeight(draw.headingFont) + 4
        draw.wrap(blurb, innerWidth()).forEach { line ->
            draw.text(line, innerLeft(), next, draw.palette.tertiary, draw.descFont)
            next += draw.lineHeight(draw.descFont) + 1
        }
        return next + 8
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
        val h = 6 + rows * metrics.row
        card(draw, innerLeft(), y, innerWidth(), h)
        var next = y + 4
        repeat(rows) { index ->
            next = row(next, index)
        }
        return y + h + 10
    }

    private fun card(draw: MenuDraw, x: Int, y: Int, w: Int, h: Int) {
        val corner = draw.chrome.cardRadius
        if (draw.chrome.layout != GuiLayoutId.RIBBON) {
            draw.round(x, y, w, h, corner, draw.palette.group)
        }
        draw.outline(x, y, w, h, corner, draw.palette.line)
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
    ): Int = actionRow(draw, y, title, subtitle, last) { cy ->
        draw.toggle(rightX(40), cy + 1, on, action)
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
        if (draw.hovered(left, y, width, row)) {
            draw.round(left + 4, y, width - 8, row - 2, draw.chrome.cardRadius, draw.palette.rowHover)
        }
        draw.text(title, left + 14, y + 6, draw.palette.text, draw.bodyFont)
        draw.text(
            draw.fit(subtitle, width - 150, draw.descFont),
            left + 14,
            y + 22,
            draw.palette.tertiary,
            draw.descFont,
        )
        controls(y + 8)
        if (!last) draw.line(left + 14, y + row - 1, width - 28)
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

    private fun applyField(id: String, value: String) {
        when {
            id == "hud:width" -> value.toIntOrNull()?.let { width ->
                TickTimers.updateHud { it.withNotifyWidth(width) }
            }
            id == "look:accent" -> TickTimers.updateLook { it.copy(accentHex = value) }
            id == "leap:duration" -> value.toIntOrNull()?.coerceIn(1, 60)?.let { seconds ->
                TickTimers.updateLeap { it.copy(durationSeconds = seconds) }
            }
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
                if (parts.size != 3) return
                val trigger = TickTimers.settings.leap.triggers.firstOrNull { it.id == parts[1] } ?: return
                when (parts[2]) {
                    "name" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(targetValue = value)) }
                    "zone" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(zone = value)) }
                    "reason" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(reason = value)) }
                    "player" -> TickTimers.updateLeap { it.upsertTrigger(trigger.copy(targetPlayer = value)) }
                    "secs" -> TimerTrigger.parseSeconds(value)?.let { seconds ->
                        val current = trigger.clock ?: ClockSpec.stormElapsed(seconds)
                        TickTimers.updateLeap {
                            it.upsertTrigger(trigger.copy(clock = ClockSpec.forSeconds(current.clock, seconds)))
                        }
                    }
                    "delay" -> value.toIntOrNull()?.coerceAtLeast(0)?.let { seconds ->
                        TickTimers.updateLeap { it.upsertTrigger(trigger.copy(chainDelaySeconds = seconds)) }
                    }
                    "hold" -> {
                        val seconds = value.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                        TickTimers.updateLeap { it.upsertTrigger(trigger.copy(chainDurationSeconds = seconds)) }
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

    private fun toast(message: String) {
        toast = message
        toastUntil = System.currentTimeMillis() + 1600
    }

    companion object {
        private val HUD_TOP = listOf(TimerHud.NECRON, TimerHud.GOLDOR)
        private val HUD_BOTTOM = listOf(
            TimerHud.PAD,
            TimerHud.LIGHTNING,
            TimerHud.PY,
            TimerHud.STORM_TICK,
            TimerHud.SECRETS,
        )
        private val ACCENT_PRESETS = listOf(
            0xFAFAFA, 0x7CFFC5, 0x3DFF8A, 0x1F5EFF, 0xFFB020, 0xFF5C8A, 0x5CE1FF, 0xFF7A40,
        )
    }
}

internal fun closeConfigUi() {
    IcantpyGui.closeIfOpen()
}

