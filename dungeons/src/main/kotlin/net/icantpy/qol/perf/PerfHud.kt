package net.icantpy.qol.perf

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small text panel with frame rate, frame time and frame-generation split. Frame times are recorded every frame
 * even while hidden so the numbers are warm when the panel is turned on. Text is rebuilt at most 4 times a second.
 */
object PerfHud {
    private const val TEXT_INTERVAL_NANOS = 250_000_000L
    private const val RATE_WINDOW_NANOS = 1_000_000_000L
    private const val LEFT = 4
    private const val TOP = 4
    private const val PAD = 4
    private const val LINE_HEIGHT = 10
    private const val BACKGROUND = 0x90000000.toInt()
    private const val TEXT = 0xFFFFFFFF.toInt()

    private val stats = FrameTimeStats()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var on = false

    /** Whether the panel is shown. Persisted in configDir/icantpy-perfhud.json. */
    val enabled: Boolean
        get() = on

    private var lastFrameNanos = 0L
    private var rateStartNanos = 0L
    private var rateStartReal = 0L
    private var rateStartGenerated = 0L
    private var realFps = 0.0
    private var generatedFps = 0.0
    private var lastTextNanos = 0L
    private var lines: List<String> = emptyList()

    fun load() {
        on = readEnabled()
        stats.clear()
        resetTiming()
    }

    fun unload() {
        resetTiming()
        lines = emptyList()
    }

    fun setEnabled(value: Boolean) {
        on = value
        lines = emptyList()
        writeEnabled(value)
    }

    /** Call first in the world render callback, once per frame. Also ends the per-frame perf counters. */
    fun onFrameStart() {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) stats.record(now - lastFrameNanos)
        lastFrameNanos = now
        updateRates(now)
        PerfCounters.endFrame()
    }

    fun render(graphics: GuiGraphicsExtractor) {
        if (!on) return
        val now = System.nanoTime()
        if (lines.isEmpty() || now - lastTextNanos >= TEXT_INTERVAL_NANOS) {
            lines = buildLines()
            lastTextNanos = now
        }
        val font = Minecraft.getInstance().font
        var width = 0
        for (line in lines) width = max(width, font.width(line))
        val height = lines.size * LINE_HEIGHT
        graphics.fill(LEFT - PAD, TOP - PAD, LEFT + width + PAD, TOP + height + PAD - 2, BACKGROUND)
        lines.forEachIndexed { index, line ->
            graphics.text(font, line, LEFT, TOP + index * LINE_HEIGHT, TEXT, true)
        }
    }

    private fun updateRates(now: Long) {
        if (rateStartNanos == 0L) {
            startRateWindow(now)
            return
        }
        val elapsed = now - rateStartNanos
        if (elapsed < RATE_WINDOW_NANOS) return
        val seconds = elapsed / 1_000_000_000.0
        realFps = (PerfCounters.realFrames - rateStartReal) / seconds
        generatedFps = (PerfCounters.generatedFrames - rateStartGenerated) / seconds
        startRateWindow(now)
    }

    private fun startRateWindow(now: Long) {
        rateStartNanos = now
        rateStartReal = PerfCounters.realFrames
        rateStartGenerated = PerfCounters.generatedFrames
    }

    private fun resetTiming() {
        lastFrameNanos = 0L
        rateStartNanos = 0L
        realFps = 0.0
        generatedFps = 0.0
        lastTextNanos = 0L
    }

    private fun buildLines(): List<String> = listOf(
        "FPS ${stats.fps().roundToInt()}   real ${realFps.roundToInt()} / gen ${generatedFps.roundToInt()}",
        "1% low ${stats.onePercentLowFps().roundToInt()}   0.1% low ${stats.pointOnePercentLowFps().roundToInt()}",
        "Frame ${fmt1(stats.averageFrameMs())} ms   max ${fmt1(stats.maxFrameMs())} ms",
        "Culled: mobs ${PerfCounters.lastCulledEntities}  block ${PerfCounters.lastCulledBlockEntities}" +
            "  particles ${PerfCounters.lastCulledParticles}  tags ${PerfCounters.lastHiddenNameTags}",
    )

    private fun fmt1(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
    }

    private fun readEnabled(): Boolean {
        val path = path()
        if (!Files.isRegularFile(path)) return false
        return try {
            JsonParser.parseString(Files.readString(path)).asJsonObject.get("enabled")?.asBoolean ?: false
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read performance HUD config; using defaults", exception)
            false
        }
    }

    private fun writeEnabled(value: Boolean) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(mapOf("version" to 1, "enabled" to value)) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write performance HUD config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-perfhud.json")
}
