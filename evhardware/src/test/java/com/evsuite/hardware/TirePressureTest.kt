package com.evsuite.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TirePressureTest {

    @Test fun validVendorPressureTakesPriority() {
        val result = EVHardware.chooseTirePressureKpa(230) { error("The standard read should be skipped") }
        assertEquals(230f, result)
    }

    @Test fun unavailableVendorPressureFallsBackToAValidStandardReading() {
        for (vendorKpa in listOf(-1, 0, 601)) {
            assertEquals(215.5f, EVHardware.chooseTirePressureKpa(vendorKpa) { 215.5f })
        }
    }

    @Test fun unavailableOrInvalidStandardPressureIsAbsent() {
        for (standardKpa in listOf(null, 0f, -1f, 1_001f, Float.POSITIVE_INFINITY, Float.NaN)) {
            assertNull(EVHardware.chooseTirePressureKpa(0) { standardKpa })
        }
    }
}
