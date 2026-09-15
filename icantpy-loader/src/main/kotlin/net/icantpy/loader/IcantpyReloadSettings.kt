package net.icantpy.loader

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object IcantpyReloadSettings {
    private val LOGGER = LoggerFactory.getLogger("icantpy-loader")
    private val GSON = Gson()

    private var cached: ReloadSource? = null

    fun source(): ReloadSource {
        cached?.let { return it }
        val file = settingsFile()
        if (!Files.isRegularFile(file)) {
            cached = ReloadSource.UPDATE
            return ReloadSource.UPDATE
        }
        return try {
            Files.newBufferedReader(file).use { reader ->
                val data = GSON.fromJson(reader, Stored::class.java)
                ReloadSource.fromName(data?.source).also { cached = it }
            }
        } catch (exception: Exception) {
            LOGGER.warn("Failed to read reload settings; using UPDATE", exception)
            ReloadSource.UPDATE.also { cached = it }
        }
    }

    fun setSource(source: ReloadSource) {
        cached = source
        Files.createDirectories(IcantpyLoaderPaths.runtime())
        val stored = Stored().apply { this.source = source.name }
        Files.newBufferedWriter(settingsFile()).use { writer ->
            GSON.toJson(stored, writer)
        }
    }

    fun toggleSource(): ReloadSource {
        val next = source().toggle()
        setSource(next)
        return next
    }

    fun settingsFile(): Path = IcantpyLoaderPaths.runtime().resolve("reload.json")

    private class Stored {
        var source: String? = null
    }
}
