package com.andrerinas.openheadunit.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings

/**
 * Lets a wireless Android Auto adapter (dongle) reach the phone before the car does (#64), as the
 * AA BootFix app does for the original AllGo app. Experimental, off by default.
 *
 * When the phone's Bluetooth comes up, the car (bonded, hands-free client) connects to it first,
 * and the adapter's own Bluetooth handshake with the phone loses the race: the adapter then starts
 * without the phone and Android Auto never comes. While armed, the car's client profiles are set
 * to OFF for every paired phone, which stops its auto-connect without removing any pairing or
 * dropping a live link.
 *
 * - Armed: at MG4Link's start (the car starting), whenever hands-free connects (so it is already
 *   in place at the next start, however the car is switched off), and when the phone leaves.
 * - Released: as soon as an Android Auto session over USB starts (MG4Link then moves the hands-free
 *   to that phone, #59), or [WAIT_MS] after the start without one, in which case Bluetooth is
 *   restarted so the car reconnects the phone as usual.
 */
object DongleBluetoothGuard {
    /** Longest the car's own Bluetooth is held back after a start. */
    const val WAIT_MS = 180_000L

    /** Settings.Global keys read by HeadsetClient, A2dpSink and PbapClient on Android 9. */
    private val KEYS = listOf("bluetooth_headset_priority_", "bluetooth_a2dp_src_priority_", "bluetooth_pbap_client_priority_")
    private const val PRIORITY_OFF = 0
    private const val PRIORITY_UNDEFINED = -1
    private const val PROFILE_HEADSET_CLIENT = 16

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var startedThisProcess = false
    @Volatile private var sessionOverUsb = false

    private fun prefs(c: Context) =
        c.createDeviceProtectedStorageContext().getSharedPreferences("dongle-bt-guard", Context.MODE_PRIVATE)

    private fun enabled(c: Context) = com.andrerinas.openheadunit.utils.Settings(c).dongleBluetoothGuard

    fun isArmed(c: Context) = prefs(c).getBoolean("armed", false)

    /** Paired phones plus the ones remembered earlier, so a release also covers an unpaired one. */
    @SuppressLint("MissingPermission")
    private fun devices(c: Context): Set<String> {
        val p = prefs(c)
        val macs = p.getStringSet("macs", emptySet())!!.toMutableSet()
        try { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.forEach { d -> d.address?.let { macs += it } } } catch (_: Exception) {}
        p.edit().putStringSet("macs", macs).apply()
        return macs
    }

    @Synchronized
    fun arm(c: Context, why: String) {
        if (!enabled(c)) return
        val cr = c.contentResolver
        val p = prefs(c)
        val already = p.getBoolean("armed", false)
        val e = p.edit()
        try {
            for (mac in devices(c)) for (k in KEYS) {
                val key = k + mac
                if (!already) e.putInt("orig_$key", AndroidSettings.Global.getInt(cr, key, PRIORITY_UNDEFINED))
                AndroidSettings.Global.putInt(cr, key, PRIORITY_OFF)
            }
        } catch (ex: Exception) {
            AppLog.w("DongleBtGuard: could not hold the car's Bluetooth back: ${ex.message}")
            return
        }
        e.putBoolean("armed", true).apply()
        if (!already) AppLog.i("DongleBtGuard: car's Bluetooth auto-connect held back ($why) (#64)")
    }

    @Synchronized
    fun release(c: Context, why: String) {
        val p = prefs(c)
        if (!p.getBoolean("armed", false)) return
        val cr = c.contentResolver
        try {
            for (mac in devices(c)) for (k in KEYS) {
                val key = k + mac
                AndroidSettings.Global.putInt(cr, key, p.getInt("orig_$key", PRIORITY_UNDEFINED))
            }
        } catch (ex: Exception) {
            AppLog.w("DongleBtGuard: could not give the car its Bluetooth back: ${ex.message}")
        }
        p.edit().putBoolean("armed", false).apply()
        AppLog.i("DongleBtGuard: car's Bluetooth auto-connect given back ($why) (#64)")
    }

    private fun handsFreeConnected(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.getProfileConnectionState(PROFILE_HEADSET_CLIENT)
            ?.let { it != BluetoothProfile.STATE_DISCONNECTED } ?: false
    } catch (_: Exception) { false }

    /** MG4Link started (the car starting): hold the car back and wait for the adapter. */
    fun onServiceStarted(c: Context) {
        if (startedThisProcess) return
        startedThisProcess = true
        if (!enabled(c)) { release(c, "off in the settings"); return }
        val app = c.applicationContext
        arm(app, "start")
        if (handsFreeConnected()) {
            AppLog.i("DongleBtGuard: the car already took the phone - restarting Bluetooth to give the adapter a chance (#64)")
            cycleBluetooth()
        }
        handler.postDelayed({
            if (!sessionOverUsb && isArmed(app)) {
                AppLog.i("DongleBtGuard: no Android Auto through the adapter in ${WAIT_MS / 1000} s (#64)")
                release(app, "timeout")
                if (!handsFreeConnected()) cycleBluetooth()
            }
        }, WAIT_MS)
    }

    /** A session started: over USB, the adapter won; the car gets its Bluetooth back. */
    fun onSessionStarted(c: Context, overUsb: Boolean) {
        if (!overUsb) return
        sessionOverUsb = true
        if (!isArmed(c)) return
        release(c, "Android Auto over USB")
        // The hands-free reconnects to the Android Auto phone (#59); keep the hold stored once it does.
        handler.postDelayed({ if (handsFreeConnected()) arm(c.applicationContext, "hands-free back") }, 20_000L)
    }

    fun onSessionEnded(c: Context) {
        sessionOverUsb = false
        if (!handsFreeConnected()) arm(c, "phone left")
    }

    /** Hands-free connected: keep the hold stored for the next start (the live link stays). */
    fun onHandsFreeConnected(c: Context) {
        arm(c, "hands-free connected")
    }

    /** Hands-free lost during a session over USB: let the phone reconnect it. */
    fun onHandsFreeDisconnected(c: Context) {
        if (sessionOverUsb) release(c, "hands-free lost during Android Auto")
    }

    /** Turning the setting off gives the car its Bluetooth back at once. */
    fun onSettingChanged(c: Context, on: Boolean) {
        if (on) arm(c, "turned on") else release(c, "turned off")
    }

    @SuppressLint("MissingPermission")
    private fun cycleBluetooth() {
        Thread({
            try {
                val ad = BluetoothAdapter.getDefaultAdapter() ?: return@Thread
                @Suppress("DEPRECATION") ad.disable()
                var i = 0
                while (ad.state != BluetoothAdapter.STATE_OFF && i++ < 50) Thread.sleep(100)
                @Suppress("DEPRECATION") ad.enable()
            } catch (e: Exception) {
                AppLog.w("DongleBtGuard: Bluetooth restart failed: ${e.message}")
            }
        }, "DongleBtCycle").start()
    }
}
