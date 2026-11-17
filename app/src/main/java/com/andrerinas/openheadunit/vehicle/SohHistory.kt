package com.andrerinas.openheadunit.vehicle

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The capacity measurements behind the state of health, MG4 Dash's and MG4Link's side by side, in
 * the log: to see whether the measurement is steady, and how the two compare on the same car.
 * MG4 Dash's is the one used when it has one; MG4Link measures too, for the comparison.
 */
object SohHistory {
    private const val TAG = "DiAuto-MG4"
    private const val MAX_LINES = 12

    /** One line per sample: date, odometer (when known), kWh, drop, state of health. */
    fun lines(samples: List<BatteryCapacityMeter.Sample>, netKwh: Float?): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return samples.takeLast(MAX_LINES).map { s ->
            val soh = netKwh?.takeIf { it > 0f }?.let { " soh %.1f %%".format(Locale.US, s.kwh / it * 100f) } ?: ""
            val km = s.km?.let { "%.0f km".format(Locale.US, it) } ?: "? km"
            "${fmt.format(Date(s.at))} $km %.1f kWh over %.1f %%%s".format(Locale.US, s.kwh, s.drop, soh)
        }
    }

    /** The weighted figure each app uses, as a state of health of [netKwh]. */
    fun summary(label: String, kwh: Float?, count: Int, netKwh: Float?, points: Float = 0f): String {
        if (kwh == null) return "$label: no measurement"
        val soh = netKwh?.takeIf { it > 0f }?.let { " = soh %.1f %%".format(Locale.US, kwh / it * 100f) } ?: ""
        return "$label: %.2f kWh%s from %d sample(s) over %.0f points of battery".format(Locale.US, kwh, soh, count, points)
    }

    fun log(context: Context, why: String) {
        runCatching {
            val net = BatteryCapacityResolver.lastNetKwh
            val dash = Mg4DashBattery.read(context)
            val dashSamples = dash?.samples.orEmpty()
            val own = BatteryCapacityMeter.samples(context)
            AppLog.i("$TAG SOH history ($why), net ${net ?: "?"} kWh:")
            AppLog.i("$TAG   " + summary("MG4 Dash (used when there)", dash?.measuredKwh, dashSamples.size, net, dashSamples.sumOf { it.drop.toDouble() }.toFloat()))
            lines(dashSamples, net).forEach { AppLog.i("$TAG     MG4 Dash $it") }
            AppLog.i("$TAG   " + summary("MG4Link", BatteryCapacityMeter.capacityKwh(context), own.size, net, own.sumOf { it.drop.toDouble() }.toFloat()))
            lines(own, net).forEach { AppLog.i("$TAG     MG4Link $it") }
        }.onFailure { AppLog.w("$TAG SOH history not logged: ${it.message}") }
    }
}
