package com.andrerinas.openheadunit.aap

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Media keys the car sends AllGo's Android Auto, as press and release, through rui
 * (IAndroidAutoMainService.sendMediaPlayControl). The SAIC launcher's CPAAModel.mediaControlCPAA
 * turns the steering wheel and its media card into: next 7/11, previous 8/12, play/pause 20/21
 * (press/release). They go to the phone as the same key press and release, as AllGo does.
 */
object AllgoMediaKeys {
    @Volatile
    private var lastPlayPauseMs = 0L

    @Volatile
    private var lastSkipMs = 0L

    /** When DiAuto last sent a wheel skip of its own (CarKeyBroadcastReceiver). */
    @Volatile
    private var lastOwnSkipMs = 0L

    fun noteOwnSkip() { lastOwnSkipMs = SystemClock.elapsedRealtime() }

    /** When DiAuto last sent the wheel's play/pause of its own. */
    @Volatile
    private var lastOwnPlayPauseMs = 0L

    fun noteOwnPlayPause() { lastOwnPlayPauseMs = SystemClock.elapsedRealtime() }
    /** When the launcher last forwarded the phone key (rui launchApp 3). */
    @Volatile @JvmField var lastLaunchPhoneMs = 0L

    /** Returns false for a code that is not one of these. */
    @JvmStatic
    fun fromRui(context: Context, code: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (code == 3 || code == 4) lastSkipMs = now
        val (key, down) = when (code) {
            7 -> KeyEvent.KEYCODE_MEDIA_NEXT to true
            11 -> KeyEvent.KEYCODE_MEDIA_NEXT to false
            8 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to true
            12 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to false
            20 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to true
            21 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to false
            else -> return false
        }
        // The same key, sent again by a launcher after DiAuto already sent it from the wheel: one
        // skip or one pause, not two. A dropped copy does not count as the launcher's own key, so a
        // real second press on the wheel right after still goes through (#28).
        val ownAt = if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) lastOwnPlayPauseMs else lastOwnSkipMs
        if (now - ownAt < OWN_KEY_WINDOW_MS) {
            AppLog.i("AllgoMediaKeys: rui code $code dropped - the wheel key was already sent")
            return true
        }
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) lastPlayPauseMs = now else lastSkipMs = now
        App.provide(context).commManager.sendKey(key, down, null, "allgo-rui")
        AppLog.i("AllgoMediaKeys: rui code $code -> key $key ${if (down) "down" else "up"}")
        return true
    }

    /** A key press and release to the phone. */
    @JvmStatic
    fun click(context: Context, key: Int, source: String) {
        val comm = App.provide(context).commManager
        comm.sendKey(key, true, null, source)
        comm.sendKey(key, false, null, source)
    }

    /**
     * DiAuto also reads the wheel's play/pause key itself (for launchers that do not tell rui).
     * When the launcher sent it through rui too, that copy is dropped so the music does not
     * pause and play again.
     */
    fun playPauseCameThroughRui(withinMs: Long = 1500L): Boolean =
        SystemClock.elapsedRealtime() - lastPlayPauseMs < withinMs

    /**
     * The stock launcher does nothing with the wheel's left/right keys for Android Auto: AllGo reads
     * them itself, and so does DiAuto. A launcher that also sends them as AllGo's next/previous gets
     * one skip, not two.
     */
    fun skipCameThroughRui(withinMs: Long = 1500L): Boolean =
        SystemClock.elapsedRealtime() - lastSkipMs < withinMs

    private const val OWN_KEY_WINDOW_MS = 1500L
}
