package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Sensors

/**
 * The car's speed, gear and parking brake for the phone (#75), as car-built Android Auto head units
 * send them: Google Maps combines the speed with the phone's sensors to keep the position when GPS
 * is weak (tunnels, car parks) and to find the road again faster after a stop.
 *
 * Speed comes from the car's speed property (km/h). The gear is 1 P, 2 R, 3 N, 4 D on every SWI
 * firmware (measured on SWI69). The MG4 holds the parking brake on in P, so P stands for it too.
 */
object VehicleSensors {
    /** Raw gear value for P on every studied firmware. */
    const val RAW_PARK = 1

    /**
     * SpeedData field 1: metres per second × 1000 (speed_e3 in the Android Auto protocol). The
     * pre-generated class calls it speed_e6; sent as × 10^6 the phone read a speed 1000 times too
     * high and Maps lost the position (#75).
     */
    fun speedE3(kmh: Float): Int = (kmh.coerceIn(0f, 300f) / 3.6f * 1_000f).toInt()

    /** The Android Auto gear for a raw firmware value, or null when it is not a known gear. */
    fun gearFor(raw: Int?): Sensors.SensorBatch.GearData.GEAR? = when (raw) {
        RAW_PARK -> Sensors.SensorBatch.GearData.GEAR.PARK
        2 -> Sensors.SensorBatch.GearData.GEAR.REVERSE
        3 -> Sensors.SensorBatch.GearData.GEAR.NEUTRAL
        4 -> Sensors.SensorBatch.GearData.GEAR.DRIVE
        else -> null
    }

    /** One batch with what is known; null when there is nothing to send. */
    fun batch(speedKmh: Float?, gearRaw: Int?, sendSpeed: Boolean, sendGear: Boolean): Sensors.SensorBatch? {
        val builder = Sensors.SensorBatch.newBuilder()
        var any = false
        if (sendSpeed && speedKmh != null) {
            builder.addSpeedData(Sensors.SensorBatch.SpeedData.newBuilder().setSpeedE6(speedE3(speedKmh)))
            any = true
        }
        if (sendGear && gearRaw != null) {
            builder.addParkingbrakeData(Sensors.SensorBatch.ParkingBrakeData.newBuilder().setIsEngaged(gearRaw == RAW_PARK))
            gearFor(gearRaw)?.let { builder.addGearData(Sensors.SensorBatch.GearData.newBuilder().setGear(it)) }
            any = true
        }
        return if (any) builder.build() else null
    }

    /** What to send this tick: the speed while moving or when it changed, the gear when it changed. */
    data class Plan(val speed: Boolean, val gear: Boolean)

    fun plan(speedKmh: Float?, lastSpeedKmh: Float?, gearRaw: Int?, lastGearRaw: Int?): Plan = Plan(
        speed = speedKmh != null && (speedKmh > 0.5f || lastSpeedKmh == null || kotlin.math.abs(speedKmh - lastSpeedKmh) > 0.5f),
        gear = gearRaw != null && gearRaw != lastGearRaw,
    )

    fun message(batch: Sensors.SensorBatch) =
        AapMessage(Channel.ID_SEN, Sensors.SensorsMsgType.SENSOR_EVENT_VALUE, batch)
}
