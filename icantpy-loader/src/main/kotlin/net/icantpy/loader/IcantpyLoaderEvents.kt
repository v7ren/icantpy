package net.icantpy.loader

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyRuntimeEvent
import net.icantpy.api.IcantpyRuntimeQuery
import net.minecraft.world.InteractionResult

object IcantpyLoaderEvents {
    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register {
            IcantpyBridge.dispatch(IcantpyRuntimeEvent("lifecycle.start_tick"))
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            IcantpyBridge.dispatch(IcantpyRuntimeEvent("lifecycle.join"))
        }
        UseItemCallback.EVENT.register { player, world, hand ->
            interaction(
                "input.use.item",
                mapOf("player" to player, "world" to world, "hand" to hand),
            )
        }
        UseBlockCallback.EVENT.register { player, world, hand, hit ->
            interaction(
                "input.use.block",
                mapOf("player" to player, "world" to world, "hand" to hand, "hit" to hit),
            )
        }
        UseEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            interaction(
                "input.use.entity",
                mapOf(
                    "player" to player,
                    "world" to world,
                    "hand" to hand,
                    "entity" to entity,
                    "hit" to hit,
                ),
            )
        }
        AttackBlockCallback.EVENT.register { player, world, hand, pos, direction ->
            interaction(
                "input.attack.block",
                mapOf(
                    "player" to player,
                    "world" to world,
                    "hand" to hand,
                    "pos" to pos,
                    "direction" to direction,
                ),
            )
        }
        AttackEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            interaction(
                "input.attack.entity",
                mapOf(
                    "player" to player,
                    "world" to world,
                    "hand" to hand,
                    "entity" to entity,
                    "hit" to hit,
                ),
            )
        }
        ClientPreAttackCallback.EVENT.register { minecraft, player, clickCount ->
            IcantpyBridge.query(
                IcantpyRuntimeQuery(
                    "input.attack.pre",
                    context = mapOf(
                        "minecraft" to minecraft,
                        "player" to player,
                        "clickCount" to clickCount,
                    ),
                ),
            ).value == true
        }
    }

    private fun interaction(id: String, context: Map<String, Any?>): InteractionResult {
        val value = IcantpyBridge.query(IcantpyRuntimeQuery(id, context = context)).value
        return value as? InteractionResult ?: InteractionResult.PASS
    }
}
