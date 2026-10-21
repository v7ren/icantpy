package net.icantpy.dungeon.dragons

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** File IO for [DragonFile]. Callers pass the path, for example config/icantpy-dragons.json. */
object DragonSettingsStore {

    /**
     * Returns null when the file is absent. A malformed file is moved aside to `<name>.invalid.<millis>`
     * so the user can recover it, and defaults are used.
     */
    fun load(path: Path): DragonFile? {
        if (!Files.isRegularFile(path)) return null
        val result = DragonSettingsCodec.decode(Files.readString(path))
        result.file?.let { return it }
        val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
        Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
        return null
    }

    fun save(path: Path, file: DragonFile) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, DragonSettingsCodec.encode(file))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
