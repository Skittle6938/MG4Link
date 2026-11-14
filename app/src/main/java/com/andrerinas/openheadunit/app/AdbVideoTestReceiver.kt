package com.andrerinas.openheadunit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * Sets the video test options from ADB, to compare them without going through the settings
 * screen, which cannot be reached while a session holds the screen:
 *
 *   adb shell am broadcast -a com.allgo.app.androidauto.VIDEO_TEST \
 *     -n com.allgo.app.androidauto/com.andrerinas.openheadunit.app.AdbVideoTestReceiver \
 *     --ei resolutionId 2 --ez lowLatency true
 *
 * Guarded by android.permission.DUMP in the manifest, which only the ADB shell and the system
 * hold. Applies at the next connection, like the settings screen.
 */
class AdbVideoTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        if (intent.hasExtra("resolutionId")) {
            val id = intent.getIntExtra("resolutionId", settings.resolutionId)
            if (Settings.Resolution.values().any { it.id == id }) settings.resolutionId = id
        }
        if (intent.hasExtra("lowLatency")) {
            settings.debugVideoLowLatency = intent.getBooleanExtra("lowLatency", false)
        }
        AppLog.i("ADB video test: resolutionId=${settings.resolutionId} lowLatency=${settings.debugVideoLowLatency}")
    }
}
