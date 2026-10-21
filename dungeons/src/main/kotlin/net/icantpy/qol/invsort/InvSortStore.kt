package net.icantpy.qol.invsort

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object InvSortStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Returns null when no file exists. Unparseable files are moved aside and defaults are returned. */
    fun load(path: Path): InvSortSettings? {
        if (!Files.isRegularFile(path)) return null
        val result = InvSortCodec.parseJson(Files.readString(path))
        if (result.error != null) {
            val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
        }
        return result.settings
    }

    fun save(path: Path, settings: InvSortSettings) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(InvSortCodec.toJson(settings)))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
