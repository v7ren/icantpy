package net.icantpy.modules.impl.appearance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemCustomizeColorCodecTest {
    @Test
    fun parsesNeuFiveFieldEncoding() {
        val color = ItemCustomizeColorCodec.parse("0:204:100:25:255").getOrThrow()
        assertEquals(0x6419FF, color.rgb)
        assertEquals(204, color.alpha)
        assertEquals(0, color.chromaSpeed)
    }

    @Test
    fun parsesHexPreservingCurrentMeaning() {
        val color = ItemCustomizeColorCodec.parse("#AABBCC").getOrThrow()
        assertEquals(0xAABBCC, color.rgb)
        assertEquals(255, color.alpha)
        assertEquals(0, color.chromaSpeed)
        assertEquals(color, ItemCustomizeColorCodec.parse("aabbcc").getOrThrow())
    }

    @Test
    fun rejectsMalformedAndOutOfRangeValues() {
        assertTrue(ItemCustomizeColorCodec.parse(null).isFailure)
        assertTrue(ItemCustomizeColorCodec.parse("").isFailure)
        assertTrue(ItemCustomizeColorCodec.parse("not-a-colour").isFailure)
        assertTrue(ItemCustomizeColorCodec.parse("1:2:3").isFailure)
        assertTrue(ItemCustomizeColorCodec.parse("256:0:0:0:0").isFailure)
        assertTrue(ItemCustomizeColorCodec.parse("#12345").isFailure)
    }

    @Test
    fun canonicalFormatIsNeuAndRoundTrips() {
        assertEquals(ItemCustomizeColor.DEFAULT_GLINT_ENCODED, ItemCustomizeColorCodec.format(ItemCustomizeColor.DEFAULT_GLINT))
        val color = ItemCustomizeColor(0x112233, alpha = 12, chromaSpeed = 200)
        val encoded = ItemCustomizeColorCodec.format(color)
        assertEquals("200:12:17:34:51", encoded)
        assertEquals(color, ItemCustomizeColorCodec.parse(encoded).getOrThrow())
    }

    @Test
    fun opaqueArgbKeepsLeatherTintVisibleInWorldRendering() {
        val translucent = ItemCustomizeColor(0x123456, alpha = 40)
        assertEquals(0xFF123456.toInt(), translucent.opaqueArgb)
        assertEquals(0xFF123456.toInt(), ItemCustomizeColor(0x123456).opaqueArgb)
    }
}

class ItemCustomizeColorEvaluatorTest {
    @Test
    fun speedZeroKeepsTheExactColour() {
        val color = ItemCustomizeColor(0x8040CC, alpha = 204)
        assertEquals(0xCC8040CC.toInt(), color.argbAt(elapsedSeconds = 12.5f))
    }

    @Test
    fun chromaPeriodMatchesNeu() {
        assertEquals(60f, ItemCustomizeColorEvaluator.secondsForSpeed(1))
        assertEquals(1f, ItemCustomizeColorEvaluator.secondsForSpeed(255))
        assertEquals(30.5f, ItemCustomizeColorEvaluator.secondsForSpeed(128))
    }

    @Test
    fun chromaRotatesHueWhileKeepingSaturationAndAlpha() {
        val red = ItemCustomizeColor(0xFF0000, alpha = 128, chromaSpeed = 255)
        val rotated = red.argbAt(0.25f)
        assertEquals(0x80, rotated ushr 24)
        assertEquals(0x80FF00, rotated and 0xFFFFFF)
    }

    @Test
    fun hueWrapsWithoutNegativeValues() {
        val red = ItemCustomizeColor(0xFF0000, chromaSpeed = 255)
        val wrapped = red.argbAt(-0.25f)
        assertEquals(0x8000FF, wrapped and 0xFFFFFF)
    }

    @Test
    fun highSpeedRotatesFasterThanLowSpeed() {
        val slow = ItemCustomizeColor(0xFF0000, chromaSpeed = 1)
        val fast = ItemCustomizeColor(0xFF0000, chromaSpeed = 255)
        assertEquals(0xFF0000, slow.argbAt(0f) and 0xFFFFFF)
        assertNotEquals(slow.argbAt(0.25f) and 0xFFFFFF, fast.argbAt(0.25f) and 0xFFFFFF)
    }
}

class CustomRenameTextTest {
    @Test
    fun normalizesNeuAmpersandsAndRemovesControlCharacters() {
        assertEquals("§aSword §lM7", CustomRenameText.normalize("&&aSword &lM7\n"))
    }

    @Test
    fun editorKeepsSingleAmpersands() {
        assertEquals("&l left alone", CustomRenameText.normalizeEditor("&l left alone"))
        assertEquals("§aColour", CustomRenameText.normalizeEditor("&&aColour"))
    }

    @Test
    fun masterStarsUseNeuGlyphs() {
        assertEquals("\u278A\u2792", CustomRenameText.normalizeEditor("*1*9"))
        assertEquals("\u278A", CustomRenameText.normalize("*1"))
        assertEquals(1, CustomRenameText.masterStarIndex('\u278A'))
        assertEquals(9, CustomRenameText.masterStarIndex('\u2792'))
        assertNull(CustomRenameText.masterStarIndex('1'))
    }

    @Test
    fun namesAreBounded() {
        assertEquals(CustomRenameText.MAX_LENGTH, CustomRenameText.normalize("x".repeat(300)).length)
    }

    @Test
    fun boundingNeverSplitsAPairAtTheLimit() {
        val max = CustomRenameText.MAX_LENGTH
        val dangling = "a".repeat(max - 1) + "§a" + "bbbb"
        assertFalse(CustomRenameText.bounded(dangling).endsWith("§"))
        assertEquals(max - 1, CustomRenameText.bounded(dangling).length)

        val emoji = "a".repeat(max - 1) + "\uD83D\uDE00" + "bb"
        val cut = CustomRenameText.bounded(emoji)
        assertEquals(max - 1, cut.length)
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test
    fun expandsChromaSpansPerCharacter() {
        assertEquals("§ca§cb", CustomRenameText.expandChroma("§zab", 0L, 100, { 1 }))
        assertEquals("plain", CustomRenameText.expandChroma("plain", 0L, 100, { 1 }))
    }

    @Test
    fun standaloneNeuRenameAliasOpensTheEditor() {
        assertTrue(CustomRename.isEditorCommand("/neurename"))
        assertTrue(CustomRename.isEditorCommand(".icantpy neurename"))
        assertTrue(CustomRename.isEditorCommand("/neucustomize"))
        assertTrue(CustomRename.isEditorCommand(".icantpy rename"))
        assertFalse(CustomRename.isEditorCommand("/neurename set Foo"))
    }

    @Test
    fun shortSetArgumentsDoNotThrow() {
        assertNull(CustomRename.setArgument("set"))
        assertNull(CustomRename.setArgument("s"))
        assertEquals("Foo", CustomRename.setArgument("set Foo"))
        assertEquals("Foo", CustomRename.setArgument("SET Foo"))
    }
}

class CustomRenameConfigTest {
    private val identity = ItemIdentity("item-uuid")

    @Test
    fun settingANameDoesNotMutateTheOriginalConfig() {
        val original = CustomRenameConfig()
        val renamed = original.withName(identity, "&&bNecron")

        assertTrue(original.names.isEmpty())
        assertEquals("§bNecron", renamed.names[identity.uuid])
    }

    @Test
    fun blankNameClearsTheEntry() {
        val renamed = CustomRenameConfig().withName(identity, "Name")
        assertFalse(renamed.withName(identity, " ").names.containsKey(identity.uuid))
    }

    @Test
    fun appearanceSettingsShareTheItemAndRemainImmutable() {
        val original = CustomRenameConfig().withName(identity, "Name")
        val changed = original.update(
            identity,
            original.item(identity)!!.copy(
                overrideEnchantGlint = true,
                enchantGlintValue = false,
                customGlintColor = ItemCustomizeColor(0x8040CC),
                customLeatherColor = ItemCustomizeColor(0x123456),
            ),
        )

        assertNull(original.item(identity)!!.customGlintColor)
        assertEquals(0x8040CC, changed.item(identity)!!.customGlintColor!!.rgb)
        assertEquals(0x123456, changed.item(identity)!!.customLeatherColor!!.rgb)
        assertEquals("Name", changed.names[identity.uuid])
    }
}

class ItemCustomizeStateTest {
    private val identity = ItemIdentity("uuid")

    private fun baseline(
        vanillaGlint: Boolean = true,
        specialRenderer: Boolean = false,
        leatherArmor: Boolean = false,
        vanillaLeatherColor: ItemCustomizeColor? = null,
        prefix: String = "",
    ) = ItemCustomizeBaseline(
        uuid = identity.uuid,
        vanillaGlint = vanillaGlint,
        specialRenderer = specialRenderer,
        leatherArmor = leatherArmor,
        vanillaLeatherColor = vanillaLeatherColor,
        originalNamePrefix = prefix,
    )

    @Test
    fun nameOnlyChangeDoesNotOverrideGlint() {
        val state = ItemCustomizeState.from(baseline(vanillaGlint = true), null).withName("&&bSword")
        val data = state.toItemData()
        assertEquals("§bSword", data.customName)
        assertFalse(data.overrideEnchantGlint)
        assertNull(data.customGlintColor)
    }

    @Test
    fun forcingGlintOffOnAVanillaGlintItemWritesAnOverride() {
        val state = ItemCustomizeState.from(baseline(vanillaGlint = true), null).setGlint(false)
        val data = state.toItemData()
        assertTrue(data.overrideEnchantGlint)
        assertFalse(data.enchantGlintValue)
        assertNull(data.customGlintColor)
    }

    @Test
    fun matchesVanillaGlintProducesNoOverride() {
        val state = ItemCustomizeState.from(baseline(vanillaGlint = false), null).setGlint(false)
        assertFalse(state.toItemData().overrideEnchantGlint)
    }

    @Test
    fun defaultGlintColourOnANormalItemIsNotStored() {
        val state = ItemCustomizeState.from(baseline(), null)
            .setGlint(true)
            .setGlintColor(ItemCustomizeColor.DEFAULT_GLINT)
        assertNull(state.toItemData().customGlintColor)
    }

    @Test
    fun specialRendererForcedOnKeepsNeuDefault() {
        val state = ItemCustomizeState.from(
            baseline(vanillaGlint = false, specialRenderer = true),
            null,
        ).setGlint(true)
        val data = state.toItemData()
        assertTrue(data.overrideEnchantGlint)
        assertTrue(data.enchantGlintValue)
        assertEquals(ItemCustomizeColor.DEFAULT_GLINT, data.customGlintColor)
    }

    @Test
    fun explicitColourIsStored() {
        val color = ItemCustomizeColor(0xFF0000, alpha = 128, chromaSpeed = 200)
        val state = ItemCustomizeState.from(baseline(), null).setGlint(true).setGlintColor(color)
        assertEquals(color, state.toItemData().customGlintColor)
    }

    @Test
    fun leatherMatchingOriginalDyeIsDropped() {
        val dye = ItemCustomizeColor(0xA06540)
        val state = ItemCustomizeState.from(
            baseline(vanillaGlint = false, leatherArmor = true, vanillaLeatherColor = dye),
            null,
        ).setLeatherColor(dye)
        assertNull(state.toItemData().customLeatherColor)
    }

    @Test
    fun leatherOverrideIsStoredAndResetRestoresUnderlyingDye() {
        val dye = ItemCustomizeColor(0xA06540)
        val chosen = ItemCustomizeColor(0x123456)
        val base = baseline(vanillaGlint = false, leatherArmor = true, vanillaLeatherColor = dye)
        val state = ItemCustomizeState.from(base, null).setLeatherColor(chosen)
        assertEquals(chosen, state.toItemData().customLeatherColor)
        assertNull(state.resetLeatherColor().toItemData().customLeatherColor)
        assertEquals(dye, state.resetLeatherColor().leatherPickerSeed)
    }

    @Test
    fun leatherColourOnNonLeatherItemsIsIgnored() {
        val state = ItemCustomizeState.from(baseline(), null)
            .setLeatherColor(ItemCustomizeColor(0x112233))
        assertNull(state.toItemData().customLeatherColor)
    }

    @Test
    fun prefixIsAppliedOnlyWhenANameExists() {
        val withName = ItemCustomizeState.from(baseline(prefix = "§5§l"), null).withName("Sword")
        assertEquals("§5§l", withName.toItemData().customNamePrefix)
        val withoutName = ItemCustomizeState.from(baseline(prefix = "§5§l"), null)
        assertEquals("", withoutName.toItemData().customNamePrefix)
    }

    @Test
    fun openingBaselineIsFixedAtConstruction() {
        val base = baseline(vanillaGlint = true)
        val state = ItemCustomizeState.from(base, null).setGlint(false)
        // Later preview toggles must not change what the stored baseline represents.
        assertEquals(base, state.baseline)
        assertTrue(state.toItemData().overrideEnchantGlint)
    }

    @Test
    fun initializationReadsStoredDataAndFallsBackToBaseline() {
        val stored = CustomRenameItemData(
            customName = "Stored",
            customNamePrefix = "§6",
            overrideEnchantGlint = true,
            enchantGlintValue = true,
            customGlintColor = ItemCustomizeColor(0x00FF00),
        )
        val state = ItemCustomizeState.from(baseline(vanillaGlint = false), stored)
        assertEquals("Stored", state.customName)
        assertTrue(state.glintEnabled)
        assertEquals(ItemCustomizeColor(0x00FF00), state.glintColor)
        assertEquals(ItemCustomizeColor(0x00FF00), state.glintPickerSeed)
    }

    @Test
    fun resetAllReturnsToCapturedBaseline() {
        val state = ItemCustomizeState.from(baseline(vanillaGlint = true), null)
            .withName("X")
            .setGlint(false)
            .setGlintColor(ItemCustomizeColor(0xFF0000))
        val reset = state.resetAll()
        assertEquals("", reset.customName)
        assertTrue(reset.glintEnabled)
        assertNull(reset.glintColor)
    }
}

class RenameTextEditorTest {
    @Test
    fun insertsAtCursorAndKeepsTextImmutable() {
        val editor = RenameTextEditor().insert("Sword")
        assertEquals("Sword", editor.text)
        assertEquals(5, editor.cursor)
        val moved = editor.moveTo(0, false).insert("§a")
        assertEquals("§aSword", moved.text)
        assertEquals("Sword", editor.text)
    }

    @Test
    fun replacesSelectionOnInsert() {
        val editor = RenameTextEditor("Sword").selectAll().insert("Axe")
        assertEquals("Axe", editor.text)
        assertEquals(3, editor.cursor)
    }

    @Test
    fun backspaceAndDeleteRespectSelection() {
        assertEquals("Swor", RenameTextEditor("Sword", cursor = 5).backspace().text)
        assertEquals("Swrd", RenameTextEditor("Sword", cursor = 2).delete().text)
        assertEquals("", RenameTextEditor("Sword").selectAll().backspace().text)
        assertEquals("Sword", RenameTextEditor("Sword", cursor = 0).backspace().text)
        assertEquals("Sword", RenameTextEditor("Sword", cursor = 5).delete().text)
    }

    @Test
    fun selectionMovementAndCopy() {
        val editor = RenameTextEditor("Sword").moveHorizontal(-5, extend = false).moveHorizontal(3, extend = true)
        assertTrue(editor.hasSelection)
        assertEquals("Swo", editor.selectedText())
        assertEquals(0, editor.selectionStart)
        assertEquals(3, editor.selectionEnd)
    }

    @Test
    fun pointerConvertsBetweenStoredAndDisplayForms() {
        assertEquals("¶aName", RenameTextEditor.toDisplay("§aName"))
        assertEquals("§aName", RenameTextEditor.toStored("¶aName"))
    }

    @Test
    fun cursorsAreClampedToTheTextBounds() {
        assertEquals(0, RenameTextEditor("abc").moveTo(-5, false).cursor)
        assertEquals(3, RenameTextEditor("abc").moveTo(99, false).cursor)
    }
}

class CustomRenameStoreTest {
    @Test
    fun parsesNamesAndIgnoresMalformedEntries() {
        val config = CustomRenameStore.parse(
            """
            {
              "version": 1,
              "names": {
                "good": "&&aAxe",
                "number": 4,
                "blank": "   "
              }
            }
            """.trimIndent(),
        )

        assertEquals("§aAxe", config.names["good"])
        assertFalse(config.names.containsKey("number"))
        assertFalse(config.names.containsKey("blank"))
    }

    @Test
    fun malformedJsonProducesAnEmptyConfig() {
        assertTrue(CustomRenameStore.parse("{not json").names.isEmpty())
    }

    @Test
    fun parsesNeuAppearanceFieldsInBothSpellings() {
        val config = CustomRenameStore.parse(
            """
            {"itemData":{
              "a":{"customName":"&&bSword","overrideEnchantGlint":true,"enchantGlintValue":false,"customGlintColour":"0:204:100:25:255","customLeatherColour":"112233"},
              "b":{"customGlintColor":"0:0:255:0:0","customLeatherColor":"#FF00FF"}
            }}
            """.trimIndent(),
        )

        val first = config.item(ItemIdentity("a"))!!
        assertEquals("§bSword", first.customName)
        assertTrue(first.overrideEnchantGlint)
        assertFalse(first.enchantGlintValue)
        assertEquals(ItemCustomizeColor.DEFAULT_GLINT, first.customGlintColor)
        assertEquals(0x112233, first.customLeatherColor!!.rgb)

        val second = config.item(ItemIdentity("b"))!!
        assertEquals(0xFF0000, second.customGlintColor!!.rgb)
        assertEquals(0xFF00FF, second.customLeatherColor!!.rgb)
    }

    @Test
    fun invalidColoursAreSkippedWithoutLosingOtherFields() {
        val config = CustomRenameStore.parse(
            """{"itemData":{"a":{"customName":"Keep","customGlintColor":"nonsense"}}}""",
        )
        val item = config.item(ItemIdentity("a"))!!
        assertEquals("Keep", item.customName)
        assertNull(item.customGlintColor)
    }

    @Test
    fun serializationRoundTripsStructuredColours() {
        val identity = ItemIdentity("round-trip")
        val config = CustomRenameConfig().update(
            identity,
            CustomRenameItemData(
                customName = "§aName",
                customNamePrefix = "§5",
                overrideEnchantGlint = true,
                enchantGlintValue = true,
                customGlintColor = ItemCustomizeColor(0x102030, alpha = 12, chromaSpeed = 200),
                customLeatherColor = ItemCustomizeColor(0x445566),
            ),
        )

        val reparsed = CustomRenameStore.parse(CustomRenameStore.serialize(config))
        assertEquals(config.item(identity), reparsed.item(identity))
        assertEquals(config.nameChromaSpeed, reparsed.nameChromaSpeed)
    }

    @Test
    fun nameChromaSpeedIsClampedToTheSupportedRange() {
        assertEquals(10, CustomRenameStore.parse("""{"nameChromaSpeed":5}""").nameChromaSpeed)
        assertEquals(5000, CustomRenameStore.parse("""{"nameChromaSpeed":9000}""").nameChromaSpeed)
        assertEquals(100, CustomRenameStore.parse("{}").nameChromaSpeed)
    }

    @Test
    fun customTooltipRoundTripsWithNewlines() {
        val identity = ItemIdentity("tooltip")
        val config = CustomRenameConfig().update(
            identity,
            CustomRenameItemData(customName = "Name", customTooltip = "\u00A7aLine1\n\u00A7bLine2"),
        )
        val reparsed = CustomRenameStore.parse(CustomRenameStore.serialize(config))
        assertEquals("\u00A7aLine1\n\u00A7bLine2", reparsed.item(identity)!!.customTooltip)
    }
}
