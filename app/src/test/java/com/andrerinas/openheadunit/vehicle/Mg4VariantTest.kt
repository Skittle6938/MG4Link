package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mg4VariantTest {
    @Test
    fun `the car's pack picks the nearest version`() {
        assertEquals(Mg4Variant.STANDARD, Mg4Variant.detect(47_858))
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.detect(61_700))
        assertEquals(Mg4Variant.EXTENDED_RANGE, Mg4Variant.detect(74_000))
    }

    @Test
    fun `a version chosen by hand wins over detection`() {
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.resolve("xpower", 61_700))
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.resolve(Mg4Variant.AUTO, 61_700))
    }

    @Test
    fun `the model tells Maps the version's real peak DC power`() {
        val bytes = VehicleEnergyModelEncoder.encode(
            VehicleEnergyModelEncoder.Snapshot(
                capacityWh = 47_858, currentWh = 21_680, rangeMeters = 136_000,
                variant = Mg4Variant.STANDARD,
            ),
        )
        assertTrue(bytes.isNotEmpty())
        assertEquals(86_000, Mg4Variant.STANDARD.maxDcW)
    }

    @Test
    fun `the curve interpolates between its 5 percent points`() {
        assertEquals(138_000, Mg4Variant.LONG_RANGE.dcPowerW(50.0))
        assertEquals(108_000, Mg4Variant.LONG_RANGE.dcPowerW(57.5))
        assertEquals(138_000, Mg4Variant.LONG_RANGE.maxDcW)
    }
    @Test
    fun `the model keeps the shape Maps was calibrated with`() {
        val model = Mg4MapsEnergyModel.build(Mg4Variant.STANDARD, 8f, true)
        val bytes = VehicleEnergyModelEncoder.encode(
            VehicleEnergyModelEncoder.Snapshot(
                capacityWh = 47_858, currentWh = 21_680, rangeMeters = 136_000,
                variant = Mg4Variant.STANDARD, outsideTempC = 8f, energyModel = model,
            ),
        )
        val top = fields(bytes)
        assertEquals(setOf(1, 2, 4, 7), top.map { it.first }.toSet())
        // Battery: energy left, capacity, air density, temperature, the two powers.
        assertEquals(listOf(3, 4, 6, 7, 9, 10), fields(top.first { it.first == 1 }.second!!).map { it.first })
        // Road load: A, B, C, each with its value and the second float Maps' servers use.
        val road = fields(top.first { it.first == 2 }.second!!)
        assertEquals(listOf(1, 2, 3), road.map { it.first })
        road.forEach { assertEquals(listOf(1, 2), fields(it.second!!).map { f -> f.first }) }
        // Energy left / capacity is the car's own %.
        assertEquals(21_680.0 / 47_858.0, 0.453, 0.001)
    }

    @Test
    fun `Maps gets the road load seen from the battery`() {
        val model = Mg4MapsEnergyModel.build(Mg4Variant.STANDARD, 8f, true)
        val bytes = VehicleEnergyModelEncoder.encode(
            VehicleEnergyModelEncoder.Snapshot(
                capacityWh = 47_858, currentWh = 21_680, rangeMeters = 136_000,
                variant = Mg4Variant.STANDARD, outsideTempC = 8f, energyModel = model,
            ),
        )
        val road = fields(fields(bytes).first { it.first == 2 }.second!!)
        fun value(term: Int): Float = com.google.protobuf.CodedInputStream.newInstance(road.first { it.first == term }.second!!)
            .let { it.readTag(); it.readFloat() }
        assertEquals(model.a / Mg4MapsEnergyModel.DRIVETRAIN, value(1), 0.01f)
        assertEquals(0f, value(2), 0f)
        assertEquals(model.c / Mg4MapsEnergyModel.DRIVETRAIN, value(3), 0.0001f)
    }

    /** Top-level fields of a protobuf message: number and, for length-delimited ones, their bytes. */
    private fun fields(bytes: ByteArray): List<Pair<Int, ByteArray?>> {
        val input = com.google.protobuf.CodedInputStream.newInstance(bytes)
        val out = mutableListOf<Pair<Int, ByteArray?>>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val field = tag ushr 3
            when (tag and 7) {
                0 -> { input.readRawVarint64(); out += field to null }
                1 -> { input.readRawLittleEndian64(); out += field to null }
                5 -> { input.readRawLittleEndian32(); out += field to null }
                2 -> out += field to input.readByteArray()
                else -> error("wire type ${tag and 7}")
            }
        }
        return out
    }

    @Test
    fun `a cold 64 kWh pack is told from its net figure, not net x SoH (#22)`() {
        // 61 kWh net at 88.75 % SoH is 54.1 kWh, nearer the 51 kWh pack's 49 kWh.
        assertEquals(Mg4Variant.STANDARD, Mg4Variant.detect(54_140))
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.resolve(Mg4Variant.AUTO, 61_000))
    }
}
