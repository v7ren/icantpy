package net.icantpy.dungeon.leap

import net.icantpy.hud.markers.MarkerClass
import net.icantpy.hud.markers.MarkerShapes
import net.icantpy.hud.markers.MarkerLook
import net.icantpy.hud.markers.MarkerSelector
import net.icantpy.hud.markers.MarkerStyle
import net.icantpy.hud.markers.PlayerMarkers

/** One resolved "Mark player" row, ready to be shown as a world marker. [stepIndex] keys the marker id. */
data class LeapMark(
    val stepIndex: Int,
    val selector: MarkerSelector,
    val style: MarkerStyle,
    val colorArgb: Int,
    /** 0 keeps the marker until the run resets. */
    val durationMs: Long,
    val showName: Boolean,
    val opacityPercent: Int = MarkerLook.DEFAULT_OPACITY,
    val brightnessPercent: Int = MarkerLook.DEFAULT_BRIGHTNESS,
    val sizePercent: Int = MarkerLook.DEFAULT_SIZE,
    val heightBlocks: Int = MarkerLook.DEFAULT_HEIGHT,
    val pulse: Boolean = true,
    val playerGlow: Boolean = false,
    val markerGlow: Boolean = false,
    val shape: String = MarkerShapes.CLASSIC,
    val spin: Boolean = false,
    val bob: Boolean = true,
    val throughWalls: Boolean = false,
)

/** Mark rows of one card: resolved marks, and rows whose target is incomplete (reported in chat). */
data class LeapMarkRoute(
    val marks: List<LeapMark>,
    val skipped: List<LeapAutoStep>,
)

/** Mark rows are instant, like leap targets: they never enter the auto queue. */
object LeapMarkRouter {
    /** Marker color choices for the Color row, in cycle order. */
    val COLOR_CHOICES: List<Pair<String, Int>> = listOf(
        "Red" to 0xFFFF5555.toInt(),
        "Orange" to 0xFFFFAA00.toInt(),
        "Yellow" to 0xFFFFFF55.toInt(),
        "Green" to 0xFF55FF55.toInt(),
        "Aqua" to 0xFF55FFFF.toInt(),
        "Blue" to 0xFF5555FF.toInt(),
        "Purple" to 0xFFAA55FF.toInt(),
        "White" to 0xFFFFFFFF.toInt(),
    )

    /** Choices for the For row, in seconds. 0 means until the run resets. */
    val SECONDS_CHOICES: List<Int> = listOf(3, 5, 10, 15, 30, 60, 0)

    const val BY_CLASS = "CLASS"
    const val BY_PLAYER = "PLAYER"

    fun route(steps: List<LeapAutoStep>): LeapMarkRoute {
        val marks = mutableListOf<LeapMark>()
        val skipped = mutableListOf<LeapAutoStep>()
        steps.forEachIndexed { index, step ->
            if (step.kind != LeapAutoKind.MARK_PLAYER) return@forEachIndexed
            val selector = selectorOf(step)
            if (selector == null) {
                skipped += step
            } else {
                marks += LeapMark(
                    stepIndex = index,
                    selector = selector,
                    style = styleOf(step),
                    colorArgb = step.markColor,
                    durationMs = step.markSeconds.coerceAtLeast(0) * 1000L,
                    showName = step.markShowName,
                    opacityPercent = step.markOpacity,
                    brightnessPercent = step.markBrightness,
                    sizePercent = step.markSize,
                    heightBlocks = step.markHeight,
                    pulse = step.markPulse,
                    playerGlow = step.markPlayerGlow,
                    markerGlow = step.markMarkerGlow,
                    shape = step.markArrowShape,
                    spin = step.markSpin,
                    bob = step.markBob,
                    throughWalls = step.markThroughWalls,
                )
            }
        }
        return LeapMarkRoute(marks, skipped)
    }

    /** Puts every mark of [marks] on the world, keyed by card and step so re-firing replaces it. */
    fun apply(marks: List<LeapMark>, cardKey: String, nowMs: Long) {
        marks.forEach { mark ->
            PlayerMarkers.mark(
                id = markId(cardKey, mark.stepIndex),
                selector = mark.selector,
                style = mark.style,
                colorArgb = mark.colorArgb,
                durationMs = mark.durationMs,
                nowMs = nowMs,
                showName = mark.showName,
                opacityPercent = mark.opacityPercent,
                brightnessPercent = mark.brightnessPercent,
                sizePercent = mark.sizePercent,
                heightBlocks = mark.heightBlocks,
                pulse = mark.pulse,
                playerGlow = mark.playerGlow,
                markerGlow = mark.markerGlow,
                shape = mark.shape,
                spin = mark.spin,
                bob = mark.bob,
                throughWalls = mark.throughWalls,
            )
        }
    }

    fun markId(cardKey: String, stepIndex: Int): String = "$cardKey#$stepIndex"

    /** The target text of a mark row, such as "Healer (arrow)". Used as the value and in the step label. */
    fun summary(step: LeapAutoStep): String {
        val target = if (step.markTargetsPlayer()) {
            step.markPlayer.trim().ifBlank { "no player" }
        } else {
            classOf(step.markClass)?.displayName ?: "no class"
        }
        return "$target (${styleOf(step).name.lowercase()})"
    }

    fun colorName(argb: Int): String = COLOR_CHOICES.firstOrNull { it.second == argb }?.first ?: "Custom"

    fun secondsLabel(seconds: Int): String = if (seconds <= 0) "Until reset" else "${seconds}s"

    fun styleOf(step: LeapAutoStep): MarkerStyle =
        MarkerStyle.entries.firstOrNull { it.name.equals(step.markStyle, ignoreCase = true) } ?: MarkerStyle.ARROW

    /** Class token to the marker class; the same names as [MarkerClass] and [LeapDungeonClass]. */
    fun classOf(token: String): LeapDungeonClass? = MarkerClass.fromToken(token)?.let { LeapDungeonClass.fromToken(it.name) }

    private fun selectorOf(step: LeapAutoStep): MarkerSelector? {
        if (step.markTargetsPlayer()) {
            return step.markPlayer.trim().takeIf { it.isNotEmpty() }?.let { MarkerSelector.ByName(it) }
        }
        return MarkerClass.fromToken(step.markClass)?.let { MarkerSelector.ByClass(it) }
    }
}
