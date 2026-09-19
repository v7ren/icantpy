package net.icantpy.dungeon.timer

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertSoundsTest {
    @Test
    fun cyclesBuiltInCuesThenFiles() {
        val dir = Files.createTempDirectory("icantpy-sounds")
        Files.writeString(dir.resolve("horn.wav"), "x")
        Files.writeString(dir.resolve("readme.txt"), "no")
        assertEquals(listOf("horn.wav"), AlertSounds.files(dir))
        assertEquals("pling", AlertSounds.next("", dir))
        assertEquals("orb", AlertSounds.next("pling", dir))
        assertEquals("horn.wav", AlertSounds.next("anvil", dir))
        assertEquals("", AlertSounds.next("horn.wav", dir))
        assertEquals("Default", AlertSounds.triggerLabel(""))
        assertEquals("None", AlertSounds.triggerLabel("none"))
        assertEquals("none", AlertSounds.nextTrigger("", dir))
    }

    @Test
    fun resolveRejectsPathEscape() {
        val dir = Files.createTempDirectory("icantpy-sounds")
        Files.writeString(dir.resolve("ok.wav"), "x")
        assertEquals("ok.wav", AlertSounds.resolve("ok.wav", dir)?.fileName?.toString())
        assertNull(AlertSounds.resolve("../ok.wav", dir))
        assertTrue(AlertSounds.options(dir).contains("pling"))
    }
}
