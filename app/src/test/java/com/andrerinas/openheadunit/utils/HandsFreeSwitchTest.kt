package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class HandsFreeSwitchTest {
    private val aaPhone = "AA:BB:CC:DD:EE:01"
    private val otherPhone = "AA:BB:CC:DD:EE:02"

    @Test fun `nothing to do when the car already holds the Android Auto phone`() =
        assertEquals(
            BluetoothHelper.HandsFreeSwitch(emptyList(), false),
            BluetoothHelper.handsFreeSwitchFor(listOf(aaPhone.lowercase()), aaPhone),
        )

    @Test fun `connects the Android Auto phone when the car holds no phone`() =
        assertEquals(
            BluetoothHelper.HandsFreeSwitch(emptyList(), true),
            BluetoothHelper.handsFreeSwitchFor(emptyList(), aaPhone),
        )

    @Test fun `lets go of the other phone before connecting the Android Auto one`() =
        assertEquals(
            BluetoothHelper.HandsFreeSwitch(listOf(otherPhone), true),
            BluetoothHelper.handsFreeSwitchFor(listOf(otherPhone), aaPhone),
        )

    @Test fun `keeps every link when the Android Auto phone is among them`() =
        assertEquals(
            BluetoothHelper.HandsFreeSwitch(emptyList(), false),
            BluetoothHelper.handsFreeSwitchFor(listOf(otherPhone, aaPhone), aaPhone),
        )
}
