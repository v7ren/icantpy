package net.icantpy.modules.impl.timer

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.icantpy.gui.configUI.GuiLookSettings
import net.icantpy.gui.configUI.HudOverlaySettings
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrientSettings
import net.icantpy.modules.impl.stats.StatsArmorSettings
import net.icantpy.modules.impl.waypoint.CommandWaypoint
import net.icantpy.modules.impl.waypoint.WaypointWorld
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

object IcantpySettingsStore {
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    fun load(): TimerSettings {
        val path = path()
        if (!Files.isRegularFile(path)) return TimerSettings()
        return try {
            val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
            val defaults = TimerSettings()
            TimerSettings(
                look = if (obj.has("gui") && obj.get("gui").isJsonObject) {
                    GuiLookSettings.fromJson(obj.getAsJsonObject("gui"))
                } else {
                    defaults.look
                },
                hud = if (obj.has("hud") && obj.get("hud").isJsonObject) {
                    HudOverlaySettings.fromJson(obj.getAsJsonObject("hud"))
                } else {
                    defaults.hud
                },
                displayInTicks = bool(obj, "displayInTicks", defaults.displayInTicks),
                showPrefix = bool(obj, "showPrefix", defaults.showPrefix),
                showSuffix = bool(obj, "showSuffix", defaults.showSuffix),
                debugMode = bool(obj, "debugMode", defaults.debugMode),
                necronHud = bool(obj, "necronHud", defaults.necronHud),
                goldorHud = bool(obj, "goldorHud", defaults.goldorHud),
                startTimer = bool(obj, "startTimer", defaults.startTimer),
                padHud = bool(obj, "padHud", defaults.padHud),
                lightningHud = bool(obj, "lightningHud", defaults.lightningHud),
                pyHud = bool(obj, "pyHud", defaults.pyHud),
                stormTickHud = bool(obj, "stormTickHud", defaults.stormTickHud),
                secretsHud = bool(obj, "secretsHud", defaults.secretsHud),
                necronX = int(obj, "necronX", defaults.necronX),
                necronY = int(obj, "necronY", defaults.necronY),
                goldorX = int(obj, "goldorX", defaults.goldorX),
                goldorY = int(obj, "goldorY", defaults.goldorY),
                padX = int(obj, "padX", defaults.padX),
                padY = int(obj, "padY", defaults.padY),
                lightningX = int(obj, "lightningX", defaults.lightningX),
                lightningY = int(obj, "lightningY", defaults.lightningY),
                pyX = int(obj, "pyX", defaults.pyX),
                pyY = int(obj, "pyY", defaults.pyY),
                stormTickX = int(obj, "stormTickX", defaults.stormTickX),
                stormTickY = int(obj, "stormTickY", defaults.stormTickY),
                secretsX = int(obj, "secretsX", defaults.secretsX),
                secretsY = int(obj, "secretsY", defaults.secretsY),
                notifyX = int(obj, "notifyX", defaults.notifyX),
                notifyY = int(obj, "notifyY", defaults.notifyY),
                triggers = triggers(obj),
                waypointsEnabled = bool(obj, "waypointsEnabled", defaults.waypointsEnabled),
                showWaypoints = bool(obj, "showWaypoints", defaults.showWaypoints),
                waypoints = waypoints(obj),
                leap = if (obj.has("leap") && obj.get("leap").isJsonObject) {
                    LeapOrientSettings.fromJson(obj.getAsJsonObject("leap"))
                } else {
                    defaults.leap
                },
                statsArmor = if (obj.has("statsArmor") && obj.get("statsArmor").isJsonObject) {
                    StatsArmorSettings.fromJson(obj.getAsJsonObject("statsArmor"))
                } else {
                    defaults.statsArmor
                },
            )
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read icantpy settings; using defaults", exception)
            TimerSettings()
        }
    }

    fun save(settings: TimerSettings) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val obj = JsonObject()
            obj.add("gui", GuiLookSettings.toJson(settings.look))
            obj.add("hud", HudOverlaySettings.toJson(settings.hud))
            obj.addProperty("displayInTicks", settings.displayInTicks)
            obj.addProperty("showPrefix", settings.showPrefix)
            obj.addProperty("showSuffix", settings.showSuffix)
            obj.addProperty("debugMode", settings.debugMode)
            obj.addProperty("necronHud", settings.necronHud)
            obj.addProperty("goldorHud", settings.goldorHud)
            obj.addProperty("startTimer", settings.startTimer)
            obj.addProperty("padHud", settings.padHud)
            obj.addProperty("lightningHud", settings.lightningHud)
            obj.addProperty("pyHud", settings.pyHud)
            obj.addProperty("stormTickHud", settings.stormTickHud)
            obj.addProperty("secretsHud", settings.secretsHud)
            obj.addProperty("necronX", settings.necronX)
            obj.addProperty("necronY", settings.necronY)
            obj.addProperty("goldorX", settings.goldorX)
            obj.addProperty("goldorY", settings.goldorY)
            obj.addProperty("padX", settings.padX)
            obj.addProperty("padY", settings.padY)
            obj.addProperty("lightningX", settings.lightningX)
            obj.addProperty("lightningY", settings.lightningY)
            obj.addProperty("pyX", settings.pyX)
            obj.addProperty("pyY", settings.pyY)
            obj.addProperty("stormTickX", settings.stormTickX)
            obj.addProperty("stormTickY", settings.stormTickY)
            obj.addProperty("secretsX", settings.secretsX)
            obj.addProperty("secretsY", settings.secretsY)
            obj.addProperty("notifyX", settings.notifyX)
            obj.addProperty("notifyY", settings.notifyY)
            val triggerArray = JsonArray()
            settings.triggers.forEach { trigger ->
                val item = JsonObject()
                item.addProperty("id", trigger.id)
                item.addProperty("clock", trigger.clock.name)
                item.addProperty("ticks", trigger.ticks)
                item.addProperty("message", trigger.message)
                item.addProperty("durationTicks", trigger.durationTicks)
                item.addProperty("enabled", trigger.enabled)
                item.addProperty("sound", trigger.sound)
                triggerArray.add(item)
            }
            obj.add("triggers", triggerArray)
            obj.addProperty("waypointsEnabled", settings.waypointsEnabled)
            obj.addProperty("showWaypoints", settings.showWaypoints)
            val waypointArray = JsonArray()
            settings.waypoints.forEach { waypoint ->
                val item = JsonObject()
                item.addProperty("id", waypoint.id)
                item.addProperty("x", waypoint.x)
                item.addProperty("y", waypoint.y)
                item.addProperty("z", waypoint.z)
                item.addProperty("command", waypoint.command)
                item.addProperty("name", waypoint.name)
                item.addProperty("enabled", waypoint.enabled)
                item.addProperty("once", waypoint.once)
                item.addProperty("world", waypoint.world)
                waypointArray.add(item)
            }
            obj.add("waypoints", waypointArray)
            obj.add("leap", LeapOrientSettings.toJson(settings.leap))
            obj.add("statsArmor", settings.statsArmor.toJson())
            Files.writeString(path, GSON.toJson(obj) + "\n")
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write icantpy settings", exception)
        }
    }

    private fun path(): Path = FabricLoader.getInstance().configDir.resolve("icantpy.json")

    private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
        if (obj.has(key)) obj.get(key).asBoolean else default

    private fun int(obj: JsonObject, key: String, default: Int): Int =
        if (obj.has(key)) obj.get(key).asInt else default

    private fun string(obj: JsonObject, key: String, default: String): String =
        if (obj.has(key)) obj.get(key).asString else default

    private fun waypoints(obj: JsonObject): List<CommandWaypoint> {
        if (!obj.has("waypoints") || !obj.get("waypoints").isJsonArray) return emptyList()
        return obj.getAsJsonArray("waypoints").mapNotNull { element ->
            try {
                val item = element.asJsonObject
                CommandWaypoint(
                    id = string(item, "id", UUID.randomUUID().toString()),
                    x = int(item, "x", 0),
                    y = int(item, "y", 0),
                    z = int(item, "z", 0),
                    command = string(item, "command", ""),
                    name = string(item, "name", ""),
                    enabled = bool(item, "enabled", true),
                    once = bool(item, "once", false),
                    world = WaypointWorld.parse(string(item, "world", WaypointWorld.ANY)).key,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun triggers(obj: JsonObject): List<TimerTrigger> {
        if (!obj.has("triggers") || !obj.get("triggers").isJsonArray) return emptyList()
        return obj.getAsJsonArray("triggers").mapNotNull { element ->
            try {
                val item = element.asJsonObject
                val clock = TimerClock.valueOf(string(item, "clock", TimerClock.PAD.name))
                TimerTrigger(
                    id = string(item, "id", UUID.randomUUID().toString()),
                    clock = clock,
                    ticks = int(item, "ticks", 0).coerceIn(-72_000, 72_000),
                    message = string(item, "message", ""),
                    durationTicks = int(item, "durationTicks", TimerTrigger.DEFAULT_DURATION_TICKS).coerceAtLeast(1),
                    enabled = bool(item, "enabled", true),
                    sound = string(item, "sound", ""),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
