package com.andrerinas.openheadunit.aap

import android.location.Location
import com.andrerinas.openheadunit.aap.protocol.messages.DrivingStatusEvent
import com.andrerinas.openheadunit.aap.protocol.messages.LocationUpdateEvent
import com.andrerinas.openheadunit.aap.protocol.messages.NightModeEvent
import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.google.protobuf.Message
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * What the phone really receives on the sensor channel, read back from the bytes against the
 * Android Auto protocol's own field numbers and units, not through our generated classes, whose
 * names were wrong once (speed_e6 for a field in m/s × 1000, #75).
 *
 * SensorBatch: 1 location, 3 speed, 7 parking brake, 8 gear, 10 night mode, 13 driving status.
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28])
class SensorWireFormatTest {

    private fun wire(message: Message): UnknownFieldSet = UnknownFieldSet.parseFrom(message.toByteArray())

    private fun sub(batch: UnknownFieldSet, field: Int): UnknownFieldSet =
        UnknownFieldSet.parseFrom(batch.getField(field).lengthDelimitedList.single())

    private fun varint(set: UnknownFieldSet, field: Int): Long = set.getField(field).varintList.single()

    /** A protobuf int32 varint holds a negative value as its 64-bit two's complement. */
    private fun int32(set: UnknownFieldSet, field: Int): Int = varint(set, field).toInt()

    @Test fun `speed is field 3, metres per second times 1000`() {
        val batch = wire(VehicleSensors.batch(90f, null, sendSpeed = true, sendGear = false)!!)
        assertEquals(25_000, int32(sub(batch, 3), 1)) // 90 km/h = 25 m/s
    }

    @Test fun `gear is field 8 with 100 drive, 101 park, 102 reverse, 0 neutral, and parking brake field 7`() {
        fun gear(raw: Int) = varint(sub(wire(VehicleSensors.batch(null, raw, false, true)!!), 8), 1)
        fun brake(raw: Int) = varint(sub(wire(VehicleSensors.batch(null, raw, false, true)!!), 7), 1)
        assertEquals(101, gear(1)); assertEquals(1, brake(1))
        assertEquals(102, gear(2)); assertEquals(0, brake(2))
        assertEquals(0, gear(3))
        assertEquals(100, gear(4)); assertEquals(0, brake(4))
    }

    @Test fun `location is field 1 with degrees times 10^7, accuracy mm, altitude cm, speed mm per s, bearing times 10^6`() {
        val location = Location("test").apply {
            latitude = 48.8566; longitude = -2.3522; accuracy = 4.5f; altitude = 35.2
            speed = 12.5f; bearing = 270.5f; time = 1_700_000_000_000
        }
        val data = sub(wire(LocationUpdateEvent.makeProto(location)), 1)
        assertEquals(488_566_000, int32(data, 2))
        assertEquals(-23_522_000, int32(data, 3))
        assertEquals(4_500, int32(data, 4))
        assertEquals(3_520, int32(data, 5))
        assertEquals(12_500, int32(data, 6))
        assertEquals(270_500_000, int32(data, 7))
    }

    @Test fun `night mode is field 10 and driving status field 13`() {
        assertEquals(1, varint(sub(wire(NightModeEvent.makeProto(true)), 10), 1))
        assertEquals(0, varint(sub(wire(NightModeEvent.makeProto(false)), 10), 1))
        val status = Sensors.SensorBatch.DrivingStatusData.Status.UNRESTRICTED
        assertEquals(0, varint(sub(wire(DrivingStatusEvent.makeProto(status)), 13), 1))
    }

    @Test fun `the announced sensor types match the protocol numbers`() {
        assertEquals(1, Sensors.SensorType.LOCATION_VALUE)
        assertEquals(3, Sensors.SensorType.CAR_SPEED_VALUE)
        assertEquals(7, Sensors.SensorType.PARKING_BRAKE_VALUE)
        assertEquals(8, Sensors.SensorType.GEAR_VALUE)
        assertEquals(10, Sensors.SensorType.NIGHT_VALUE)
        assertEquals(13, Sensors.SensorType.DRIVING_STATUS_VALUE)
        assertEquals(23, Sensors.SensorType.VEHICLE_ENERGY_MODEL_VALUE)
    }
}
