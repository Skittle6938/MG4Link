package com.andrerinas.openheadunit.aap

/** When a cable link the phone no longer answers on may be reset (see AapService.resetDeadUsbLink). */
internal object UsbLinkResetPolicy {
    /** Lets the failed connection close the device before it is opened again for the reset. */
    const val SETTLE_MS = 300L

    /** A reset that did not bring the phone back is not repeated before this. */
    const val COOLDOWN_MS = 60_000L

    /** After the reset, when to look for the phone again if no attach event came. */
    const val RESCAN_MS = 2_000L

    fun mayReset(nowMs: Long, lastResetMs: Long): Boolean = lastResetMs == 0L || nowMs - lastResetMs >= COOLDOWN_MS
}
