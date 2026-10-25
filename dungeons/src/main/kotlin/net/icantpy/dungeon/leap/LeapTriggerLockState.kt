package net.icantpy.dungeon.leap

data class DeferredLeapTrigger(
    val trigger: LeapOrientTrigger,
    val event: LeapTriggerEvent,
    val sender: String,
    val leaped: LeapedChatMatch?,
    val message: String,
)

/** One action row waiting for a leap to [LeapAutoStep.requires]; it runs on its own once that leap happens. */
data class DeferredLeapStep(
    val trigger: LeapOrientTrigger,
    val step: LeapAutoStep,
    val sender: String,
    val message: String,
)

/** Tracks class leaps that have completed the current ordered trigger chain. */
object LeapTriggerLockState {
    private val completedTargets = linkedSetOf<LeapDungeonClass>()
    private val deferred = linkedMapOf<String, DeferredLeapTrigger>()
    private val deferredSteps = mutableListOf<DeferredLeapStep>()

    fun reset() {
        completedTargets.clear()
        deferred.clear()
        deferredSteps.clear()
    }

    fun isSatisfied(trigger: LeapOrientTrigger): Boolean =
        !trigger.requiresTargetClass.isReal() || trigger.requiresTargetClass in completedTargets

    /** Whether an action row's own requirement is met. A row with no requirement is always ready. */
    fun isSatisfied(step: LeapAutoStep): Boolean =
        !step.requires.isReal() || step.requires in completedTargets

    fun defer(value: DeferredLeapTrigger) {
        deferred[value.trigger.id] = value
    }

    /** Parks [value] until the player leaps to its class. The same row of the same card is parked once. */
    fun deferStep(value: DeferredLeapStep) {
        deferredSteps.removeAll { it.trigger.id == value.trigger.id && it.step == value.step }
        deferredSteps += value
    }

    fun markCompleted(target: LeapDungeonClass): List<DeferredLeapTrigger> {
        if (!target.isReal()) return emptyList()
        completedTargets += target
        val ready = deferred.values.filter { it.trigger.requiresTargetClass == target }
        ready.forEach { deferred.remove(it.trigger.id) }
        return ready
    }

    /** Removes and returns the parked action rows that were waiting for a leap to [target]. */
    fun takeSteps(target: LeapDungeonClass): List<DeferredLeapStep> {
        val ready = deferredSteps.filter { it.step.requires == target }
        deferredSteps.removeAll(ready.toSet())
        return ready
    }

    fun completed(): Set<LeapDungeonClass> = completedTargets.toSet()

    fun deferredCount(): Int = deferred.size + deferredSteps.size
}
