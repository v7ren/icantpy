package net.icantpy.dungeon.terminal

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Loads and saves config/icantpy-terminals.json. A malformed file is moved aside to `.invalid.<time>` and defaults
 * are returned. Saves go through a temp file so a crash never leaves a half-written config.
 */
object TerminalSettingsStore {
    private const val FILE_NAME = "icantpy-terminals.json"

    fun defaultPath(): Path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

    fun load(path: Path = defaultPath()): TerminalSettings {
        if (!Files.isRegularFile(path)) return TerminalSettings()
        val decoded = TerminalSettingsCodec.decode(Files.readString(path))
        if (decoded != null) return decoded
        val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
        Icantpy.LOGGER.warn("Malformed terminal settings moved to {}", backup.fileName)
        Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
        return TerminalSettings()
    }

    fun save(path: Path, settings: TerminalSettings) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, TerminalSettingsCodec.encode(settings))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
