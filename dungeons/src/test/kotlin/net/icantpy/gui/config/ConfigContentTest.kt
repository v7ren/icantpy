package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigContentTest {
    @Test
    fun everyPageHasAsManyBodiesAsSubtabs() {
        ConfigPage.entries.forEach { page ->
            val expected = page.subtabs.size.coerceAtLeast(1)
            assertEquals(expected, ConfigContents.of(page).size, "subtab count mismatch for $page")
        }
    }

    @Test
    fun everyBodyIsDispatchedExactlyOnce() {
        val dispatched = ConfigPage.entries.flatMap { ConfigContents.of(it) }
        assertEquals(dispatched.size, dispatched.toSet().size, "a body is drawn by more than one tab")
        assertEquals(ConfigContent.entries.toSet(), dispatched.toSet(), "a body has no page")
    }

    @Test
    fun everySubtabIndexResolvesToItsOwnBody() {
        ConfigPage.entries.forEach { page ->
            val bodies = ConfigContents.of(page)
            bodies.indices.forEach { index ->
                assertEquals(bodies[index], ConfigContents.at(page, index), "$page subtab $index")
            }
        }
    }

    @Test
    fun outOfRangeSubtabsClampToTheNearestBody() {
        ConfigPage.entries.forEach { page ->
            val bodies = ConfigContents.of(page)
            assertEquals(bodies.first(), ConfigContents.at(page, -3))
            assertEquals(bodies.last(), ConfigContents.at(page, 99))
            assertTrue(ConfigContents.at(page, Int.MAX_VALUE) in bodies)
        }
    }

    @Test
    fun leapTabsMapToTheirOwnBodies() {
        val bodies = (0..5).map { ConfigContents.at(ConfigPage.LEAP, it) }
        assertEquals(
            listOf(
                ConfigContent.LEAP_ROUTES,
                ConfigContent.LEAP_LAYOUT,
                ConfigContent.LEAP_LOOK,
                ConfigContent.LEAP_QUICK_LEAP,
                ConfigContent.LEAP_KEYBINDS,
                ConfigContent.LEAP_DEBUG,
            ),
            bodies,
        )
    }
}
