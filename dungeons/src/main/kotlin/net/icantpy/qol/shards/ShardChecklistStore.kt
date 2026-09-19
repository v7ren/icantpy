package net.icantpy.qol.shards

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object ShardChecklistStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    fun load(path: Path, catalog: Collection<ShardDefinition>): ShardChecklist? {
        if (!Files.isRegularFile(path)) return null
        val result = ShardChecklistCodec.parseJson(Files.readString(path), catalog)
        if (result.error != null) {
            val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
            return null
        }
        return result.checklist
    }

    fun save(path: Path, checklist: ShardChecklist) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(checklist))
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
    }
}
