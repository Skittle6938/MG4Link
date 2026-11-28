package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #75: the car's speed, gear and parking brake for the phone. */
class VehicleSensorsTest {

    @Test fun `speed goes in metres per second times a thousand`() {
        assertEquals(10_000, VehicleSensors.speedE3(36f))
        assertEquals(0, VehicleSensors.speedE3(-5f))
    }

    @Test fun `P means the parking brake is on`() {
        assertEquals(Sensors.SensorBatch.GearData.GEAR.PARK, VehicleSensors.gearFor(1))
        assertNull(VehicleSensors.gearFor(7))
        val inPark = VehicleSensors.batch(0f, 1, sendSpeed = false, sendGear = true)!!
        assertTrue(inPark.getParkingbrakeData(0).isEngaged)
        assertEquals(Sensors.SensorBatch.GearData.GEAR.PARK, inPark.getGearData(0).gear)
        val driving = VehicleSensors.batch(50f, 9, sendSpeed = true, sendGear = true)!!
        assertFalse(driving.getParkingbrakeData(0).isEngaged)
        assertEquals(0, driving.gearDataCount)
        assertEquals(1, driving.speedDataCount)
    }

    @Test fun `raw gears 1 to 4 are P, R, N and D`() {
        val prnd = listOf(Sensors.SensorBatch.GearData.GEAR.PARK, Sensors.SensorBatch.GearData.GEAR.REVERSE,
            Sensors.SensorBatch.GearData.GEAR.NEUTRAL, Sensors.SensorBatch.GearData.GEAR.DRIVE)
        assertEquals(prnd, (1..4).map { VehicleSensors.gearFor(it) })
        assertNull(VehicleSensors.gearFor(0))
        val reverse = VehicleSensors.batch(0f, 2, sendSpeed = false, sendGear = true)!!
        assertEquals(Sensors.SensorBatch.GearData.GEAR.REVERSE, reverse.getGearData(0).gear)
        assertFalse(reverse.getParkingbrakeData(0).isEngaged)
    }

    @Test fun `speed while moving or when it changes, gear when it changes`() {
        assertTrue(VehicleSensors.plan(50f, 50f, 3, 3).speed)
        assertFalse(VehicleSensors.plan(0f, 0f, 1, 1).speed)
        assertTrue(VehicleSensors.plan(0f, 30f, 1, 1).speed)
        assertFalse(VehicleSensors.plan(0f, 0f, 1, 1).gear)
        assertTrue(VehicleSensors.plan(0f, 0f, 3, 1).gear)
        assertNull(VehicleSensors.batch(null, null, sendSpeed = true, sendGear = true))
    }
}
