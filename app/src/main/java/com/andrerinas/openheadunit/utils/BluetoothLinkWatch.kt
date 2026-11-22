package com.andrerinas.openheadunit.utils

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Watches the car's Bluetooth links to phones (#36).
 *
 * At a cold start the car reconnects the phone by itself (hands-free, Bluetooth music, remote
 * control, contacts, messages). When MG4Link calls the phone first, Android Auto can start while
 * that reconnection is still going on, and a link coming up or down on the phone at the moment the
 * music auto-starts sends the music to the phone's own speaker. This logs every such change with
 * its time, and lets the wireless handshake wait until the links have been quiet for a moment
 * before it asks the phone to start Android Auto.
 */
object BluetoothLinkWatch {
    /** No link change for this long counts as settled. */
    const val QUIET_MS = 1_500L
    /** Never hold the handshake longer than this, settled or not. */
    const val MAX_WAIT_MS = 5_000L
    private const val POLL_MS = 100L

    /** elapsedRealtime of the last link change seen, 0 when none yet. */
    @Volatile var lastChangeAtMs = 0L
        private set

    private val profileActions = linkedMapOf(
        "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED" to "hands-free",
        "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED" to "hands-free audio",
        "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED" to "music",
        "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED" to "music playing",
        "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED" to "remote control",
        "android.bluetooth.avrcp-controller.profile.action.BROWSE_CONNECTION_STATE_CHANGED" to "remote control browsing",
        "android.bluetooth.pbapclient.profile.action.CONNECTION_STATE_CHANGED" to "contacts",
        "android.bluetooth.mapmce.profile.action.CONNECTION_STATE_CHANGED" to "messages",
        "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" to "music (source role)",
        "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" to "hands-free (gateway role)",
        BluetoothDevice.ACTION_ACL_CONNECTED to "link up",
        BluetoothDevice.ACTION_ACL_DISCONNECTED to "link down",
    )

    private var registeredOn: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val what = profileActions[action] ?: return
            // A playing-state change is not a link change: log it, but it does not reset the wait.
            if (!action.endsWith("PLAYING_STATE_CHANGED")) lastChangeAtMs = SystemClock.elapsedRealtime()
            val device: BluetoothDevice? = try {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } catch (_: Exception) { null }
            val address = try { device?.address } catch (_: Exception) { null } ?: "?"
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, Int.MIN_VALUE)
            val previous = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, Int.MIN_VALUE)
            val change = when {
                state == Int.MIN_VALUE -> ""
                action.endsWith("AUDIO_STATE_CHANGED") -> " ${audioName(previous)} → ${audioName(state)}"
                action.endsWith("PLAYING_STATE_CHANGED") -> if (state == 10) " playing" else " not playing"
                else -> " ${stateName(previous)} → ${stateName(state)}"
            }
            AppLog.i("BtLink: $what$change ($address)")
        }
    }

    fun register(context: Context) {
        if (registeredOn != null) return
        val filter = IntentFilter().apply { profileActions.keys.forEach { addAction(it) } }
        try {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registeredOn = context
        } catch (e: Exception) {
            AppLog.w("BtLink: could not watch the Bluetooth links: ${e.message}")
        }
    }

    fun unregister() {
        val context = registeredOn ?: return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        registeredOn = null
    }

    /**
     * How much longer to wait, given the time of the last link change and how long the caller has
     * already waited. 0 once the links have been quiet for [quietMs] or [maxMs] is used up.
     */
    fun remainingWaitMs(nowMs: Long, lastChangeMs: Long, waitedMs: Long, quietMs: Long = QUIET_MS, maxMs: Long = MAX_WAIT_MS): Long {
        if (waitedMs >= maxMs) return 0L
        if (lastChangeMs <= 0L) return 0L
        val quietFor = nowMs - lastChangeMs
        if (quietFor >= quietMs) return 0L
        return minOf(quietMs - quietFor, maxMs - waitedMs)
    }

    /** Waits until the links are quiet (or [MAX_WAIT_MS]); returns how long it waited. */
    suspend fun awaitQuiet(): Long {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val left = remainingWaitMs(now, lastChangeAtMs, now - start)
            if (left <= 0L) return now - start
            delay(minOf(left, POLL_MS))
        }
    }

    private fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "off"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_CONNECTED -> "on"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        Int.MIN_VALUE -> "?"
        else -> state.toString()
    }

    /** Hands-free audio (SCO) states: 0 off, 1 connecting, 2 on. */
    private fun audioName(state: Int): String = when (state) {
        0 -> "audio off"
        1 -> "audio connecting"
        2 -> "audio on"
        Int.MIN_VALUE -> "?"
        else -> state.toString()
    }
}
