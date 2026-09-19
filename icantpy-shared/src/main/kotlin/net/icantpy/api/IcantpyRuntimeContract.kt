package net.icantpy.api

import java.util.Collections
import java.util.LinkedHashMap

object IcantpyRuntimeContract {
    const val ABI_MAJOR: Int = 1
    const val ABI_MINOR: Int = 0
    const val ABI: String = "$ABI_MAJOR.$ABI_MINOR"

    val CAPABILITIES: Set<String> = setOf(
        "lifecycle.tick",
        "lifecycle.start_tick",
        "lifecycle.join",
        "lifecycle.disconnect",
        "network.inbound",
        "commands.root",
        "input.native",
        "input.action",
        "input.keybinds",
        "gui.menu",
        "gui.screen",
        "render.hud",
        "render.world",
        "appearance.item",
    )
}

/**
 * Stable, namespaced runtime event identifiers used by the resident bridge.
 * Context values are opaque to the bridge and are valid only for the dispatch call.
 */
class IcantpyRuntimeEvent(
    val id: String,
    val schemaVersion: Int = 1,
    context: Map<String, Any?> = emptyMap(),
) {
    val context: Map<String, Any?> = Collections.unmodifiableMap(LinkedHashMap(context))

    init {
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(EVENT_ID.matches(id)) { "Invalid runtime event id: $id" }
    }

    companion object {
        private val EVENT_ID = Regex("[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)?(?:[._-][a-z0-9]+)*")
    }
}

class IcantpyRuntimeQuery(
    val id: String,
    val schemaVersion: Int = 1,
    context: Map<String, Any?> = emptyMap(),
) {
    val context: Map<String, Any?> = Collections.unmodifiableMap(LinkedHashMap(context))

    init {
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(QUERY_ID.matches(id)) { "Invalid runtime query id: $id" }
    }

    companion object {
        private val QUERY_ID = Regex("[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)?(?:[._-][a-z0-9]+)*")
    }
}

enum class IcantpyDispatchResult {
    PASS,
    HANDLED,
    OVERRIDE,
}

data class IcantpyQueryResult(
    val result: IcantpyDispatchResult,
    val value: Any? = null,
)

private inline fun <reified T> IcantpyRuntimeEvent.value(key: String): T? = context[key] as? T
private inline fun <reified T> IcantpyRuntimeQuery.value(key: String): T? = context[key] as? T

/**
 * Generic dispatch is the extension point for future payload features. The legacy
 * callbacks below remain as defaults so existing payloads continue to work unchanged.
 */
internal fun dispatchLegacy(payload: IcantpyPayload, event: IcantpyRuntimeEvent): IcantpyDispatchResult = when (event.id) {
    "lifecycle.tick" -> {
        payload.onTick()
        IcantpyDispatchResult.PASS
    }
    "lifecycle.server_tick" -> {
        payload.onServerTick()
        IcantpyDispatchResult.PASS
    }
    "lifecycle.disconnect" -> {
        payload.onDisconnect()
        IcantpyDispatchResult.PASS
    }
    "chat.incoming" -> {
        event.value<String>("message")?.let(payload::onIncomingChat)
        IcantpyDispatchResult.PASS
    }
    "chat.outgoing" -> if (event.value<String>("message")?.let(payload::onOutgoingChat) == true) {
        IcantpyDispatchResult.HANDLED
    } else {
        IcantpyDispatchResult.PASS
    }
    "network.boss_bar" -> {
        val name = event.value<String>("name")
        val progress = event.value<Float>("progress")
        if (name != null && progress != null) payload.onBossBar(name, progress)
        IcantpyDispatchResult.PASS
    }
    "world.block" -> {
        val pos = event.value<net.minecraft.core.BlockPos>("pos")
        val state = event.value<net.minecraft.world.level.block.state.BlockState>("state")
        if (pos != null && state != null) payload.onBlock(pos, state)
        IcantpyDispatchResult.PASS
    }
    "render.world" -> {
        payload.onRenderWorld()
        IcantpyDispatchResult.PASS
    }
    "render.hud" -> {
        event.value<net.minecraft.client.gui.GuiGraphicsExtractor>("graphics")?.let(payload::onRenderHud)
        IcantpyDispatchResult.PASS
    }
    "input.open_gui" -> {
        payload.openGui()
        IcantpyDispatchResult.HANDLED
    }
    "input.add_waypoint" -> {
        payload.addWaypointAtLook()
        IcantpyDispatchResult.HANDLED
    }
    "input.open_stats" -> {
        payload.openStats()
        IcantpyDispatchResult.HANDLED
    }
    "input.open_loadout" -> {
        payload.openLoadout()
        IcantpyDispatchResult.HANDLED
    }
    else -> IcantpyDispatchResult.PASS
}

internal fun queryLegacy(payload: IcantpyPayload, query: IcantpyRuntimeQuery): IcantpyQueryResult = when (query.id) {
    "gui.menu.wants.leap" -> queryBoolean(payload.wantsLeapMenu(query.value<String>("title") ?: return passQuery()))
    "gui.menu.wants.stats" -> queryBoolean(payload.wantsStatsMenu(query.value<String>("title") ?: return passQuery()))
    "gui.menu.wants.loadout" -> queryBoolean(payload.wantsLoadoutMenu(query.value<String>("title") ?: return passQuery()))
    "gui.menu.open.leap" -> queryBoolean(
        payload.openLeapMenu(
            query.value<net.minecraft.world.inventory.AbstractContainerMenu>("menu") ?: return passQuery(),
            query.value<net.minecraft.network.chat.Component>("title") ?: return passQuery(),
        ),
    )
    "gui.menu.open.stats" -> queryBoolean(
        payload.openStatsMenu(
            query.value<net.minecraft.world.inventory.AbstractContainerMenu>("menu") ?: return passQuery(),
            query.value<net.minecraft.network.chat.Component>("title") ?: return passQuery(),
        ),
    )
    "gui.menu.open.loadout" -> queryBoolean(
        payload.openLoadoutMenu(
            query.value<net.minecraft.world.inventory.AbstractContainerMenu>("menu") ?: return passQuery(),
            query.value<net.minecraft.network.chat.Component>("title") ?: return passQuery(),
        ),
    )
    "appearance.item.name" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val vanilla = query.value<net.minecraft.network.chat.Component>("vanilla") ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, payload.customItemName(stack, vanilla))
    }
    "appearance.item.glint.override" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val value = payload.customGlintOverride(stack) ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }
    "appearance.item.glint.color" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val value = payload.customGlintColor(stack) ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }
    "appearance.item.leather.color" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val vanilla = query.value<Int>("vanilla") ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, payload.customLeatherColor(stack, vanilla))
    }
    "appearance.item.tooltip" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, payload.customTooltip(stack))
    }
    "appearance.item.model" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val value = payload.customItemModel(stack) ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }
    "appearance.item.head" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val value = payload.customHeadTexture(stack) ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }
    "appearance.item.trim" -> {
        val stack = query.value<net.minecraft.world.item.ItemStack>("stack") ?: return passQuery()
        val value = payload.customTrim(stack) ?: return passQuery()
        IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }
    else -> passQuery()
}

private fun queryBoolean(handled: Boolean): IcantpyQueryResult = IcantpyQueryResult(
    if (handled) IcantpyDispatchResult.HANDLED else IcantpyDispatchResult.PASS,
    handled,
)

private fun passQuery(): IcantpyQueryResult = IcantpyQueryResult(IcantpyDispatchResult.PASS)
