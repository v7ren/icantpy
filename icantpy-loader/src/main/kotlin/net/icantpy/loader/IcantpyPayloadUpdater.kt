package net.icantpy.loader

import com.google.gson.Gson
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.api.IcantpyRuntimeContract
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.jar.JarFile
import kotlin.math.min

object IcantpyPayloadUpdater {
    private val LOGGER = LoggerFactory.getLogger("icantpy-loader")
    private val GSON = Gson()
    private val HTTP: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val LOCAL_HTTP: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(500))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    const val LOCAL_UPDATE_BASE: String = "http://127.0.0.1:8964/icantpy"
    /**
     * Payloads download in ranges this size. Large single responses through the public Cloudflare tunnel get cut
     * off or answered with 502/530, while small ranges get through, so every range is small and retried on its own.
     */
    const val DOWNLOAD_CHUNK_BYTES: Long = 4L * 1024 * 1024

    /** Tries per range before the whole download from that URL is given up (about a minute of back-off). */
    const val CHUNK_ATTEMPTS: Int = 10

    /**
     * Pause between two ranges from a remote server. Back-to-back ranges make the tunnel drop requests for seconds at
     * a time; with this pause a 91 MB payload came through in about 23 s with no retries.
     */
    const val CHUNK_PAUSE_MS: Long = 250L
    val HOST_ABI: String = IcantpyRuntimeContract.ABI
    val HOST_CAPABILITIES: Set<String> = IcantpyRuntimeContract.CAPABILITIES

    fun rangeWindows(total: Long, chunkBytes: Long = DOWNLOAD_CHUNK_BYTES): List<LongRange> {
        require(total > 0) { "total must be positive" }
        require(chunkBytes > 0) { "chunkBytes must be positive" }
        val windows = ArrayList<LongRange>()
        var offset = 0L
        while (offset < total) {
            val end = min(offset + chunkBytes - 1, total - 1)
            windows.add(offset..end)
            offset = end + 1
        }
        return windows
    }

    /**
     * Runs [block] up to [attempts] times, sleeping [delayMs] of the failed attempt number between tries, and
     * rethrows the last failure. An interrupt is never retried.
     */
    fun <T> withRetries(
        attempts: Int,
        delayMs: (Int) -> Long = ::retryDelayMs,
        sleep: (Long) -> Unit = Thread::sleep,
        onRetry: (Int, Exception) -> Unit = { _, _ -> },
        block: (Int) -> T,
    ): T {
        require(attempts > 0) { "attempts must be positive" }
        var last: Exception? = null
        for (attempt in 1..attempts) {
            try {
                return block(attempt)
            } catch (error: InterruptedException) {
                throw error
            } catch (error: Exception) {
                last = error
                if (attempt < attempts) {
                    onRetry(attempt, error)
                    sleep(delayMs(attempt))
                }
            }
        }
        throw last!!
    }

    /** Back-off after failed attempt [attempt]: 0.5 s, 2 s, 4.5 s, then 8 s. */
    fun retryDelayMs(attempt: Int): Long = min(500L * attempt * attempt, 8_000L)

    /** Start offset from a Content-Range header such as "bytes 0-4194303/91364768", or null. */
    fun parseContentRangeStart(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""").matchEntire(header.trim()) ?: return null
        return match.groupValues[1].toLong()
    }

    fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(header.trim()) ?: return null
        return match.groupValues[3].toLong()
    }

    fun minecraftVersion(): String =
        FabricLoader.getInstance().getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

    fun pickArtifact(manifest: Manifest, minecraftVersion: String): ResolvedArtifact {
        validate(manifest)
        val versionKey = minecraftVersion.trim()
        val override = manifest.minecraft?.get(versionKey)
        val version = override?.latest?.takeIf { it.isNotBlank() } ?: manifest.latest
        val file = override?.file?.takeIf { it.isNotBlank() } ?: manifest.file
        val url = override?.url?.takeIf { it.isNotBlank() } ?: manifest.url
        if (override == null && versionKey != "26.2") {
            throw IllegalStateException("manifest has no artifact for Minecraft $minecraftVersion")
        }
        if (version.isNullOrBlank() || file.isNullOrBlank() || url.isNullOrBlank()) {
            throw IllegalStateException("manifest artifact for Minecraft $minecraftVersion is incomplete")
        }
        val artifact = ResolvedArtifact(
            version = version,
            file = file,
            url = url,
            minecraftVersion = versionKey,
            hostAbi = override?.hostAbi ?: manifest.hostAbi,
            requiredCapabilities = override?.requiredCapabilities ?: manifest.requiredCapabilities.orEmpty(),
            size = override?.size ?: manifest.size,
            sha256 = override?.sha256 ?: manifest.sha256,
        )
        requireCompatible(artifact)
        return artifact
    }

    fun installLatest(): String {
        Files.createDirectories(IcantpyLoaderPaths.runtime())
        val payload = IcantpyLoaderPaths.payload()
        val installed = readInstalled()
        val mcVersion = minecraftVersion()

        val manifest = try {
            fetchManifest().also { validate(it) }
        } catch (updateError: Exception) {
            if (Files.isRegularFile(payload)) {
                val version = installed?.version?.takeIf { it.isNotBlank() } ?: "cached"
                LOGGER.warn("Update check failed; using cached payload ({})", version, updateError)
                return version
            }
            throw updateError
        }

        val artifact = pickArtifact(manifest, mcVersion)
        val payloadValid = Files.isRegularFile(payload) && verifyArtifact(payload, artifact)
        if (needsDownload(!payloadValid, installed, artifact)) {
            try {
                download(artifact, payload)
                writeInstalled(artifact)
                LOGGER.info(
                    "Installed icantpy {} ({}) for Minecraft {} to {}",
                    artifact.version,
                    artifact.file,
                    artifact.minecraftVersion,
                    payload,
                )
            } catch (downloadError: Exception) {
                if (Files.isRegularFile(payload) && verifyArtifact(payload, artifact)) {
                    val version = installed?.version?.takeIf { it.isNotBlank() } ?: "cached"
                    LOGGER.warn("Payload download failed; using cached payload ({})", version, downloadError)
                    return version
                }
                throw downloadError
            }
        } else {
            LOGGER.info("icantpy payload is current ({}) for Minecraft {}", artifact.version, mcVersion)
        }
        return artifact.version
    }

    fun validate(manifest: Manifest) {
        if (manifest.latest.isNullOrBlank() || manifest.file.isNullOrBlank() || manifest.url.isNullOrBlank()) {
            throw IllegalStateException("manifest is missing latest, file, or url")
        }
        validateFileName(manifest.file!!, "manifest file")
        validateUrl(manifest.url!!, "manifest url")
        validateMetadata(manifest.size, manifest.sha256, "manifest")
        manifest.minecraft?.forEach { (minecraft, artifact) ->
            artifact.file?.let { validateFileName(it, "manifest override for Minecraft $minecraft file") }
            artifact.url?.let { validateUrl(it, "manifest override for Minecraft $minecraft url") }
            validateMetadata(artifact.size, artifact.sha256, "manifest override for Minecraft $minecraft")
        }
    }

    fun needsDownload(
        payloadExists: Boolean,
        installed: Installed?,
        artifact: ResolvedArtifact,
    ): Boolean {
        if (!payloadExists || installed == null) return true
        if (artifact.sha256 != null && installed.sha256 != artifact.sha256) return true
        return installed.version != artifact.version
            || installed.file != artifact.file
            || installed.minecraft != artifact.minecraftVersion
    }

    fun verifyArtifact(path: Path, artifact: ResolvedArtifact): Boolean {
        if (!isPayloadJar(path)) return false
        if (artifact.size != null && Files.size(path) != artifact.size) return false
        if (artifact.sha256 != null && !artifact.sha256.equals(sha256(path), ignoreCase = true)) return false
        return true
    }

    fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun isPayloadJar(path: Path): Boolean {
        if (!Files.isRegularFile(path) || Files.size(path) < 4) return false
        return try {
            JarFile(path.toFile()).use { jar ->
                jar.getEntry("net/icantpy/Icantpy.class") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    fun payloadDownloadUrls(file: String?, publicUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        if (!file.isNullOrBlank()) {
            urls.add("$LOCAL_UPDATE_BASE/$file")
        }
        if (publicUrl.isNotBlank()) {
            urls.add(publicUrl)
        }
        return urls.toList()
    }

    private fun fetchManifest(): Manifest {
        val urls = linkedSetOf(
            "$LOCAL_UPDATE_BASE/manifest-v2.json",
            IcantpyUpdateSettings.modernManifestUrl(),
            "$LOCAL_UPDATE_BASE/manifest.json",
            IcantpyUpdateSettings.manifestUrl(),
        )
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return fetchManifestFrom(url)
            } catch (error: Exception) {
                LOGGER.warn("Manifest fetch from {} failed", url, error)
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("manifest fetch failed")
    }

    private fun fetchManifestFrom(url: String): Manifest {
        LOGGER.info("Fetching update manifest from {}", url)
        val response = clientFor(url).send(request(url, Duration.ofSeconds(12)), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IllegalStateException("manifest HTTP ${response.statusCode()} for $url")
        }
        InputStreamReader(response.body()).use { reader ->
            return GSON.fromJson(reader, Manifest::class.java)
        }
    }

    private fun download(artifact: ResolvedArtifact, payload: Path) {
        LOGGER.info("Downloading icantpy {} ({})", artifact.version, artifact.file)
        val urls = payloadDownloadUrls(artifact.file, artifact.url)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadFrom(url, artifact, payload)
                return
            } catch (error: Exception) {
                LOGGER.warn("Payload download from {} failed", url, error)
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("payload download failed")
    }

    private fun downloadFrom(url: String, artifact: ResolvedArtifact, payload: Path) {
        val temporary = IcantpyLoaderPaths.runtime().resolve("icantpy.jar.part")
        Files.deleteIfExists(temporary)
        try {
            LOGGER.info("Fetching payload from {} in {} byte ranges", url, DOWNLOAD_CHUNK_BYTES)
            val expected = writeRangedPayload(url, temporary)
            val copied = Files.size(temporary)
            if (expected != null && copied != expected) {
                throw IllegalStateException("payload size $copied did not match $expected")
            }
            if (!verifyArtifact(temporary, artifact)) {
                throw IllegalStateException("downloaded payload failed size, hash, or jar validation")
            }
            replaceAtomically(temporary, payload)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** One downloaded range: its bytes, and the file size from Content-Range. */
    private class Chunk(val bytes: ByteArray, val total: Long)

    /**
     * Downloads [url] into [destination] range by range. Each range is read whole into memory and checked (status,
     * start offset, length) before it is written, and retried up to [CHUNK_ATTEMPTS] times on its own, so a dropped
     * connection costs one small range instead of the whole download. A server that ignores Range and answers 200
     * gets the old single-stream copy. Returns the expected file size.
     */
    private fun writeRangedPayload(url: String, destination: Path): Long? {
        val client = clientFor(url)
        val pauseMs = if (client === LOCAL_HTTP) 0L else CHUNK_PAUSE_MS
        val onRetry = { attempt: Int, error: Exception ->
            LOGGER.warn("Payload range from {} failed (attempt {}/{}): {}", url, attempt, CHUNK_ATTEMPTS, error.toString())
        }
        var whole: Long? = null
        val first = withRetries(CHUNK_ATTEMPTS, onRetry = onRetry) {
            val response = client.send(
                request(url, Duration.ofSeconds(60), mapOf("Range" to "bytes=0-${DOWNLOAD_CHUNK_BYTES - 1}")),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            if (response.statusCode() == 200) {
                response.body().use { input ->
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
                }
                whole = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull() ?: -1L
                null
            } else {
                readChunk(response, url, 0L, null)
            }
        }
        if (first == null) return whole?.takeIf { it >= 0 }
        Files.newOutputStream(destination).use { output ->
            output.write(first.bytes)
            for (window in rangeWindows(first.total).drop(1)) {
                if (pauseMs > 0) Thread.sleep(pauseMs)
                val chunk = withRetries(CHUNK_ATTEMPTS, onRetry = onRetry) {
                    val response = client.send(
                        request(url, Duration.ofSeconds(60), mapOf("Range" to "bytes=${window.first}-${window.last}")),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                    readChunk(response, url, window.first, window.last - window.first + 1)
                }
                output.write(chunk.bytes)
            }
        }
        return first.total
    }

    /** Reads a 206 range fully and checks it starts at [start] and, when known, is [length] bytes long. */
    private fun readChunk(response: HttpResponse<InputStream>, url: String, start: Long, length: Long?): Chunk {
        if (response.statusCode() != 206) {
            response.body().close()
            throw IllegalStateException("payload HTTP ${response.statusCode()} for $url range from $start")
        }
        val header = response.headers().firstValue("Content-Range").orElse(null)
        val total = parseContentRangeTotal(header)
        if (total == null || parseContentRangeStart(header) != start) {
            response.body().close()
            throw IllegalStateException("payload range from $start has bad Content-Range '$header' for $url")
        }
        val bytes = response.body().use { it.readAllBytes() }
        val wanted = length ?: min(DOWNLOAD_CHUNK_BYTES, total - start)
        if (bytes.size.toLong() != wanted) {
            throw IllegalStateException("payload range from $start was ${bytes.size} of $wanted bytes for $url")
        }
        return Chunk(bytes, total)
    }

    private fun clientFor(url: String): HttpClient {
        val host = URI.create(url).host
        return if (host == "127.0.0.1" || host == "localhost") LOCAL_HTTP else HTTP
    }

    private fun request(
        url: String,
        timeout: Duration,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(timeout)
            .header("User-Agent", "IcantpyLoader/1.0")
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        return builder.GET().build()
    }

    private fun readInstalled(): Installed? {
        val file = IcantpyLoaderPaths.installedManifest()
        if (!Files.isRegularFile(file)) return null
        return try {
            Files.newBufferedReader(file).use { reader ->
                GSON.fromJson(reader, Installed::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeInstalled(artifact: ResolvedArtifact) {
        val installed = Installed().apply {
            version = artifact.version
            file = artifact.file
            minecraft = artifact.minecraftVersion
            size = artifact.size
            sha256 = artifact.sha256
        }
        val target = IcantpyLoaderPaths.installedManifest()
        val temporary = target.resolveSibling("${target.fileName}.part")
        Files.newBufferedWriter(temporary).use { writer ->
            GSON.toJson(installed, writer)
        }
        replaceAtomically(temporary, target)
    }

    private fun requireCompatible(artifact: ResolvedArtifact) {
        // Capabilities are host-independent and must be validated even when no ABI is declared.
        val missing = artifact.requiredCapabilities - HOST_CAPABILITIES
        if (missing.isNotEmpty()) {
            throw IllegalStateException("payload ${artifact.version} requires unsupported capabilities: ${missing.sorted()}")
        }
        val abi = artifact.hostAbi ?: return
        val major = abi.substringBefore('.').toIntOrNull()
            ?: throw IllegalStateException("invalid host ABI '$abi' in ${artifact.file}")
        if (major != IcantpyRuntimeContract.ABI_MAJOR) {
            throw IllegalStateException("payload ${artifact.version} requires host ABI $abi, host supports $HOST_ABI")
        }
    }

    private fun validateMetadata(size: Long?, sha256: String?, label: String) {
        if (size != null && size <= 0) throw IllegalStateException("$label size must be positive")
        if (sha256 != null && !sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw IllegalStateException("$label sha256 must be a 64-character hexadecimal digest")
        }
    }

    private fun validateFileName(file: String, label: String) {
        if (!file.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.jar"))) {
            throw IllegalStateException("$label is not a safe jar filename")
        }
    }

    private fun validateUrl(url: String, label: String) {
        val scheme = runCatching { URI.create(url).scheme?.lowercase() }.getOrNull()
        if (scheme !in setOf("http", "https")) {
            throw IllegalStateException("$label must use HTTP or HTTPS")
        }
    }

    private fun replaceAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    data class ResolvedArtifact(
        val version: String,
        val file: String,
        val url: String,
        val minecraftVersion: String,
        val hostAbi: String? = null,
        val requiredCapabilities: Set<String> = emptySet(),
        val size: Long? = null,
        val sha256: String? = null,
    )

    class Manifest {
        var latest: String? = null
        var file: String? = null
        var url: String? = null
        var minecraft: Map<String, VersionArtifact>? = null
        var loaderVersion: String? = null
        var hostAbi: String? = null
        var platformId: String? = null
        var requiredCapabilities: Set<String>? = null
        var size: Long? = null
        var sha256: String? = null
    }

    class VersionArtifact {
        var latest: String? = null
        var file: String? = null
        var url: String? = null
        var hostAbi: String? = null
        var requiredCapabilities: Set<String>? = null
        var size: Long? = null
        var sha256: String? = null
    }

    class Installed {
        var version: String? = null
        var file: String? = null
        var minecraft: String? = null
        var size: Long? = null
        var sha256: String? = null
    }
}
