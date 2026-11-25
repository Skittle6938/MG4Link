package com.andrerinas.openheadunit.aap

/** A radio band a soft AP can be brought up on. */
enum class ApBand { BAND_5GHZ, BAND_2GHZ }

/**
 * Which band to bring the head unit's access point up on for wireless Android Auto, and in what
 * order to try.
 *
 * 5 GHz first is not a preference, it is what the link needs. On 2.4 GHz the handshake completes
 * and the projection then dies within seconds — a failure mode that reads as "Android Auto starts
 * and drops" and has cost several rounds of log-reading to recognise. The reference head unit
 * software running this route on the same class of hardware brings its AP up on channel 36, and
 * the AA protocol itself carries the phone's answer to the same question (`selected_wifi_channel
 * _type`, one of 2.4-only / 5-only / dual).
 *
 * There is no 2.4 GHz fallback any more: every MG4 radio hosts its access point on 5 GHz, and the
 * retry on 2.4 GHz only ever took down a 5 GHz access point that was arriving late (#73).
 */
object SoftApBandPolicy {

    /** `SoftApConfiguration.BAND_2GHZ` / `BAND_5GHZ` (API 30+). */
    fun softApConfigurationBand(band: ApBand): Int = when (band) {
        ApBand.BAND_2GHZ -> 1
        ApBand.BAND_5GHZ -> 2
    }

    /** The hidden `WifiConfiguration.apBand` field's values, used below API 30. */
    fun legacyApBand(band: ApBand): Int = when (band) {
        ApBand.BAND_2GHZ -> 0
        ApBand.BAND_5GHZ -> 1
    }

    /** How the band reads in a log line users paste into bug reports. */
    fun describe(band: ApBand): String = when (band) {
        ApBand.BAND_2GHZ -> "2.4 GHz"
        ApBand.BAND_5GHZ -> "5 GHz"
    }
}
