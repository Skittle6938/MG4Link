package com.andrerinas.openheadunit.vehicle

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [33])
class TripLogTest {

    private fun trip() = TripLog(JSONObject().put("version", "Standard 51 kWh"), 42, 80f)

    @Test
    fun `energy, regeneration and distance add up`() {
        val t = trip()
        t.tick(3600f, 100f, 10_000f, 70f, 10f, false, null)
        t.tick(360f, 50f, -5_000f, 69f, 10f, false, null)
        val s = t.segments.single()
        assertEquals(105f, s.km, 0.01f)
        assertEquals(10f, s.outKwh, 0.01f)
        assertEquals(0.5f, s.regenKwh, 0.01f)
    }

    @Test
    fun `a climate or temperature change starts a segment`() {
        val t = trip()
        t.tick(1f, 50f, 1000f, 70f, 10f, false, null)
        t.tick(1f, 50f, 1000f, 70f, 10.5f, false, null)
        assertEquals(1, t.segments.size)
        t.tick(1f, 50f, 1000f, 70f, 10.5f, true, null)
        assertEquals(2, t.segments.size)
        t.tick(1f, 50f, 1000f, 70f, 12f, true, null)
        assertEquals(3, t.segments.size)
    }

    @Test
    fun `arrival is the charge when the car last moved`() {
        val t = trip()
        t.tick(1f, 50f, 1000f, 70f, 10f, false, null)
        t.tick(100f, 0f, 500f, 69f, 10f, false, null)
        assertEquals(70f, t.arrivalPercent, 0.01f)
        assertEquals(100f, t.stoppedFor, 0.01f)
    }

    @Test
    fun `only a long enough trip with a real drop is representative`() {
        val good = trip().apply { tick(3600f, 20f, 1000f, 72f, 10f, false, null) }
        assertEquals(true, good.isRepresentative)
        val short = trip().apply { tick(700f, 20f, 1000f, 60f, 10f, false, null) }
        assertEquals(false, short.isRepresentative)
        val smallDrop = trip().apply { tick(3600f, 50f, 1000f, 77f, 10f, false, null) }
        assertEquals(false, smallDrop.isRepresentative)
    }

    @Test
    fun `speeds fall into their buckets`() {
        assertEquals(0, TripLog.bucketOf(10f))
        assertEquals(1, TripLog.bucketOf(30f))
        assertEquals(6, TripLog.bucketOf(140f))
    }

    @Test
    fun `a saved trip comes back unchanged`() {
        val t = trip()
        t.tick(10f, 90f, 20_000f, 70f, 10f, true, TripLog.Model(200f, 0.39f, 900) { 15_000f })
        val back = TripLog.fromJson(JSONObject(t.toJson().toString()))
        assertEquals(42, back.mapsArrivalPercent)
        assertEquals(80f, back.startPercent, 0.01f)
        val s = back.segments.single()
        assertEquals(t.segments.single().outKwh, s.outKwh, 0.001f)
        assertEquals(900, s.modelAuxW)
        assertEquals(10f, s.speedSeconds[4], 0.01f)
        assertEquals("Standard 51 kWh", back.header.getString("version"))
    }

    @Test
    fun `climb speeding up and the pack resistance are kept`() {
        val t = trip()
        // 0 to 36 km/h: 1/2 v^2 = 50 J/kg = 13.9 Wh per tonne.
        t.tick(1f, 0f, 1000f, 80f, 10f, false, null, 350f, 10f, TripLog.Extras(altitudeM = 100.0))
        t.tick(1f, 36f, 30_000f, 80f, 10f, false, null, 340f, 90f, TripLog.Extras(altitudeM = 101.0))
        t.tick(1f, 36f, 20_000f, 80f, 10f, false, null, 344f, 60f, TripLog.Extras(altitudeM = 110.0))
        for (k in 0 until 40) t.tick(1f, 36f, 10_000f, 80f, 10f, false, null, 350f - (k % 4) * 3f, 10f + (k % 4) * 30f, TripLog.Extras(altitudeM = 104.0))
        val seg = t.toJson().getJSONArray("segments").getJSONObject(0)
        assertEquals(13.9, seg.getDouble("accelWhPerT"), 0.1)
        assertEquals(10.0, seg.getDouble("climbM"), 0.01)
        assertEquals(6.0, seg.getDouble("descentM"), 0.01)
        assertEquals(0.1, seg.getDouble("packOhm"), 0.02)
    }

    @Test
    fun `the exported file is schema 2 and keeps the header`() {
        val header = JSONObject().put("version", "Standard 51 kWh").put("netSource", "CAR").put("sohSource", "MEASURED")
            .put("measuredKwh", 58.5).put("precisePercent", true).put("modelRolling", 0.0097)
            .put("tireKpa", org.json.JSONArray().put(250.0).put(250.0).put(JSONObject.NULL).put(240.0))
        val t = TripLog(header, 42, 80f)
        t.tick(10f, 50f, 8_000f, 79f, 10f, false, null)
        val json = t.toJson()
        assertEquals(2, json.getInt("schema"))
        assertEquals("CAR", json.getString("netSource"))
        assertEquals("MEASURED", json.getString("sohSource"))
        assertEquals(58.5, json.getDouble("measuredKwh"), 0.001)
        assertEquals(true, json.getBoolean("precisePercent"))
        assertEquals(4, json.getJSONArray("tireKpa").length())
        // And back, after the app restarted in the middle of a trip.
        val back = TripLog.fromJson(JSONObject(json.toString())).toJson()
        assertEquals(2, back.getInt("schema"))
        assertEquals("CAR", back.getString("netSource"))
        assertEquals(0.0097, back.getDouble("modelRolling"), 0.00001)
    }

    @Test
    fun `the drive mode is kept for each segment`() {
        val t = trip()
        t.tick(10f, 50f, 8_000f, 79f, 10f, false, null, extras = TripLog.Extras(driveMode = 4))
        t.tick(10f, 50f, 8_000f, 79f, 10f, false, null, extras = TripLog.Extras(driveMode = 2))
        val seg = TripLog.fromJson(JSONObject(t.toJson().toString())).toJson().getJSONArray("segments").getJSONObject(0)
        assertEquals(2, seg.getInt("driveMode"))
    }
}
