package net.icantpy.qol.waypoint

import net.icantpy.dungeon.timer.TimerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandWaypointTest {
    @Test
    fun occupiesTheBlockThePlayerIsInAndTheBlockBelow() {
        val waypoint = CommandWaypoint("a", 10, 64, -4)
        assertTrue(waypoint.occupies(10.4, 64.0, -3.2))
        assertTrue(waypoint.occupies(10.4, 65.0, -3.2))
        assertFalse(waypoint.occupies(11.1, 64.0, -3.2))
        assertFalse(waypoint.occupies(10.4, 66.0, -3.2))
    }

    @Test
    fun slashCommandsAreSentAsCommands() {
        assertEquals(true to "pc hello", CommandWaypoint.parseDispatch("/pc hello"))
        assertEquals(true to "partychat \"message\"", CommandWaypoint.parseDispatch("/partychat \"message\""))
        assertEquals(false to "hello party", CommandWaypoint.parseDispatch("hello party"))
        assertNull(CommandWaypoint.parseDispatch("   "))
    }

    @Test
    fun settingsUpsertAndRemoveWaypoints() {
        val waypoint = CommandWaypoint.create(1, 2, 3, command = "/pc go")
        val settings = TimerSettings().upsertWaypoint(waypoint)
        assertEquals(1, settings.waypoints.size)
        assertEquals("/pc go", settings.waypoints[0].command)
        assertTrue(settings.removeWaypoint(waypoint.id).waypoints.isEmpty())
    }

    @Test
    fun chatCommandsParseWithoutAPlayer() {
        assertEquals(null, CommandWaypoints.handleCommand("hello"))
        assertTrue(CommandWaypoints.handleCommand(".icantpy wp")!!.contains("wp add"))
        assertEquals("no waypoints", CommandWaypoints.handleCommand(".icantpy wp list"))
        assertEquals("no waypoints", CommandWaypoints.handleCommand("/icantpy wp list"))
        assertEquals("no waypoints", CommandWaypoints.handleCommand("/crypt wp list"))
        assertEquals("wp list", net.icantpy.api.IcantpyCommandPrefix.body("/icantpy wp list"))
        assertEquals(".icantpy reload", net.icantpy.api.IcantpyCommandPrefix.canonical("/icantpy reload"))
        assertEquals(".neurename", net.icantpy.api.IcantpyCommandPrefix.canonical("/neurename"))
        assertEquals(".icantpy reload", net.icantpy.api.IcantpyCommandPrefix.canonical("icantpy reload"))
        assertEquals(".icantpy reload", net.icantpy.api.IcantpyCommandPrefix.canonical(", icantpy reload"))
        assertEquals(null, net.icantpy.api.IcantpyCommandPrefix.canonical("hello"))
        assertEquals(null, net.icantpy.api.IcantpyCommandPrefix.canonical("crypt"))
        assertEquals(null, net.icantpy.api.IcantpyCommandPrefix.canonical("crypt reload"))
        assertEquals("reload", net.icantpy.api.IcantpyCommandPrefix.body(net.icantpy.api.IcantpyCommandPrefix.canonical("/icantpy reload")!!))
        assertEquals(".icantpy wp add /pc hello", net.icantpy.api.IcantpyCommandPrefix.toDotMessage("icantpy", "wp add /pc hello"))
    }

    @Test
    fun anyWorldMatchesDungeonAndHub() {
        val any = CommandWaypoint.create(1, 2, 3, world = WaypointWorld.ANY)
        assertTrue(any.inWorld(WaypointWorld(WaypointWorld.DUNGEON)))
        assertTrue(any.inWorld(WaypointWorld(WaypointWorld.HUB)))
        assertTrue(any.inWorld(SkyblockWorlds.fromSidebar(listOf("Area: Crimson Isle"))))
    }

    @Test
    fun dungeonWaypointsIgnoreTheHub() {
        val dungeon = CommandWaypoint.create(1, 2, 3, world = "dungeon")
        assertTrue(dungeon.inWorld(SkyblockWorlds.fromSidebar(listOf("The Catacombs (F7)", "Cleared: 10%"))))
        assertFalse(dungeon.inWorld(SkyblockWorlds.fromSidebar(listOf("Area: Hub"))))
        assertFalse(dungeon.inWorld(SkyblockWorlds.fromSidebar(listOf("SkyBlock Hub"))))
    }

    @Test
    fun sidebarMapsHubIslandGardenAndNamedAreas() {
        assertEquals(WaypointWorld.HUB, SkyblockWorlds.fromSidebar(listOf("Area: Hub")).key)
        assertEquals(WaypointWorld.DUNGEON_HUB, SkyblockWorlds.fromSidebar(listOf("Area: Dungeon Hub")).key)
        assertEquals(WaypointWorld.ISLAND, SkyblockWorlds.fromSidebar(listOf("Area: Private Island")).key)
        assertEquals(WaypointWorld.GARDEN, SkyblockWorlds.fromSidebar(listOf("Area: Garden")).key)
        assertEquals("crimson_isle", SkyblockWorlds.fromSidebar(listOf("Area: Crimson Isle")).key)
        val crimson = CommandWaypoint.create(0, 0, 0, world = "crimson isle")
        assertTrue(crimson.inWorld(SkyblockWorlds.fromSidebar(listOf("Area: Crimson Isle"))))
        assertFalse(crimson.inWorld(SkyblockWorlds.fromSidebar(listOf("Area: Hub"))))
    }

    @Test
    fun worldCycleIncludesTheCurrentNamedArea() {
        assertEquals(WaypointWorld.DUNGEON, WaypointWorld.cycle(WaypointWorld.ANY))
        assertEquals(WaypointWorld.ANY, WaypointWorld.cycle(WaypointWorld.GARDEN))
        assertEquals("crimson_isle", WaypointWorld.cycle(WaypointWorld.GARDEN, extras = listOf("crimson_isle")))
        assertEquals(WaypointWorld.ANY, WaypointWorld.cycle("crimson_isle", extras = listOf("crimson_isle")))
        assertEquals(WaypointWorld.ANY, WaypointWorld.cycle("crimson_isle", extras = listOf("crimson_isle", "hub")))
    }

    @Test
    fun missingWorldDefaultsToAnySoOldWaypointsKeepFiring() {
        val waypoint = CommandWaypoint("old", 1, 2, 3)
        assertEquals(WaypointWorld.ANY, waypoint.world)
        assertTrue(waypoint.inWorld(WaypointWorld(WaypointWorld.HUB)))
    }
}
