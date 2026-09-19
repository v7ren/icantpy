package net.icantpy.gui.config

import net.icantpy.api.IcantpyClientActions
import net.icantpy.gui.IcantpyGui
import net.icantpy.qol.shards.ShardChecklistCodec
import net.icantpy.qol.shards.ShardChecklistController
import net.icantpy.qol.shards.ShardDefinition
import net.icantpy.qol.shards.ShardMath
import net.icantpy.qol.shards.ShardProgress
import net.minecraft.client.Minecraft

/** The shards view embedded in the shared config screen. */
internal class ShardChecklistPage {
    var search: String = ""
    private var missingOnly = false

    fun draw(
        draw: MenuDraw,
        metrics: ConfigMetrics,
        startY: Int,
        focused: String?,
        toast: (String) -> Unit,
        resetScroll: () -> Unit,
    ): Int {
        val left = metrics.innerLeft
        val width = metrics.innerWidth.coerceAtLeast(160)
        var y = startY
        draw.heading("Shard checklist", left, y)
        y += 22
        val searchW = (width - 8).coerceAtLeast(80)
        draw.field("shards:search", search, "Search shard or ability", left, y, searchW, focused == "shards:search")
        y += 30
        var chipX = left
        chipX += draw.chip("All", chipX, y, !missingOnly) { missingOnly = false; resetScroll() } + 6
        draw.chip("Missing", chipX, y, missingOnly) { missingOnly = true; resetScroll() }
        y += 30

        val definitions = trackedDefinitions()
        val filtered = definitions.filter { definition ->
            val query = search.trim().lowercase()
            val matches = query.isEmpty() || definition.displayName.lowercase().contains(query) || definition.abilityName.lowercase().contains(query)
            val progress = ShardChecklistController.progress(definition.displayName)
            matches && (!missingOnly || progress?.level == null || progress.level < target(definition))
        }
        val entries = ShardChecklistController.checklist().shards
        val unknown = definitions.count { ShardChecklistController.progress(it.displayName)?.level == null }
        val unlocked = definitions.count { (ShardChecklistController.progress(it.displayName)?.level ?: 0) > 0 }
        val atTarget = definitions.count { (ShardChecklistController.progress(it.displayName)?.level ?: 0) >= target(it) }
        val summary = "${entries.size} tracked • $unlocked unlocked • $atTarget at target • $unknown unscanned"
        draw.wrap(summary, width, draw.smallFont).forEach { line ->
            draw.text(line, left, y, draw.palette.secondary, draw.smallFont)
            y += draw.lineHeight(draw.smallFont)
        }
        y += 8

        y = actionRow(draw, left, y, width, toast, resetScroll)
        y += 8
        draw.wrap("Scan the Advanced Menu and Hunting Box pages to update observed levels.", width, draw.microFont).forEach { line ->
            draw.text(line, left, y, draw.palette.tertiary, draw.microFont)
            y += draw.lineHeight(draw.microFont)
        }
        y += 6
        filtered.forEach { definition ->
            val rowH = drawRow(draw, definition, left, y, width) { ShardChecklistController.buy(definition) }
            y += rowH + 6
        }
        if (filtered.isEmpty()) {
            draw.text("No matching shards", left, y, draw.palette.tertiary, draw.descFont)
            y += 24
        }
        return y
    }

    private fun drawRow(draw: MenuDraw, definition: ShardDefinition, x: Int, y: Int, w: Int, buy: () -> Unit): Int {
        val progress = ShardChecklistController.progress(definition.displayName)
        val target = target(definition)
        val level = progress?.level?.toString() ?: "?"
        val needed = ShardMath.stillToObtain(definition, progress ?: ShardProgress(), target)?.toString() ?: "?"
        val max = ShardMath.neededToMax(definition, progress ?: ShardProgress(), target)?.toString() ?: "?"
        val buttonW = draw.measure("Bazaar", draw.buttonFont) + 20
        val stats = draw.wrap("Lv $level/$target  •  To max: $max  •  To get: $needed", w - 20, draw.microFont)
        val statsPitch = draw.lineHeight(draw.microFont)
        val cardH = 52 + stats.size * statsPitch
        draw.round(x, y, w, cardH, draw.chrome.cardRadius, draw.palette.group)
        draw.outline(x, y, w, cardH, draw.chrome.cardRadius, draw.palette.line)
        val textW = (w - buttonW - 28).coerceAtLeast(60)
        draw.text(draw.fit(definition.displayName, textW, draw.bodyFont), x + 10, y + 8, draw.palette.text, draw.bodyFont)
        draw.text(draw.fit(definition.abilityName, w - 20, draw.smallFont), x + 10, y + 26, draw.palette.secondary, draw.smallFont)
        stats.forEachIndexed { index, line ->
            draw.text(line, x + 10, y + 44 + index * statsPitch, draw.palette.secondary, draw.microFont)
        }
        draw.button("Bazaar", x + w - buttonW - 10, y + 7, ButtonStyle.GHOST, buy)
        return cardH
    }

    private fun actionRow(draw: MenuDraw, x: Int, y: Int, width: Int, toast: (String) -> Unit, resetScroll: () -> Unit): Int {
        var cy = y
        var cx = x
        val labels = listOf("Import", "Export", "Default", "AI prompt", "Reset scan", "/am", "/huntingbox")
        labels.forEach { label ->
            val buttonW = draw.measure(label, draw.buttonFont) + 20
            if (cx != x && cx + buttonW > x + width) {
                cy += 28
                cx = x
            }
            draw.button(label, cx, cy, if (label == "Reset scan") ButtonStyle.DANGER else ButtonStyle.GHOST) {
                performAction(label, toast, resetScroll)
            }
            cx += buttonW + 6
        }
        return cy + 28
    }

    private fun performAction(label: String, toast: (String) -> Unit, resetScroll: () -> Unit) {
        val keyboard = Minecraft.getInstance().keyboardHandler
        when (label) {
            "Import", "Default" -> {
                val text = if (label == "Import") keyboard.clipboard else
                    ShardChecklistCodec.encode(ShardChecklistController.defaultPreset())
                val result = ShardChecklistController.importText(text)
                toast(result.error ?: if (label == "Import") "Imported checklist" else "Default preset loaded")
                if (result.checklist != null) resetScroll()
            }
            "Export" -> {
                keyboard.clipboard = ShardChecklistController.exportText()
                toast("Exported checklist")
            }
            "AI prompt" -> {
                keyboard.clipboard = aiPrompt()
                toast("AI prompt copied")
            }
            "Reset scan" -> {
                ShardChecklistController.resetProgress()
                toast("Scan reset")
            }
            "/am", "/huntingbox" -> {
                IcantpyGui.closeIfOpen()
                IcantpyClientActions.sendCommand(label.removePrefix("/"))
            }
        }
    }

    private fun trackedDefinitions(): List<ShardDefinition> = ShardChecklistController.definitions().filter { definition ->
        ShardChecklistController.checklist().shards.any { ShardChecklistCodec.normalizeName(it.name) == ShardChecklistCodec.normalizeName(definition.displayName) }
    }

    private fun target(definition: ShardDefinition): Int = ShardChecklistController.checklist().shards.firstOrNull {
        ShardChecklistCodec.normalizeName(it.name) == ShardChecklistCodec.normalizeName(definition.displayName)
    }?.targetLevel ?: definition.maxLevel

    private fun aiPrompt(): String = "Convert the following user bullet list into raw JSON only. A mob appears inside the first parentheses; use that mob's name as the shard name. Default targetLevel to 10. Schema: {\"version\":1,\"name\":\"My shards\",\"shards\":[{\"name\":\"Flash\",\"targetLevel\":10}]}\n[Paste your bullet list here]"
}
