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
 *   (or --es language en, for the README screenshots)
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
        // App language for the README screenshots (e.g. "en"; "" = the car's language).
        intent.getStringExtra("language")?.let {
            com.andrerinas.openheadunit.utils.LocaleHelper.setScreenshotLanguage(context, it)
        }
        // Side strip over Android Auto, as SideMarginReceiver gets from other apps (#69).
        if (intent.hasExtra("reserveRight") || intent.hasExtra("reserveLeft")) {
            val side = if (intent.hasExtra("reserveLeft")) com.andrerinas.openheadunit.aap.SideMargins.Side.LEFT
                else com.andrerinas.openheadunit.aap.SideMargins.Side.RIGHT
            val px = intent.getIntExtra(if (side == com.andrerinas.openheadunit.aap.SideMargins.Side.LEFT) "reserveLeft" else "reserveRight", 0)
            com.andrerinas.openheadunit.aap.SideMargins.request("adb", side, px,
                com.andrerinas.openheadunit.utils.HeadUnitScreenConfig.getUsableWidth())
            com.andrerinas.openheadunit.aap.SideMargins.apply(context, "ADB test")
        }
        // A sentence for the phone's assistant, as AssistantCommandReceiver gets it from other apps
        // (#71): --es say "Play Michael Jackson on YouTube Music" or --es navigate "Gare de Lyon",
        // [--es lang fr] [--ez speakReply true]
        if (intent.hasExtra("say") || intent.hasExtra("navigate")) {
            com.andrerinas.openheadunit.aap.AssistantCommand.request(context,
                com.andrerinas.openheadunit.aap.AssistantCommand.Command(
                    phrase = intent.getStringExtra("say"),
                    label = intent.getStringExtra("navigate"),
                    language = intent.getStringExtra("lang"),
                    speakReply = intent.getBooleanExtra("speakReply", false)))
        }
        // Hear the speech engine on the car's speakers: --es previewSay "Navigue vers la gare de Lyon"
        // [--es lang fr] [--es engine com.mg4link.picotts]
        intent.getStringExtra("previewSay")?.let { phrase ->
            com.andrerinas.openheadunit.aap.SpeechSynthesis.preview(context,
                intent.getStringExtra("lang") ?: java.util.Locale.getDefault().language, phrase,
                intent.getStringExtra("engine"))
        }
        AppLog.i("ADB video test: resolutionId=${settings.resolutionId} lowLatency=${settings.debugVideoLowLatency}")
    }
}
