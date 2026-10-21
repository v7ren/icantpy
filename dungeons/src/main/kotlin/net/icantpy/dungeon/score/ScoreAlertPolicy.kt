package net.icantpy.dungeon.score

/**
 * Decides which score thresholds to announce. Each threshold fires at most once per run, the first time the
 * total reaches it. Thresholds that are disabled are still marked reached, so enabling them later in the
 * same run does not produce a late alert. Call [reset] when a new run starts.
 */
class ScoreAlertPolicy {
    private val reached = HashSet<Int>()

    fun reset() {
        reached.clear()
    }

    /**
     * [targets] pairs each threshold with its enabled flag. Returns the enabled thresholds newly reached by [total],
     * ascending.
     */
    fun evaluate(total: Int, targets: List<Pair<Int, Boolean>>): List<Int> {
        val crossed = targets.filter { (threshold, _) -> total >= threshold && reached.add(threshold) }
        return crossed.filter { (_, enabled) -> enabled }.map { (threshold, _) -> threshold }.sorted()
    }
}
