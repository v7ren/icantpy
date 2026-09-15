package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.modules.impl.timer.TimerClock
import kotlin.test.Test
import kotlin.test.assertEquals

class LeapTriggerCardsTest {
    @Test
    fun groupsDefaultArcherAndMageMaxorRowsIntoOneCard() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers")

        val card = LeapTriggerCards.group(listOf(archer, mage)).single()

        assertEquals(listOf(archer, mage), card.triggers)
        assertEquals(archer, card.trigger)
        assertEquals("Archer, Mage", card.actorLabel())
    }

    @Test
    fun keepsTankYellowPadSeparateAndPreservesFirstAppearanceOrder() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers")
        val tank = preset("tank-maxor-bers")

        val cards = LeapTriggerCards.group(listOf(tank, mage, archer))

        assertEquals(listOf(listOf(tank), listOf(mage, archer)), cards.map { it.triggers })
        assertEquals(listOf(tank, mage), cards.map { it.trigger })
        assertEquals("Yellow pad", cards.first().trigger.zone)
        assertEquals("Tank", cards.first().actorLabel())
    }

    @Test
    fun neverCombinesIdenticalCustomRowsWithEachOtherOrPresets() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers")
        val firstCustom = archer.copy(id = "custom-first", presetId = null)
        val secondCustom = firstCustom.copy(id = "custom-second")
        val rows = listOf(firstCustom, archer, secondCustom, mage)

        val cards = LeapTriggerCards.group(rows)

        assertEquals(
            listOf(listOf(firstCustom), listOf(archer, mage), listOf(secondCustom)),
            cards.map { it.triggers },
        )
    }

    @Test
    fun mixedEnabledPresetRowsRemainSeparate() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers").copy(enabled = false)

        assertEquals(
            listOf(listOf(archer), listOf(mage)),
            LeapTriggerCards.group(listOf(archer, mage)).map { it.triggers },
        )
        assertEquals(
            listOf(listOf(archer.copy(enabled = false), mage)),
            LeapTriggerCards.group(listOf(archer.copy(enabled = false), mage)).map { it.triggers },
        )
    }

    @Test
    fun everyFieldExceptIdPresetIdAndForClassParticipatesInGrouping() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers")
        val differences = listOf(
            "enabled" to mage.copy(enabled = false),
            "event" to mage.copy(event = LeapTriggerEvent.BOSS_STORM_END),
            "whenState" to mage.copy(whenState = "any"),
            "match" to mage.copy(match = "self"),
            "target" to mage.copy(target = LeapCenterTarget.NAME),
            "targetValue" to mage.copy(targetValue = "healer"),
            "targetPlayer" to mage.copy(targetPlayer = "Ren"),
            "priority" to mage.copy(priority = mage.priority + 1),
            "floor" to mage.copy(floor = LeapFloor.M7),
            "phase" to mage.copy(phase = LeapPhase.P3),
            "section" to mage.copy(section = 1),
            "route" to mage.copy(route = LeapStormRoute.GY),
            "ee2Owner" to mage.copy(ee2Owner = LeapDungeonClass.MAGE),
            "clock" to mage.copy(clock = ClockSpec.stormElapsed(35.0)),
            "chainDelaySeconds" to mage.copy(chainDelaySeconds = 1),
            "chainDurationSeconds" to mage.copy(chainDurationSeconds = 2),
            "zone" to mage.copy(zone = "Yellow pad"),
            "reason" to mage.copy(reason = "Different reason"),
            "group" to mage.copy(group = "Other"),
        )

        differences.forEach { (field, different) ->
            assertEquals(
                listOf(listOf(archer), listOf(different)),
                LeapTriggerCards.group(listOf(archer, different)).map { it.triggers },
                "$field must keep preset rows separate",
            )
        }
    }

    @Test
    fun comparesClockValuesIncludingThresholdMetricAndCrossing() {
        val spec = ClockSpec.stormElapsed(35.0)
        val archer = preset("arch-maxor-bers").copy(event = LeapTriggerEvent.CLOCK, clock = spec)
        val mage = archer.copy(id = "mage-clock", presetId = "mage-clock", forClass = LeapDungeonClass.MAGE)
        assertEquals(
            listOf(listOf(archer, mage)),
            LeapTriggerCards.group(listOf(archer, mage.copy(clock = spec.copy()))).map { it.triggers },
        )
        val differences = listOf(
            spec.copy(clock = TimerClock.GOLDOR_ELAPSED),
            spec.copy(thresholdTicks = spec.thresholdTicks + 1),
            spec.copy(metric = ClockMetric.REMAINING),
            spec.copy(crossing = ClockCrossing.FALLING),
        )

        differences.forEach { different ->
            val changed = mage.copy(clock = different)
            assertEquals(
                listOf(listOf(archer), listOf(changed)),
                LeapTriggerCards.group(listOf(archer, changed)).map { it.triggers },
                "Different clock specification: $different",
            )
        }
    }

    @Test
    fun emptyRowsProduceNoCards() {
        assertEquals(emptyList(), LeapTriggerCards.group(emptyList()))
    }

    @Test
    fun disablingAndReenablingCardUpdatesOnlyItsIdsAndPreservesOtherSettings() {
        val archer = preset("arch-maxor-bers")
        val mage = preset("mage-maxor-bers")
        val tank = preset("tank-maxor-bers").copy(enabled = false)
        val custom = archer.copy(id = "custom", presetId = null)
        val disabledCustom = custom.copy(id = "disabled-custom", enabled = false)
        val rows = listOf(custom, archer, tank, disabledCustom, mage)
        val unrelatedDisabled = setOf(tank.id, "absent-preset")
        val settings = LeapOrientSettings(
            durationSeconds = 17,
            keyword = "[test]",
            classPlayers = mapOf("mage" to "Ren"),
            triggers = rows,
            disabledPresetIds = unrelatedDisabled,
        )
        val card = LeapTriggerCards.group(listOf(archer, mage)).single()
        val disabledRows = rows.map {
            if (it.id == archer.id || it.id == mage.id) it.copy(enabled = false) else it
        }

        val disabled = LeapTriggerCards.setEnabled(settings, card, false)

        assertEquals(
            settings.copy(
                triggers = disabledRows,
                disabledPresetIds = unrelatedDisabled + setOf(archer.id, mage.id),
            ),
            disabled,
        )
        assertEquals(rows, settings.triggers)
        assertEquals(unrelatedDisabled, settings.disabledPresetIds)
        val disabledCard = LeapTriggerCards.group(disabled.triggers)
            .single { it.triggers.map { row -> row.id } == listOf(archer.id, mage.id) }

        assertEquals(settings, LeapTriggerCards.setEnabled(disabled, disabledCard, true))
        assertEquals(disabledRows, disabled.triggers)
        assertEquals(unrelatedDisabled + setOf(archer.id, mage.id), disabled.disabledPresetIds)
    }

    @Test
    fun togglingCustomCardDoesNotCreateDisabledPresetIds() {
        val archer = preset("arch-maxor-bers")
        val custom = archer.copy(id = "custom", presetId = null)
        val otherCustom = custom.copy(id = "other-custom")
        val settings = LeapOrientSettings(
            triggers = listOf(archer, custom, otherCustom),
            disabledPresetIds = setOf("absent-preset"),
        )
        val card = LeapTriggerCards.group(listOf(custom)).single()

        val disabled = LeapTriggerCards.setEnabled(settings, card, false)

        assertEquals(settings.copy(triggers = listOf(archer, custom.copy(enabled = false), otherCustom)), disabled)
        assertEquals(settings, LeapTriggerCards.setEnabled(disabled, card, true))
    }

    private fun preset(id: String): LeapOrientTrigger =
        LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == id }
}
