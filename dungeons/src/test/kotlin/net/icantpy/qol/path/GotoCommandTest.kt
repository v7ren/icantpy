package net.icantpy.qol.path

import kotlin.test.Test
import kotlin.test.assertEquals

class GotoCommandTest {
    @Test
    fun formatsPlainBlockCoordinates() {
        assertEquals("/icantpy goto -35 176 78", gotoCommand(-35, 176, 78))
        assertEquals("/icantpy goto 0 -64 1200", gotoCommand(0, -64, 1200))
    }
}
