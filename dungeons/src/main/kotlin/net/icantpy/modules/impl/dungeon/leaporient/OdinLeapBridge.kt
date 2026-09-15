package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.Icantpy
import java.lang.reflect.Field

object OdinLeapBridge {
    private var initialized = false
    private var present = false
    private var holder: Any? = null
    private var playerClass: Class<*>? = null
    private var leapTeammatesField: Field? = null
    private var dungeonTeammatesField: Field? = null
    private var playerNameField: Field? = null
    private var playerClazzField: Field? = null
    private var playerDeadField: Field? = null

    fun init() {
        if (initialized) return
        initialized = true
        val owners = listOf(
            "com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils",
            "com.odtheking.odin.utils.skyblock.dungeon.DungeonListener",
        )
        for (ownerName in owners) {
            try {
                val owner = Class.forName(ownerName)
                val player = Class.forName("com.odtheking.odin.utils.skyblock.dungeon.DungeonPlayer")
                val field = fieldOf(owner, "leapTeammates") ?: continue
                holder = instanceOf(owner)
                playerClass = player
                leapTeammatesField = field
                dungeonTeammatesField = fieldOf(owner, "dungeonTeammates")
                playerNameField = fieldOf(player, "name")
                playerClazzField = fieldOf(player, "clazz", "dungeonClass", "klass")
                playerDeadField = fieldOf(player, "isDead", "dead")
                present = true
                Icantpy.LOGGER.info("Leap Orient: Odin bridge active ({})", ownerName)
                return
            } catch (exception: Exception) {
                Icantpy.LOGGER.debug("Leap Orient: Odin {} ({})", ownerName, exception.toString())
            }
        }
        present = false
    }

    fun isPresent(): Boolean {
        init()
        return present
    }

    fun readLeapPlayers(): List<LeapPlayer> {
        return mapPlayers(readLeapTeammates())
    }

    /** Unlike leapTeammates, the full roster includes the local player for Auto class detection. */
    fun readDungeonPlayers(): List<LeapPlayer> {
        init()
        val all = dungeonTeammatesField?.let { field ->
            try {
                (readField(field) as? List<*>)?.filterNotNull().orEmpty()
            } catch (exception: Exception) {
                Icantpy.LOGGER.debug("Leap Orient: failed to read Odin dungeon roster", exception)
                emptyList()
            }
        }.orEmpty()
        return playersForClassLookup(mapPlayers(all), readLeapPlayers())
    }

    internal fun playersForClassLookup(dungeon: List<LeapPlayer>, leap: List<LeapPlayer>): List<LeapPlayer> =
        (dungeon + leap).distinctBy { it.name.lowercase() }

    private fun mapPlayers(players: List<Any>): List<LeapPlayer> {
        return players.mapNotNull { raw ->
            val name = playerName(raw) ?: return@mapNotNull null
            LeapPlayer(name, playerClazz(raw), playerDead(raw))
        }
    }

    fun readLeapTeammates(): List<Any> {
        init()
        if (!present) return emptyList()
        val field = leapTeammatesField ?: return emptyList()
        val value = try {
            readField(field)
        } catch (exception: Exception) {
            Icantpy.LOGGER.debug("Leap Orient: failed to read Odin teammates", exception)
            return emptyList()
        }
        return when (value) {
            is List<*> -> value.filterNotNull()
            else -> emptyList()
        }
    }

    fun playerName(player: Any): String? {
        init()
        val field = playerNameField ?: return null
        return try {
            field.get(player) as? String
        } catch (_: Exception) {
            null
        }
    }

    fun seedDebugTeammates(names: List<String>) {
        init()
        if (!present) return
        try {
            val players = names.mapNotNull { name -> newDungeonPlayer(name) }
            if (players.isEmpty()) return
            writeField(leapTeammatesField, players.take(4))
            fieldOf(holder?.javaClass ?: return, "dungeonTeammatesNoSelf")?.let { field ->
                writeField(field, players)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Leap Orient: failed to seed Odin teammates", exception)
        }
    }

    private fun newDungeonPlayer(name: String): Any? {
        val player = playerClass ?: return null
        val clazzEnum = Class.forName("com.odtheking.odin.utils.skyblock.dungeon.DungeonClass")
        val mage = clazzEnum.enumConstants?.firstOrNull { constant ->
            (constant as Enum<*>).name.equals("Mage", ignoreCase = true)
        } ?: clazzEnum.enumConstants?.firstOrNull()
        val constructors = player.declaredConstructors.sortedByDescending { it.parameterCount }
        for (ctor in constructors) {
            ctor.isAccessible = true
            val args = ctor.parameterTypes.map { type ->
                when {
                    type == String::class.java -> name
                    type == clazzEnum -> mage
                    type == Int::class.javaPrimitiveType -> 50
                    type == Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }.toTypedArray()
            try {
                return ctor.newInstance(*args)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun playerClazz(player: Any): LeapDungeonClass {
        val field = playerClazzField ?: return LeapDungeonClass.EMPTY
        val value = try {
            field.get(player)
        } catch (_: Exception) {
            return LeapDungeonClass.EMPTY
        } ?: return LeapDungeonClass.EMPTY
        val token = if (value is Enum<*>) value.name else value.toString()
        return LeapDungeonClass.fromToken(token) ?: LeapDungeonClass.EMPTY
    }

    private fun playerDead(player: Any): Boolean {
        val field = playerDeadField ?: return false
        return try {
            field.get(player) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun instanceOf(type: Class<*>): Any? {
        return try {
            type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        } catch (_: Exception) {
            null
        }
    }

    private fun readField(field: Field): Any? {
        val target = holder
        return try {
            field.get(target)
        } catch (_: Exception) {
            field.get(null)
        }
    }

    private fun writeField(field: Field?, value: Any) {
        if (field == null) return
        val target = holder
        try {
            field.set(target, value)
        } catch (_: Exception) {
            field.set(null, value)
        }
    }

    private fun fieldOf(type: Class<*>, vararg names: String): Field? {
        names.forEach { name ->
            try {
                return type.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
            }
        }
        return type.declaredFields.firstOrNull { field ->
            names.any { field.name.equals(it, ignoreCase = true) }
        }?.apply { isAccessible = true }
    }
}
