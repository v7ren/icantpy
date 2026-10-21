package net.icantpy.qol.shards

import net.icantpy.api.IcantpyClientActions
import net.icantpy.api.IcantpyCommandPrefix
import net.minecraft.client.Minecraft
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import net.icantpy.gui.McUi
import net.icantpy.gui.IcantpyGui
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.icantpy.cosmetics.items.HypixelItemData
import net.minecraft.core.component.DataComponents

object ShardChecklistController {
    private var catalog: List<ShardDefinition> = emptyList()
    private var checklist = ShardChecklist("My shards", emptyList())
    private var openNextTick = false
    private val session = ShardSession()
    private var configPath: Path? = null
    private var scanTicks = 0

    fun configure(definitions: Collection<ShardDefinition>, loaded: ShardChecklist? = null) {
        catalog = definitions.toList()
        checklist = loaded ?: ShardChecklist("My shards", emptyList())
    }

    fun load() {
        configPath = FabricLoader.getInstance().configDir.resolve("icantpy-shards.json")
        catalog = ShardCatalog.definitions
        checklist = try { ShardChecklistStore.load(configPath!!, catalog) ?: ShardCatalog.defaultChecklist() }
        catch (e: Exception) { net.icantpy.Icantpy.LOGGER.warn("Failed to read shard checklist; original preserved", e); ShardCatalog.defaultChecklist() }
    }

    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw)?.trim() ?: return null
        if (!body.equals("shards", true)) return null
        openNextTick = true
        return ""
    }

    fun tick() {
        scanTicks = (scanTicks + 1) % 5
        // Container scanning only runs while the feature is on; the page still opens when disabled.
        if (scanTicks == 0 && checklist.enabled) scanMenu()
        if (!openNextTick) return
        openNextTick = false
        // The screen is intentionally opened from tick so outgoing chat cannot replace it.
        IcantpyGui.showShards()
    }

    private fun scanMenu() {
        val mc = Minecraft.getInstance()
        val screen = McUi.currentScreen(mc) as? AbstractContainerScreen<*> ?: return
        val title = screen.title.string
        if (!ShardMenuParser.isMenu(title)) return
        val player = mc.player ?: return
        val menu = screen.menu
        menu.slots.filter { it.index in 9..44 && it.index % 9 !in setOf(0, 8) && it.container !== player.inventory && !it.item.isEmpty }.forEach { slot ->
            val item = slot.item
            val rawName = HypixelItemData.raw(item, DataComponents.CUSTOM_NAME)?.string
                ?: HypixelItemData.raw(item, DataComponents.ITEM_NAME)?.string
                ?: item.getItemName().string
            val lore = HypixelItemData.raw(item, DataComponents.LORE)?.lines?.map { it.string }.orEmpty()
            val parsed = ShardMenuParser.parse(rawName, lore, HypixelItemData.skyblockId(item))
            parsed.observation?.let(session::merge)
        }
    }

    fun onIncomingChat(message: String) { if (session.shouldResetForChat(message)) session.resetObservations() }
    fun onDisconnect() { session.clear(); openNextTick = false }
    fun onUnload() { session.clear(); openNextTick = false; IcantpyGui.closeIfOpen() }
    fun checklist(): ShardChecklist = checklist

    fun enabled(): Boolean = checklist.enabled

    /** Persists the Attribute Shards master switch. Disabling stops container scanning and clears observations. */
    fun setEnabled(enabled: Boolean) {
        if (checklist.enabled == enabled) return
        val next = checklist.copy(enabled = enabled)
        val path = configPath
        if (path == null) {
            net.icantpy.Icantpy.LOGGER.warn("Shard checklist path unavailable; enabled flag kept for this session only")
        } else {
            try { ShardChecklistStore.save(path, next) }
            catch (e: Exception) { net.icantpy.Icantpy.LOGGER.warn("Could not save shard enabled flag", e) }
        }
        checklist = next
        if (!enabled) { session.clear(); openNextTick = false }
    }

    fun importText(text: String): ShardImportResult {
        val result = ShardChecklistCodec.decode(text, catalog)
        // Imports replace the list only; the local master switch is kept.
        val imported = result.checklist?.copy(enabled = checklist.enabled) ?: return result
        try { ShardChecklistStore.save(configPath ?: return ShardImportResult(error = "Checklist path unavailable"), imported) }
        catch (e: Exception) { return ShardImportResult(error = "Could not save checklist: ${e.message}") }
        checklist = imported
        return result
    }
    fun exportText(): String = ShardChecklistCodec.encode(checklist)
    fun definitions(): List<ShardDefinition> = catalog
    fun progress(name: String): ShardProgress? = session.get(name)
    fun resetProgress() = session.clear()
    fun defaultPreset(): ShardChecklist = ShardCatalog.defaultChecklist()
    fun setTracked(definition: ShardDefinition, tracked: Boolean): String {
        val next = ShardQuery.withTracked(checklist, definition, tracked)
        val path = configPath ?: return "Checklist path unavailable"
        try { ShardChecklistStore.save(path, next) }
        catch (e: Exception) { return "Could not save checklist: ${e.message}" }
        checklist = next
        return if (tracked) "Tracking ${definition.attributeLabel} (${definition.shardLabel})"
        else "Stopped tracking ${definition.attributeLabel}"
    }
    fun buy(definition: ShardDefinition) {
        IcantpyGui.closeIfOpen()
        IcantpyClientActions.sendCommand("bazaar ${definition.displayName} Shard")
    }
}
