package net.icantpy.gui.config

import net.icantpy.api.IcantpyClientActions
import net.icantpy.gui.IcantpyGui
import net.icantpy.qol.shards.ShardBrowseMode
import net.icantpy.qol.shards.ShardBrowseResult
import net.icantpy.qol.shards.ShardChecklistCodec
import net.icantpy.qol.shards.ShardChecklistController
import net.icantpy.qol.shards.ShardDefinition
import net.icantpy.qol.shards.ShardMath
import net.icantpy.qol.shards.ShardProgress
import net.icantpy.qol.shards.ShardQuery
import net.minecraft.client.Minecraft

/** The shards view embedded in the shared config screen. */
internal class ShardChecklistPage {
    var search: String = ""
    private var mode = ShardBrowseMode.TRACKED
    private var toolsOpen = false

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
        draw.heading("Attribute shards", left, y)
        y += 20
        if (!ShardChecklistController.enabled()) {
            draw.text(
                "Attribute shards are off: /am Advanced and /huntingbox pages are not scanned. Turn the feature on in the header to resume.",
                left, y, draw.palette.accent, draw.descFont,
            )
            y += 18
        }
        draw.field("shards:search", search, "Shard, attribute, or id", left, y, width, focused == "shards:search")
        y += 34
        y = filterRow(draw, left, y, width, resetScroll)
        if (toolsOpen) y = toolsRow(draw, left, y, width, toast, resetScroll)
        val result = browse()
        y = summary(draw, left, y, width, result)
        y = rows(draw, result.listed, left, y, width, toast)
        if (result.extra.isNotEmpty()) {
            draw.text("Not on your list — Add to track", left, y, draw.palette.accent, draw.smallFont)
            y += 16
            y = rows(draw, result.extra, left, y, width, toast)
        }
        if (result.listed.isEmpty() && result.extra.isEmpty()) {
            draw.text(emptyMessage(), left, y, draw.palette.tertiary, draw.descFont)
            y += 22
        }
        return y
    }

    private fun filterRow(draw: MenuDraw, x: Int, y: Int, width: Int, resetScroll: () -> Unit): Int {
        var cy = y
        var cx = x
        fun wrap(needed: Int) {
            if (cx != x && cx + needed > x + width) {
                cy += 32
                cx = x
            }
        }
        ShardBrowseMode.entries.forEach { option ->
            val label = when (option) {
                ShardBrowseMode.TRACKED -> "Tracked"
                ShardBrowseMode.CATALOG -> "Catalog"
                ShardBrowseMode.MISSING -> "Missing"
            }
            wrap(draw.measure(label, draw.buttonFont) + 22)
            cx += draw.chip(label, cx, cy, mode == option) { mode = option; resetScroll() } + 6
        }
        listOf("Advanced", "Box", "More").forEach { label ->
            val buttonW = draw.measure(label, draw.buttonFont) + 28
            wrap(buttonW)
            val style = if (label == "More" && toolsOpen) ButtonStyle.PRIMARY else ButtonStyle.GHOST
            draw.button(label, cx, cy, style) {
                when (label) {
                    "Advanced" -> openCommand("am")
                    "Box" -> openCommand("huntingbox")
                    "More" -> toolsOpen = !toolsOpen
                }
            }
            cx += buttonW + 6
        }
        return cy + 32
    }

    private fun toolsRow(
        draw: MenuDraw,
        x: Int,
        y: Int,
        width: Int,
        toast: (String) -> Unit,
        resetScroll: () -> Unit,
    ): Int {
        var cy = y
        var cx = x
        listOf("Import", "Export", "Default", "Reset scan", "AI prompt").forEach { label ->
            val buttonW = draw.measure(label, draw.buttonFont) + 28
            if (cx != x && cx + buttonW > x + width) {
                cy += 30
                cx = x
            }
            draw.button(label, cx, cy, if (label == "Reset scan") ButtonStyle.DANGER else ButtonStyle.GHOST) {
                performTool(label, toast, resetScroll)
            }
            cx += buttonW + 6
        }
        return cy + 32
    }

    private fun summary(draw: MenuDraw, x: Int, y: Int, width: Int, result: ShardBrowseResult): Int {
        var ty = y
        val tracked = trackedDefinitions()
        val unknown = tracked.count { ShardChecklistController.progress(it.displayName)?.level == null }
        val unlocked = tracked.count { (ShardChecklistController.progress(it.displayName)?.level ?: 0) > 0 }
        val atTarget = tracked.count { (ShardChecklistController.progress(it.displayName)?.level ?: 0) >= target(it) }
        val shown = result.listed.size + result.extra.size
        val line = "${tracked.size} tracked • $unlocked unlocked • $atTarget at target • $unknown unscanned • $shown shown"
        draw.wrap(line, width, draw.smallFont).forEach { text ->
            draw.text(text, x, ty, draw.palette.secondary, draw.smallFont)
            ty += draw.lineHeight(draw.smallFont)
        }
        ty += 4
        draw.wrap("Open Advanced and every Hunting Box page to scan levels. Search matches shard or attribute names.", width, draw.microFont).forEach { text ->
            draw.text(text, x, ty, draw.palette.tertiary, draw.microFont)
            ty += draw.lineHeight(draw.microFont)
        }
        return ty + 8
    }

    private fun rows(
        draw: MenuDraw,
        definitions: List<ShardDefinition>,
        x: Int,
        y: Int,
        w: Int,
        toast: (String) -> Unit,
    ): Int {
        var ty = y
        definitions.forEach { definition ->
            ty += drawRow(draw, definition, x, ty, w, toast) + 5
        }
        return ty
    }

    private fun drawRow(
        draw: MenuDraw,
        definition: ShardDefinition,
        x: Int,
        y: Int,
        w: Int,
        toast: (String) -> Unit,
    ): Int {
        val tracked = isTracked(definition)
        val progress = ShardChecklistController.progress(definition.displayName)
        val target = target(definition)
        val trackLabel = if (tracked) "Remove" else "Add"
        val bazaarW = draw.measure("Bazaar", draw.buttonFont) + 28
        val trackW = draw.measure(trackLabel, draw.buttonFont) + 28
        val buttons = bazaarW + 6 + trackW
        val textW = (w - buttons - 24).coerceAtLeast(70)
        val stats = rowStats(definition, progress, target)
        val idBit = definition.shardId.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val subtitle = draw.fit("${definition.shardLabel}$idBit  •  $stats", textW, draw.microFont)
        val cardH = 42
        val fill = if (tracked) draw.palette.group else draw.palette.field
        draw.round(x, y, w, cardH, draw.chrome.cardRadius, fill)
        draw.outline(x, y, w, cardH, draw.chrome.cardRadius, draw.palette.line)
        if (tracked) draw.round(x, y + 8, 3, cardH - 16, 0f, draw.palette.accent)
        val textX = x + 10
        draw.text(draw.fit(definition.attributeLabel, textW, draw.bodyFont), textX, y + 6, draw.palette.text, draw.bodyFont)
        draw.text(subtitle, textX, y + 24, draw.palette.secondary, draw.microFont)
        val trackX = x + w - buttons - 8
        draw.button(trackLabel, trackX, y + 7, if (tracked) ButtonStyle.GHOST else ButtonStyle.PRIMARY) {
            toast(ShardChecklistController.setTracked(definition, !tracked))
        }
        draw.button("Bazaar", trackX + trackW + 6, y + 7, ButtonStyle.GHOST) { ShardChecklistController.buy(definition) }
        return cardH
    }

    private fun rowStats(definition: ShardDefinition, progress: ShardProgress?, target: Int): String {
        val level = progress?.level?.toString() ?: "?"
        val needed = ShardMath.stillToObtain(definition, progress ?: ShardProgress(), target)?.toString() ?: "?"
        return "Lv $level/$target  need $needed"
    }

    private fun performTool(label: String, toast: (String) -> Unit, resetScroll: () -> Unit) {
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
        }
    }

    private fun openCommand(command: String) {
        IcantpyGui.closeIfOpen()
        IcantpyClientActions.sendCommand(command)
    }

    private fun browse(): ShardBrowseResult {
        val tracked = trackedDefinitions()
        return ShardQuery.browse(search, mode, ShardChecklistController.definitions(), tracked) { definition ->
            val progress = ShardChecklistController.progress(definition.displayName)
            progress?.level == null || progress.level < target(definition)
        }
    }

    private fun trackedDefinitions(): List<ShardDefinition> {
        val catalog = ShardChecklistController.definitions().associateBy { ShardChecklistCodec.normalizeName(it.displayName) }
        return ShardChecklistController.checklist().shards.mapNotNull { catalog[ShardChecklistCodec.normalizeName(it.name)] }
    }

    private fun isTracked(definition: ShardDefinition): Boolean {
        val key = ShardChecklistCodec.normalizeName(definition.displayName)
        return ShardChecklistController.checklist().shards.any { ShardChecklistCodec.normalizeName(it.name) == key }
    }

    private fun target(definition: ShardDefinition): Int = ShardChecklistController.checklist().shards.firstOrNull {
        ShardChecklistCodec.normalizeName(it.name) == ShardChecklistCodec.normalizeName(definition.displayName)
    }?.targetLevel ?: definition.maxLevel

    private fun emptyMessage(): String = when {
        search.isNotBlank() -> "No shards match that name or attribute"
        mode == ShardBrowseMode.MISSING -> "Nothing missing on the current list"
        mode == ShardBrowseMode.TRACKED -> "Nothing tracked yet — search the catalog"
        else -> "No shards in the catalog"
    }

    private fun aiPrompt(): String =
        "Convert the following user bullet list into raw JSON only. A mob appears inside the first parentheses; use that mob's name as the shard name. Default targetLevel to 10. Schema: {\"version\":1,\"name\":\"My shards\",\"shards\":[{\"name\":\"Flash\",\"targetLevel\":10}]}\n[Paste your bullet list here]"
}
