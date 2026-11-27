package com.andrerinas.openheadunit.hud

import android.content.Context
import android.content.ServiceConnection
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Shows the track playing over Android Auto in the instrument cluster (#76), on its media page.
 *
 * The cluster is the car's second Android display: a window is added there, in the free area right
 * of the dials, only while the cluster shows its media page, music plays, the car is not in reverse
 * and the screen is on (see [ClusterMediaPolicy]). Off unless the experimental setting is on.
 *
 * Main thread for the window; car reads and cover decoding on their own thread.
 */
internal class ClusterMediaOverlay(context: Context, private val enabled: () -> Boolean) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("cluster-media").also { it.start() }
    private val io = Handler(thread.looper)
    private val displays = this.context.getSystemService(DisplayManager::class.java)
    private val power = this.context.getSystemService(PowerManager::class.java)
    private val tabReader = ClusterTabReader(this.context)

    // Track state, main thread.
    private var song = ""
    private var artist = ""
    private var cover: Bitmap? = null
    private var durationMs = 0L
    private var playing = false
    private var positionMs = 0L
    private var positionAtMs = 0L
    private var metadataId = 0

    // Car state, written by the IO thread.
    @Volatile private var tab: Int? = null
    @Volatile private var tabAtMs = 0L
    @Volatile private var gearRaw: Int? = null
    private var readPending = false

    private var windows: WindowManager? = null
    private var card: ClusterMediaCard? = null
    private var closed = false

    fun onMetadata(title: String, by: String, coverBytes: ByteArray?, durationSeconds: Long) = main.post {
        if (closed) return@post
        song = title
        artist = by
        durationMs = durationSeconds * 1000
        cover = null
        val id = ++metadataId
        if (enabled() && coverBytes != null && coverBytes.isNotEmpty()) io.post {
            val bitmap = decode(coverBytes)
            main.post { if (id == metadataId && !closed) { cover = bitmap; refresh() } }
        }
        refresh()
    }

    fun onStatus(isPlaying: Boolean?, positionSeconds: Long?) = main.post {
        if (closed) return@post
        if (isPlaying != null) playing = isPlaying
        if (positionSeconds != null) {
            positionMs = positionSeconds * 1000
            positionAtMs = SystemClock.elapsedRealtime()
        }
        refresh()
    }

    /** The session ended: nothing plays any more. */
    fun clear() = main.post {
        song = ""; artist = ""; cover = null; playing = false; durationMs = 0; metadataId++
        refresh()
    }

    fun close() = main.post {
        closed = true
        main.removeCallbacksAndMessages(null)
        hide()
        io.post { tabReader.close(); thread.quitSafely() }
    }

    private val tick = Runnable { refresh() }

    private fun refresh() {
        main.removeCallbacks(tick)
        if (closed) return
        val wanted = enabled() && song.isNotEmpty() && playing
        if (!wanted) {
            hide()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!readPending) {
            readPending = true
            io.post {
                tab = tabReader.read()?.also { tabAtMs = SystemClock.elapsedRealtime() }
                gearRaw = runCatching { com.evsuite.hardware.EVHardware.getGearRaw() }.getOrNull()
                main.post { readPending = false }
            }
        }
        val show = ClusterMediaPolicy.visible(true, true, true, tab, now - tabAtMs, gearRaw, power?.isInteractive != false)
        if (show) {
            if (card == null) show()
            card?.bind(song, artist, cover, ClusterMediaPolicy.positionMs(positionMs, positionAtMs, now, playing, durationMs), durationMs)
        } else hide()
        main.postDelayed(tick, TICK_MS)
    }

    private fun clusterDisplay(): Display? {
        var other: Display? = null
        for (display in displays?.displays.orEmpty()) {
            if (display.displayId == Display.DEFAULT_DISPLAY || !display.isValid) continue
            val type = runCatching { Display::class.java.getMethod("getType").invoke(display) as Int }.getOrNull()
            if (type != null && type != 1 && type != 2) continue // built-in or HDMI, not virtual
            if (display.displayId == 1) return display
            if (type != null && other == null) other = display
        }
        return other
    }

    private fun show() {
        val display = clusterDisplay() ?: return
        val size = Point().also { display.getRealSize(it) }
        val (x, y, side) = ClusterMediaPolicy.bounds(size.x, size.y).let { Triple(it[0], it[1], it[2]) }
        if (side == 0) return
        try {
            val displayContext = context.createDisplayContext(display)
            val wm = displayContext.getSystemService(WindowManager::class.java)
            val newCard = ClusterMediaCard(displayContext, side)
            val params = WindowManager.LayoutParams(side, side,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x; this.y = y
                title = "MG4Link cluster media"
            }
            wm.addView(newCard.view, params)
            windows = wm
            card = newCard
            AppLog.i("ClusterMedia: card shown on display ${display.displayId} ${size.x}x${size.y} at $x,$y side $side")
        } catch (e: Exception) {
            AppLog.w("ClusterMedia: cannot show the card: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun hide() {
        val view = card?.view ?: return
        runCatching { windows?.removeViewImmediate(view) }
        card = null
        windows = null
        AppLog.i("ClusterMedia: card hidden")
    }

    private fun decode(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ClusterMediaPolicy.MAX_SIDE) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TICK_MS = 250L
    }
}

/**
 * Reads the cluster's page through the car service, without changing anything: the vendor
 * instrument cluster manager first, then the generic property manager. Reflection, as android.car
 * is only on the car. IO thread only.
 */
internal class ClusterTabReader(private val context: Context) {
    private var car: Any? = null
    private var cluster: Any? = null
    private var properties: Any? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { cluster = null; properties = null }
        override fun onServiceDisconnected(name: ComponentName?) { cluster = null; properties = null }
    }

    fun read(): Int? = try {
        val c = car ?: Class.forName("android.car.Car").let { type ->
            val created = type.getMethod("createCar", Context::class.java, ServiceConnection::class.java)
                .invoke(null, context, connection) ?: return null
            type.getMethod("connect").invoke(created)
            created
        }.also { car = it }
        val manager = c.javaClass.getMethod("getCarManager", String::class.java)
        if (cluster == null) cluster = runCatching { manager.invoke(c, "vendorinstrumentcluster") }.getOrNull()
        readFrom(cluster, vendor = true) ?: run {
            if (properties == null) properties = runCatching { manager.invoke(c, "property") }.getOrNull()
            readFrom(properties, vendor = false)
        }
    } catch (e: Throwable) {
        null
    }

    private fun readFrom(manager: Any?, vendor: Boolean): Int? {
        manager ?: return null
        for (area in if (vendor) intArrayOf(0) else intArrayOf(0x1000000, 0)) {
            try {
                var value: Any? = if (vendor)
                    manager.javaClass.getMethod("getGlobalProperty", Class::class.java, Int::class.javaPrimitiveType)
                        .invoke(manager, Integer::class.java, ClusterMediaPolicy.TAB_PROPERTY)
                else manager.javaClass.getMethod("getProperty", Class::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(manager, Integer::class.java, ClusterMediaPolicy.TAB_PROPERTY, area)
                val raw = value
                if (raw != null && raw !is Int) {
                    val status = runCatching { raw.javaClass.getMethod("getStatus").invoke(raw) as? Int }.getOrNull()
                    if (status != null && status != 0) continue
                    value = raw.javaClass.getMethod("getValue").invoke(raw)
                }
                if (value is Int && value in 0..7) return value
            } catch (_: Exception) { }
        }
        return null
    }

    fun close() {
        runCatching { car?.javaClass?.getMethod("disconnect")?.invoke(car) }
        car = null; cluster = null; properties = null
    }
}
