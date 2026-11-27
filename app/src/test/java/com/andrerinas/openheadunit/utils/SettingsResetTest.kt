package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Reset settings" clears the choices and keeps the state. */
class SettingsResetTest {

    @Test fun `choices go back to their defaults`() {
        listOf("keep-car-wifi", "dongle-bt-guard", "dongle-wait-s", "abrp-enabled", "maps-energy-model",
            "wifi-channel", "night-mode", "key-codes", "test-mode").forEach { assertFalse(it, SettingsReset.keeps(it)) }
    }

    @Test fun `phones, first-run steps, learned values and ABRP credentials stay`() {
        listOf("auto-start-bt-macs", "has-completed-setup-wizard", "tutorial-shown", "battery-net-capacity-v2-kwh",
            "wifi-shared-channel", "abrp-token").forEach { assertTrue(it, SettingsReset.keeps(it)) }
    }
}
