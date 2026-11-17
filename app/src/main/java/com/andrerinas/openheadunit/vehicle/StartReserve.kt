package com.andrerinas.openheadunit.vehicle

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Energy set aside at the start of a drive, taken off the energy left sent to Google Maps (field
 * 1.3): bringing the cabin and the battery to temperature costs about the same whatever the trip,
 * so it shows as a fixed offset. On 3 routes from 89 % at 13 °C (2026-11-17), Maps forecast 3 to 4
 * points more at arrival than ABRP whatever the distance (83/80, 66/62, 6/3): 0.9 kWh then.
 *
 * Worked out from the temperatures when the car is switched on, not when guidance starts:
 * [BASE_WH], plus the cabin (only while the climate is on, read live) and the battery (pack under [PACK_COLD_C]),
 * capped at [MAX_WH]. It fades with what the drive has already done: [FADE_KM] or [FADE_MIN],
 * whichever comes first, so guidance started in a warm car, or later on, sets nothing aside.
 * Theoretical figures except the 0.9 kWh at 13 °C, to be checked this winter.
 */
object StartReserve {
    const val BASE_WH = 300
    const val HEAT_WH_PER_C = 75f
    const val COOL_WH_PER_C = 50f
    const val PACK_WH_PER_C = 100f
    const val PACK_COLD_C = 5f
    const val DEFAULT_SET_C = 21f
    const val MAX_WH = 3_500
    const val FADE_KM = 15f
    const val FADE_MIN = 15f

    /**
     * The whole reserve, from the temperatures when the car was switched on. [cabinC] stands in
     * for nothing: without it the outside temperature is used, and without a setpoint 21 °C.
     */
    fun fullWh(climateOn: Boolean, setC: Float?, cabinC: Float?, outsideC: Float?, packC: Float?): Int {
        var wh = BASE_WH.toFloat()
        val cabin = cabinC ?: outsideC
        if (climateOn && cabin != null) {
            val diff = (setC ?: DEFAULT_SET_C) - cabin
            wh += if (diff > 0f) HEAT_WH_PER_C * diff else COOL_WH_PER_C * -diff
        }
        val pack = packC ?: outsideC
        if (pack != null && pack < PACK_COLD_C) wh += PACK_WH_PER_C * (PACK_COLD_C - pack)
        return wh.roundToInt().coerceIn(0, MAX_WH)
    }

    /** What is left of [fullWh] after [km] and [minutes] since the car was switched on. */
    fun remainingWh(fullWh: Int, km: Float, minutes: Float): Int {
        val done = max(km.coerceAtLeast(0f) / FADE_KM, minutes.coerceAtLeast(0f) / FADE_MIN).coerceAtMost(1f)
        return (fullWh * (1f - done)).roundToInt().coerceIn(0, fullWh)
    }

    /** The energy left to send: [currentWh] less [reserveWh], never below 1 Wh. */
    fun sentWh(currentWh: Int, reserveWh: Int): Int = (currentWh - reserveWh).coerceAtLeast(1)
}
