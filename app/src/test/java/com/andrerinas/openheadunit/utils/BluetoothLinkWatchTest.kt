package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothLinkWatchTest {
    private fun left(now: Long, last: Long, waited: Long) = BluetoothLinkWatch.remainingWaitMs(now, last, waited)

    @Test fun `no link change seen means no wait`() = assertEquals(0L, left(10_000, 0, 0))

    @Test fun `links quiet long enough means no wait`() = assertEquals(0L, left(10_000, 8_000, 0))

    @Test fun `a recent change waits until it has been quiet for the window`() =
        assertEquals(1_200L, left(10_000, 9_700, 0))

    @Test fun `the wait never goes past the cap`() {
        assertEquals(500L, left(10_000, 9_900, 4_500))
        assertEquals(0L, left(10_000, 9_990, 5_000))
    }
}
