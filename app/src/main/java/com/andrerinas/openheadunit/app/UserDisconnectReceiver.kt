package com.andrerinas.openheadunit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.Mg4DashContract
import com.andrerinas.openheadunit.utils.AppLog

/**
 * MG4Dash saw three fingers slide down from the top of the screen, over the car's status bar where
 * MG4Link gets no touch: it asks MG4Link to disconnect, as its own three finger gesture does.
 * Guarded by a signature permission in the manifest; ignored when no session is up.
 */
class UserDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val commManager = App.provide(context).commManager
        val sessionUp = commManager.isConnected || commManager.isBusy
        if (!Mg4DashContract.shouldDisconnect(intent.action, sessionUp)) {
            AppLog.i("MG4 Dash disconnect request ignored (action=${intent.action}, session=$sessionUp)")
            return
        }
        AppLog.i("MG4 Dash asked to disconnect (three fingers from the top)")
        commManager.disconnect(sendByeBye = true)
        context.startActivity(
            Intent(context, com.andrerinas.openheadunit.main.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }
}
