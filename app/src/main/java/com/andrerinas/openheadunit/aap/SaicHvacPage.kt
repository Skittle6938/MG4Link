package com.andrerinas.openheadunit.aap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog
import com.evsuite.hardware.FirmwareInfo

/** The rest of the watch, from the first time Android Auto was shown ([firstShownAtMs]). */
internal object HvacWatchWindow {
    fun leftMs(firstShownAtMs: Long, nowMs: Long, watchMs: Long): Long = watchMs - (nowMs - firstShownAtMs)
}

/**
 * When the climate page is to be closed: the climate has come on (at the first reading, or after
 * being off) and Android Auto is in front, once for each time the climate came on. The close is
 * not lost when Android Auto is not in front at the reading that sees the climate on.
 */
internal class HvacCloseWhen {
    private var lastPower: Boolean? = null
    private var pending = false

    /** @param power the climate's state, null when it could not be read. @return close the page now. */
    fun onReading(power: Boolean?, androidAutoInFront: Boolean): Boolean {
        if (power == true && lastPower != true) pending = true
        if (power == false) pending = false
        if (power != null) lastPower = power
        if (pending && power == true && androidAutoInFront) {
            pending = false
            return true
        }
        return false
    }
}

/**
 * SWI68 climate page (#13): when the climate comes back on as the car starts, SystemUI opens its
 * climate page on its own, on top of Android Auto. MG4Link closes it, as SystemUI itself does,
 * through its exported StartActivityService, during the first minute after Android Auto first
 * shows only: a page the driver opens later stays open. The minute used to count from MG4Link's
 * start, which remote pre-conditioning brings minutes before the driver gets in (#32). R73 names the interface IPageService (closeHvac),
 * R46 IStartActivityService (stopHVAC); both use transaction 2. MG4 Dash does the same (#62).
 */
object SaicHvacPage {
    private const val ACTION = "com.android.systemui.saicmotor.action.StartActivity"
    private val DESCRIPTORS = listOf("com.saicmotor.sdk.external.IPageService", "com.android.systemui.IStartActivityService")
    private const val TX_CLOSE = 2
    private const val WATCH_MS = 60_000L
    private const val POLL_MS = 500L
    /** SystemUI posts the opening on its handler: closing at once could come before it. */
    private const val CLOSE_DELAY_MS = 300L

    private val handler = Handler(Looper.getMainLooper())
    /** When Android Auto first came to the screen in this process; 0 until then. */
    @Volatile private var firstShownAtMs = 0L
    @Volatile private var binder: IBinder? = null
    @Volatile private var binding = false
    private var watching = false
    private var lastPower: Boolean? = null
    private var appContext: Context? = null
    private var closeWhen = HvacCloseWhen()

    private fun applies() = FirmwareInfo.getGeneration() == FirmwareInfo.Gen.SWI68

    /** Android Auto took the screen: watches the climate for the rest of the first minute. */
    fun onProjectionShown(context: Context) {
        if (!applies() || watching) return
        val now = SystemClock.elapsedRealtime()
        if (firstShownAtMs == 0L) firstShownAtMs = now
        val left = HvacWatchWindow.leftMs(firstShownAtMs, now, WATCH_MS)
        if (left <= 0) return
        appContext = context.applicationContext
        bind(context.applicationContext)
        watching = true
        lastPower = null
        closeWhen = HvacCloseWhen()
        handler.post(poll)
        handler.postDelayed({ stop() }, left)
    }

    private fun stop() {
        watching = false
        handler.removeCallbacks(poll)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!watching) return
            Thread({
                val power = runCatching { com.evsuite.hardware.saic.SaicClimate.powerOn() }.getOrNull()
                handler.post {
                    // On at the first reading too: the page opened just before Android Auto came up.
                    // Remote pre-conditioning pauses the screen several times as the car starts (#32):
                    // the close then waits for the moment Android Auto is in front.
                    if (closeWhen.onReading(power, AapProjectionActivity.isForeground)) {
                        AppLog.i("SaicHvacPage: climate on at start with Android Auto in front - closing the climate page")
                        handler.postDelayed({ Thread({ close() }, "hvac-page").start() }, CLOSE_DELAY_MS)
                    }
                    if (power != null) lastPower = power
                    if (watching) handler.postDelayed(this, POLL_MS)
                }
            }, "hvac-page").start()
        }
    }

    private fun bind(ctx: Context) {
        if (binder?.isBinderAlive == true || binding) return
        binding = true
        val i = Intent(ACTION).setPackage("com.android.systemui")
            .setComponent(ComponentName("com.android.systemui", "com.android.systemui.StartActivityService"))
        val ok = try {
            ctx.bindService(i, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; binding = false }
                override fun onServiceDisconnected(name: ComponentName?) { binder = null; binding = false }
            }, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            AppLog.w("SaicHvacPage: bind failed: ${e.message}")
            false
        }
        if (!ok) {
            binding = false
            AppLog.i("SaicHvacPage: SystemUI StartActivityService not available on this firmware")
        }
    }

    private fun close() {
        val b = binder?.takeIf { it.isBinderAlive } ?: run {
            appContext?.let { bind(it) }
            AppLog.i("SaicHvacPage: SystemUI service not connected yet - page left open")
            return
        }
        for (descriptor in DESCRIPTORS) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(descriptor)
                b.transact(TX_CLOSE, data, reply, 0)
                reply.readException()
                AppLog.i("SaicHvacPage: climate page closed (${descriptor.substringAfterLast('.')})")
                return
            } catch (e: Exception) {
                AppLog.i("SaicHvacPage: ${descriptor.substringAfterLast('.')} refused: ${e.message}")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
