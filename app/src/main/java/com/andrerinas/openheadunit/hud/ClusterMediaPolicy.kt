package com.andrerinas.openheadunit.hud

/**
 * When and where the playing track goes in the instrument cluster (#76). Pure, so it can be tested.
 *
 * The cluster is the car's second Android display. Its page is the vendor property
 * IPK_CLUSTER_TAB_BTN_STS (1 navigation, 2 media), as MG4-360-Camera-App reads it for its rear view;
 * the free area it leaves on the right is a square of up to 260 px.
 */
internal object ClusterMediaPolicy {
    /** IPK_CLUSTER_TAB_BTN_STS: the page the cluster shows. */
    const val TAB_PROPERTY = 0x21407b18
    const val TAB_MEDIA = 2

    /** A page read longer ago than this is not trusted. */
    const val TAB_FRESH_MS = 1_000L

    /** Raw gear for R (1 P, 2 R, 3 N, 4 D on every SWI firmware). */
    const val RAW_REVERSE = 2

    /** Largest side of the card, cluster pixels. */
    const val MAX_SIDE = 260

    fun visible(
        enabled: Boolean,
        hasTrack: Boolean,
        playing: Boolean,
        tab: Int?,
        tabAgeMs: Long,
        gearRaw: Int?,
        screenOn: Boolean,
    ): Boolean = enabled && hasTrack && playing && screenOn &&
        tab == TAB_MEDIA && tabAgeMs <= TAB_FRESH_MS && gearRaw != RAW_REVERSE

    /** x, y and side of the card on a cluster of [width] × [height], the area right of the dials. */
    fun bounds(width: Int, height: Int): IntArray {
        val side = maxOf(0, minOf(MAX_SIDE, minOf(width, height)))
        val x = if (width >= 424 + side) (width + 424 - side) / 2 else (width - side) / 2
        val y = if (height >= 32 + side) (height + 32 - side) / 2 else (height - side) / 2
        return intArrayOf(x, y, side)
    }

    /** Where playback is now, from the last position the phone sent and when it sent it. */
    fun positionMs(lastPositionMs: Long, receivedAtMs: Long, nowMs: Long, playing: Boolean, durationMs: Long): Long {
        val moved = if (playing) (nowMs - receivedAtMs).coerceAtLeast(0) else 0
        val position = lastPositionMs + moved
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }
}
