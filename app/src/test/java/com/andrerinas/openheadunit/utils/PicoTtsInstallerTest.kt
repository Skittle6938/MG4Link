package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.PicoTtsInstaller.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoTtsInstallerTest {

    @Test fun `the engine is installed when the car does not have it`() {
        assertEquals(Action.INSTALL, PicoTtsInstaller.actionFor(2, null, sameSigner = false))
    }

    @Test fun `the same or a newer engine on the car is left alone`() {
        assertEquals(Action.NOTHING, PicoTtsInstaller.actionFor(2, 2, sameSigner = true))
        assertEquals(Action.NOTHING, PicoTtsInstaller.actionFor(2, 3, sameSigner = false))
    }

    @Test fun `an older engine is updated, or replaced when signed with another key`() {
        assertEquals(Action.INSTALL, PicoTtsInstaller.actionFor(3, 2, sameSigner = true))
        assertEquals(Action.REPLACE, PicoTtsInstaller.actionFor(3, 2, sameSigner = false))
    }

    @Test fun `the user's own default engine is kept`() {
        assertFalse(PicoTtsInstaller.shouldBecomeDefault("com.google.android.tts", currentInstalled = true))
        assertTrue(PicoTtsInstaller.shouldBecomeDefault(null, currentInstalled = false))
        assertTrue(PicoTtsInstaller.shouldBecomeDefault("", currentInstalled = false))
        assertTrue(PicoTtsInstaller.shouldBecomeDefault("com.removed.tts", currentInstalled = false))
    }
}
