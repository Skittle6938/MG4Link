package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarInternalUsbTest {
    @Test
    fun `the car's 4G modem is never taken for a phone (#25)`() {
        assertTrue(UsbDeviceCompat.isCarInternalModem(0x05C6, 0x902D, "Android MDM9628-MTP _SN:8169F919"))
        assertTrue(UsbDeviceCompat.isCarInternalModem(0x05C6, 0x902D, null))
        assertTrue(UsbDeviceCompat.isCarInternalModem(0x1234, 0x0001, "MDM9607"))
        assertFalse(UsbDeviceCompat.isCarInternalModem(0x22B8, 0x2E81, "moto edge 60"))
        assertFalse(UsbDeviceCompat.isCarInternalModem(0x05C6, 0x9039, "Android"))
    }
}
