package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapTriggerAutoActionTest {
    private val statsStep = LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "helm-1")
    private val loadoutStep = LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 3)
    private val leapStep = LeapAutoStep.leapTarget()
    private val statsTrigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(
        id = "stats-on-maxor",
        steps = listOf(statsStep),
    )
    private val loadoutTrigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_GOLDOR_DEATH).copy(
        id = "loadout-on-goldor",
        steps = listOf(loadoutStep),
    )
    private val labels = mapOf("helm-1" to "Helmet: Aurora Helmet")

    private fun jsonOf(triggers: List<LeapOrientTrigger>): JsonObject =
        JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(triggers)) }

    private fun parse(text: String): List<LeapOrientTrigger> =
        LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!

    private fun reparse(triggers: List<LeapOrientTrigger>): List<LeapOrientTrigger> =
        parse(jsonOf(triggers).toString())

    @Test
    fun stepsAndArmLeapSurviveJsonRoundTrip() {
        assertEquals(listOf(statsTrigger, loadoutTrigger), reparse(listOf(statsTrigger, loadoutTrigger)))
    }

    @Test
    fun newFormatWritesArmLeapAndStepsWithoutLegacyActionFields() {
        val item = jsonOf(listOf(statsTrigger)).getAsJsonArray("triggers").single().asJsonObject
        assertFalse(item.has("action"))
        assertFalse(item.has("sortPreset"))
        assertFalse(item.has("statsPreset"))
        assertFalse(item.has("loadout"))
        assertFalse(item.has("armLeap"), "the leap target is a row in steps, not a separate flag")
        assertEquals("EQUIP_STATS", item.getAsJsonArray("steps").single().asJsonObject.get("kind").asString)
    }

    @Test
    fun oldStatsActionMigratesToOneStatsStep() {
        val loaded = parse("""{"triggers": [{"id": "s", "event": "BOSS_STORM_START", "action": "EQUIP_STATS", "statsPreset": "helm-1"}]}""").single()
        assertFalse(loaded.armLeap)
        assertEquals(listOf(statsStep), loaded.steps)
    }

    @Test
    fun oldLoadoutActionMigratesToOneLoadoutStep() {
        val loaded = parse("""{"triggers": [{"id": "l", "event": "BOSS_GOLDOR_DEATH", "action": "SWITCH_LOADOUT", "loadout": 3}]}""").single()
        assertFalse(loaded.armLeap)
        assertEquals(listOf(loadoutStep), loaded.steps)
    }

    @Test
    fun oldSortActionMigratesToOneSortStep() {
        val loaded = parse("""{"triggers": [{"id": "o", "event": "BOSS_STORM_START", "action": "SORT_INVENTORY", "sortPreset": "Dps"}]}""").single()
        assertFalse(loaded.armLeap)
        assertEquals(listOf(LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Dps")), loaded.steps)
    }

    @Test
    fun oldArmLeapActionMigratesToLeapOnly() {
        val loaded = parse("""{"triggers": [{"id": "a", "event": "BOSS_STORM_START", "action": "ARM_LEAP", "target": "CLASS", "targetValue": "mage"}]}""").single()
        assertTrue(loaded.armLeap)
        assertEquals(listOf(leapStep), loaded.steps)
    }

    @Test
    fun oldArmLeapTrueWithStepsInsertsLeapTargetFirst() {
        val text = """{"triggers": [{"id": "m", "event": "BOSS_STORM_START", "armLeap": true,
            "steps": [{"kind": "SORT_INVENTORY", "sortPreset": "Dps"}, {"kind": "SWITCH_LOADOUT", "loadout": 3}]}]}"""
        val loaded = parse(text).single()
        assertTrue(loaded.armLeap)
        assertEquals(listOf(leapStep, LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Dps"), loadoutStep), loaded.steps)
    }

    @Test
    fun oldArmLeapTrueAtTheCapDropsTheLastStepToMakeRoom() {
        val six = (1..6).joinToString(",") { """{"kind": "SORT_INVENTORY", "sortPreset": "P$it"}""" }
        val loaded = parse("""{"triggers": [{"id": "c", "event": "CLOCK", "armLeap": true, "steps": [$six]}]}""").single()
        assertEquals(LeapAutoStep.MAX_STEPS, loaded.steps.size)
        assertEquals(leapStep, loaded.steps.first())
        assertEquals("P1", loaded.steps[1].sortPreset)
        assertEquals("P5", loaded.steps.last().sortPreset)
    }

    @Test
    fun oldArmLeapFalseWithStepsAddsNoLeapTarget() {
        val loaded = parse("""{"triggers": [{"id": "f", "event": "CLOCK", "armLeap": false,
            "steps": [{"kind": "EQUIP_STATS", "statsPreset": "helm-1"}]}]}""").single()
        assertFalse(loaded.armLeap)
        assertEquals(listOf(statsStep), loaded.steps)
    }

    @Test
    fun currentFormatKeepsLeapTargetWhereItWasSaved() {
        val text = """{"triggers": [{"id": "n", "event": "BOSS_STORM_START", "steps": [
            {"kind": "SWITCH_LOADOUT", "loadout": 3}, {"kind": "LEAP_TARGET"}]}]}"""
        val loaded = parse(text).single()
        assertTrue(loaded.armLeap)
        assertEquals(listOf(loadoutStep, leapStep), loaded.steps)
    }

    @Test
    fun currentFormatWithoutLeapTargetDoesNotArmLeap() {
        val loaded = parse("""{"triggers": [{"id": "p", "event": "CLOCK", "steps": []}]}""").single()
        assertFalse(loaded.armLeap)
        assertTrue(loaded.steps.isEmpty())
    }

    @Test
    fun duplicateLeapTargetRowsKeepOnlyTheFirst() {
        val text = """{"triggers": [{"id": "d", "event": "CLOCK", "steps": [
            {"kind": "LEAP_TARGET"}, {"kind": "SORT_INVENTORY", "sortPreset": "A"}, {"kind": "LEAP_TARGET"}]}]}"""
        val loaded = parse(text).single()
        assertEquals(listOf(leapStep, LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "A")), loaded.steps)
    }

    @Test
    fun newTriggerFactoryHasOnlyTheEventAndNoActions() {
        val fresh = LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer")
        assertEquals(LeapTriggerEvent.CLOCK, fresh.event)
        assertFalse(fresh.armLeap)
        assertTrue(fresh.steps.isEmpty())
        assertEquals("no action", fresh.subtitle())
    }

    @Test
    fun presetRowsLoadWithALeapTargetStep() {
        val presets = LeapOrientSettings.DEFAULT_TRIGGERS.filter { it.isPreset }
        assertTrue(presets.isNotEmpty())
        presets.forEach { preset ->
            assertTrue(preset.armLeap, "preset ${preset.id} arms its leap")
            assertEquals(listOf(leapStep), preset.steps, "preset ${preset.id}")
        }
    }

    @Test
    fun oldStatsTriggerKeepsUnsetPresetAsEmptyStep() {
        val loaded = parse("""{"triggers": [{"id": "s", "event": "CLOCK", "action": "EQUIP_STATS"}]}""").single()
        assertEquals(listOf(LeapAutoStep(LeapAutoKind.EQUIP_STATS)), loaded.steps)
    }

    @Test
    fun negativeLoadoutInLegacyJsonIsClampedToUnset() {
        val loaded = parse("""{"triggers": [{"id": "x", "event": "CLOCK", "action": "SWITCH_LOADOUT", "loadout": -4}]}""").single()
        assertEquals(listOf(LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 0)), loaded.steps)
    }

    @Test
    fun unknownStepKindIsSkippedAndStepsAreCapped() {
        val eight = (1..8).joinToString(",") { """{"kind": "SORT_INVENTORY", "sortPreset": "P$it"}""" }
        val text = """{"triggers": [{"id": "x", "event": "CLOCK",
            "steps": [{"kind": "NOPE"}, $eight]}]}"""
        val steps = parse(text).single().steps
        assertEquals(LeapAutoStep.MAX_STEPS, steps.size)
        assertEquals("P1", steps.first().sortPreset)
    }

    @Test
    fun statsStepShowsPresetLabelAndMissingIdIsVisible() {
        assertEquals("Storm starts (P2) → Stats: Helmet: Aurora Helmet", statsTrigger.summary(labels))
        assertEquals("Stats: Helmet: Aurora Helmet", statsTrigger.subtitle(labels))
        assertEquals("Stats: (missing)", statsTrigger.copy(steps = listOf(LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "gone"))).subtitle(labels))
        assertEquals("Stats: no preset", statsTrigger.copy(steps = listOf(LeapAutoStep(LeapAutoKind.EQUIP_STATS))).subtitle(labels))
    }

    @Test
    fun upcomingPreviewUsesRealStatsPresetLabel() {
        val settings = LeapOrientSettings(triggers = listOf(statsTrigger))
        val context = LeapRouteContext(
            floor = LeapFloor.F7,
            phase = LeapPhase.P2,
            section = 0,
            route = LeapStormRoute.PY,
            selfClass = LeapDungeonClass.HEALER,
        )
        assertEquals(
            "Storm starts (P2) → Stats: Helmet: Aurora Helmet",
            LeapOrientEngine.nextPreview(settings, context, labels),
        )
        assertEquals("Storm starts (P2) → Stats: (missing)", LeapOrientEngine.nextPreview(settings, context))
    }

    @Test
    fun loadoutStepShowsLoadoutNumber() {
        assertEquals("Goldor dies → Loadout 3", loadoutTrigger.summary())
        assertEquals("Loadout 3", loadoutTrigger.subtitle())
        assertEquals("no loadout", loadoutTrigger.copy(steps = listOf(LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT))).subtitle())
    }

    @Test
    fun leapAndStepsReadAsOneSubtitleInOrder() {
        val combined = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START, "healer").copy(
            steps = listOf(
                leapStep,
                LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Dps"),
                LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 3),
            ),
        )
        assertEquals("Leap to Healer · Sort: Dps · Loadout 3", combined.subtitle())
        assertEquals("Storm starts (P2) → Leap to Healer · Sort: Dps · Loadout 3", combined.summary())
    }

    @Test
    fun leapTextFollowsItsRowPositionInTheList() {
        val sortFirst = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START, "healer").copy(
            steps = listOf(loadoutStep, leapStep),
        )
        assertEquals("Loadout 3 · Leap to Healer", sortFirst.subtitle())
    }

    @Test
    fun triggerWithNoLeapAndNoStepsSaysNoAction() {
        assertEquals("no action", statsTrigger.copy(steps = emptyList()).subtitle())
    }

    @Test
    fun applySharedCopiesLeapTargetAndStepsToCard() {
        val preset = LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == "arch-maxor-bers" }
        val settings = LeapOrientSettings(triggers = listOf(preset))
        val card = LeapTriggerCard(listOf(preset))
        val template = preset.copy(steps = listOf(loadoutStep, leapStep, statsStep))
        val updated = LeapTriggerCards.applyShared(settings, card, template)
        val stored = updated.triggers.single { it.id == preset.id }
        assertTrue(stored.armLeap)
        assertEquals(listOf(loadoutStep, leapStep, statsStep), stored.steps)
    }

    @Test
    fun applySharedWithoutLeapTargetDisarmsTheCard() {
        val preset = LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == "arch-maxor-bers" }
        val settings = LeapOrientSettings(triggers = listOf(preset))
        val updated = LeapTriggerCards.applyShared(settings, LeapTriggerCard(listOf(preset)), preset.copy(steps = listOf(statsStep)))
        val stored = updated.triggers.single { it.id == preset.id }
        assertFalse(stored.armLeap)
        assertEquals(listOf(statsStep), stored.steps)
    }
}
