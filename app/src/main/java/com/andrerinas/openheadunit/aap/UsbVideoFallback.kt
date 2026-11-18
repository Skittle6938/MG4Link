package com.andrerinas.openheadunit.aap

import android.os.SystemClock

/**
 * Phones and tablets that accept the 1080p picture over USB and then never send a frame (#31: an
 * Ulefone tablet on Android 13 took 1920x1080, sent nothing, and its Android Auto gave up with
 * "Communication error 7", while AllGo, which asks for less, worked). When a USB session has had
 * its video set up and no picture comes within [NO_VIDEO_MS], the device is remembered and asked
 * for 720p from then on; the session is ended so the reconnection gets that size at once.
 */
object UsbVideoFallback {
    const val NO_VIDEO_MS = 10_000L

    /** Messages on the video channel up to this size are acknowledgements and codec headers. */
    const val MIN_FRAME_BYTES = 512

    /** "Manufacturer Product" of the USB session being set up; null over Wi-Fi. */
    @Volatile var currentDevice: String? = null
        private set
    @Volatile private var videoSetupAtMs = 0L
    @Volatile private var videoSeen = false

    fun key(manufacturer: String?, product: String?): String? =
        listOfNotNull(manufacturer?.trim()?.takeIf { it.isNotEmpty() }, product?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ").takeIf { it.isNotEmpty() }

    fun sessionStarting(device: String?) {
        currentDevice = device
        videoSetupAtMs = 0L
        videoSeen = false
    }

    fun onVideoSetup() {
        videoSetupAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * The phone sends no picture while the projection is not on screen (connected in the
     * background), so the wait only counts while it is.
     */
    fun holdClock() {
        if (videoSetupAtMs > 0L) videoSetupAtMs = SystemClock.elapsedRealtime()
    }

    val pictureArrived: Boolean get() = videoSeen

    fun onVideoMessage(size: Int) {
        if (size >= MIN_FRAME_BYTES) videoSeen = true
    }

    /** True once a USB session with video set up has gone [NO_VIDEO_MS] without a frame. */
    fun pictureMissing(now: Long = SystemClock.elapsedRealtime()): Boolean =
        shouldFallBack(currentDevice != null, videoSetupAtMs, videoSeen, now)

    fun shouldFallBack(overUsb: Boolean, videoSetupAtMs: Long, videoSeen: Boolean, now: Long): Boolean =
        overUsb && videoSetupAtMs > 0L && !videoSeen && now - videoSetupAtMs >= NO_VIDEO_MS

    /** Whether the session being set up must ask for 720p at most. */
    fun capActive(devices: Set<String>): Boolean = currentDevice?.let { it in devices } ?: false
}
