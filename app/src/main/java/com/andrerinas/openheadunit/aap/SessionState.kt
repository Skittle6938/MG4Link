package com.andrerinas.openheadunit.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Tells other apps signed like MG4Link (platform key) whether an Android Auto session is running,
 * and over which transport (#78). Nothing about the phone: no name, no address.
 *
 * - On every change MG4Link broadcasts [ACTION_STATE] with [EXTRA_CONNECTED] and
 *   [EXTRA_TRANSPORT], to receivers holding [PERMISSION].
 * - An app started later asks with [ACTION_QUERY] (same permission). Sent as an ordered broadcast,
 *   the answer comes back in the result extras; sent as a plain one, [ACTION_STATE] is broadcast
 *   again.
 */
object SessionState {
    const val ACTION_STATE = "com.allgo.app.androidauto.SESSION_STATE"
    const val ACTION_QUERY = "com.allgo.app.androidauto.QUERY_SESSION_STATE"
    const val PERMISSION = "com.allgo.app.androidauto.permission.SESSION_STATE"
    /** Boolean: an Android Auto session is running. */
    const val EXTRA_CONNECTED = "connected"
    /** String: [TRANSPORT_WIRELESS] or [TRANSPORT_USB] while connected, empty otherwise. */
    const val EXTRA_TRANSPORT = "transport"
    const val TRANSPORT_WIRELESS = "wireless"
    const val TRANSPORT_USB = "usb"

    @Volatile var connected = false
        private set
    @Volatile var transport = ""
        private set

    /** The extras describing a state; the transport is only given while connected. */
    fun extras(connected: Boolean, wireless: Boolean): Bundle = Bundle().apply {
        putBoolean(EXTRA_CONNECTED, connected)
        putString(EXTRA_TRANSPORT, if (!connected) "" else if (wireless) TRANSPORT_WIRELESS else TRANSPORT_USB)
    }

    /** Records the new state and broadcasts it when it changed. */
    fun publish(context: Context, connected: Boolean, wireless: Boolean) {
        val extras = extras(connected, wireless)
        val newTransport = extras.getString(EXTRA_TRANSPORT).orEmpty()
        if (connected == this.connected && newTransport == transport) return
        this.connected = connected
        transport = newTransport
        AppLog.i("SessionState: connected=$connected transport=${newTransport.ifEmpty { "-" }} (#78)")
        broadcast(context)
    }

    fun broadcast(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ACTION_STATE).putExtras(extras(connected, transport == TRANSPORT_WIRELESS)), PERMISSION)
    }
}

/** [SessionState.ACTION_QUERY] from apps signed like MG4Link. */
class SessionStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = SessionState.extras(SessionState.connected, SessionState.transport == SessionState.TRANSPORT_WIRELESS)
        if (isOrderedBroadcast) {
            setResultCode(1)
            setResultExtras(extras)
        } else {
            SessionState.broadcast(context)
        }
    }
}
