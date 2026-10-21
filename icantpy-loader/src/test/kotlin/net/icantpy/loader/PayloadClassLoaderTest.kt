package net.icantpy.loader

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PayloadClassLoaderTest {
    @Test
    fun skiaAndSkikoAreChildFirstOtherPackagesAreNot() {
        assertTrue(PayloadClassLoader.isChildFirst("org.jetbrains.skia.FontStyle"))
        assertTrue(PayloadClassLoader.isChildFirst("org.jetbrains.skiko.Library"))
        assertFalse(PayloadClassLoader.isChildFirst("org.jetbrains.skiax.Fake"))
        assertFalse(PayloadClassLoader.isChildFirst("kotlin.Unit"))
        assertFalse(PayloadClassLoader.isChildFirst("net.icantpy.api.IcantpyPayload"))
    }

    @Test
    fun payloadCopyOfSkiaWinsOverAnOlderCopyInTheGameClassLoader() {
        val gameDir = compile("game")
        val payloadDir = compile("payload")
        URLClassLoader(arrayOf(gameDir.toUri().toURL()), null).use { game ->
            PayloadClassLoader(arrayOf(payloadDir.toUri().toURL()), game).use { payload ->
                assertEquals("payload", origin(payload, "org.jetbrains.skia.Probe"))
                // Everything else keeps the shared, parent-first copy (bridge API, Kotlin, Minecraft).
                assertEquals("game", origin(payload, "demo.Shared"))
            }
        }
    }

    @Test
    fun childFirstFallsBackToParentWhenPayloadLacksTheClass() {
        val gameDir = compile("game")
        val emptyPayload = Files.createTempDirectory("icantpy-empty-payload")
        URLClassLoader(arrayOf(gameDir.toUri().toURL()), null).use { game ->
            PayloadClassLoader(arrayOf(emptyPayload.toUri().toURL()), game).use { payload ->
                assertEquals("game", origin(payload, "org.jetbrains.skia.Probe"))
            }
        }
    }

    private fun origin(loader: ClassLoader, name: String): String =
        loader.loadClass(name).getField("ORIGIN").get(null) as String

    private fun compile(origin: String): Path {
        val src = Files.createTempDirectory("icantpy-cl-src-$origin")
        val out = Files.createTempDirectory("icantpy-cl-out-$origin")
        val probe = src.resolve("org/jetbrains/skia/Probe.java")
        val shared = src.resolve("demo/Shared.java")
        Files.createDirectories(probe.parent)
        Files.createDirectories(shared.parent)
        Files.writeString(probe, "package org.jetbrains.skia; public class Probe { public static final String ORIGIN = \"$origin\"; }")
        Files.writeString(shared, "package demo; public class Shared { public static final String ORIGIN = \"$origin\"; }")
        val compiler = ToolProvider.getSystemJavaCompiler()
        val status = compiler.run(null, null, null, "-d", out.toString(), probe.toString(), shared.toString())
        assertEquals(0, status, "test fixture failed to compile")
        return out
    }
}
