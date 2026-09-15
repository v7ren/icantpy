package net.icantpy.loader

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.Locale

object IcantpyUpdateSettings {
    const val UPDATE_BASE_KEY: String = "ICANTPY_UPDATE_BASE"
    const val UPDATE_DOMAIN_KEY: String = "ICANTPY_UPDATE_DOMAIN"
    const val DEFAULT_UPDATE_BASE: String = "https://mod.v7ren.com/icantpy"

    private val LOGGER = LoggerFactory.getLogger("icantpy-loader")
    private var cached: Map<String, String>? = null

    fun manifestUrl(): String = updateBase() + "/manifest.json"

    /** Modern loaders use a separate channel so legacy loaders keep their old contract. */
    fun modernManifestUrl(): String = updateBase() + "/manifest-v2.json"

    @Synchronized
    fun reload() {
        cached = null
    }

    fun updateBase(): String {
        val settings = load()
        var base = firstNonBlank(settings[UPDATE_BASE_KEY], System.getenv(UPDATE_BASE_KEY))
        if (base == null) {
            val domain = firstNonBlank(settings[UPDATE_DOMAIN_KEY], System.getenv(UPDATE_DOMAIN_KEY))
            if (domain != null) {
                base = "https://${stripDomain(domain)}/icantpy"
            }
        }
        return trimTrailingSlash((base ?: DEFAULT_UPDATE_BASE).trim())
    }

    @Synchronized
    private fun load(): Map<String, String> {
        cached?.let { return it }
        val settings = LinkedHashMap<String, String>()
        for (path in candidatePaths()) {
            if (!Files.isRegularFile(path)) continue
            try {
                parse(path, settings)
                LOGGER.info("Loaded update settings from {}", path.toAbsolutePath())
                break
            } catch (exception: Exception) {
                LOGGER.warn("Failed to read update settings from {}", path.toAbsolutePath(), exception)
            }
        }
        cached = settings
        return settings
    }

    private fun candidatePaths(): Array<Path> {
        return try {
            val game = FabricLoader.getInstance().gameDir
            val config = FabricLoader.getInstance().configDir
            arrayOf(
                game.resolve("icantpy/.env"),
                game.resolve(".env"),
                config.resolve("icantpy/.env"),
                Path.of(".env"),
            )
        } catch (_: Throwable) {
            arrayOf(Path.of(".env"))
        }
    }

    private fun parse(path: Path, settings: MutableMap<String, String>) {
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                var trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (trimmed.startsWith("export ")) trimmed = trimmed.substring(7).trim()
                    val separator = trimmed.indexOf('=')
                    if (separator > 0) {
                        val key = trimmed.substring(0, separator).trim()
                        if (key.isNotEmpty()) {
                            settings[key] = stripQuotes(trimmed.substring(separator + 1).trim())
                        }
                    }
                }
                line = reader.readLine()
            }
        }
    }

    private fun stripQuotes(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        val last = value.last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
    }

    private fun stripDomain(value: String): String {
        var domain = value.trim()
        val lower = domain.lowercase(Locale.ROOT)
        domain = when {
            lower.startsWith("https://") -> domain.substring(8)
            lower.startsWith("http://") -> domain.substring(7)
            else -> domain
        }
        domain = trimTrailingSlash(domain)
        if (domain.lowercase(Locale.ROOT).endsWith("/icantpy")) {
            domain = domain.substring(0, domain.length - "/icantpy".length)
        }
        return domain
    }

    private fun trimTrailingSlash(value: String): String {
        var end = value.length
        while (end > 0 && value[end - 1] == '/') end--
        return value.substring(0, end)
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}
