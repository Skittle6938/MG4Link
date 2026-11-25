package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class StartReserveTest {
    @Test
    fun `0,9 kWh at 13 degrees with the heating on, as measured against ABRP`() {
        assertEquals(900, StartReserve.fullWh(climateOn = true, setC = 21f, cabinC = 13f, outsideC = 13f, packC = 15f))
    }

    @Test
    fun `climate off or the cabin already at the setpoint gives the base only`() {
        assertEquals(300, StartReserve.fullWh(climateOn = false, setC = 21f, cabinC = 13f, outsideC = 13f, packC = 15f))
        assertEquals(300, StartReserve.fullWh(climateOn = true, setC = 21f, cabinC = 21f, outsideC = 13f, packC = 15f))
    }

    @Test
    fun `cooling costs less per degree than heating`() {
        assertEquals(1_000, StartReserve.fullWh(climateOn = true, setC = 21f, cabinC = 35f, outsideC = 30f, packC = 30f))
    }

    @Test
    fun `a cold pack adds its own share`() {
        // -5 °C: cabin 26 °C to heat (1 950 Wh) and the pack 10 °C under 5 °C (1 000 Wh).
        assertEquals(3_250, StartReserve.fullWh(climateOn = true, setC = 21f, cabinC = -5f, outsideC = -5f, packC = -5f))
    }

    @Test
    fun `capped at 3,5 kWh`() {
        assertEquals(3_500, StartReserve.fullWh(climateOn = true, setC = 22f, cabinC = -15f, outsideC = -15f, packC = -15f))
    }

    @Test
    fun `without the cabin or the pack, the outside temperature stands in, and 21 degrees for the setpoint`() {
        assertEquals(900, StartReserve.fullWh(climateOn = true, setC = null, cabinC = null, outsideC = 13f, packC = null))
        assertEquals(300 + 75 * 21 + 500, StartReserve.fullWh(climateOn = true, setC = null, cabinC = null, outsideC = 0f, packC = null))
    }

    @Test
    fun `it fades over 15 km or 15 minutes, whichever comes first`() {
        assertEquals(900, StartReserve.remainingWh(900, 0f, 0f))
        assertEquals(450, StartReserve.remainingWh(900, 7.5f, 2f))
        assertEquals(450, StartReserve.remainingWh(900, 1f, 7.5f))
        assertEquals(0, StartReserve.remainingWh(900, 15f, 3f))
        // Guidance started in a car that has been on for 20 minutes: nothing.
        assertEquals(0, StartReserve.remainingWh(900, 0f, 20f))
    }

    @Test
    fun `about 2 points of a 45,9 kWh battery at the start, never below 1 Wh`() {
        val current = (45_933 * 0.89).toInt()
        assertEquals(87.0, StartReserve.sentWh(current, 900) * 100.0 / 45_933, 0.1)
        assertEquals(1, StartReserve.sentWh(500, 900))
    }

    @Test
    fun `a short guided trip sets aside only what it spends`() {
        // 4 km to go: 4/15 of 900 Wh.
        assertEquals(154, StartReserve.withinRouteWh(900, 900, 4f))
        // A longer route, or no guidance: what is left of the reserve.
        assertEquals(900, StartReserve.withinRouteWh(900, 900, 40f))
        assertEquals(450, StartReserve.withinRouteWh(900, 450, 40f))
        assertEquals(900, StartReserve.withinRouteWh(900, 900, null))
        assertEquals(0, StartReserve.withinRouteWh(900, 900, 0f))
    }

    @Test fun `short guided routes set aside even less, from 5 km nothing changes`() {
        assertEquals(0.0213f, StartReserve.routeShare(2f), 0.0005f)
        assertEquals(0.1707f, StartReserve.routeShare(4f), 0.0005f)
        assertEquals(0.3333f, StartReserve.routeShare(5f), 0.0005f)
        assertEquals(0.6667f, StartReserve.routeShare(10f), 0.0005f)
    }
}
