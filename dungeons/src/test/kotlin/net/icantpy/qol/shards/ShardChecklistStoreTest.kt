package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.nio.file.Files

class ShardChecklistStoreTest {
    @Test fun roundTripAndMalformedBackup() {
        val dir = Files.createTempDirectory("icantpy-shards")
        val path = dir.resolve("checklist.json")
        val catalog = listOf(ShardDefinition("Flash", "Light", "", costs = List(10) { 1 }))
        val source = ShardChecklist("test", listOf(ShardChecklistEntry("Flash", 4)))
        ShardChecklistStore.save(path, source)
        assertEquals(4, ShardChecklistStore.load(path, catalog)!!.shards.single().targetLevel)
        Files.writeString(path, "{invalid")
        assertEquals(null, ShardChecklistStore.load(path, catalog))
        assertNotNull(Files.list(dir).use { stream -> stream.filter { it.fileName.toString().contains(".invalid.") }.findFirst().orElse(null) })
    }
}

class ShardChecklistEnabledTest {
    private val catalog = listOf(ShardDefinition("Flash", "Light", "", costs = List(10) { 1 }))

    @Test fun enabledFlagPersistsAcrossSaveAndLoad() {
        val path = Files.createTempDirectory("icantpy-shards-enabled").resolve("checklist.json")
        ShardChecklistStore.save(path, ShardChecklist("test", listOf(ShardChecklistEntry("Flash", 4)), enabled = false))
        val loaded = ShardChecklistStore.load(path, catalog)!!
        assertEquals(false, loaded.enabled)
        assertEquals(4, loaded.shards.single().targetLevel)
    }

    @Test fun oldFileWithoutEnabledKeyLoadsAsEnabled() {
        val path = Files.createTempDirectory("icantpy-shards-old").resolve("checklist.json")
        Files.writeString(path, """{"name":"test","shards":[{"name":"Flash","targetLevel":3}],"version":1}""")
        assertEquals(true, ShardChecklistStore.load(path, catalog)!!.enabled)
    }

    @Test fun exportedTextDoesNotCarryTheLocalSwitch() {
        val encoded = ShardChecklistCodec.encode(ShardChecklist("x", listOf(ShardChecklistEntry("Flash", 2)), enabled = false))
        val json = String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        assertEquals(false, json.contains("enabled"))
        assertEquals(true, ShardChecklistCodec.decode(encoded, catalog).checklist!!.enabled)
    }
}
