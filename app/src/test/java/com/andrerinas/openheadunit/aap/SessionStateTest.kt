package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #78: what other apps are told about the Android Auto session. */
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28])
class SessionStateTest {

    @Test fun `a running session says its transport`() {
        val wireless = SessionState.extras(connected = true, wireless = true)
        assertTrue(wireless.getBoolean(SessionState.EXTRA_CONNECTED))
        assertEquals(SessionState.TRANSPORT_WIRELESS, wireless.getString(SessionState.EXTRA_TRANSPORT))
        assertEquals(SessionState.TRANSPORT_USB,
            SessionState.extras(connected = true, wireless = false).getString(SessionState.EXTRA_TRANSPORT))
    }

    @Test fun `no session, no transport, and nothing about the phone`() {
        val none = SessionState.extras(connected = false, wireless = true)
        assertFalse(none.getBoolean(SessionState.EXTRA_CONNECTED))
        assertEquals("", none.getString(SessionState.EXTRA_TRANSPORT))
        assertEquals(setOf(SessionState.EXTRA_CONNECTED, SessionState.EXTRA_TRANSPORT), none.keySet())
    }
}
