package net.icantpy.dungeon.leap

data class DeferredLeapTrigger(
    val trigger: LeapOrientTrigger,
    val event: LeapTriggerEvent,
    val sender: String,
    val leaped: LeapedChatMatch?,
    val message: String,
)

/** Tracks class leaps that have completed the current ordered trigger chain. */
object LeapTriggerLockState {
    private val completedTargets = linkedSetOf<LeapDungeonClass>()
    private val deferred = linkedMapOf<String, DeferredLeapTrigger>()

    fun reset() {
        completedTargets.clear()
        deferred.clear()
    }

    fun isSatisfied(trigger: LeapOrientTrigger): Boolean =
        !trigger.requiresTargetClass.isReal() || trigger.requiresTargetClass in completedTargets

    fun defer(value: DeferredLeapTrigger) {
        deferred[value.trigger.id] = value
    }

    fun markCompleted(target: LeapDungeonClass): List<DeferredLeapTrigger> {
        if (!target.isReal()) return emptyList()
        completedTargets += target
        val ready = deferred.values.filter { it.trigger.requiresTargetClass == target }
        ready.forEach { deferred.remove(it.trigger.id) }
        return ready
    }

    fun completed(): Set<LeapDungeonClass> = completedTargets.toSet()

    fun deferredCount(): Int = deferred.size
}
