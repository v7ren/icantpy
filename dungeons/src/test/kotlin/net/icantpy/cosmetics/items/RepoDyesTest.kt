package net.icantpy.cosmetics.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepoDyesTest {
    @Test
    fun bundledDyeCatalogLoadsStaticAndAnimatedEntries() {
        assertTrue(RepoDyes.names().isNotEmpty())
        assertEquals(0x960018, RepoDyes.staticColor("DYE_CARMINE"))
        assertNotNull(RepoDyes.animatedColors("DYE_ROSE"))
    }
}
