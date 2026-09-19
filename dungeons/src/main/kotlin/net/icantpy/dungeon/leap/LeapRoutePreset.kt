package net.icantpy.dungeon.leap

import net.icantpy.dungeon.timer.TimerClock

object LeapRoutePreset {
    private val legacyP5PresetIds = mapOf(
        "heal-p5-relic-bers" to "heal-p5-purple-bers",
        "mage-p5-relic-bers" to "mage-p5-blue-bers",
        "tank-p5-relic-bers" to "tank-p5-green-arch",
    )

    fun rows(
        route: LeapStormRoute = LeapStormRoute.PY,
        ee2: LeapDungeonClass = LeapDungeonClass.MAGE,
    ): List<LeapOrientTrigger> {
        val storm = if (route == LeapStormRoute.GY) LeapStormRoute.GY else LeapStormRoute.PY
        val ee2Class = if (ee2.isReal()) ee2 else LeapDungeonClass.MAGE
        return buildList {
            addAll(maxorToStorm(storm))
            addAll(stormPhase(storm))
            addAll(goldor(ee2Class))
            addAll(goldorDeath())
            addAll(necronFight())
            addAll(necron())
            addAll(p5())
        }
    }

    fun apply(
        current: LeapOrientSettings,
        route: LeapStormRoute = current.stormRoute,
        ee2: LeapDungeonClass = current.ee2Class,
    ): LeapOrientSettings = merge(current, route, ee2, replace = false)

    fun replace(
        current: LeapOrientSettings,
        route: LeapStormRoute = current.stormRoute,
        ee2: LeapDungeonClass = current.ee2Class,
    ): LeapOrientSettings = merge(current, route, ee2, replace = true)

    private fun merge(
        current: LeapOrientSettings,
        route: LeapStormRoute,
        ee2: LeapDungeonClass,
        replace: Boolean,
    ): LeapOrientSettings {
        val generated = rows(route, ee2).map { row ->
            val id = row.presetId ?: row.id
            row.copy(id = id, enabled = id !in current.disabledPresetIds)
        }
        val savedById = current.triggers.associateBy { it.id }
        val generatedIds = generated.map { it.id }.toSet()
        val mergedGenerated = generated.map { row ->
            val saved = savedById[row.id]
            when {
                replace || saved == null || !saved.isPreset -> row
                else -> saved.copy(
                    id = row.id,
                    presetId = row.presetId,
                    enabled = saved.enabled && row.id !in current.disabledPresetIds,
                )
            }
        }
        val hiddenPresets = if (replace) {
            emptyList()
        } else {
            current.triggers.filter { it.isPreset && it.id !in generatedIds }
        }
        val custom = current.triggers.filter { it.presetId == null }
        return current.copy(
            stormRoute = if (route == LeapStormRoute.ANY) current.stormRoute else route,
            ee2Class = if (ee2.isReal()) ee2 else current.ee2Class,
            triggers = mergedGenerated + hiddenPresets + custom,
        )
    }

    /** Keeps user-edited preset rows while adding rows introduced by newer preset versions. */
    fun migrate(current: LeapOrientSettings): LeapOrientSettings {
        val generated = rows(current.stormRoute, current.ee2Class)
        val existing = current.triggers.associateBy { it.id }
        val merged = generated.map { preset ->
            val legacyId = legacyP5PresetIds[preset.id]
            val saved = existing[preset.id] ?: legacyId?.let(existing::get)
            val disabled = preset.id in current.disabledPresetIds ||
                legacyId?.let { it in current.disabledPresetIds } == true
            when {
                saved == null -> preset.copy(enabled = !disabled)
                legacyId != null && saved.id == legacyId -> preset.copy(enabled = saved.enabled && !disabled)
                saved.event == LeapTriggerEvent.BOSS_P5 && preset.event == LeapTriggerEvent.RELIC_PICKUP ->
                    saved.copy(
                        enabled = saved.enabled && !disabled,
                        event = LeapTriggerEvent.RELIC_PICKUP,
                        whenState = LeapTriggerEvent.RELIC_PICKUP.nativeState() ?: "p5",
                        phase = LeapTriggerEvent.RELIC_PICKUP.nativePhase() ?: LeapPhase.P5,
                    )
                else -> saved.copy(enabled = saved.enabled && !disabled)
            }
        }
        val custom = current.triggers.filter { it.presetId == null }
        return current.copy(triggers = merged + custom)
    }

    private fun maxorToStorm(route: LeapStormRoute): List<LeapOrientTrigger> {
        val healerTarget = if (route == LeapStormRoute.GY) LeapDungeonClass.MAGE else LeapDungeonClass.BERSERK
        val healerZone = if (route == LeapStormRoute.GY) "GY Storm" else "Storm / Bers"
        return listOf(
            boss(
                id = "arch-maxor-bers",
                clazz = LeapDungeonClass.ARCHER,
                target = LeapDungeonClass.BERSERK,
                zone = "Storm start",
                reason = "Maxor died",
                group = "P1",
                route = LeapStormRoute.ANY,
                event = LeapTriggerEvent.BOSS_STORM_START,
            ),
            boss(
                id = "mage-maxor-bers",
                clazz = LeapDungeonClass.MAGE,
                target = LeapDungeonClass.BERSERK,
                zone = "Storm start",
                reason = "Maxor died",
                group = "P1",
                route = LeapStormRoute.ANY,
                event = LeapTriggerEvent.BOSS_STORM_START,
            ),
            boss(
                id = "tank-maxor-bers",
                clazz = LeapDungeonClass.TANK,
                target = LeapDungeonClass.BERSERK,
                zone = "Yellow pad",
                reason = "Maxor died",
                group = "P1",
                route = LeapStormRoute.ANY,
                event = LeapTriggerEvent.BOSS_STORM_START,
            ),
            boss(
                id = "heal-predev",
                clazz = LeapDungeonClass.HEALER,
                target = healerTarget,
                zone = healerZone,
                reason = if (route == LeapStormRoute.GY) "GY predev" else "PY predev",
                group = "P1",
                route = route,
                event = LeapTriggerEvent.BOSS_STORM_START,
            ),
        )
    }

    private fun stormPhase(route: LeapStormRoute): List<LeapOrientTrigger> {
        val rows = ArrayList<LeapOrientTrigger>()
        if (route == LeapStormRoute.PY) {
            rows += clock(
                id = "mage-py-lightning-tank",
                clazz = LeapDungeonClass.MAGE,
                target = LeapDungeonClass.TANK,
                zone = "Purple checkpoint",
                reason = "PY lightning",
                group = "P2",
                route = LeapStormRoute.PY,
                spec = ClockSpec.remaining(TimerClock.LIGHTNING, 0.0),
            )
            rows += clock(
                id = "mage-py-35s-healer",
                clazz = LeapDungeonClass.MAGE,
                target = LeapDungeonClass.HEALER,
                zone = "Yellow",
                reason = "PY upcrush",
                group = "P2",
                route = LeapStormRoute.PY,
                spec = ClockSpec.stormElapsed(35.0),
            ).copy(requiresTargetClass = LeapDungeonClass.TANK)
            rows += chain(
                id = "heal-py-landed-bers",
                clazz = LeapDungeonClass.HEALER,
                leapedTo = LeapDungeonClass.MAGE,
                target = LeapDungeonClass.BERSERK,
                zone = "SS",
                reason = "Mage landed / move to SS",
                group = "P2",
                route = LeapStormRoute.PY,
                phase = LeapPhase.P2,
            )
        }
        listOf(
            LeapDungeonClass.ARCHER,
            LeapDungeonClass.BERSERK,
            LeapDungeonClass.MAGE,
            LeapDungeonClass.TANK,
        ).forEach { clazz ->
            rows += boss(
                id = "${clazz.shortName.lowercase()}-storm-healer",
                clazz = clazz,
                target = LeapDungeonClass.HEALER,
                zone = "SS",
                reason = "Storm defeated · P3 entry",
                group = "P2",
                route = LeapStormRoute.ANY,
                event = LeapTriggerEvent.BOSS_STORM_END,
            )
        }
        return rows
    }

    private fun goldor(ee2: LeapDungeonClass): List<LeapOrientTrigger> = listOf(
        clock(
            id = "tank-s1-ee2",
            clazz = LeapDungeonClass.TANK,
            target = ee2,
            zone = "EE2",
            reason = "S1 preleap 9s",
            group = "S1",
            route = LeapStormRoute.ANY,
            spec = ClockSpec.goldorElapsed(9.0),
            phase = LeapPhase.P3,
            section = 1,
            ee2Owner = ee2,
        ),
        clock(
            id = "arch-s1-ee2",
            clazz = LeapDungeonClass.ARCHER,
            target = ee2,
            zone = "EE2",
            reason = "S1 preleap 12s",
            group = "S1",
            route = LeapStormRoute.ANY,
            spec = ClockSpec.goldorElapsed(12.0),
            phase = LeapPhase.P3,
            section = 1,
            ee2Owner = ee2,
        ),
        clock(
            id = "bers-s1-ee2",
            clazz = LeapDungeonClass.BERSERK,
            target = ee2,
            zone = "EE2",
            reason = "S1 preleap 9s",
            group = "S1",
            route = LeapStormRoute.ANY,
            spec = ClockSpec.goldorElapsed(9.0),
            phase = LeapPhase.P3,
            section = 1,
            ee2Owner = ee2,
        ),
    )

    private fun goldorDeath(): List<LeapOrientTrigger> = listOf(
        LeapDungeonClass.ARCHER,
        LeapDungeonClass.BERSERK,
        LeapDungeonClass.MAGE,
        LeapDungeonClass.TANK,
    ).map { clazz ->
        boss(
            id = "${clazz.shortName.lowercase()}-goldor-healer",
            clazz = clazz,
            target = LeapDungeonClass.HEALER,
            zone = "P4",
            reason = "Goldor defeated · Necron entry",
            group = "P3",
            route = LeapStormRoute.ANY,
            event = LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            phase = LeapPhase.P4,
            floor = LeapFloor.ANY,
        )
    }

    fun necronFightIds(): Set<String> = necronFight().map { it.id }.toSet()

    private fun necronFight(): List<LeapOrientTrigger> = listOf(
        LeapDungeonClass.ARCHER, LeapDungeonClass.MAGE, LeapDungeonClass.TANK, LeapDungeonClass.HEALER,
    ).map { clazz ->
        boss(
            id = "${clazz.shortName.lowercase()}-necron-bers",
            clazz = clazz,
            target = LeapDungeonClass.BERSERK,
            zone = "Necron fight",
            reason = "Bers until Necron says ARGH!",
            group = "P4",
            route = LeapStormRoute.ANY,
            event = LeapTriggerEvent.BOSS_NECRON,
            phase = LeapPhase.P4,
        ).copy(priority = -30)
    }

    private fun necron(): List<LeapOrientTrigger> = listOf(
        LeapDungeonClass.ARCHER,
        LeapDungeonClass.MAGE,
        LeapDungeonClass.TANK,
        LeapDungeonClass.BERSERK,
    ).map { clazz ->
        boss(
            id = "${clazz.shortName.lowercase()}-necron-healer",
            clazz = clazz,
            target = LeapDungeonClass.HEALER,
            zone = "P5",
            reason = "Necron → P5",
            group = "P4",
            route = LeapStormRoute.ANY,
            event = LeapTriggerEvent.BOSS_NECRON_DEATH,
            phase = LeapPhase.P4,
            floor = LeapFloor.ANY,
        )
    }

    private fun p5(): List<LeapOrientTrigger> = LeapDungeonClass.entries
        .filter(LeapDungeonClass::isReal)
        .map { clazz ->
            boss(
                id = "${clazz.shortName.lowercase()}-p5-relic-bers",
                clazz = clazz,
                target = LeapDungeonClass.BERSERK,
                zone = "P5 relic",
                reason = "Relic picked up → Bers",
                group = "P5",
                route = LeapStormRoute.ANY,
                event = LeapTriggerEvent.RELIC_PICKUP,
                phase = LeapPhase.P5,
            )
        }

    private fun boss(
        id: String,
        clazz: LeapDungeonClass,
        target: LeapDungeonClass,
        zone: String,
        reason: String,
        group: String,
        route: LeapStormRoute,
        event: LeapTriggerEvent,
        phase: LeapPhase = event.nativePhase() ?: LeapPhase.ANY,
        section: Int = 0,
        floor: LeapFloor = LeapFloor.ANY,
        ee2Owner: LeapDungeonClass = LeapDungeonClass.EMPTY,
    ): LeapOrientTrigger = LeapOrientTrigger(
        id = id,
        event = event,
        whenState = event.nativeState() ?: "any",
        match = "any",
        target = LeapCenterTarget.CLASS,
        targetValue = target.name.lowercase(),
        floor = floor,
        phase = phase,
        section = section,
        route = route,
        forClass = clazz,
        ee2Owner = ee2Owner,
        zone = zone,
        reason = reason,
        presetId = id,
        group = group,
    )

    private fun clock(
        id: String,
        clazz: LeapDungeonClass,
        target: LeapDungeonClass,
        zone: String,
        reason: String,
        group: String,
        route: LeapStormRoute,
        spec: ClockSpec,
        phase: LeapPhase = LeapPhase.P2,
        section: Int = 0,
        ee2Owner: LeapDungeonClass = LeapDungeonClass.EMPTY,
    ): LeapOrientTrigger = LeapOrientTrigger(
        id = id,
        event = LeapTriggerEvent.CLOCK,
        whenState = "any",
        match = "any",
        target = LeapCenterTarget.CLASS,
        targetValue = target.name.lowercase(),
        phase = phase,
        section = section,
        route = route,
        forClass = clazz,
        ee2Owner = ee2Owner,
        clock = spec,
        zone = zone,
        reason = reason,
        presetId = id,
        group = group,
    )

    private fun chain(
        id: String,
        clazz: LeapDungeonClass,
        leapedTo: LeapDungeonClass,
        target: LeapDungeonClass,
        zone: String,
        reason: String,
        group: String,
        route: LeapStormRoute,
        phase: LeapPhase,
        delaySeconds: Int = 0,
        durationSeconds: Int = 0,
    ): LeapOrientTrigger = LeapOrientTrigger(
        id = id,
        event = LeapTriggerEvent.LEAP_CHAIN,
        whenState = "any",
        match = leapedTo.name.lowercase(),
        target = LeapCenterTarget.CLASS,
        targetValue = target.name.lowercase(),
        phase = phase,
        route = route,
        forClass = clazz,
        chainDelaySeconds = delaySeconds,
        chainDurationSeconds = durationSeconds,
        zone = zone,
        reason = reason,
        presetId = id,
        group = group,
    )

    private fun leaped(
        id: String,
        clazz: LeapDungeonClass,
        target: LeapDungeonClass,
        zone: String,
        reason: String,
        group: String,
        route: LeapStormRoute,
        phase: LeapPhase,
    ): LeapOrientTrigger = LeapOrientTrigger(
        id = id,
        event = LeapTriggerEvent.LEAPED_TO,
        whenState = "p2",
        match = "self",
        target = LeapCenterTarget.CLASS,
        targetValue = target.name.lowercase(),
        phase = phase,
        route = route,
        forClass = clazz,
        zone = zone,
        reason = reason,
        presetId = id,
        group = group,
    )
}
