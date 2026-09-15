package net.icantpy.modules.impl.stats

import net.icantpy.modules.impl.appearance.HypixelItemData
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

enum class MaskType(
    val displayName: String,
    val cooldownTicks: Int,
    val invulnerabilityTicks: Int,
) {
    BONZO("Bonzo Mask", 180 * 20, 3 * 20),
    SPIRIT("Spirit Mask", 30 * 20, 3 * 20),
    PHOENIX("Phoenix Pet", 60 * 20, 4 * 20),
}

data class MaskTimerValue(
    val cooldownTicks: Int = 0,
    val invulnerabilityTicks: Int = 0,
)

class MaskTimerState {
    private var values: Map<MaskType, MaskTimerValue> = MaskType.entries.associateWith { MaskTimerValue() }

    fun proc(type: MaskType, cooldownTicks: Int = type.cooldownTicks) {
        values = values + (type to MaskTimerValue(cooldownTicks.coerceAtLeast(0), type.invulnerabilityTicks))
    }

    fun tick() {
        values = values.mapValues { (_, value) ->
            value.copy(
                cooldownTicks = (value.cooldownTicks - 1).coerceAtLeast(0),
                invulnerabilityTicks = (value.invulnerabilityTicks - 1).coerceAtLeast(0),
            )
        }
    }

    fun value(type: MaskType): MaskTimerValue = values[type] ?: MaskTimerValue()

    fun cooldown(type: MaskType): Int = value(type).cooldownTicks

    fun invulnerability(type: MaskType): Int = value(type).invulnerabilityTicks

    fun display(type: MaskType): String = "${"%.1f".format(value(type).cooldownTicks / 20f)}s"

    fun reset() {
        values = MaskType.entries.associateWith { MaskTimerValue() }
    }
}

object MaskTimers {
    private val state = MaskTimerState()
    private val cooldownRegex = Regex("^Cooldown: ([\\d.]+)s$")
    private val procPatterns = listOf(
        MaskType.SPIRIT to Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"),
        MaskType.BONZO to Regex("^Your (?:.+ )?Bonzo's Mask saved your life!$"),
        MaskType.PHOENIX to Regex("^Your Phoenix Pet saved you from certain death!$"),
    )

    fun onChat(raw: String) {
        val message = net.icantpy.util.Text.strip(raw).trim()
        val type = procPatterns.firstOrNull { (_, regex) -> regex.matches(message) }?.first ?: return
        val cooldown = if (type == MaskType.BONZO) bonzoCooldownTicks() else type.cooldownTicks
        state.proc(type, cooldown)
    }

    fun onServerTick() = state.tick()

    fun onDisconnect() = state.reset()

    fun value(type: MaskType): MaskTimerValue = state.value(type)

    fun typeOf(stack: ItemStack): MaskType? {
        if (stack.isEmpty) return null
        val id = HypixelItemData.skyblockId(stack).orEmpty()
        val name = net.icantpy.util.Text.strip(stack.hoverName.string)
        return when {
            id == "BONZO_MASK" || id == "STARRED_BONZO_MASK" || name.contains("Bonzo's Mask", true) -> MaskType.BONZO
            id == "SPIRIT_MASK" || id == "STARRED_SPIRIT_MASK" || name.contains("Spirit Mask", true) -> MaskType.SPIRIT
            id.contains("PHOENIX") || name.contains("Phoenix", true) -> MaskType.PHOENIX
            else -> null
        }
    }

    fun textFor(stack: ItemStack): String? {
        val type = typeOf(stack) ?: return null
        val value = value(type)
        return when {
            value.invulnerabilityTicks > 0 -> "${"%.1f".format(value.invulnerabilityTicks / 20f)}s"
            value.cooldownTicks > 0 -> "${"%.1f".format(value.cooldownTicks / 20f)}s"
            else -> "READY"
        }
    }

    private fun bonzoCooldownTicks(): Int {
        val stack = net.minecraft.client.Minecraft.getInstance().player?.getItemBySlot(EquipmentSlot.HEAD)
            ?: return MaskType.BONZO.cooldownTicks
        val lore = stack.get(DataComponents.LORE)?.lines().orEmpty()
        val seconds = lore.asReversed().firstNotNullOfOrNull { line ->
            cooldownRegex.matchEntire(net.icantpy.util.Text.strip(line.string).trim())?.groupValues?.get(1)?.toDoubleOrNull()
        }
        return seconds?.times(20.0)?.roundToInt() ?: MaskType.BONZO.cooldownTicks
    }
}
