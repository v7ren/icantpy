package net.icantpy.gui.config

import net.icantpy.dungeon.dragons.DragonSettings
import net.icantpy.dungeon.dragons.DragonTimerStyle
import net.icantpy.dungeon.dragons.SoloDebuffClass
import kotlin.test.Test
import kotlin.test.assertEquals

class DragonsPageCycleTest {
    @Test
    fun powerAddsOneAndWrapsAfterTheMaximum() {
        assertEquals(23, DragonsPage.nextPower(22))
        assertEquals(DragonSettings.MIN_POWER, DragonsPage.nextPower(DragonSettings.MAX_POWER))
    }

    @Test
    fun soloClassAndTimerStyleCycleThroughEveryOption() {
        assertEquals(SoloDebuffClass.HEALER, DragonsPage.nextSolo(SoloDebuffClass.TANK))
        assertEquals(SoloDebuffClass.TANK, DragonsPage.nextSolo(SoloDebuffClass.HEALER))
        assertEquals(DragonTimerStyle.SECONDS, DragonsPage.nextStyle(DragonTimerStyle.MILLISECONDS))
        assertEquals(DragonTimerStyle.MILLISECONDS, DragonsPage.nextStyle(DragonTimerStyle.TICKS))
    }
}
