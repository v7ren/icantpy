package net.icantpy.modules.impl.dungeon.leaporient

internal object LeapPySequence {
    fun shouldDefer(triggerId: String, tankRuleActive: Boolean, tankLeaped: Boolean): Boolean =
        triggerId == "mage-py-35s-healer" && tankRuleActive && !tankLeaped
}
