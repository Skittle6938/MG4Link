package com.andrerinas.openheadunit.vehicle

import org.json.JSONArray
import org.json.JSONObject

/**
 * The figures of one recorded trip, for tuning the Maps energy model: no position, road or phone.
 * A new segment starts whenever the climate or the outside temperature changes, so their cost can be
 * told apart from the rest. Fed once a second by [TripRecorder].
 */
class TripLog(val header: JSONObject, val mapsArrivalPercent: Int, val startPercent: Float) {

    class Segment(val climateOn: Boolean?, val tempC: Float?) {
        var seconds = 0f
        var stoppedSeconds = 0f
        var km = 0f
        var outKwh = 0f
        var regenKwh = 0f
        var modelKwh = 0f
        var modelA: Float? = null
        var modelC: Float? = null
        var modelAuxW: Int? = null
        var startPercent: Float? = null
        var endPercent: Float? = null
        val speedSeconds = FloatArray(SPEED_BUCKETS)
        val speedKm = FloatArray(SPEED_BUCKETS)
        val speedKwh = FloatArray(SPEED_BUCKETS)
        /**
         * Pack voltage against current, as running sums (n, ΣI, ΣV, ΣI², ΣI·V), to measure the pack's
         * resistance: the slope of V over I is −R. For Maps' battery-loss table (field 5).
         */
        val vi = DoubleArray(5)
        /** Seconds into the trip when the segment started. */
        var startedAtS = 0f
        /** Climb and descent from the car's GPS altitude (no position kept), m. */
        var climbM = 0f
        var descentM = 0f
        /** Kinetic energy put in by speeding up, Wh per tonne: the cost of stop-and-go. */
        var accelWhPerT = 0f
        /** Sums for averages over the samples that read: pack °C, cabin °C, set °C, fan step. */
        val sums = DoubleArray(8)
        var acOnSeconds = 0f
        var regenLevel: Int? = null
        /** Drive mode (2 Eco, 3 Normal, 4 Sport, 6 Snow, 7 Custom) at the last reading. */
        var driveMode: Int? = null
    }

    /** Readings that change slowly, taken by [TripRecorder] every few seconds. */
    class Extras(
        val altitudeM: Double? = null,
        val packTempC: Float? = null,
        val cabinTempC: Float? = null,
        val setTempC: Float? = null,
        val fanStep: Int? = null,
        val acOn: Boolean? = null,
        val regenLevel: Int? = null,
        val driveMode: Int? = null,
    )

    private var elapsedS = 0f
    private var lastSpeedMs = 0f
    private var altitudeRef: Double? = null

    val segments = mutableListOf<Segment>()

    /** Charge when the car last moved: waiting parked afterwards is not part of the trip. */
    var arrivalPercent: Float = startPercent
        private set

    /** Seconds since the car last moved. */
    var stoppedFor = 0f
        private set

    var plugged = false

    val totalKm: Float get() = segments.sumOf { it.km.toDouble() }.toFloat()

    /** Long enough to say something about the forecast: shorter trips are mostly rounding. */
    val isRepresentative: Boolean
        get() = totalKm >= MIN_KM && startPercent - arrivalPercent >= MIN_DROP_PERCENT

    fun tick(
        dtS: Float, speedKmh: Float, powerW: Float?, percent: Float?, tempC: Float?, climateOn: Boolean?, model: Model?,
        volts: Float? = null, amps: Float? = null, extras: Extras? = null,
    ) {
        val current = segmentFor(climateOn, tempC, percent, model)
        if (current.seconds == 0f && current.km == 0f) current.startedAtS = elapsedS
        elapsedS += dtS
        val speedMs = speedKmh / 3.6f
        if (speedMs > lastSpeedMs) current.accelWhPerT += (speedMs * speedMs - lastSpeedMs * lastSpeedMs) * 1000f / 2f / 3600f
        lastSpeedMs = speedMs
        extras?.let { x ->
            x.altitudeM?.let { alt ->
                // 3 m hysteresis: GPS altitude jitters by a few metres.
                val ref = altitudeRef
                if (ref == null) altitudeRef = alt
                else if (alt - ref >= 3.0) { current.climbM += (alt - ref).toFloat(); altitudeRef = alt }
                else if (ref - alt >= 3.0) { current.descentM += (ref - alt).toFloat(); altitudeRef = alt }
            }
            val s = current.sums
            x.packTempC?.let { s[0] += it.toDouble(); s[1] += 1.0 }
            x.cabinTempC?.let { s[2] += it.toDouble(); s[3] += 1.0 }
            x.setTempC?.let { s[4] += it.toDouble(); s[5] += 1.0 }
            x.fanStep?.let { s[6] += it.toDouble(); s[7] += 1.0 }
            x.regenLevel?.let { current.regenLevel = it }
            x.driveMode?.let { current.driveMode = it }
        }
        if (extras?.acOn == true) current.acOnSeconds += dtS
        if (volts != null && amps != null && volts in 100f..900f && amps in -600f..600f) {
            val v = current.vi
            v[0] += 1.0; v[1] += amps.toDouble(); v[2] += volts.toDouble(); v[3] += amps.toDouble() * amps; v[4] += amps.toDouble() * volts
        }
        val moving = speedKmh >= MOVING_KMH
        current.seconds += dtS
        if (moving) stoppedFor = 0f else {
            current.stoppedSeconds += dtS
            stoppedFor += dtS
        }
        val km = speedKmh * dtS / 3600f
        current.km += km
        val kwh = (powerW ?: 0f) * dtS / 3_600_000f
        if (kwh >= 0f) current.outKwh += kwh else current.regenKwh -= kwh
        model?.let { current.modelKwh += it.watts(speedKmh) * dtS / 3_600_000f }
        val bucket = bucketOf(speedKmh)
        current.speedSeconds[bucket] += dtS
        current.speedKm[bucket] += km
        current.speedKwh[bucket] += kwh
        percent?.let {
            if (current.startPercent == null) current.startPercent = it
            current.endPercent = it
            if (moving) arrivalPercent = it
        }
    }

    private fun segmentFor(climateOn: Boolean?, tempC: Float?, percent: Float?, model: Model?): Segment {
        val last = segments.lastOrNull()
        if (last != null && last.climateOn == climateOn && sameTemp(last.tempC, tempC)) return last
        return Segment(climateOn, tempC).also {
            it.startPercent = percent
            it.modelA = model?.a
            it.modelC = model?.c
            it.modelAuxW = model?.auxW
            segments += it
        }
    }

    private fun sameTemp(a: Float?, b: Float?): Boolean =
        a == b || (a != null && b != null && kotlin.math.abs(a - b) < TEMP_STEP_C)

    /** What the model sent to Maps expects, for comparing with what the battery really gave. */
    class Model(val a: Float, val c: Float, val auxW: Int, val watts: (Float) -> Float)

    fun toJson(reason: String? = null): JSONObject = JSONObject(header.toString()).apply {
        put("schema", SCHEMA)
        put("mapsArrivalPercent", mapsArrivalPercent)
        put("startPercent", startPercent.toDouble())
        put("arrivalPercent", arrivalPercent.toDouble())
        put("km", totalKm.toDouble())
        put("plugged", plugged)
        reason?.let { put("endReason", it) }
        put("speedBucketsKmh", JSONArray(SPEED_EDGES.toList()))
        put("segments", JSONArray().also { list ->
            segments.forEach { s ->
                list.put(JSONObject().apply {
                    put("climateOn", s.climateOn ?: JSONObject.NULL)
                    put("tempC", s.tempC?.toDouble() ?: JSONObject.NULL)
                    put("seconds", s.seconds.toDouble())
                    put("stoppedSeconds", s.stoppedSeconds.toDouble())
                    put("km", s.km.toDouble())
                    put("outKwh", s.outKwh.toDouble())
                    put("regenKwh", s.regenKwh.toDouble())
                    put("modelKwh", s.modelKwh.toDouble())
                    put("modelA", s.modelA?.toDouble() ?: JSONObject.NULL)
                    put("modelC", s.modelC?.toDouble() ?: JSONObject.NULL)
                    put("modelAuxW", s.modelAuxW ?: JSONObject.NULL)
                    put("startPercent", s.startPercent?.toDouble() ?: JSONObject.NULL)
                    put("endPercent", s.endPercent?.toDouble() ?: JSONObject.NULL)
                    put("speedSeconds", floats(s.speedSeconds))
                    put("speedKm", floats(s.speedKm))
                    put("speedKwh", floats(s.speedKwh))
                    put("startedAtS", s.startedAtS.toDouble())
                    put("climbM", s.climbM.toDouble())
                    put("descentM", s.descentM.toDouble())
                    put("accelWhPerT", s.accelWhPerT.toDouble())
                    put("acOnSeconds", s.acOnSeconds.toDouble())
                    put("regenLevel", s.regenLevel ?: JSONObject.NULL)
                    put("driveMode", s.driveMode ?: JSONObject.NULL)
                    avg(s.sums, 0)?.let { put("packTempC", it) }
                    avg(s.sums, 2)?.let { put("cabinTempC", it) }
                    avg(s.sums, 4)?.let { put("setTempC", it) }
                    avg(s.sums, 6)?.let { put("fanStep", it) }
                    put("sums", JSONArray().also { a -> s.sums.forEach { a.put(it) } })
                    if (s.vi[0] > 0) {
                        put("vi", JSONArray().also { a -> s.vi.forEach { a.put(it) } })
                        packOhm(s.vi)?.let { put("packOhm", it) }
                    }
                })
            }
        })
    }

    private fun avg(sums: DoubleArray, at: Int): Double? =
        if (sums[at + 1] > 0) Math.round(sums[at] / sums[at + 1] * 10.0) / 10.0 else null

    /** Pack resistance from the running sums: −slope of V over I, when the current varied enough. */
    private fun packOhm(v: DoubleArray): Double? {
        val n = v[0]
        if (n < 30) return null
        val varI = v[3] / n - (v[1] / n) * (v[1] / n)
        if (varI < 25.0) return null
        val cov = v[4] / n - (v[1] / n) * (v[2] / n)
        return (-cov / varI).takeIf { it.isFinite() }
    }

    private fun floats(values: FloatArray) = JSONArray().also { a -> values.forEach { a.put(it.toDouble()) } }

    companion object {
        /** Below this, the car counts as stopped. */
        const val MOVING_KMH = 1f
        const val MIN_KM = 5f
        /** Kept from 4 % used (daily trips; less representative, but that is what there is). */
        const val MIN_DROP_PERCENT = 4f
        private const val TEMP_STEP_C = 1f
        val SPEED_EDGES = intArrayOf(0, 30, 50, 70, 90, 110, 130)
        private val SPEED_BUCKETS = SPEED_EDGES.size

        /**
         * Format of the exported file. 1: up to 2.2.7-beta2. 2: climb, pack voltage and current, A/C,
         * fan, regeneration, the energy spent speeding up (2.2.7-beta3 on), and from 2.2.7-beta16 the
         * source of the capacity, drive mode, tyre pressures and the model's rolling term.
         */
        const val SCHEMA = 2

        internal fun bucketOf(speedKmh: Float): Int =
            SPEED_EDGES.indexOfLast { speedKmh >= it }.coerceAtLeast(0)

        /** Back from a saved [toJson], after the app or the car restarted during the trip. */
        fun fromJson(json: JSONObject): TripLog {
            val header = JSONObject(json.toString()).apply {
                listOf("schema", "mapsArrivalPercent", "startPercent", "arrivalPercent", "km", "plugged",
                    "endReason", "speedBucketsKmh", "segments").forEach { remove(it) }
            }
            val log = TripLog(header, json.getInt("mapsArrivalPercent"), json.getDouble("startPercent").toFloat())
            log.arrivalPercent = json.getDouble("arrivalPercent").toFloat()
            log.plugged = json.optBoolean("plugged")
            val list = json.getJSONArray("segments")
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val segment = Segment(
                    if (o.isNull("climateOn")) null else o.getBoolean("climateOn"),
                    o.floatOrNull("tempC"),
                )
                segment.seconds = o.getDouble("seconds").toFloat()
                segment.stoppedSeconds = o.getDouble("stoppedSeconds").toFloat()
                segment.km = o.getDouble("km").toFloat()
                segment.outKwh = o.getDouble("outKwh").toFloat()
                segment.regenKwh = o.getDouble("regenKwh").toFloat()
                segment.modelKwh = o.getDouble("modelKwh").toFloat()
                segment.modelA = o.floatOrNull("modelA")
                segment.modelC = o.floatOrNull("modelC")
                segment.modelAuxW = if (o.isNull("modelAuxW")) null else o.getInt("modelAuxW")
                segment.startPercent = o.floatOrNull("startPercent")
                segment.endPercent = o.floatOrNull("endPercent")
                o.getJSONArray("speedSeconds").readInto(segment.speedSeconds)
                o.getJSONArray("speedKm").readInto(segment.speedKm)
                o.getJSONArray("speedKwh").readInto(segment.speedKwh)
                o.optJSONArray("vi")?.let { a -> for (k in 0 until minOf(5, a.length())) segment.vi[k] = a.getDouble(k) }
                o.optJSONArray("sums")?.let { a -> for (k in 0 until minOf(8, a.length())) segment.sums[k] = a.getDouble(k) }
                segment.startedAtS = o.optDouble("startedAtS", 0.0).toFloat()
                segment.climbM = o.optDouble("climbM", 0.0).toFloat()
                segment.descentM = o.optDouble("descentM", 0.0).toFloat()
                segment.accelWhPerT = o.optDouble("accelWhPerT", 0.0).toFloat()
                segment.acOnSeconds = o.optDouble("acOnSeconds", 0.0).toFloat()
                segment.regenLevel = if (o.has("regenLevel") && !o.isNull("regenLevel")) o.getInt("regenLevel") else null
                segment.driveMode = if (o.has("driveMode") && !o.isNull("driveMode")) o.getInt("driveMode") else null
                log.segments += segment
            }
            return log
        }

        private fun JSONObject.floatOrNull(key: String): Float? =
            if (isNull(key)) null else getDouble(key).toFloat()

        private fun JSONArray.readInto(target: FloatArray) {
            for (i in target.indices) target[i] = getDouble(i).toFloat()
        }
    }
}
