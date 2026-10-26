package net.icantpy.gui.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.icantpy.dungeon.leap.LeapTriggerEvent
import java.nio.file.Files

/** The Spirit Leap debug page's last choices, kept in config/icantpy-debug-ui.json so they survive reloads and restarts. */
internal object DebugUiMemory {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false

    var fireEvent: LeapTriggerEvent = LeapTriggerEvent.SECRETS_REACHED
        set(value) {
            field = value
            save()
        }

    var fireValue: String = ""
        set(value) {
            if (field == value) return
            field = value
            save()
        }

    var bossEvent: LeapTriggerEvent = LeapTriggerEvent.BOSS_STORM_END
        set(value) {
            field = value
            save()
        }

    /** Reads the file once; call before the first use. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val path = path()
            if (!Files.isRegularFile(path)) return
            val root = JsonParser.parseString(Files.readString(path)) as? JsonObject ?: return
            root.get("fireEvent")?.asString?.let { name -> LeapTriggerEvent.entries.firstOrNull { it.name == name } }?.let { fireEvent = it }
            root.get("fireValue")?.asString?.let { fireValue = it }
            root.get("bossEvent")?.asString?.let { name -> LeapTriggerEvent.entries.firstOrNull { it.name == name } }?.let { bossEvent = it }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read debug ui memory; using defaults", exception)
        }
    }

    private fun save() {
        if (!loaded) return
        try {
            val path = path()
            Files.createDirectories(path.parent)
            val body = gson.toJson(
                mapOf("fireEvent" to fireEvent.name, "fireValue" to fireValue, "bossEvent" to bossEvent.name),
            ) + "\n"
            Files.writeString(path, body)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write debug ui memory", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-debug-ui.json")
}
