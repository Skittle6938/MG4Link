package com.andrerinas.openheadunit.vehicle

import com.evsuite.hardware.EVHardware
import com.evsuite.hardware.saic.SaicClimate

/**
 * When the car was switched on, and its temperatures then, for [StartReserve]. Fed every second
 * while the car is on (whether or not Android Auto is connected); a gap longer than [GAP_MS] means
 * the car was off, and the next reading starts a new drive. The climate and its setpoint are read
 * live for as long as the reserve lasts: switched on or off during the first 15 km or 15 minutes,
 * the reserve follows.
 */
object DriveStart {
    private const val GAP_MS = 20 * 60_000L

    @Volatile private var startAtMs = 0L
    @Volatile private var lastSeenMs = 0L
    @Volatile private var startOdometerKm: Float? = null
    @Volatile var cabinC: Float? = null; private set
    @Volatile var outsideC: Float? = null; private set
    @Volatile var packC: Float? = null; private set
    @Volatile var setC: Float? = null; private set
    @Volatile var climateOn = false; private set

    @Synchronized
    fun onReading(nowMs: Long) {
        if (startAtMs == 0L || nowMs - lastSeenMs > GAP_MS) {
            startAtMs = nowMs
            startOdometerKm = read { EVHardware.getOdometerKm() }
            cabinC = read { EVHardware.getCabinTemperatureCelsius() }?.takeIf { it in -40f..80f }
            outsideC = read { SaicClimate.outsideTempCelsius() ?: EVHardware.getOutsideTempCelsius() }?.takeIf { it in -40f..60f }
            packC = read { EVHardware.getBatteryTemperatureCelsius() }?.takeIf { it in -40f..80f }
            setC = read { EVHardware.getTemperatureSetCelsius() }?.takeIf { it in 10f..35f }
            climateOn = read { SaicClimate.powerOn() } == true
        }
        if (startOdometerKm == null) startOdometerKm = read { EVHardware.getOdometerKm() }
        lastSeenMs = nowMs
    }

    /** The reserve now; 0 before the first reading. */
    fun reserveWh(nowMs: Long): Int {
        if (startAtMs == 0L) return 0
        // The climate as it is now, the temperatures as they were at the start.
        read { SaicClimate.powerOn() }?.let { climateOn = it }
        read { EVHardware.getTemperatureSetCelsius() }?.takeIf { it in 10f..35f }?.let { setC = it }
        val full = StartReserve.fullWh(climateOn, setC, cabinC, outsideC, packC)
        val odometer = read { EVHardware.getOdometerKm() }
        val start = startOdometerKm
        val km = if (odometer != null && start != null) odometer - start else 0f
        val minutes = (nowMs - startAtMs) / 60_000f
        val remaining = StartReserve.remainingWh(full, km, minutes)
        return StartReserve.withinRouteWh(full, remaining, RouteLeft.km(nowMs))
    }

    fun describe(): String =
        "climate=${if (climateOn) "on" else "off"} set=${setC ?: "-"} cabin=${cabinC ?: "-"} outside=${outsideC ?: "-"} pack=${packC ?: "-"}"

    private inline fun <T> read(block: () -> T?): T? = runCatching(block).getOrNull()
}
