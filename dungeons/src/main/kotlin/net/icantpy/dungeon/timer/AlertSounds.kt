package net.icantpy.dungeon.timer

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

object AlertSounds {
    private val playback = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "icantpy-alert-sound").apply { isDaemon = true }
    }

    fun directory(): Path =
        FabricLoader.getInstance().configDir.resolve("icantpy").resolve("sounds")

    fun ensureDirectory(dir: Path = directory()): Path {
        Files.createDirectories(dir)
        val readme = dir.resolve("README.txt")
        if (!Files.isRegularFile(readme)) {
            Files.writeString(
                readme,
                "Drop .wav alert files here, then pick them in icantpy Alerts.\n",
            )
        }
        return dir
    }

    fun files(dir: Path = directory()): List<String> {
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && isAudio(it) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    fun options(dir: Path = directory()): List<String> =
        listOf("", "pling", "orb", "anvil") + files(dir)

    fun next(current: String, dir: Path = directory()): String {
        val all = options(dir)
        val index = all.indexOfFirst { it.equals(current, ignoreCase = true) }
        return all[(index + 1).coerceAtLeast(0) % all.size]
    }

    fun previous(current: String, dir: Path = directory()): String {
        val all = options(dir)
        val index = all.indexOfFirst { it.equals(current, ignoreCase = true) }
        val from = if (index < 0) 0 else index
        return all[(from - 1 + all.size) % all.size]
    }

    fun label(name: String): String = when (name.lowercase(Locale.ROOT)) {
        "", "none" -> "None"
        "pling" -> "Pling"
        "orb" -> "Orb"
        "anvil" -> "Anvil"
        else -> name
    }

    fun triggerLabel(name: String): String =
        if (name.isBlank()) "Default" else label(name)

    fun triggerOptions(dir: Path = directory()): List<String> =
        listOf("", "none") + options(dir).filter { it.isNotEmpty() }

    fun nextTrigger(current: String, dir: Path = directory()): String {
        val all = triggerOptions(dir)
        val index = all.indexOfFirst { it.equals(current, ignoreCase = true) }
        return all[(index + 1).coerceAtLeast(0) % all.size]
    }

    fun openFolder(): String {
        val dir = ensureDirectory()
        return try {
            Desktop.getDesktop().open(dir.toFile())
            "sounds folder opened"
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Could not open sounds folder", exception)
            dir.toAbsolutePath().toString()
        }
    }

    fun play(name: String) {
        val key = name.trim()
        if (key.isEmpty() || key.equals("none", ignoreCase = true)) return
        vanilla(key)?.let { event ->
            val mc = Minecraft.getInstance()
            mc.execute { mc.soundManager.play(SimpleSoundInstance.forUI(event, 1f)) }
            return
        }
        val file = resolve(key) ?: return
        playback.execute { playFile(file) }
    }

    fun resolve(name: String, dir: Path = directory()): Path? {
        val clean = Path.of(name).fileName.toString()
        if (clean.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) return null
        val root = dir.toAbsolutePath().normalize()
        val path = root.resolve(clean).normalize()
        if (!path.startsWith(root)) return null
        return path.takeIf { Files.isRegularFile(it) }
    }

    private fun vanilla(name: String): SoundEvent? = when (name.lowercase(Locale.ROOT)) {
        "pling" -> SoundEvents.NOTE_BLOCK_PLING.value()
        "orb" -> SoundEvents.EXPERIENCE_ORB_PICKUP
        "anvil" -> SoundEvents.ANVIL_LAND
        else -> null
    }

    private fun isAudio(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".wav") || name.endsWith(".aiff") || name.endsWith(".aif")
    }

    private fun playFile(path: Path) {
        try {
            AudioSystem.getAudioInputStream(path.toFile()).use { original ->
                AudioSystem.getAudioInputStream(pcm(original.format), original).use { stream ->
                    val info = DataLine.Info(SourceDataLine::class.java, stream.format)
                    val line = AudioSystem.getLine(info) as SourceDataLine
                    line.open(stream.format)
                    try {
                        line.start()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            line.write(buffer, 0, read)
                        }
                        line.drain()
                    } finally {
                        line.stop()
                        line.close()
                    }
                }
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to play alert sound {}", path.fileName, exception)
        }
    }

    private fun pcm(format: AudioFormat): AudioFormat =
        AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            format.sampleRate,
            16,
            format.channels,
            format.channels * 2,
            format.sampleRate,
            false,
        )
}
