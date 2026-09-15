package net.icantpy.loader

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcantpyPayloadUpdaterTest {
    @Test
    fun requiresLatestFileAndUrl() {
        assertFailsWith<IllegalStateException> {
            IcantpyPayloadUpdater.validate(IcantpyPayloadUpdater.Manifest())
        }
        IcantpyPayloadUpdater.validate(
            IcantpyPayloadUpdater.Manifest().apply {
                latest = "1.0.0"
                file = "icantpy-1.0.0.jar"
                url = "https://mod.v7ren.com/icantpy/icantpy-1.0.0.jar"
            },
        )
    }

    @Test
    fun downloadsWhenVersionOrFileChanges() {
        val installed = IcantpyPayloadUpdater.Installed().apply {
            version = "1.0.0"
            file = "icantpy-1.0.0.jar"
            minecraft = "26.2"
        }
        val current = IcantpyPayloadUpdater.ResolvedArtifact(
            "1.0.0",
            "icantpy-1.0.0.jar",
            "https://example/icantpy-1.0.0.jar",
            "26.2",
        )
        assertFalse(
            IcantpyPayloadUpdater.needsDownload(
                payloadExists = true,
                installed = installed,
                artifact = current,
            ),
        )
        assertTrue(
            IcantpyPayloadUpdater.needsDownload(
                payloadExists = true,
                installed = installed,
                artifact = current.copy(version = "1.0.1", file = "icantpy-1.0.1.jar"),
            ),
        )
        assertTrue(
            IcantpyPayloadUpdater.needsDownload(
                payloadExists = false,
                installed = installed,
                artifact = current,
            ),
        )
        assertTrue(
            IcantpyPayloadUpdater.needsDownload(
                payloadExists = true,
                installed = installed.apply { sha256 = "old" },
                artifact = current.copy(sha256 = "new"),
            ),
        )
    }

    @Test
    fun pickArtifactUsesTopLevelFor26_2() {
        val manifest = IcantpyPayloadUpdater.Manifest().apply {
            latest = "1.0.0"
            file = "icantpy-1.0.0.jar"
            url = "https://mod.v7ren.com/icantpy/icantpy-1.0.0.jar"
        }
        val artifact = IcantpyPayloadUpdater.pickArtifact(manifest, "26.2")
        assertEquals("1.0.0", artifact.version)
        assertEquals("icantpy-1.0.0.jar", artifact.file)
        assertEquals("26.2", artifact.minecraftVersion)
    }

    @Test
    fun prefersLocalUpdateServerBeforePublicUrl() {
        assertEquals(
            listOf(
                "http://127.0.0.1:8965/icantpy/icantpy-1.0.0.jar",
                "https://mod.v7ren.com/icantpy/icantpy-1.0.0.jar",
            ),
            IcantpyPayloadUpdater.payloadDownloadUrls(
                "icantpy-1.0.0.jar",
                "https://mod.v7ren.com/icantpy/icantpy-1.0.0.jar",
            ),
        )
    }

    @Test
    fun pickArtifactUsesPerVersionOverride() {
        val manifest = IcantpyPayloadUpdater.Manifest().apply {
            latest = "1.0.0"
            file = "icantpy-1.0.0.jar"
            url = "https://mod.v7ren.com/icantpy/icantpy-1.0.0.jar"
            minecraft = mapOf(
                "26.1.2" to IcantpyPayloadUpdater.VersionArtifact().apply {
                    file = "icantpy-1.0.0-mc26.1.2.jar"
                    url = "https://mod.v7ren.com/icantpy/icantpy-1.0.0-mc26.1.2.jar"
                },
            )
        }
        val artifact = IcantpyPayloadUpdater.pickArtifact(manifest, "26.1.2")
        assertEquals("1.0.0", artifact.version)
        assertEquals("icantpy-1.0.0-mc26.1.2.jar", artifact.file)
        assertEquals("https://mod.v7ren.com/icantpy/icantpy-1.0.0-mc26.1.2.jar", artifact.url)
        assertEquals("26.1.2", artifact.minecraftVersion)
    }

    @Test
    fun rangeWindowsStayUnderCloudflareLimit() {
        val windows = IcantpyPayloadUpdater.rangeWindows(172_048_592L)
        assertEquals(6, windows.size)
        assertEquals(0L..33_554_431L, windows.first())
        assertEquals(167_772_160L..172_048_591L, windows.last())
        assertTrue(windows.all { it.last - it.first + 1 <= IcantpyPayloadUpdater.DOWNLOAD_CHUNK_BYTES })
    }

    @Test
    fun parseContentRangeTotalReadsFileSize() {
        assertEquals(172_048_592L, IcantpyPayloadUpdater.parseContentRangeTotal("bytes 0-33554431/172048592"))
        assertEquals(null, IcantpyPayloadUpdater.parseContentRangeTotal("bytes 0-10/*"))
    }

    @Test
    fun artifactRequirementsAreValidatedBeforeSelection() {
        val incompatibleAbi = IcantpyPayloadUpdater.Manifest().apply {
            latest = "1.0.0"
            file = "icantpy-1.0.0.jar"
            url = "https://example/icantpy-1.0.0.jar"
            hostAbi = "2.0"
        }
        assertFailsWith<IllegalStateException> {
            IcantpyPayloadUpdater.pickArtifact(incompatibleAbi, "26.2")
        }

        val missingCapability = IcantpyPayloadUpdater.Manifest().apply {
            latest = "1.0.0"
            file = "icantpy-1.0.0.jar"
            url = "https://example/icantpy-1.0.0.jar"
            requiredCapabilities = setOf("startup.never-installed")
        }
        assertFailsWith<IllegalStateException> {
            IcantpyPayloadUpdater.pickArtifact(missingCapability, "26.2")
        }
    }

    @Test
    fun artifactHashAndSizeAreCopiedFromManifest() {
        val manifest = IcantpyPayloadUpdater.Manifest().apply {
            latest = "1.0.0"
            file = "icantpy-1.0.0.jar"
            url = "https://example/icantpy-1.0.0.jar"
            size = 42
            sha256 = "a".repeat(64)
        }
        val artifact = IcantpyPayloadUpdater.pickArtifact(manifest, "26.2")

        assertEquals(42, artifact.size)
        assertEquals("a".repeat(64), artifact.sha256)
    }

    @Test
    fun sha256UsesContentNotFilename() {
        val file = Files.createTempFile("icantpy-hash", ".jar")
        Files.writeString(file, "stable payload")
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("stable payload".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        assertEquals(expected, IcantpyPayloadUpdater.sha256(file))
    }
}
