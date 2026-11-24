package com.andrerinas.openheadunit.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.HeadUnitScreenConfig
import kotlin.math.roundToInt

/**
 * Side strips other apps reserve over Android Auto (#69): an app that draws a panel along the left
 * or right edge asks for a margin, and Android Auto lays itself out in the remaining width.
 *
 * Each app keeps its own request per side; the widest request on a side wins, and an app that
 * sends 0 only withdraws its own. Requests stay until withdrawn or MG4Link restarts, and are applied
 * again at every new session, so the caller does not need to know when a session starts.
 */
object SideMargins {
    enum class Side { LEFT, RIGHT }

    /** No side may take more than a third of the screen width. */
    const val MAX_FRACTION = 3

    private val requests = mutableMapOf<Pair<String, Side>, Int>()

    /** Records [owner]'s request; returns true when the effective margins changed. */
    @Synchronized
    fun request(owner: String, side: Side, px: Int, screenWidthPx: Int): Boolean {
        val before = effective(screenWidthPx)
        if (px <= 0) requests.remove(owner to side) else requests[owner to side] = px
        return effective(screenWidthPx) != before
    }

    /** Effective (left, right) reservation in screen pixels, widest request per side, capped. */
    @Synchronized
    fun effective(screenWidthPx: Int): Pair<Int, Int> {
        val cap = if (screenWidthPx > 0) screenWidthPx / MAX_FRACTION else 0
        fun side(s: Side) = (requests.filterKeys { it.second == s }.values.maxOrNull() ?: 0).coerceIn(0, cap)
        return side(Side.LEFT) to side(Side.RIGHT)
    }

    @Synchronized
    fun clear() = requests.clear()

    /** Screen pixels to the video's pixels, the unit Android Auto's margins are given in. */
    fun toVideoPx(screenPx: Int, screenWidthPx: Int, videoWidthPx: Int): Int =
        if (screenWidthPx <= 0) 0 else (screenPx.toLong() * videoWidthPx / screenWidthPx).toInt()

    /** The margins to send to the phone: the screen's own plus the reserved strips, in video pixels. */
    fun uiMargins(): IntArray {
        val screenW = HeadUnitScreenConfig.getUsableWidth()
        val (left, right) = effective(screenW)
        // The video maps onto the screen with the side margins removed.
        val videoW = HeadUnitScreenConfig.getNegotiatedWidth() - HeadUnitScreenConfig.getWidthMargin()
        return intArrayOf(
            HeadUnitScreenConfig.getLeftMargin() + toVideoPx(left, screenW, videoW),
            HeadUnitScreenConfig.getTopMargin(),
            HeadUnitScreenConfig.getRightMargin() + toVideoPx(right, screenW, videoW),
            HeadUnitScreenConfig.getBottomMargin(),
        )
    }

    /** The left reservation in video pixels, the offset touches have to lose (0 when none). */
    fun leftVideoPx(): Int {
        val screenW = HeadUnitScreenConfig.getUsableWidth()
        val left = effective(screenW).first
        if (left == 0) return 0
        val videoW = HeadUnitScreenConfig.getNegotiatedWidth() - HeadUnitScreenConfig.getWidthMargin()
        return toVideoPx(left, screenW, videoW)
    }

    /** Sends the current margins when a session is running. */
    fun apply(context: Context, reason: String) {
        val comm = App.provide(context).commManager
        if (comm.connectionState.value !is CommManager.ConnectionState.TransportStarted) return
        val m = uiMargins()
        AppLog.i("SideMargins: $reason - margins L=${m[0]} T=${m[1]} R=${m[2]} B=${m[3]} (#69)")
        comm.sendUpdateUiConfigRequest(m[0], m[1], m[2], m[3])
    }

    /** At the start of a session's video: re-apply reserved strips, if any. */
    fun onVideoStarted(context: Context) {
        val (left, right) = effective(HeadUnitScreenConfig.getUsableWidth())
        if (left == 0 && right == 0) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ apply(context, "session start") }, 1_000L)
    }

    /** Handles RESERVE_LEFT / RESERVE_RIGHT with "px" (0 withdraws) and an optional "package". */
    fun handle(context: Context, intent: Intent) {
        val side = when (intent.action) {
            ACTION_RESERVE_LEFT -> Side.LEFT
            ACTION_RESERVE_RIGHT -> Side.RIGHT
            else -> return
        }
        val px = intent.getIntExtra(EXTRA_PX, 0)
        val owner = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotBlank() } ?: "unnamed"
        val changed = request(owner, side, px, HeadUnitScreenConfig.getUsableWidth())
        AppLog.i("SideMargins: $owner asks $px px on the ${side.name.lowercase()} (changed=$changed) (#69)")
        if (changed) apply(context, "request from $owner")
    }

    const val ACTION_RESERVE_LEFT = "com.allgo.app.androidauto.RESERVE_LEFT"
    const val ACTION_RESERVE_RIGHT = "com.allgo.app.androidauto.RESERVE_RIGHT"
    const val EXTRA_PX = "px"
    const val EXTRA_PACKAGE = "package"
}

/** Receives the reservations; the manifest restricts it to apps signed like MG4Link (platform key). */
class SideMarginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SideMargins.handle(context, intent)
}
