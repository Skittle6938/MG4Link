package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class StartReserveTest {
    @Test
    fun `the whole reserve at the start`() {
        assertEquals(900, StartReserve.wh(0f))
    }

    @Test
    fun `it fades over the first 15 km`() {
        assertEquals(600, StartReserve.wh(5f))
        assertEquals(300, StartReserve.wh(10f))
        assertEquals(0, StartReserve.wh(15f))
        assertEquals(0, StartReserve.wh(120f))
    }

    @Test
    fun `about 2 points of a 45,9 kWh battery at the start`() {
        // Marc's car: 89 % sent as about 87 %.
        val current = (45_933 * 0.89).toInt()
        val sent = StartReserve.sentWh(current, StartReserve.wh(0f))
        assertEquals(87.0, sent * 100.0 / 45_933, 0.1)
    }

    @Test
    fun `never below 1 Wh`() {
        assertEquals(1, StartReserve.sentWh(500, 900))
    }
}
