package net.icantpy.loader

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcantpyLoaderTest {
    @Test
    fun payloadFileNameUsesVersion() {
        assertEquals("icantpy-1.0.0.jar", IcantpyLoaderPaths.payloadFileName("1.0.0"))
        assertEquals("icantpy-1.2.3-beta.jar", IcantpyLoaderPaths.payloadFileName("1.2.3-beta"))
    }

    @Test
    fun reloadCommandClassification() {
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify(".icantpy reload"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify(".icantpy reload now"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify(",icantpy reload"))
        assertEquals(IcantpyChatAction.TOGGLE, IcantpyChatCommands.classify(".icantpy reload toggle"))
        assertEquals(IcantpyChatAction.UNKNOWN_RELOAD, IcantpyChatCommands.classify(".icantpy reload xyz"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify(".icantpy"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify(".icantpygui"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify(".crypt reload"))
        assertEquals(IcantpyChatAction.TOGGLE, IcantpyChatCommands.classify(".crypt reload toggle"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify(".crypt"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify("hello"))
        assertEquals(".icantpy", IcantpyChatCommands.fromSlashCommand("icantpy"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify("/icantpy reload"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify("icantpy reload"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify("/icantpy reload now"))
        assertEquals(IcantpyChatAction.TOGGLE, IcantpyChatCommands.classify("/icantpy reload toggle"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify(", icantpy reload"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify("crypt"))
        assertEquals(IcantpyChatAction.FORWARD, IcantpyChatCommands.classify("crypt reload"))
        assertEquals(IcantpyChatAction.RELOAD, IcantpyChatCommands.classify("/crypt reload"))
        assertEquals(".icantpy wp add /pc hello", IcantpyChatCommands.fromSlashCommand("icantpy wp add /pc hello"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage("/icantpy"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage("/icantpy gui"))
        assertEquals(".crypt reload", IcantpyChatCommands.fromSlashCommand("crypt reload"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage(".icantpy"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage(".icantpygui"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage("icantpy"))
        assertTrue(IcantpyChatCommands.isOpenGuiMessage(IcantpyChatCommands.fromSlashCommand("icantpy")))
        assertFalse(IcantpyChatCommands.isOpenGuiMessage(".icantpy reload"))
        assertFalse(IcantpyChatCommands.isOpenGuiMessage("/icantpy reload"))
        assertFalse(IcantpyChatCommands.isOpenGuiMessage("crypt"))
        assertFalse(IcantpyChatCommands.isOpenGuiMessage("hello"))
    }

    @Test
    fun payloadJarMustContainIcantpyEntrypoint() {
        val dir = Files.createTempDirectory("icantpy-payload")
        val valid = dir.resolve("valid.jar")
        ZipOutputStream(Files.newOutputStream(valid)).use { zip ->
            zip.putNextEntry(ZipEntry("net/icantpy/Icantpy.class"))
            zip.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            zip.closeEntry()
        }
        val invalid = dir.resolve("invalid.jar")
        ZipOutputStream(Files.newOutputStream(invalid)).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        assertTrue(IcantpyPayloadManager.isPayloadJar(valid))
        assertFalse(IcantpyPayloadManager.isPayloadJar(invalid))
    }
}
