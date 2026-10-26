package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapAutoStep
import net.icantpy.dungeon.leap.LeapBranch
import net.icantpy.dungeon.leap.LeapCenterTarget
import net.icantpy.dungeon.leap.LeapConditions

/** Integer screen rectangle used by the leap trigger card layout. */
internal data class LayoutRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun contains(px: Int, py: Int): Boolean = px in x until right && py in y until bottom

    fun overlaps(other: LayoutRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

/** One expanded-card field. Labels are shown in the left column. */
internal enum class LeapCardRow(val label: String) {
    TRIGGER("Trigger"),
    STEP("Step"),
    ADD_STEP("Actions"),
    SORT_ARMED("Wait"),
    SORT_LOCK("Keep open"),
    SORT_REOPEN("Block reopen"),
    SORT_ARM_TIMEOUT("Arm expires"),
    TIMER("Timer"),
    SECONDS("Seconds"),
    METRIC("Metric"),
    CLOCK_WHEN("When"),
    HEALTH_BELOW("Below %"),
    THRESHOLD("At least"),
    MATCH("When"),
    CHAIN_DELAY("Delay (s)"),
    HOLD_MODE("Hold"),
    HOLD_DURATION("Duration"),
    HOLD_LOCK("Lock"),
    ONLY_WHEN("Only when"),
    FOR_PRESET("For"),
    CENTER_ON("Center on"),
    CLASS("Class"),
    REQUIRES("Requires"),
    PHASE("Phase"),
    FLOOR("Floor"),
    ROUTE("Route"),
    SECTION("Section"),
    BOSS_ONLY("Boss fight"),
    TARGET_NAME("Target"),
    TARGET_INFO("Target"),
    PLAYER("Player"),
    COLOR("Color"),
    ZONE("Zone"),
    REASON("Reason"),
    VALUE("Match"),
    CHAT_SENDER("From"),
    CHAT_PLAYER("Player"),
    CHAT_CHANNEL("Channel"),
    CHAT_MODE("Match"),
    CHAT_TEXT("Text"),
    CHAT_COOLDOWN("Cooldown"),
    FOLLOW_CENTER("Center on"),
    FOLLOW_CLASS("Class"),
    FOLLOW_PLAYER("Target"),
    FOLLOW_HOLD_MODE("Hold"),
    FOLLOW_HOLD_DURATION("Duration"),
    FOLLOW_HOLD_LOCK("Lock"),
    FOLLOW_WITHIN("Wait up to"),
    MARK_BY("Mark"),
    MARK_TARGET_CLASS("Class"),
    MARK_TARGET_PLAYER("Player"),
    MARK_STYLE("Style"),
    MARK_COLOR("Color"),
    MARK_FOR("For"),
    MARK_SHOW_NAME("Show name"),
    MARK_OPACITY("Opacity"),
    MARK_BRIGHTNESS("Brightness"),
    MARK_SIZE("Size"),
    MARK_HEIGHT("Height"),
    MARK_PULSE("Pulse"),
    MARK_SHAPE("Shape"),
    MARK_SPIN("Spin"),
    MARK_BOB("Bob"),
    MARK_PLAYER_GLOW("Player glow (like spectral arrow)"),
    MARK_MARKER_GLOW("Marker glow"),
    MARK_THROUGH("Through walls"),
    STEP_BRANCH("Run"),
    STEP_COND("If"),
    STEP_COND_ADD(""),
    STEP_REQUIRES("Requires"),
}

/**
 * A row in the card. [step] is the zero-based action index for [LeapCardRow.STEP] and [LeapCardRow.ADD_STEP].
 * [indented] marks the leap settings shown under the leap-target action row.
 */
internal data class LeapCardSlot(
    val row: LeapCardRow,
    val step: Int = 0,
    val indented: Boolean = false,
    /** Zero-based condition term for [LeapCardRow.STEP_COND]. */
    val term: Int = 0,
)

/**
 * Which optional rows a trigger shows. Derived from trigger state only, never from layout.
 * [stepKinds] lists the action rows in run order; a [LeapAutoKind.LEAP_TARGET] row turns the leap settings on.
 */
internal data class LeapCardShape(
    val clock: Boolean,
    val leaped: Boolean,
    val chain: Boolean,
    val groupedPreset: Boolean,
    val centerTarget: LeapCenterTarget,
    val stepKinds: List<LeapAutoKind> = emptyList(),
    /** Indexes into [stepKinds] of sort steps that are armed; only those show the armed option rows. */
    val sortArmedSteps: Set<Int> = emptySet(),
    val health: Boolean = false,
    // Event names a player in its chat line, so the Player row filters on that name.
    val namedMatch: Boolean = false,
    // Event names a relic color in its match text, so the Color row filters on that color.
    val colorMatch: Boolean = false,
    // Event captures a place, class or blessing, so the Match row filters on that text.
    val valueMatch: Boolean = false,
    // Progress event with an "At least" count.
    val threshold: Boolean = false,
    // Chat message event: sender, channel, match mode, text and cooldown rows.
    val chat: Boolean = false,
    // Chat sender is a named player, so the Player row is shown.
    val chatFromPlayer: Boolean = false,
    /** The leap target holds for a set time, so the Duration row is shown. */
    val timedHold: Boolean = true,
    /** Indexes into [stepKinds] of follow-up steps whose hold is timed; only those show the Duration row. */
    val timedFollowSteps: Set<Int> = emptySet(),
    /** Follow-up steps by index into [stepKinds], with the target each lands on. Decides the Class or Target row. */
    val followTargets: Map<Int, LeapCenterTarget> = emptyMap(),
    /** Indexes into [stepKinds] of mark steps that name a player; the others name a class. */
    val markPlayerSteps: Set<Int> = emptySet(),
    /** Indexes of MARK_PLAYER steps whose style is Arrow; only those show the shape, spin and bob rows. */
    val markArrowSteps: Set<Int> = emptySet(),
    /** Branch role by index into [stepKinds]. A missing index means [LeapBranch.ALWAYS]. */
    val stepBranches: Map<Int, LeapBranch> = emptyMap(),
    /** Number of condition terms by index into [stepKinds]. Only branches with a condition show them. */
    val stepTermCounts: Map<Int, Int> = emptyMap(),
) {
    val armLeap: Boolean get() = LeapAutoKind.LEAP_TARGET in stepKinds
}

internal data class LeapCardHeader(
    val chevron: LayoutRect,
    val titleX: Int,
    val titleWidth: Int,
    val toggle: LayoutRect,
    /** Disable button, shown only for preset cards. Left of [test]. */
    val off: LayoutRect?,
    /** Runs the card as a demo. Ghost button immediately left of [delete]. */
    val test: LayoutRect?,
    val delete: LayoutRect,
)

/** Controls of one action row: kind picker, value picker, and the move up, move down and remove buttons. */
internal data class StepControls(
    val kind: LayoutRect,
    val value: LayoutRect,
    val up: LayoutRect,
    val down: LayoutRect,
    val remove: LayoutRect,
)

/** Controls of one condition row. [join] is null on the first term. */
internal data class CondControls(
    val join: LayoutRect?,
    val not: LayoutRect,
    val fact: LayoutRect,
    val value: LayoutRect,
    val remove: LayoutRect,
)

/**
 * Geometry for leap trigger cards. Drawing and hit-testing both read these rectangles,
 * so a control's click area always matches what is painted.
 */
internal object LeapTriggerLayout {
    const val COLLAPSED_HEIGHT = 58
    const val CARD_GAP = 10
    const val SIDE_PAD = 14
    const val HEADER_TOP = 10
    const val HEADER_HEIGHT = 28
    const val CHEVRON_WIDTH = 16
    const val TITLE_GAP = 6
    const val TOGGLE_GAP = 10
    const val BUTTON_GAP = 8
    const val HELP_TOP = 40
    const val BODY_TOP = 62
    const val ROW_HEIGHT = 24
    const val ROW_GAP = 6
    const val BOTTOM_PAD = 12
    const val LABEL_WIDTH = 96
    const val LABEL_GAP = 8

    /** Horizontal inset of the leap settings under a leap-target action row. */
    const val LEAP_INDENT = 16
    const val MIN_CONTROL_WIDTH = 96

    /** Width reserved to the right of the Text field for the "Invalid regex" note. */
    const val CHAT_NOTE_WIDTH = 110
    const val DROPDOWN_GAP = 2
    const val REVEAL_MARGIN = 12

    /** Square icon buttons (move up, move down, remove) at the end of an action row. */
    const val STEP_BUTTON_SIZE = 20
    const val STEP_BUTTON_GAP = 4

    /** Share of the control width given to the kind picker; the value picker takes the rest. */
    const val STEP_KIND_PERCENT = 40

    /** Room kept for the "12." step number at the start of an action row; the pickers use the rest of the label column. */
    const val STEP_NUMBER_WIDTH = 28

    /**
     * Rows shown for an expanded card, in order: event filters first, then the action list. The leap
     * settings sit indented under the leap-target action row, so they appear only while one exists.
     */
    fun rows(shape: LeapCardShape): List<LeapCardSlot> = buildList {
        add(slot(LeapCardRow.TRIGGER))
        timingRows(shape).forEach { add(slot(it)) }
        add(slot(if (shape.groupedPreset) LeapCardRow.FOR_PRESET else LeapCardRow.ONLY_WHEN))
        if (shape.namedMatch) add(slot(LeapCardRow.PLAYER))
        if (shape.colorMatch) add(slot(LeapCardRow.COLOR))
        if (shape.valueMatch) add(slot(LeapCardRow.VALUE))
        listOf(LeapCardRow.PHASE, LeapCardRow.FLOOR, LeapCardRow.ROUTE, LeapCardRow.SECTION, LeapCardRow.BOSS_ONLY).forEach { add(slot(it)) }
        addAll(actionRows(shape))
    }

    /** One row per ordered action, leap settings under the leap-target row, then the add row while there is room. */
    private fun actionRows(shape: LeapCardShape): List<LeapCardSlot> {
        val kinds = shape.stepKinds.take(LeapAutoStep.MAX_STEPS)
        val rows = kinds.flatMapIndexed { index, kind ->
            val stepRow = LeapCardSlot(LeapCardRow.STEP, index)
            val common = branchRows(shape, index, kind)
            when (kind) {
                LeapAutoKind.LEAP_TARGET -> listOf(stepRow) + common + leapSettingRows(shape).map { it.copy(indented = true) }
                LeapAutoKind.FOLLOWUP_LEAP -> listOf(stepRow) + common + followUpSettingRows(shape, index)
                LeapAutoKind.SORT_INVENTORY -> listOf(stepRow) + common + sortSettingRows(shape, index)
                LeapAutoKind.MARK_PLAYER -> listOf(stepRow) + common + markSettingRows(shape, index)
                else -> listOf(stepRow) + common
            }
        }
        return if (kinds.size < LeapAutoStep.MAX_STEPS) rows + LeapCardSlot(LeapCardRow.ADD_STEP, kinds.size) else rows
    }

    /**
     * Indented rows every action has: its branch role, one row per condition term while the role carries a condition
     * (plus an add row while there is room), and the leap requirement for rows that can wait on a leap.
     */
    private fun branchRows(shape: LeapCardShape, step: Int, kind: LeapAutoKind): List<LeapCardSlot> = buildList {
        add(LeapCardSlot(LeapCardRow.STEP_BRANCH, step, indented = true))
        val branch = shape.stepBranches[step] ?: LeapBranch.ALWAYS
        if (branch.hasCondition) {
            val terms = (shape.stepTermCounts[step] ?: 0).coerceIn(0, LeapConditions.MAX_TERMS)
            for (term in 0 until terms) add(LeapCardSlot(LeapCardRow.STEP_COND, step, indented = true, term = term))
            if (terms < LeapConditions.MAX_TERMS) add(LeapCardSlot(LeapCardRow.STEP_COND_ADD, step, indented = true))
        }
        if (kind != LeapAutoKind.LEAP_TARGET && kind != LeapAutoKind.FOLLOWUP_LEAP) {
            add(LeapCardSlot(LeapCardRow.STEP_REQUIRES, step, indented = true))
        }
    }

    /**
     * Indented options under a sort step: the armed toggle always, and the lock, reopen-block and
     * arm-expiry rows only while the step is armed. [step] is the action index the rows belong to.
     */
    private fun sortSettingRows(shape: LeapCardShape, step: Int): List<LeapCardSlot> = buildList {
        add(LeapCardSlot(LeapCardRow.SORT_ARMED, step, indented = true))
        if (step in shape.sortArmedSteps) {
            add(LeapCardSlot(LeapCardRow.SORT_LOCK, step, indented = true))
            add(LeapCardSlot(LeapCardRow.SORT_REOPEN, step, indented = true))
            add(LeapCardSlot(LeapCardRow.SORT_ARM_TIMEOUT, step, indented = true))
        }
    }

    /**
     * Indented options under a mark step: the target is a class or a player name (never both), then the
     * style, color, duration and name toggle. [step] is the action index the rows belong to.
     */
    private fun markSettingRows(shape: LeapCardShape, step: Int): List<LeapCardSlot> = buildList {
        add(LeapCardSlot(LeapCardRow.MARK_BY, step, indented = true))
        val target = if (step in shape.markPlayerSteps) LeapCardRow.MARK_TARGET_PLAYER else LeapCardRow.MARK_TARGET_CLASS
        add(LeapCardSlot(target, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_STYLE, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_COLOR, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_FOR, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_SHOW_NAME, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_OPACITY, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_BRIGHTNESS, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_SIZE, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_HEIGHT, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_PULSE, step, indented = true))
        if (step in shape.markArrowSteps) {
            add(LeapCardSlot(LeapCardRow.MARK_SHAPE, step, indented = true))
            add(LeapCardSlot(LeapCardRow.MARK_SPIN, step, indented = true))
            add(LeapCardSlot(LeapCardRow.MARK_BOB, step, indented = true))
        }
        add(LeapCardSlot(LeapCardRow.MARK_PLAYER_GLOW, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_MARKER_GLOW, step, indented = true))
        add(LeapCardSlot(LeapCardRow.MARK_THROUGH, step, indented = true))
    }

    /**
     * Indented leap settings under a follow-up step: where it lands, its hold, and the wait window. [step] is the
     * action index the rows belong to. Duration shows only for a timed hold.
     */
    private fun followUpSettingRows(shape: LeapCardShape, step: Int): List<LeapCardSlot> = buildList {
        fun followRow(row: LeapCardRow) = add(LeapCardSlot(row, step, indented = true))
        followRow(LeapCardRow.FOLLOW_CENTER)
        when (shape.followTargets[step]) {
            LeapCenterTarget.CLASS -> followRow(LeapCardRow.FOLLOW_CLASS)
            LeapCenterTarget.NAME -> followRow(LeapCardRow.FOLLOW_PLAYER)
            else -> Unit
        }
        followRow(LeapCardRow.FOLLOW_HOLD_MODE)
        if (step in shape.timedFollowSteps) followRow(LeapCardRow.FOLLOW_HOLD_DURATION)
        followRow(LeapCardRow.FOLLOW_HOLD_LOCK)
        followRow(LeapCardRow.FOLLOW_WITHIN)
    }

    private fun leapSettingRows(shape: LeapCardShape): List<LeapCardSlot> = buildList {
        if (shape.chain) add(slot(LeapCardRow.CHAIN_DELAY))
        add(slot(LeapCardRow.HOLD_MODE))
        if (shape.timedHold) add(slot(LeapCardRow.HOLD_DURATION))
        add(slot(LeapCardRow.HOLD_LOCK))
        add(slot(LeapCardRow.CENTER_ON))
        if (shape.centerTarget == LeapCenterTarget.CLASS) add(slot(LeapCardRow.CLASS))
        add(slot(LeapCardRow.REQUIRES))
        targetRow(shape.centerTarget)?.let { add(slot(it)) }
        add(slot(LeapCardRow.ZONE))
        add(slot(LeapCardRow.REASON))
    }

    private fun slot(row: LeapCardRow): LeapCardSlot = LeapCardSlot(row)

    private fun timingRows(shape: LeapCardShape): List<LeapCardRow> = when {
        shape.clock -> listOf(LeapCardRow.TIMER, LeapCardRow.SECONDS, LeapCardRow.METRIC, LeapCardRow.CLOCK_WHEN)
        shape.health -> listOf(LeapCardRow.HEALTH_BELOW)
        shape.threshold -> listOf(LeapCardRow.THRESHOLD)
        shape.leaped -> listOf(LeapCardRow.MATCH)
        shape.chat -> buildList {
            add(LeapCardRow.CHAT_SENDER)
            if (shape.chatFromPlayer) add(LeapCardRow.CHAT_PLAYER)
            add(LeapCardRow.CHAT_CHANNEL)
            add(LeapCardRow.CHAT_MODE)
            add(LeapCardRow.CHAT_TEXT)
            add(LeapCardRow.CHAT_COOLDOWN)
        }
        else -> emptyList()
    }

    private fun targetRow(target: LeapCenterTarget): LeapCardRow? = when (target) {
        LeapCenterTarget.CLASS -> null
        LeapCenterTarget.NAME -> LeapCardRow.TARGET_NAME
        else -> LeapCardRow.TARGET_INFO
    }

    fun cardHeight(expanded: Boolean, rowCount: Int): Int =
        if (!expanded) {
            COLLAPSED_HEIGHT
        } else {
            BODY_TOP + rowCount * ROW_HEIGHT + (rowCount - 1).coerceAtLeast(0) * ROW_GAP + BOTTOM_PAD
        }

    /** Full-width row rectangle for [index] inside a card at ([cardX], [cardY]). */
    fun rowRect(cardX: Int, cardY: Int, cardW: Int, index: Int): LayoutRect = LayoutRect(
        x = cardX + SIDE_PAD,
        y = cardY + BODY_TOP + index * (ROW_HEIGHT + ROW_GAP),
        w = cardW - 2 * SIDE_PAD,
        h = ROW_HEIGHT,
    )

    /** Shifts a row right for the leap settings nested under a leap-target action. */
    fun indentedRect(row: LayoutRect): LayoutRect = LayoutRect(row.x + LEAP_INDENT, row.y, row.w - LEAP_INDENT, row.h)

    fun labelRect(row: LayoutRect): LayoutRect = LayoutRect(row.x, row.y, LABEL_WIDTH, row.h)

    fun controlRect(row: LayoutRect): LayoutRect {
        val x = row.x + LABEL_WIDTH + LABEL_GAP
        return LayoutRect(x, row.y, (row.right - x).coerceAtLeast(MIN_CONTROL_WIDTH), row.h)
    }

    /** Splits an action row's control area into kind, value and three icon buttons, right to left for the buttons. */
    fun stepControls(control: LayoutRect): StepControls {
        val size = STEP_BUTTON_SIZE
        val top = control.y + (control.h - size) / 2
        val remove = LayoutRect(control.right - size, top, size, size)
        val down = LayoutRect(remove.x - STEP_BUTTON_GAP - size, top, size, size)
        val up = LayoutRect(down.x - STEP_BUTTON_GAP - size, top, size, size)
        val kind = LayoutRect(control.x, control.y, control.w * STEP_KIND_PERCENT / 100, control.h)
        val valueX = kind.right + STEP_BUTTON_GAP
        val value = LayoutRect(valueX, control.y, (up.x - STEP_BUTTON_GAP - valueX).coerceAtLeast(0), control.h)
        return StepControls(kind = kind, value = value, up = up, down = down, remove = remove)
    }

    /** Width of the And / Or button and of the is / NOT button at the start of a condition row. */
    const val COND_BUTTON_WIDTH = 44

    /** Splits a condition row's control area into join, NOT, fact picker, value picker and a remove button. */
    fun condControls(control: LayoutRect, hasJoin: Boolean): CondControls {
        val size = STEP_BUTTON_SIZE
        val top = control.y + (control.h - size) / 2
        val remove = LayoutRect(control.right - size, top, size, size)
        var x = control.x
        val join = if (hasJoin) {
            LayoutRect(x, control.y, COND_BUTTON_WIDTH, control.h).also { x = it.right + STEP_BUTTON_GAP }
        } else {
            null
        }
        val not = LayoutRect(x, control.y, COND_BUTTON_WIDTH, control.h)
        x = not.right + STEP_BUTTON_GAP
        val room = (remove.x - STEP_BUTTON_GAP - x).coerceAtLeast(0)
        val fact = LayoutRect(x, control.y, room * COND_FACT_PERCENT / 100, control.h)
        val valueX = fact.right + STEP_BUTTON_GAP
        val value = LayoutRect(valueX, control.y, (remove.x - STEP_BUTTON_GAP - valueX).coerceAtLeast(0), control.h)
        return CondControls(join = join, not = not, fact = fact, value = value, remove = remove)
    }

    /** Share of the room left after the buttons that goes to the fact picker; the value picker takes the rest. */
    const val COND_FACT_PERCENT = 55

    /**
     * Header row: chevron, title, enable toggle, then the optional Off and Test buttons and Delete,
     * laid out right to left. A zero [offWidth] or [testWidth] omits that button. The title takes whatever
     * room is left and collapses to zero rather than running under the toggle on narrow cards.
     */
    fun header(cardX: Int, cardY: Int, cardW: Int, deleteWidth: Int, offWidth: Int = 0, testWidth: Int = 0): LeapCardHeader {
        val top = cardY + HEADER_TOP
        val deleteX = cardX + cardW - SIDE_PAD - deleteWidth
        val test = if (testWidth > 0) {
            LayoutRect(deleteX - BUTTON_GAP - testWidth, top, testWidth, HEADER_HEIGHT)
        } else {
            null
        }
        val beforeTestX = test?.x ?: deleteX
        val off = if (offWidth > 0) {
            LayoutRect(beforeTestX - BUTTON_GAP - offWidth, top, offWidth, HEADER_HEIGHT)
        } else {
            null
        }
        val leftmostButtonX = off?.x ?: beforeTestX
        val toggleX = leftmostButtonX - TOGGLE_GAP - TOGGLE_WIDTH
        val chevronX = cardX + SIDE_PAD
        val titleX = chevronX + CHEVRON_WIDTH + TITLE_GAP
        return LeapCardHeader(
            chevron = LayoutRect(chevronX, top, CHEVRON_WIDTH, HEADER_HEIGHT),
            titleX = titleX,
            titleWidth = (toggleX - TOGGLE_GAP - titleX).coerceAtLeast(0),
            toggle = LayoutRect(toggleX, top + (HEADER_HEIGHT - TOGGLE_HEIGHT) / 2, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            off = off,
            test = test,
            delete = LayoutRect(deleteX, top, deleteWidth, HEADER_HEIGHT),
        )
    }

    /** Places a dropdown under its anchor, or above it when it would pass [bottomLimit]. */
    fun dropdownTop(anchorTop: Int, anchorHeight: Int, menuHeight: Int, bottomLimit: Int): Int {
        val below = anchorTop + anchorHeight + DROPDOWN_GAP
        return if (below + menuHeight <= bottomLimit) below else anchorTop - DROPDOWN_GAP - menuHeight
    }

    /**
     * Scroll offset that puts a card's top just under the view top.
     * [cardTopInScrollSpace] is the card's top measured from the top of the scrolled content.
     */
    fun revealScroll(cardTopInScrollSpace: Int): Int = (cardTopInScrollSpace - REVEAL_MARGIN).coerceAtLeast(0)

    /** Trigger text shown in the UI. Arrows are written as ASCII because not every font has them. */
    fun plainText(label: String): String = label.replace("→", "->")
}
