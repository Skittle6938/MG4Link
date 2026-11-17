package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SohHistoryTest {
    @Test
    fun `a sample reads as date, odometer, kWh, drop and state of health`() {
        val line = SohHistory.lines(listOf(BatteryCapacityMeter.Sample(0L, 45.9f, 15.2f, 12_345f)), 49f).single()
        assertTrue(line, line.endsWith("12345 km 45.9 kWh over 15.2 % soh 93.7 %"))
    }

    @Test
    fun `an unknown odometer or net capacity is said so`() {
        val line = SohHistory.lines(listOf(BatteryCapacityMeter.Sample(0L, 45.9f, 15.2f)), null).single()
        assertTrue(line, line.endsWith("? km 45.9 kWh over 15.2 %"))
    }

    @Test
    fun `only the last samples are listed`() {
        val many = (1..30).map { BatteryCapacityMeter.Sample(it.toLong(), 45f, 15f) }
        assertEquals(12, SohHistory.lines(many, 49f).size)
    }

    @Test
    fun `the summary gives the state of health of the figure in use`() {
        assertEquals("MG4Link: 45.93 kWh = soh 93.7 % from 4 sample(s)", SohHistory.summary("MG4Link", 45.93f, 4, 49f))
        assertEquals("MG4Link: no measurement", SohHistory.summary("MG4Link", null, 0, 49f))
    }
}
