package com.andrerinas.openheadunit.vehicle

import com.evsuite.hardware.EVHardware
import com.evsuite.hardware.saic.SaicClimate

/**
 * When the car was switched on, and its temperatures then, for [StartReserve]. Fed every second
 * while the car is on (whether or not Android Auto is connected); a gap longer than [GAP_MS] means
 * the car was off, and the next reading starts a new drive. The climate counts if it is switched on
 * in the first [CLIMATE_WINDOW_MS], as it often is a few seconds after the start.
 */
object DriveStart {
    private const val GAP_MS = 20 * 60_000L
    private const val CLIMATE_WINDOW_MS = 2 * 60_000L

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
        } else if (!climateOn && nowMs - startAtMs < CLIMATE_WINDOW_MS && read { SaicClimate.powerOn() } == true) {
            climateOn = true
            setC = read { EVHardware.getTemperatureSetCelsius() }?.takeIf { it in 10f..35f } ?: setC
        }
        if (startOdometerKm == null) startOdometerKm = read { EVHardware.getOdometerKm() }
        lastSeenMs = nowMs
    }

    /** The reserve now; 0 before the first reading. */
    fun reserveWh(nowMs: Long): Int {
        if (startAtMs == 0L) return 0
        val full = StartReserve.fullWh(climateOn, setC, cabinC, outsideC, packC)
        val odometer = read { EVHardware.getOdometerKm() }
        val start = startOdometerKm
        val km = if (odometer != null && start != null) odometer - start else 0f
        val minutes = (nowMs - startAtMs) / 60_000f
        return StartReserve.remainingWh(full, km, minutes)
    }

    fun describe(): String =
        "climate=${if (climateOn) "on" else "off"} set=${setC ?: "-"} cabin=${cabinC ?: "-"} outside=${outsideC ?: "-"} pack=${packC ?: "-"}"

    private inline fun <T> read(block: () -> T?): T? = runCatching(block).getOrNull()
}
