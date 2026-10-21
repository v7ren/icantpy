package net.icantpy

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * icantpy's logger: logs through [delegate] exactly as before, and mirrors every line (debug included) into
 * `<game dir>/icantpy/logs/latest.log`, a file with only icantpy's own output. Same call shapes as SLF4J, so
 * `Icantpy.LOGGER.info("a {}", x)` and `warn("msg", exception)` keep working.
 */
class IcantpyLog(private val delegate: Logger) {
    fun info(message: String, vararg args: Any?) {
        delegate.info(message, *args)
        IcantpyLogFile.append("INFO", message, args)
    }

    fun warn(message: String, vararg args: Any?) {
        delegate.warn(message, *args)
        IcantpyLogFile.append("WARN", message, args)
    }

    fun error(message: String, vararg args: Any?) {
        delegate.error(message, *args)
        IcantpyLogFile.append("ERROR", message, args)
    }

    fun debug(message: String, vararg args: Any?) {
        delegate.debug(message, *args)
        IcantpyLogFile.append("DEBUG", message, args)
    }
}

/**
 * The file behind [IcantpyLog]. Lines are queued from any thread and written by [flush] once per client tick, so
 * logging never blocks on disk. The first flush of a game session moves the previous session's `latest.log` aside
 * as `icantpy-<time>.log` (keeping [KEEP_OLD]); a payload reload keeps appending to the same file.
 */
object IcantpyLogFile {
    private const val KEEP_OLD = 10
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val queue = ConcurrentLinkedQueue<String>()
    private var writer: BufferedWriter? = null
    private var failed = false

    val dir: Path by lazy { FabricLoader.getInstance().gameDir.resolve("icantpy").resolve("logs") }

    fun append(level: String, message: String, args: Array<out Any?>) {
        if (failed) return
        val tuple = MessageFormatter.arrayFormat(message, args)
        val line = StringBuilder()
            .append('[').append(LocalTime.now().format(TIME)).append("] [")
            .append(Thread.currentThread().name).append("] ")
            .append(level).append(": ").append(tuple.message)
        tuple.throwable?.let { error ->
            val trace = StringWriter()
            error.printStackTrace(PrintWriter(trace))
            line.append(System.lineSeparator()).append(trace.toString().trimEnd())
        }
        queue.add(line.toString())
    }

    /** Writes queued lines. Call once per client tick and on unload. */
    fun flush() {
        if (queue.isEmpty() || failed) return
        val out = writer ?: open() ?: return
        try {
            while (true) {
                val line = queue.poll() ?: break
                out.write(line)
                out.newLine()
            }
            out.flush()
        } catch (_: Exception) {
            close()
        }
    }

    /** Flushes and closes the file; the next line reopens it in append mode. */
    fun close() {
        val out = writer ?: return
        writer = null
        try {
            while (true) {
                val line = queue.poll() ?: break
                out.write(line)
                out.newLine()
            }
            out.close()
        } catch (_: Exception) {
        }
    }

    private fun open(): BufferedWriter? = try {
        Files.createDirectories(dir)
        val latest = dir.resolve("latest.log")
        rotateIfFromEarlierSession(latest)
        Files.newBufferedWriter(
            latest, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
        ).also { writer = it }
    } catch (_: Exception) {
        // Never let logging break the mod; the normal log still has everything.
        failed = true
        queue.clear()
        null
    }

    /** A latest.log last written before this JVM started belongs to an earlier game session: move it aside. */
    private fun rotateIfFromEarlierSession(latest: Path) {
        if (!Files.exists(latest)) return
        val modified = Files.getLastModifiedTime(latest).toMillis()
        if (modified >= ManagementFactory.getRuntimeMXBean().startTime) return
        val stamp = Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).format(STAMP)
        Files.move(latest, dir.resolve("icantpy-$stamp.log"))
        Files.list(dir).use { files ->
            files.filter { it.fileName.toString().startsWith("icantpy-") && it.fileName.toString().endsWith(".log") }
                .sorted(Comparator.comparing<Path, String> { it.fileName.toString() }.reversed())
                .skip(KEEP_OLD.toLong())
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
