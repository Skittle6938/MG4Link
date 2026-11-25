package com.andrerinas.openheadunit.connection

/**
 * When to restart an access point that was switched on but never became usable (#73).
 *
 * Seen on a car: a start sweep left the access point interface up with no address, and the phone
 * then asked for the network every few seconds for 11 minutes, because a refresh only looks again
 * and switching it on is tried once per run. A clean stop and start is what such a state needs;
 * it waits [RESTART_AFTER_MS] after the last switch-on, so an access point that is merely slow is
 * not raced, and happens at most [MAX_RESTARTS] times per run.
 */
object StuckAccessPointPolicy {
    const val RESTART_AFTER_MS = 20_000L
    const val MAX_RESTARTS = 2

    /**
     * @param lastSwitchOnMs when the last switch-on (or restart) finished, null when none was tried
     * @param startInFlight a start sweep is still running: never interrupted
     */
    fun shouldRestart(
        nowMs: Long,
        lastSwitchOnMs: Long?,
        restartsDone: Int,
        autoEnable: Boolean,
        startInFlight: Boolean,
    ): Boolean = autoEnable && !startInFlight && lastSwitchOnMs != null &&
        nowMs - lastSwitchOnMs >= RESTART_AFTER_MS && restartsDone < MAX_RESTARTS
}
