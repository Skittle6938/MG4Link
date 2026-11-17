package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Test

class HvacCloseWhenTest {
    private fun run(vararg readings: Pair<Boolean?, Boolean>): List<Boolean> {
        val c = HvacCloseWhen()
        return readings.map { (power, front) -> c.onReading(power, front) }
    }

    @Test
    fun `cold start, climate on and Android Auto in front, closes once`() {
        assertEquals(listOf(true, false, false), run(true to true, true to true, true to true))
    }

    @Test
    fun `remote pre-conditioning, screen paused at the first reading, closes when it is back in front`() {
        // #32: the climate was on at the first reading while the screen was paused.
        assertEquals(listOf(false, false, true, false), run(true to false, true to false, true to true, true to true))
    }

    @Test
    fun `climate off never closes`() {
        assertEquals(listOf(false, false, false), run(false to true, false to true, false to false))
    }

    @Test
    fun `climate switched on later closes again`() {
        assertEquals(listOf(true, false, false, true), run(true to true, false to true, false to true, true to true))
    }

    @Test
    fun `an unreadable climate changes nothing`() {
        assertEquals(listOf(false, false, true), run(true to false, null to true, true to true))
    }

    @Test
    fun `the climate going off while waiting cancels the close`() {
        assertEquals(listOf(false, false, false), run(true to false, false to false, false to true))
    }
}
