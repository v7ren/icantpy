package net.icantpy.qol.camera

import com.google.gson.JsonParser
import net.minecraft.client.CameraType
import org.lwjgl.glfw.GLFW

enum class FreelookMode(val id: String, val label: String) {
    HOLD("hold", "Hold"),
    TOGGLE("toggle", "Toggle"),
    BOTH("both", "Hold or toggle"),
    ;

    fun next(): FreelookMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromId(raw: String?): FreelookMode? = entries.firstOrNull {
            it.id == raw?.trim()?.lowercase()
        }
    }
}

data class FreelookConfig(
    val enabled: Boolean = true,
    val key: Int = DEFAULT_KEY,
    val mode: FreelookMode = FreelookMode.BOTH,
) {
    companion object {
        const val UNBOUND: Int = -1
        const val DEFAULT_KEY: Int = GLFW.GLFW_KEY_F6

        fun parse(json: String): FreelookConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return FreelookConfig()
            }
            if (!root.isJsonObject) return FreelookConfig()
            val obj = root.asJsonObject
            val enabled = obj.get("enabled")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: true
            val key = obj.get("key")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt
                ?: DEFAULT_KEY
            val mode = FreelookMode.fromId(
                obj.get("mode")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString,
            ) ?: FreelookMode.BOTH
            return FreelookConfig(enabled, if (key == 0) UNBOUND else key, mode)
        }
    }
}

internal data class FreelookAngles(val yaw: Float, val pitch: Float)

internal object FreelookMath {
    const val TURN_FACTOR: Double = 0.15

    fun applyTurn(yaw: Float, pitch: Float, deltaYaw: Double, deltaPitch: Double): FreelookAngles {
        val nextYaw = yaw + (deltaYaw * TURN_FACTOR).toFloat()
        val nextPitch = (pitch + (deltaPitch * TURN_FACTOR).toFloat()).coerceIn(-90f, 90f)
        return FreelookAngles(nextYaw, nextPitch)
    }
}

internal object FreelookCamera {
    fun whileActive(current: CameraType): CameraType {
        return if (current.isFirstPerson()) CameraType.THIRD_PERSON_BACK else current
    }
}

internal enum class FreelookPulse { NONE, START, STOP }

internal data class FreelookKeyState(
    val active: Boolean = false,
    val latched: Boolean = false,
    val lastDown: Boolean = false,
    val pressTime: Long = 0L,
)

internal data class FreelookKeyStep(val state: FreelookKeyState, val pulse: FreelookPulse)

internal object FreelookKeys {
    const val HOLD_MS: Long = 250L

    fun step(state: FreelookKeyState, mode: FreelookMode, down: Boolean, now: Long): FreelookKeyStep {
        val pressed = down && !state.lastDown
        val released = !down && state.lastDown
        val next = when (mode) {
            FreelookMode.HOLD -> hold(state, pressed, released)
            FreelookMode.TOGGLE -> toggle(state, pressed, now)
            FreelookMode.BOTH -> both(state, pressed, released, now)
        }
        return FreelookKeyStep(next.state.copy(lastDown = down), next.pulse)
    }

    private fun hold(state: FreelookKeyState, pressed: Boolean, released: Boolean): FreelookKeyStep {
        if (pressed) return FreelookKeyStep(state.copy(active = true, latched = false), FreelookPulse.START)
        if (released && state.active) {
            return FreelookKeyStep(state.copy(active = false, latched = false), FreelookPulse.STOP)
        }
        return FreelookKeyStep(state, FreelookPulse.NONE)
    }

    private fun toggle(state: FreelookKeyState, pressed: Boolean, now: Long): FreelookKeyStep {
        if (!pressed) return FreelookKeyStep(state, FreelookPulse.NONE)
        return if (state.active) {
            FreelookKeyStep(state.copy(active = false, latched = false), FreelookPulse.STOP)
        } else {
            FreelookKeyStep(state.copy(active = true, latched = true, pressTime = now), FreelookPulse.START)
        }
    }

    private fun both(
        state: FreelookKeyState,
        pressed: Boolean,
        released: Boolean,
        now: Long,
    ): FreelookKeyStep {
        if (pressed) {
            return if (state.active && state.latched) {
                FreelookKeyStep(state.copy(active = false, latched = false), FreelookPulse.STOP)
            } else if (!state.active) {
                FreelookKeyStep(state.copy(active = true, latched = false, pressTime = now), FreelookPulse.START)
            } else {
                FreelookKeyStep(state, FreelookPulse.NONE)
            }
        }
        if (released && state.active && !state.latched) {
            return if (now - state.pressTime > HOLD_MS) {
                FreelookKeyStep(state.copy(active = false, latched = false), FreelookPulse.STOP)
            } else {
                FreelookKeyStep(state.copy(latched = true), FreelookPulse.NONE)
            }
        }
        return FreelookKeyStep(state, FreelookPulse.NONE)
    }
}
