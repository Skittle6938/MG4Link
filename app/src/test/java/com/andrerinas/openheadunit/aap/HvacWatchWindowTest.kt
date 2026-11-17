package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Test

class HvacWatchWindowTest {
    @Test
    fun `the minute starts when Android Auto first shows, not when MG4Link started`() {
        // #32: MG4Link started with remote pre-conditioning at 0, Android Auto shown 6.5 min later.
        val shownAt = 390_000L
        assertEquals(60_000L, HvacWatchWindow.leftMs(shownAt, shownAt, 60_000L))
        assertEquals(20_000L, HvacWatchWindow.leftMs(shownAt, shownAt + 40_000L, 60_000L))
        assertEquals(-1_000L, HvacWatchWindow.leftMs(shownAt, shownAt + 61_000L, 60_000L))
    }
}
