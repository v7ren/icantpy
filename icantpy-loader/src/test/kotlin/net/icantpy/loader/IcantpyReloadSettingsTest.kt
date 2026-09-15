package net.icantpy.loader

import kotlin.test.Test
import kotlin.test.assertEquals

class IcantpyReloadSettingsTest {
    @Test
    fun toggleCyclesBetweenLocalAndUpdate() {
        assertEquals(ReloadSource.LOCAL, ReloadSource.UPDATE.toggle())
        assertEquals(ReloadSource.UPDATE, ReloadSource.LOCAL.toggle())
    }

    @Test
    fun fromNameDefaultsToUpdate() {
        assertEquals(ReloadSource.UPDATE, ReloadSource.fromName(null))
        assertEquals(ReloadSource.LOCAL, ReloadSource.fromName("local"))
    }
}
