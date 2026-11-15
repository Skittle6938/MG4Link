package com.andrerinas.openheadunit.vehicle

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

/**
 * Wire encoder for Android Auto VehicleEnergyModel (sensor type 23/25).
 *
 * The message has exactly the shape a production car sends (a Ford, captured by the aa-proxy-rs
 * project, decoded field by field 2026-11-15), and nothing that car does not send:
 *
 *  - 1 = battery and air: 3 energy left (Wh), 4 capacity (Wh), 6 air density (kg/m³) for which the
 *    air term below holds, 7 outside temperature (°C). There is no percentage field: Maps shows
 *    energy left / capacity, so the % it shows at departure is the car's.
 *  - 2 = road load F = A + B·v + C·v² (N, v in m/s): three messages holding only their value.
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
    }

    /** Dry air at sea level, kg/m³. */
    private fun airDensity(tempC: Float): Float = (101_325.0 / (287.05 * (tempC + 273.15))).toFloat()

    /** Road load: constant (N), linear (N·s/m) and quadratic (N·s²/m²) terms. */
    private fun encodeRoadLoad(a: Float, b: Float, c: Float): ByteArray = message { cos ->
        cos.writeByteArray(1, coefficient(a))
        cos.writeByteArray(2, coefficient(b))
        cos.writeByteArray(3, coefficient(c))
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

    /** A road-load term: its value alone, as the production car sends it. */
    private fun coefficient(value: Float): ByteArray = message { it.writeFloat(1, value) }

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
