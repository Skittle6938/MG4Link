package com.andrerinas.openheadunit.vehicle

/**
 * The distance left to the final destination while Android Auto guides, from the navigation
 * status Maps sends the car (NavigationCurrentPosition, the last stop). For [StartReserve]: on a
 * guided trip shorter than the reserve's 15 km, only what the trip spends is set aside (#14).
 */
object RouteLeft {
    /** A reading older than this means guidance stopped or Android Auto went away. */
    const val STALE_MS = 60_000L

    @Volatile private var meters = -1
    @Volatile private var atMs = 0L

    fun update(metersToDestination: Int?, nowMs: Long) {
        meters = metersToDestination?.takeIf { it >= 0 } ?: -1
        atMs = nowMs
    }

    fun clear() {
        meters = -1
    }

    /** Km left to the destination, or null without a fresh reading. */
    fun km(nowMs: Long): Float? =
        meters.takeIf { it >= 0 && nowMs - atMs <= STALE_MS }?.let { it / 1000f }
}
