package net.icantpy.dungeon.leap

import net.icantpy.qol.auto.AutoTarget

/**
 * What pressing Test on one trigger card does: whether its leap is armed, which queued actions run in
 * order, and which actions cannot run yet. Free of Minecraft types.
 */
data class LeapTestPlan(
    val armLeap: Boolean,
    val targets: List<AutoTarget>,
    val skipped: List<LeapAutoStep>,
    /** Armed sort steps: Test arms them too, so the user can test by opening inventory. */
    val armedSorts: List<LeapAutoStep> = emptyList(),
    /** Mark rows, shown on the world at once as Test runs. */
    val marks: List<LeapMark> = emptyList(),
) {
    /** A card with no leap, marks and no actions at all has nothing to demonstrate. */
    val nothingToTest: Boolean
        get() = !armLeap && targets.isEmpty() && skipped.isEmpty() && armedSorts.isEmpty() && marks.isEmpty()
}

object LeapTestPlanner {
    /**
     * Leap target is applied instantly (armLeap), and so are marks. Queued actions keep their order; those
     * without a complete target are reported as skipped and the rest still run. Armed sorts are armed, not queued.
     */
    fun plan(trigger: LeapOrientTrigger): LeapTestPlan {
        val route = LeapAutoRouter.route(trigger.queueableSteps())
        val marks = LeapMarkRouter.route(trigger.steps)
        return LeapTestPlan(
            armLeap = trigger.armLeap,
            targets = route.targets,
            skipped = route.skipped + marks.skipped,
            armedSorts = route.armedSorts,
            marks = marks.marks,
        )
    }
}
