package com.andrerinas.openheadunit.vehicle

import kotlin.math.roundToInt

/**
 * Energy set aside at the start of a drive, taken off the energy left sent to Google Maps (field
 * 1.3). Warming up the cabin, the battery and the electronics costs about the same whatever the
 * trip, so it shows as a fixed offset: on 3 routes from 89 % at 13 °C (2026-11-17), Maps forecast
 * 3 to 4 points more at arrival than ABRP, whatever the distance (83/80, 66/62, 6/3). The reserve
 * fades to nothing over the first [FADE_KM], as that energy gets spent. Test value, from one car.
 */
object StartReserve {
    const val RESERVE_WH = 900
    const val FADE_KM = 15f

    /** Reserve in Wh after [kmSinceStart] km driven since the car was switched on. */
    fun wh(kmSinceStart: Float): Int =
        (RESERVE_WH * (1f - kmSinceStart.coerceAtLeast(0f) / FADE_KM)).roundToInt().coerceIn(0, RESERVE_WH)

    /** The energy left to send: [currentWh] less [reserveWh], never below 1 Wh. */
    fun sentWh(currentWh: Int, reserveWh: Int): Int = (currentWh - reserveWh).coerceAtLeast(1)
}
