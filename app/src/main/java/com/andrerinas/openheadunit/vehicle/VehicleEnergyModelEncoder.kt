package com.andrerinas.openheadunit.vehicle

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

/**
 * Wire encoder for Android Auto VehicleEnergyModel (sensor type 23/25).
 *
 * Fields as Google Maps parses them (schema recovered from Maps' own code by the egmp-aa-ev-feat
 * project): Maps reads only the energy left and the capacity on the phone; everything else goes
 * unchanged to Google's servers, where the prediction is made.
 *
 *  - 1 = battery and air: 3 energy left (Wh), 4 capacity (Wh), 6 air density (kg/m³) for which the
 *    air term below holds, 7 outside temperature (°C), 9 and 10 two powers in W (Maps' own demo
 *    models set them; here peak motor power and max DC power). There is no percentage field: Maps
 *    shows energy left / capacity, so the % it shows at departure is the car's.
 *  - 2 = road load F = A + B·v + C·v² (N, v in m/s), each term {1 value, 2 a second float}.
 *    2.2.5-beta24 to 2.2.6 dropped the second floats and fields 1.9 / 1.10 to copy a Ford capture,
 *    and Maps became ~4 points more optimistic on a daily 10 % trip, against ~1 point before:
 *    the servers do use them. They are back as they were.
 *  - 4 = DC charging curve: (energy in the pack Wh, max power W) every 5 %.
 *  - 7 = climate and accessories power by outside temperature: {1 °C, 4 W}.
 *
 * Two road loads, as chosen in Settings > Car: MG4Link's consumption model ([Mg4MapsEnergyModel],
 * the default, the one being calibrated) or a constant force taken from the car's own range.
 * The car's range itself is not sent: Maps works its range out from the energy and the road load.
 */
object VehicleEnergyModelEncoder {

    private const val MIN_WH_PER_KM = 100f
    private const val MAX_WH_PER_KM = 220f
    private const val DEFAULT_WH_PER_KM = 160f // typical MG4 Long Range ballpark

    data class Snapshot(
        val capacityWh: Int,
        val currentWh: Int,
        /** The car's range: only for the road load from the range (no consumption model). */
        val rangeMeters: Int,
        val batteryPercent: Double = 0.0,
        val maxChargePowerW: Int = 140_000,
        val peakMotorPowerW: Int = 150_000,
        val variant: Mg4Variant? = null,
        val outsideTempC: Float? = null,
        /** MG4Link's road load, climate power and temperature; null = constant force from the range. */
        val energyModel: Mg4MapsEnergyModel.Model? = null,
    )

    fun encode(snapshot: Snapshot): ByteArray {
        require(snapshot.capacityWh > 0)
        require(snapshot.currentWh > 0)
        require(snapshot.rangeMeters > 0)

        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        val model = snapshot.energyModel
        // With the model, its own temperature, the one its air term was worked out for.
        val temp = if (model != null) model.tempC else snapshot.outsideTempC
        cos.writeByteArray(1, encodeBattery(snapshot, temp))
        if (model != null) {
            cos.writeByteArray(2, encodeRoadLoad(model.a, 0f, model.c))
        } else {
            // F ≈ Wh/km · 3.6 N at any speed with B = C = 0.
            cos.writeByteArray(2, encodeRoadLoad((derivedWhPerKm(snapshot) * 3.6f).coerceIn(200f, 900f), 0f, 0f))
        }
        snapshot.variant?.let { cos.writeByteArray(4, encodeChargeCurve(snapshot.capacityWh, it)) }
        model?.aux?.forEach { (tempC, watts) ->
            cos.writeByteArray(7, message { row ->
                row.writeFloat(1, tempC)
                row.writeByteArray(4, valueOf(watts))
            })
        }
        cos.flush()
        return out.toByteArray()
    }

    private fun derivedWhPerKm(snapshot: Snapshot): Float {
        val fromRange = snapshot.currentWh.toFloat() / snapshot.rangeMeters.toFloat() * 1000f
        return if (fromRange.isFinite() && fromRange > 0f) {
            fromRange.coerceIn(MIN_WH_PER_KM, MAX_WH_PER_KM)
        } else {
            DEFAULT_WH_PER_KM
        }
    }

    /**
     * Field 1. Ford's 1.2 (3175 for a 92 kWh pack) is most likely a reserve in Wh, not a mass, so it
     * is left out rather than guessed.
     */
    private fun encodeBattery(snapshot: Snapshot, tempC: Float?): ByteArray = message { cos ->
        cos.writeByteArray(3, valueOf(snapshot.currentWh))
        cos.writeByteArray(4, valueOf(snapshot.capacityWh))
        tempC?.takeIf { it in -40f..60f }?.let { temp ->
            cos.writeFloat(6, airDensity(temp))
            cos.writeFloat(7, temp)
        }
        cos.writeUInt32(9, snapshot.peakMotorPowerW.coerceAtLeast(1))
        cos.writeUInt32(10, (snapshot.variant?.maxDcW ?: snapshot.maxChargePowerW).coerceAtLeast(1))
    }

    /** Dry air at sea level, kg/m³. */
    private fun airDensity(tempC: Float): Float = (101_325.0 / (287.05 * (tempC + 273.15))).toFloat()

    /** Road load: constant (N), linear (N·s/m) and quadratic (N·s²/m²) terms. */
    private fun encodeRoadLoad(a: Float, b: Float, c: Float): ByteArray = message { cos ->
        cos.writeByteArray(1, coefficient(a, 10f))
        cos.writeByteArray(2, coefficient(b, 0.25f))
        cos.writeByteArray(3, coefficient(c, 0.125f))
    }

    /** Maximum DC power the pack takes, as (energy in the pack Wh, power W) every 5 %. */
    private fun encodeChargeCurve(capacityWh: Int, variant: Mg4Variant): ByteArray = message { cos ->
        for (percent in 5..100 step 5) {
            cos.writeByteArray(1, message { point ->
                point.writeUInt64(1, capacityWh * percent / 100L)
                point.writeUInt64(2, variant.dcPowerW(percent.toDouble()).toLong())
            })
        }
    }

    private fun valueOf(value: Int): ByteArray = message { it.writeInt32(1, value) }

    /** A road-load term: its value, and the second float sent since 2.2.0 (see the class comment). */
    private fun coefficient(value: Float, second: Float): ByteArray = message { cos ->
        cos.writeFloat(1, value)
        cos.writeFloat(2, second)
    }

    private inline fun message(write: (CodedOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        write(cos)
        cos.flush()
        return out.toByteArray()
    }

    /** Exposed for unit tests — Wh/km implied by the snapshot after clamping. */
    internal fun whPerKmForTest(snapshot: Snapshot): Float = derivedWhPerKm(snapshot)

    /** Exposed for unit tests — constant force (N) written into road-load field 1. */
    internal fun constantForceNForTest(snapshot: Snapshot): Float =
        (derivedWhPerKm(snapshot) * 3.6f).coerceIn(200f, 900f)
}
