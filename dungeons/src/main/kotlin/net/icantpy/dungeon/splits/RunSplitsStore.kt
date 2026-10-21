package net.icantpy.dungeon.splits

import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Thin file IO around [RunSplitsCodec]. */
object RunSplitsStore {
    const val FILE_NAME: String = "icantpy-splits.json"

    fun defaultPath(): Path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

    /** Missing file gives defaults. A malformed file is moved aside to `<name>.invalid.<millis>` and defaults are returned. */
    fun load(path: Path): RunSplitsDocument {
        if (!Files.isRegularFile(path)) return RunSplitsDocument()
        val text = try {
            Files.readString(path)
        } catch (_: IOException) {
            return RunSplitsDocument()
        }
        val decoded = RunSplitsCodec.decode(text)
        if (!decoded.malformed) return decoded.document
        val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // Leave the bad file in place; defaults are still returned.
        }
        return RunSplitsDocument()
    }

    fun save(path: Path, document: RunSplitsDocument) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, RunSplitsCodec.encode(document))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
