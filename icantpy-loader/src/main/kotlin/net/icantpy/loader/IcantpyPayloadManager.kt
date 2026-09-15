package net.icantpy.loader

import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyPayload
import org.slf4j.LoggerFactory
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

object IcantpyPayloadManager {
    private val LOGGER = LoggerFactory.getLogger("icantpy-loader")

    private data class LoadedPayload(
        val classLoader: URLClassLoader,
        val payload: IcantpyPayload,
        val version: String,
    )

    private data class ResolvedPayload(
        val path: Path,
        val version: String,
    )

    private var active: LoadedPayload? = null
    var loadedVersion: String = "unknown"
        private set

    @Synchronized
    fun loadFromSource(source: ReloadSource): String {
        val previous = active
        val previousVersion = loadedVersion
        val resolved = resolvePayloadPath(source)
        val payloadPath = resolved.path
        if (!isPayloadJar(payloadPath)) {
            throw IllegalStateException("Jar at $payloadPath is not an icantpy payload")
        }
        val cacheJar = copyToCache(payloadPath)
        val candidate = prepare(cacheJar, resolved.version)

        try {
            if (previous != null) {
                IcantpyBridge.setPayload(null)
                unloadPayload(previous)
            }
            candidate.payload.onLoad()
            IcantpyBridge.setPayload(candidate.payload)
            active = candidate
            loadedVersion = candidate.version
            previous?.let(::closePayload)
            LOGGER.info("Loaded icantpy payload {} from {}", loadedVersion, cacheJar.fileName)
            return loadedVersion
        } catch (exception: Throwable) {
            try {
                candidate.payload.onUnload()
            } catch (cleanupError: Throwable) {
                LOGGER.error("Candidate payload cleanup failed after activation failure", cleanupError)
            }
            closePayload(candidate)
            active = previous
            loadedVersion = previousVersion
            if (previous != null) {
                try {
                    previous.payload.onLoad()
                    IcantpyBridge.setPayload(previous.payload)
                    LOGGER.warn("Restored previous icantpy payload {} after activation failure", previous.version)
                } catch (restoreError: Throwable) {
                    IcantpyBridge.setPayload(null)
                    LOGGER.error("Failed to restore previous icantpy payload", restoreError)
                }
            } else {
                IcantpyBridge.setPayload(null)
            }
            throw IllegalStateException("Payload activation failed; previous runtime was restored when possible", exception)
        }
    }

    @Synchronized
    fun reload(): String = loadFromSource(IcantpyReloadSettings.source())

    @Synchronized
    fun unload() {
        IcantpyBridge.setPayload(null)

        val old = active ?: return
        active = null
        unloadPayload(old)
        closePayload(old)
        loadedVersion = "unknown"
    }

    private fun resolvePayloadPath(source: ReloadSource): ResolvedPayload {
        return when (source) {
            ReloadSource.UPDATE -> {
                val version = IcantpyPayloadUpdater.installLatest()
                ResolvedPayload(IcantpyLoaderPaths.payload(), version)
            }
            ReloadSource.LOCAL -> {
                val payload = IcantpyLoaderPaths.payload()
                if (!Files.isRegularFile(payload)) {
                    throw IllegalStateException("No local payload at $payload")
                }
                ResolvedPayload(payload, "local")
            }
        }
    }

    private fun copyToCache(sourceJar: Path): Path {
        val cacheDir = IcantpyLoaderPaths.payloadCache()
        Files.createDirectories(cacheDir)
        val digest = IcantpyPayloadUpdater.sha256(sourceJar)
        val target = cacheDir.resolve("icantpy-payload-$digest.jar")
        if (Files.isRegularFile(target) && isPayloadJar(target)) return target
        Files.copy(sourceJar, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun prepare(cacheJar: Path, version: String): LoadedPayload {
        val urls = collectClassPathUrls(cacheJar)
        val parent = IcantpyPayload::class.java.classLoader
        val loader = URLClassLoader(urls.toTypedArray(), parent)
        try {
            val instance = resolvePayloadInstance(loader)
            val icantpyPayload = instance as? IcantpyPayload
                ?: throw IllegalStateException("${instance.javaClass.name} does not implement IcantpyPayload")
            return LoadedPayload(loader, icantpyPayload, version)
        } catch (exception: Throwable) {
            try {
                loader.close()
            } catch (closeError: Exception) {
                LOGGER.warn("Failed to close candidate payload classloader", closeError)
            }
            throw exception
        }
    }

    private fun unloadPayload(loaded: LoadedPayload) {
        try {
            loaded.payload.onUnload()
        } catch (exception: Throwable) {
            LOGGER.error("Payload onUnload failed", exception)
        }
    }

    private fun closePayload(loaded: LoadedPayload) {
        try {
            loaded.classLoader.close()
        } catch (exception: Exception) {
            LOGGER.warn("Failed to close payload classloader", exception)
        }
    }

    private fun collectClassPathUrls(mainJar: Path): List<URL> {
        val urls = mutableListOf(mainJar.toUri().toURL())
        val libsDir = IcantpyLoaderPaths.libraryCache()
        Files.createDirectories(libsDir)

        JarFile(mainJar.toFile()).use { jarFile ->
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (entry.isDirectory || !name.startsWith("META-INF/jars/") || !name.endsWith(".jar")) {
                    continue
                }
                val temporary = Files.createTempFile(libsDir, "icantpy-lib-", ".part")
                try {
                    jarFile.getInputStream(entry).use { input ->
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
                    }
                    val digest = IcantpyPayloadUpdater.sha256(temporary)
                    val out = libsDir.resolve("icantpy-lib-$digest.jar")
                    if (Files.isRegularFile(out)) {
                        Files.deleteIfExists(temporary)
                    } else {
                        try {
                            Files.move(temporary, out, StandardCopyOption.ATOMIC_MOVE)
                        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                            Files.move(temporary, out)
                        }
                    }
                    urls.add(out.toUri().toURL())
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
        return urls
    }

    private fun resolvePayloadInstance(loader: ClassLoader): Any {
        val clazz = Class.forName(IcantpyLoaderPaths.PAYLOAD_ENTRYPOINT, true, loader)
        val instanceField = clazz.fields.find { it.name == "INSTANCE" }
        if (instanceField != null) {
            return instanceField.get(null)
                ?: throw IllegalStateException("INSTANCE field on ${clazz.name} is null")
        }
        return clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }

    fun isPayloadJar(path: Path): Boolean = IcantpyPayloadUpdater.isPayloadJar(path)
}
