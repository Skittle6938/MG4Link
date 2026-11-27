package com.andrerinas.openheadunit.utils

/** Which stored values "Reset settings" keeps: state and learned values, not choices. */
object SettingsReset {

    private val KEPT = setOf(
        // Phones and devices already set up
        "allow-devices", "bt-address", "usb-720p-devices",
        "auto-start-bt-macs", "auto-start-bt-mac", "auto-start-bt-order", "auto-start-bt-name",
        "last-connection-type", "last-connection-ip", "last-connection-usb-device",
        // First-run steps already done
        "has-accepted-disclaimer", "has-completed-setup-wizard", "onboarding-version",
        "tutorial-shown", "gesture_hint_shown", "pending-renderer-confirm", "video-test-options-retired",
        // Learned or measured on this car
        "last-loc-latitude", "last-loc-longitude",
        "cached-surface-width", "cached-surface-height", "cached-surface-settings-hash",
        "native-aa-server-failures", "hotspot-teardown-proven-unsafe",
        "wifi-shared-channel", "wifi-auto-channel",
        "battery-net-capacity-v2-kwh", "battery-net-capacity-kwh", "battery-state-of-health-percent",
        // The user's ABRP credentials (ABRP itself goes off)
        "abrp-token", "abrp-api-key",
    )

    fun keeps(key: String): Boolean = key in KEPT
}
