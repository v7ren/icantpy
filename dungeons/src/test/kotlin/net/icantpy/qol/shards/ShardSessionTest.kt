package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShardSessionTest {
    @Test fun mergesPartialObservationsAndClearsStaleSyphonedOnLevelChange() {
        val session = ShardSession()
        session.merge(ShardObservation("Flash", level = 3, syphoned = 13))
        session.merge(ShardObservation("Flash", owned = 20))
        assertEquals(ShardProgress(3, 20, 13), session.get("flash"))
        session.merge(ShardObservation("Flash", level = 4))
        assertEquals(ShardProgress(4, 20, null), session.get("Flash"))
    }

    @Test fun snapshotsAreImmutableAndProfileResetsOncePerProfile() {
        val session = ShardSession()
        session.merge(ShardObservation("Flash", level = 3))
        val snapshot = session.snapshot()
        session.clear()
        assertEquals(1, snapshot.size)
        assertTrue(session.shouldResetForChat("§eYou are playing on profile: Ironman"))
        session.resetObservations()
        session.merge(ShardObservation("Flash", owned = 2))
        assertFalse(session.shouldResetForChat("You are playing on profile: Ironman"))
        assertTrue(session.shouldResetForChat("Your profile was changed to: Normal"))
        assertNull(session.get("Flash"))
    }
}
