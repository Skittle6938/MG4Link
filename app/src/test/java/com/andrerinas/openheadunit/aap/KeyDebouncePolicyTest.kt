package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.KeyDebouncePolicy.KeyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDebouncePolicyTest {

    /**
     * Feeds deliveries through the policy the way CommManager does, and records what would have
     * reached the phone. A "delivery" is one call to sendKey.
     */
    private class Key(private val isMediaKey: Boolean = true) {
        var state = KeyState()
        val sent = mutableListOf<Pair<Boolean, Long>>()

        fun deliver(isPress: Boolean, now: Long, downTime: Long? = null, fromCarKey: Boolean = false) {
            val result = KeyDebouncePolicy.decide(state, isPress, downTime, isMediaKey, now, fromCarKey)
            state = result.state
            if (result.releaseFirst) sent.add(false to now)
            if (result.forward) sent.add(isPress to now)
        }

        /** The press/release sequence that reached the phone. */
        fun edges(): List<Boolean> = sent.map { it.first }
    }

    @Test
    fun `one press one click`() {
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 100)
        key.deliver(isPress = false, now = 60, downTime = 100)
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `the same key event arriving twice is one click - activity first`() {
        // The ordering seen in the reporter's logs: the foreground activity gets the raw KeyEvent,
        // the media button broadcast follows a few milliseconds later.
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 500)     // activity DOWN
        key.deliver(isPress = true, now = 4, downTime = 500)     // media session DOWN
        key.deliver(isPress = false, now = 4, downTime = 500)    // media session UP
        key.deliver(isPress = false, now = 6, downTime = 500)    // activity UP
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `the same key event arriving twice is one click - broadcast first`() {
        // The same press with the two paths swapped. Under the old code this ordering produced a
        // stray extra release; it must produce the same single click as the other one.
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 500)     // media session DOWN
        key.deliver(isPress = false, now = 0, downTime = 500)    // media session UP
        key.deliver(isPress = true, now = 2, downTime = 500)     // activity DOWN
        key.deliver(isPress = false, now = 8, downTime = 500)    // activity UP
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `two deliberate presses inside the old window both get through when they carry identity`() {
        // 200 ms apart is well inside the 600 ms media window that used to merge them into one.
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 100)
        key.deliver(isPress = false, now = 50, downTime = 100)
        key.deliver(isPress = true, now = 200, downTime = 300)
        key.deliver(isPress = false, now = 250, downTime = 300)
        assertEquals(listOf(true, false, true, false), key.edges())
    }

    @Test
    fun `a duplicate with no identity is still dropped by the window`() {
        // The proprietary OEM broadcasts carry no KeyEvent, so elapsed time is all there is.
        val key = Key()
        key.deliver(isPress = true, now = 0)
        key.deliver(isPress = false, now = 20)
        key.deliver(isPress = true, now = 30)
        key.deliver(isPress = false, now = 40)
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `a dropped press drops its release too`() {
        // The defect this replaces: the old code marked the key down before returning, so the
        // release of a dropped press was transmitted and Android Auto saw DOWN, UP, UP.
        val key = Key()
        key.deliver(isPress = true, now = 0)
        key.deliver(isPress = false, now = 20)
        key.deliver(isPress = true, now = 30)     // duplicate press, dropped
        key.deliver(isPress = false, now = 34)    // its release must go too
        assertEquals(listOf(true, false), key.edges())
        assertFalse(key.state.down)
    }

    @Test
    fun `a release with no outstanding press is never sent`() {
        val key = Key()
        key.deliver(isPress = false, now = 0, downTime = 100)
        assertTrue(key.edges().isEmpty())
    }

    @Test
    fun `a press whose release never arrives does not swallow the next press`() {
        // A delivery path that sends a press and no release used to latch the key: every later
        // press matched the held state and was dropped in silence until the next disconnect.
        val key = Key()
        key.deliver(isPress = true, now = 0)
        key.deliver(isPress = true, now = KeyDebouncePolicy.STUCK_PRESS_MS)
        key.deliver(isPress = false, now = KeyDebouncePolicy.STUCK_PRESS_MS + 50)
        // The stale press is closed out before the new one, so the phone is never left holding a
        // key it was told about.
        assertEquals(listOf(true, false, true, false), key.edges())
        assertFalse(key.state.down)
    }

    @Test
    fun `a genuine press and hold is not broken up`() {
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 100)
        // Auto-repeat redeliveries of the same gesture, well past the window.
        key.deliver(isPress = true, now = 1_000, downTime = 100)
        key.deliver(isPress = false, now = 1_500, downTime = 100)
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `identity is ignored when it cannot be trusted`() {
        // A synthetic KeyEvent built from an action and a keycode alone carries downTime 0. Treating
        // that as an identity would make every press look like a redelivery of the last one.
        val key = Key()
        key.deliver(isPress = true, now = 0, downTime = 0)
        key.deliver(isPress = false, now = 20, downTime = 0)
        key.deliver(isPress = true, now = 900, downTime = 0)
        key.deliver(isPress = false, now = 950, downTime = 0)
        assertEquals(listOf(true, false, true, false), key.edges())
    }

    @Test
    fun `a press with identity following one without falls back to the window`() {
        // One press reaching us by two routes, only one of which carries a KeyEvent. Identity
        // cannot prove these are different presses, so the window has to decide.
        val key = Key()
        key.deliver(isPress = true, now = 0)                   // no identity
        key.deliver(isPress = true, now = 5, downTime = 700)   // same press, with identity
        key.deliver(isPress = false, now = 10, downTime = 700)
        key.deliver(isPress = false, now = 12)
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `non-media keys use the shorter window`() {
        val key = Key(isMediaKey = false)
        key.deliver(isPress = true, now = 0)
        key.deliver(isPress = false, now = 20)
        // 400 ms is inside the media window but outside the 300 ms one, so this is a second press.
        key.deliver(isPress = true, now = 400)
        key.deliver(isPress = false, now = 420)
        assertEquals(listOf(true, false, true, false), key.edges())

        val media = Key(isMediaKey = true)
        media.deliver(isPress = true, now = 0)
        media.deliver(isPress = false, now = 20)
        media.deliver(isPress = true, now = 400)
        media.deliver(isPress = false, now = 420)
        assertEquals(listOf(true, false), media.edges())
    }

    @Test
    fun `the very first press is never treated as a duplicate`() {
        // Elapsed-time state starts at zero, and "now" can legitimately be small.
        val key = Key()
        key.deliver(isPress = true, now = 5)
        assertEquals(listOf(true), key.edges())
    }

    // --- #28: quick series of clicks on the wheel's own key --------------------------------------

    @Test
    fun `quick clicks on the wheel key all get through`() {
        // Clicks 192 ms and 214 ms apart, as in the reporter's log (09:09:37).
        val key = Key()
        for (t in listOf(0L, 192L, 406L)) {
            key.deliver(isPress = true, now = t, fromCarKey = true)
            key.deliver(isPress = false, now = t + 20, fromCarKey = true)
        }
        assertEquals(listOf(true, false, true, false, true, false), key.edges())
    }

    @Test
    fun `the same wheel press delivered twice at once is one click`() {
        val key = Key()
        key.deliver(isPress = true, now = 0, fromCarKey = true)
        key.deliver(isPress = true, now = 5, fromCarKey = true)
        key.deliver(isPress = false, now = 10, fromCarKey = true)
        key.deliver(isPress = false, now = 12, fromCarKey = true)
        assertEquals(listOf(true, false), key.edges())
    }

    @Test
    fun `the launcher's late copy of a wheel press is dropped, even after the old window`() {
        // Wheel press at 0, the launcher's copy 650 ms later (after the 600 ms window), then a real
        // second click at 800 ms: only the copy is dropped.
        val key = Key()
        key.deliver(isPress = true, now = 0, fromCarKey = true)
        key.deliver(isPress = false, now = 20, fromCarKey = true)
        key.deliver(isPress = true, now = 650)
        key.deliver(isPress = false, now = 660)
        key.deliver(isPress = true, now = 800, fromCarKey = true)
        key.deliver(isPress = false, now = 820, fromCarKey = true)
        assertEquals(listOf(true, false, true, false), key.edges())
    }

    @Test
    fun `a late copy does not block the next wheel clicks`() {
        // 09:09:15 in the log: the launcher's copy used to reset the window and block clicks 4 and 5.
        val key = Key()
        key.deliver(isPress = true, now = 0, fromCarKey = true)
        key.deliver(isPress = false, now = 20, fromCarKey = true)
        key.deliver(isPress = true, now = 250)
        key.deliver(isPress = false, now = 260)
        key.deliver(isPress = true, now = 400, fromCarKey = true)
        key.deliver(isPress = false, now = 420, fromCarKey = true)
        key.deliver(isPress = true, now = 640, fromCarKey = true)
        key.deliver(isPress = false, now = 660, fromCarKey = true)
        assertEquals(listOf(true, false, true, false, true, false), key.edges())
    }

    @Test
    fun `without the wheel key, keys from other routes keep the old window`() {
        val key = Key()
        key.deliver(isPress = true, now = 0)
        key.deliver(isPress = false, now = 20)
        key.deliver(isPress = true, now = 400)
        key.deliver(isPress = false, now = 420)
        assertEquals(listOf(true, false), key.edges())
    }
}
