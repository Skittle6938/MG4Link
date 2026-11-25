package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #73: an access point switched on but never usable is restarted, without racing a slow one. */
class StuckAccessPointPolicyTest {

    private val switchedOn = 100_000L

    @Test fun `restarted once it has been unusable long enough after switching it on`() {
        assertTrue(StuckAccessPointPolicy.shouldRestart(switchedOn + 20_000, switchedOn, 0, autoEnable = true, startInFlight = false))
    }

    @Test fun `a slow access point is given its time`() {
        assertFalse(StuckAccessPointPolicy.shouldRestart(switchedOn + 19_999, switchedOn, 0, autoEnable = true, startInFlight = false))
    }

    @Test fun `never while a start is running, nor before any switch-on`() {
        assertFalse(StuckAccessPointPolicy.shouldRestart(switchedOn + 60_000, switchedOn, 0, autoEnable = true, startInFlight = true))
        assertFalse(StuckAccessPointPolicy.shouldRestart(switchedOn + 60_000, null, 0, autoEnable = true, startInFlight = false))
    }

    @Test fun `at most twice per run, and only with auto-enable on`() {
        assertTrue(StuckAccessPointPolicy.shouldRestart(switchedOn + 60_000, switchedOn, 1, autoEnable = true, startInFlight = false))
        assertFalse(StuckAccessPointPolicy.shouldRestart(switchedOn + 60_000, switchedOn, 2, autoEnable = true, startInFlight = false))
        assertFalse(StuckAccessPointPolicy.shouldRestart(switchedOn + 60_000, switchedOn, 0, autoEnable = false, startInFlight = false))
    }
}
