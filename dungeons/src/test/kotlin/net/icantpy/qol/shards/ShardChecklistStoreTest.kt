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
