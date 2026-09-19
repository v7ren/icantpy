package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigTabTest {
    @Test
    fun sidebarUsesDevonianStyleCategories() {
        assertEquals(
            listOf("Dungeons", "Cosmetics", "QoL", "Slayer", "GUI"),
            ConfigTab.entries.map { it.label() },
        )
        assertEquals(listOf("Timer", "Alerts", "Leap"), ConfigTab.DUNGEONS.pages())
        assertEquals(listOf("Items", "Morph"), ConfigTab.COSMETICS.pages())
        assertEquals(listOf("Camera", "Boxes", "Stats", "Combat", "Shards"), ConfigTab.QOL.pages())
        assertEquals(listOf("Carries"), ConfigTab.SLAYER.pages())
        assertEquals(listOf("Theme"), ConfigTab.GUI.pages())
    }

    @Test
    fun labelsStayUnique() {
        val labels = ConfigTab.entries.map { it.label() }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(ConfigTab.entries.size == 5)
    }
}
