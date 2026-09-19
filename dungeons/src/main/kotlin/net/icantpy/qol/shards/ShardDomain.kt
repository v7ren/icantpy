package net.icantpy.qol.shards

import kotlin.math.max

data class ShardDefinition(
    val displayName: String,
    val abilityName: String,
    val rarity: String,
    val alignment: String = "",
    val maxLevel: Int = 10,
    val costs: List<Int> = emptyList(),
    val internalName: String = "",
    val bazaarName: String = "SHARD_${displayName.uppercase().replace(' ', '_')}",
    val aliases: List<String> = emptyList()
)

data class ShardChecklistEntry(val name: String, val targetLevel: Int = 10)

data class ShardObservation(val name: String, val level: Int? = null, val owned: Int? = null, val syphoned: Int? = null)

data class ShardProgress(val level: Int? = null, val owned: Int? = null, val syphoned: Int? = null) {
    val known: Boolean get() = level != null || owned != null || syphoned != null
}

object ShardMath {
    fun consumed(definition: ShardDefinition, level: Int, untilNext: Int? = null): Int? {
        if (level !in 0..definition.maxLevel || definition.costs.size < definition.maxLevel) return null
        if (untilNext == null || level == definition.maxLevel) return definition.costs.take(level).sum()
        val next = definition.costs.getOrNull(level) ?: return null
        if (untilNext !in 1..next) return null
        return definition.costs.take(level + 1).sum() - untilNext
    }

    fun neededToMax(definition: ShardDefinition, progress: ShardProgress, target: Int = definition.maxLevel): Int? {
        val level = progress.level ?: return null
        if (level !in 0..definition.maxLevel || target !in 0..definition.maxLevel) return null
        if (level >= target) return 0
        val total = consumed(definition, target) ?: return null
        val consumed = progress.syphoned ?: return null
        val minimum = definition.costs.take(level).sum()
        val maximum = if (level == definition.maxLevel) minimum else definition.costs.take(level + 1).sum() - 1
        if (consumed !in minimum..maximum) return null
        return max(total - consumed, 0)
    }

    fun stillToObtain(definition: ShardDefinition, progress: ShardProgress, target: Int = definition.maxLevel): Int? {
        if (progress.level != null && progress.level !in 0..definition.maxLevel) return null
        if (target !in 0..definition.maxLevel) return null
        if (progress.level != null && progress.level >= target) return 0
        val need = neededToMax(definition, progress, target) ?: return null
        val owned = progress.owned ?: return null
        if (owned < 0) return null
        return max(need - owned, 0)
    }
}
