package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShardChecklistCodecTest {
    private val catalog = listOf(
        ShardDefinition("Flash", "Light Elemental", "COMMON", aliases = listOf("Light Elemental"), costs = listOf(1, 3, 5)),
        ShardDefinition("Burning Soul", "Inferno", "RARE", costs = listOf(2, 4, 8))
    )

    @Test fun roundTripJsonAndBase64() {
        val source = ShardChecklist("x", listOf(ShardChecklistEntry("Flash", 3)))
        val result = ShardChecklistCodec.decode(ShardChecklistCodec.encode(source), catalog)
        assertEquals("Flash", result.checklist!!.shards.single().name)
        assertEquals(3, result.checklist!!.shards.single().targetLevel)
    }

    @Test fun roundTripUrlSafeBase64AndRawJson() {
        val source = ShardChecklist("x", listOf(ShardChecklistEntry("Flash", 4)))
        val json = String(java.util.Base64.getDecoder().decode(ShardChecklistCodec.encode(source)))
        val encoded = java.util.Base64.getUrlEncoder().encodeToString(json.toByteArray())
        assertEquals(4, ShardChecklistCodec.decode(encoded, catalog).checklist!!.shards.single().targetLevel)
        assertEquals(4, ShardChecklistCodec.parseJson(json, catalog).checklist!!.shards.single().targetLevel)
    }

    @Test fun markdownResolvesAbilityMobAndAliases() {
        val markdown = "# Shards\n\n- **Light Elemental (Flash) -- 10 Strength**\n- Blazing Resistance (Bezal) -- effect\n- **Inferno (Burning Soul) -- effect**"
        val result = ShardChecklistCodec.parseMarkdown(markdown, catalog + ShardDefinition("Blazing Resistance", "Bezal", "COMMON"))
        assertNotNull(result.checklist)
        assertEquals(listOf("Flash", "Blazing Resistance", "Burning Soul"), result.checklist!!.shards.map { it.name })
    }

    @Test fun rejectsUnknownAndDeduplicates() {
        val result = ShardChecklistCodec.parseJson("{\"version\":1,\"shards\":[\"Flash\",{\"name\":\"flash\",\"targetLevel\":2},\"Nope\"]}", catalog)
        assertEquals(null, result.checklist)
        assertTrue(result.unknown.contains("Nope"))
    }

    @Test fun deduplicatesAliasesUsingHighestTargetAndRejectsUnknownTransactionally() {
        val result = ShardChecklistCodec.parseJson("{\"shards\":[{\"name\":\"Flash\",\"targetLevel\":2},{\"name\":\"Light Elemental\",\"targetLevel\":7},\"Nope\"]}", catalog)
        assertEquals(null, result.checklist)
        assertTrue(result.unknown.contains("Nope"))
        val valid = ShardChecklistCodec.parseJson("{\"shards\":[{\"name\":\"Flash\",\"targetLevel\":2},{\"name\":\"Light Elemental\",\"targetLevel\":7}]}", catalog)
        assertEquals(7, valid.checklist!!.shards.single().targetLevel)
    }

    @Test fun rejectsFractionalAndNonStringFields() {
        listOf(
            "{\"version\":1.5,\"shards\":[]}",
            "{\"shards\":[{\"name\":\"Flash\",\"targetLevel\":\"2\"}]}",
            "{\"shards\":[{\"name\":true}]}",
            "{\"shards\":[null]}"
        ).forEach { assertEquals(null, ShardChecklistCodec.parseJson(it, catalog).checklist) }
    }

    @Test fun rejectsOversizedInputsBeforeParsing() {
        val result = ShardChecklistCodec.decode("x".repeat(90_001), catalog)
        assertEquals(null, result.checklist)
        assertTrue(result.error!!.contains("large"))
        assertEquals(null, ShardChecklistCodec.parseJson("{}" + " ".repeat(64_001), catalog).checklist)
        assertEquals(null, ShardChecklistCodec.parseMarkdown("- Flash -- effect\n" + "x".repeat(64_001), catalog).checklist)
    }

    @Test fun rejectsMalformedBase64AndUtf8() {
        assertEquals(null, ShardChecklistCodec.decode("%%%", catalog).checklist)
        val malformed = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0xc3.toByte(), 0x28))
        assertEquals(null, ShardChecklistCodec.decode(malformed, catalog).checklist)
    }

    @Test fun actualCatalogRoundTripsDefaultAndResolvesStarbornCollision() {
        val definitions = ShardCatalog.definitions
        val encoded = ShardChecklistCodec.encode(ShardCatalog.defaultChecklist())
        val imported = ShardChecklistCodec.decode(encoded, definitions)
        assertNotNull(imported.checklist)
        assertEquals(ShardCatalog.defaultChecklist().shards.size, imported.checklist!!.shards.size)
        val starborn = ShardChecklistCodec.parseJson("{\"shards\":[\"Starborn\"]}", definitions)
        assertEquals("Starborn", starborn.checklist!!.shards.single().name)
        val sunFish = ShardChecklistCodec.parseJson("{\"shards\":[\"Sun Fish\"]}", definitions)
        assertEquals("Sun Fish", sunFish.checklist!!.shards.single().name)
    }

    @Test fun progressDoesNotInventMissing() {
        val session = ShardSession()
        session.merge(ShardObservation("Flash", level = 3))
        assertEquals(null, session.get("Flash")!!.owned)
        assertTrue(session.shouldResetForChat("You are playing on profile: Ironman"))
    }
}
