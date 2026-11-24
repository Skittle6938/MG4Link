package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AapControlBluetoothTest {

    @Test
    fun `pairing response matches aap_protobuf layout - status SUCCESS then already_paired`() {
        val bytes = AapControlBluetooth.pairingResponse(alreadyPaired = true).toByteArray()

        assertArrayEquals(byteArrayOf(0x08, 0x00, 0x10, 0x01), bytes)
    }

    @Test
    fun `pairing response still carries status when the phone is not bonded`() {
        val bytes = AapControlBluetooth.pairingResponse(alreadyPaired = false).toByteArray()

        assertArrayEquals(byteArrayOf(0x08, 0x00, 0x10, 0x00), bytes)
    }

    @Test
    fun `authentication data matches the AllGo passkey and pairing method layout`() {
        val bytes = AapControlBluetooth.authenticationData(passkey = 12345, method = 4).toByteArray()

        assertArrayEquals(byteArrayOf(0x0a, 0x05, 0x31, 0x32, 0x33, 0x34, 0x35, 0x10, 0x04), bytes)
    }
}
