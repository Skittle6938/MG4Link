package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.TopGestureRecognizer.Action
import com.andrerinas.openheadunit.aap.TopGestureRecognizer.Env
import com.andrerinas.openheadunit.aap.TopGestureRecognizer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gestures from the top of the screen, as MG4Link and MG4Dash agreed them (2026-11-17), on the
 * two layouts of the car: the window from the top of the screen (full screen) and the window below
 * the car's 80 px status bar (NAV_ONLY).
 */
class TopGestureRecognizerTest {
    private val width = 1920
    private val withDash = Env(usableWidth = width, density = 1f, mg4DashStrip = true, topGestures = true)
    private val withoutDash = withDash.copy(mg4DashStrip = false)
    private val carKeepsTop = withDash.copy(topGestures = false)

    private class Touch(val windowTop: Float = 0f) {
        val r = TopGestureRecognizer()
        var env = Env(1920, 1f, true, true)
        val kinds = ArrayList<Kind>()
        var consumedAny = false

        fun send(action: Action, count: Int, x0: Float, y0: Float, y1: Float = Float.NaN) {
            val res = r.onTouch(
                TopGestureRecognizer.Sample(action, count, x0, y0, y1, x0, y0 + windowTop), env
            )
            res.events.forEach { kinds += it.kind }
            if (res.consumed) consumedAny = true
        }
    }

    private fun touch(env: Env, windowTop: Float = 0f) = Touch(windowTop).also { it.env = env }

    // --- two fingers from the top: back to the launcher ---------------------------------------

    @Test
    fun `two fingers from the top slide down to the launcher`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 30f)
        t.send(Action.POINTER_DOWN, 2, 500f, 30f, y1 = 40f)
        t.send(Action.MOVE, 2, 500f, 150f, y1 = 160f)
        assertTrue(t.kinds.isEmpty())
        t.send(Action.MOVE, 2, 505f, 260f, y1 = 270f)
        assertEquals(listOf(Kind.HOME), t.kinds)
        assertTrue(t.consumedAny)
    }

    @Test
    fun `two fingers from the top work below the car's bar too`() {
        val t = touch(withDash, windowTop = 80f)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.POINTER_DOWN, 2, 500f, 5f, y1 = 12f)
        t.send(Action.MOVE, 2, 500f, 230f, y1 = 240f)
        assertEquals(listOf(Kind.HOME), t.kinds)
    }

    @Test
    fun `two fingers starting lower than 100 px are left to Android Auto`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 300f)
        t.send(Action.POINTER_DOWN, 2, 500f, 300f, y1 = 310f)
        t.send(Action.MOVE, 2, 500f, 600f, y1 = 610f)
        assertTrue(t.kinds.isEmpty())
        assertFalse(t.consumedAny)
    }

    @Test
    fun `a slide that wanders sideways is not the gesture`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 30f)
        t.send(Action.POINTER_DOWN, 2, 500f, 30f, y1 = 40f)
        t.send(Action.MOVE, 2, 700f, 260f, y1 = 270f)
        assertTrue(t.kinds.isEmpty())
    }

    // --- three fingers: disconnect, from anywhere ----------------------------------------------

    @Test
    fun `three fingers slide down from the middle of the screen disconnect`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 900f, 300f)
        t.send(Action.POINTER_DOWN, 2, 900f, 300f, y1 = 310f)
        t.send(Action.POINTER_DOWN, 3, 900f, 300f, y1 = 310f)
        t.send(Action.MOVE, 3, 905f, 560f, y1 = 570f)
        assertEquals(listOf(Kind.DISCONNECT), t.kinds)
    }

    @Test
    fun `three fingers from the top disconnect as well`() {
        val t = touch(withDash, windowTop = 80f)
        t.send(Action.DOWN, 1, 900f, 10f)
        t.send(Action.POINTER_DOWN, 2, 900f, 10f, y1 = 15f)
        t.send(Action.POINTER_DOWN, 3, 900f, 10f, y1 = 15f)
        t.send(Action.MOVE, 3, 900f, 250f, y1 = 255f)
        assertEquals(listOf(Kind.DISCONNECT), t.kinds)
    }

    @Test
    fun `three fingers keep working when the car keeps the top`() {
        val t = touch(carKeepsTop)
        t.send(Action.DOWN, 1, 900f, 300f)
        t.send(Action.POINTER_DOWN, 2, 900f, 300f, y1 = 310f)
        t.send(Action.POINTER_DOWN, 3, 900f, 300f, y1 = 310f)
        t.send(Action.MOVE, 3, 900f, 560f, y1 = 570f)
        assertEquals(listOf(Kind.DISCONNECT), t.kinds)
    }

    @Test
    fun `three fingers slid sideways do not disconnect`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 900f, 300f)
        t.send(Action.POINTER_DOWN, 2, 900f, 300f, y1 = 310f)
        t.send(Action.POINTER_DOWN, 3, 900f, 300f, y1 = 310f)
        t.send(Action.MOVE, 3, 1200f, 560f, y1 = 570f)
        assertTrue(t.kinds.isEmpty())
    }

    // --- the car keeps the top of the screen ---------------------------------------------------

    @Test
    fun `with the car's pull-down on, two fingers from the top do nothing`() {
        val t = touch(carKeepsTop)
        t.send(Action.DOWN, 1, 500f, 30f)
        t.send(Action.POINTER_DOWN, 2, 500f, 30f, y1 = 40f)
        t.send(Action.MOVE, 2, 500f, 300f, y1 = 310f)
        assertTrue(t.kinds.isEmpty())
        assertFalse(t.consumedAny)
    }

    @Test
    fun `with the car's pull-down on, one finger from the top is not relayed`() {
        val t = touch(carKeepsTop)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.MOVE, 1, 500f, 120f)
        assertTrue(t.kinds.isEmpty())
        assertFalse(t.consumedAny)
    }

    // --- one finger: MG4Dash's shortcuts menu --------------------------------------------------

    @Test
    fun `one finger from the top is relayed to MG4Dash, then ended`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.MOVE, 1, 500f, 20f)
        assertTrue(t.kinds.isEmpty())
        t.send(Action.MOVE, 1, 500f, 60f)
        t.send(Action.MOVE, 1, 500f, 140f)
        t.send(Action.UP, 1, 500f, 140f)
        assertEquals(listOf(Kind.MENU_PULL_BEGIN, Kind.MENU_PULL_MOVE, Kind.MENU_PULL_END), t.kinds)
        assertTrue(t.consumedAny)
    }

    @Test
    fun `one finger relay reaches MG4Dash from below the bar too`() {
        val t = touch(withDash, windowTop = 80f)
        t.send(Action.DOWN, 1, 500f, 50f)
        t.send(Action.MOVE, 1, 500f, 150f)
        assertEquals(listOf(Kind.MENU_PULL_BEGIN), t.kinds)
    }

    @Test
    fun `one finger relay needs the band to be that close to the top`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 25f)
        t.send(Action.MOVE, 1, 500f, 200f)
        assertTrue(t.kinds.isEmpty())
        val u = touch(withDash, windowTop = 80f)
        u.send(Action.DOWN, 1, 500f, 70f)
        u.send(Action.MOVE, 1, 500f, 200f)
        assertTrue(u.kinds.isEmpty())
    }

    @Test
    fun `a short or sideways slide is no menu pull`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.MOVE, 1, 500f, 30f)
        t.send(Action.UP, 1, 500f, 30f)
        val u = touch(withDash)
        u.send(Action.DOWN, 1, 500f, 5f)
        u.send(Action.MOVE, 1, 640f, 100f)
        assertTrue(t.kinds.isEmpty())
        assertTrue(u.kinds.isEmpty())
    }

    @Test
    fun `a second finger gives the menu pull up`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.MOVE, 1, 500f, 100f)
        t.send(Action.POINTER_DOWN, 2, 500f, 100f, y1 = 90f)
        assertEquals(listOf(Kind.MENU_PULL_BEGIN, Kind.MENU_PULL_CANCEL), t.kinds)
    }

    // --- without MG4Dash: MG4Link keeps its own gestures ---------------------------------------

    @Test
    fun `without MG4Dash nothing is relayed but the two and three finger gestures work`() {
        val t = touch(withoutDash)
        t.send(Action.DOWN, 1, 500f, 5f)
        t.send(Action.MOVE, 1, 500f, 150f)
        assertTrue(t.kinds.isEmpty())
        t.send(Action.UP, 1, 500f, 150f)

        t.send(Action.DOWN, 1, 500f, 30f)
        t.send(Action.POINTER_DOWN, 2, 500f, 30f, y1 = 40f)
        t.send(Action.MOVE, 2, 500f, 260f, y1 = 270f)
        assertEquals(listOf(Kind.HOME), t.kinds)

        val u = touch(withoutDash)
        u.send(Action.DOWN, 1, 900f, 300f)
        u.send(Action.POINTER_DOWN, 2, 900f, 300f, y1 = 310f)
        u.send(Action.POINTER_DOWN, 3, 900f, 300f, y1 = 310f)
        u.send(Action.MOVE, 3, 900f, 560f, y1 = 570f)
        assertEquals(listOf(Kind.DISCONNECT), u.kinds)
    }

    // --- the edges -----------------------------------------------------------------------------

    @Test
    fun `two fingers from the right edge slid left toggle full screen`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 1880f, 400f)
        t.send(Action.POINTER_DOWN, 2, 1880f, 400f, y1 = 420f)
        t.send(Action.MOVE, 2, 1700f, 410f, y1 = 430f)
        assertTrue(t.kinds.isEmpty())
        t.send(Action.MOVE, 2, 1650f, 410f, y1 = 430f)
        assertEquals(listOf(Kind.TOGGLE_FULLSCREEN), t.kinds)
    }

    @Test
    fun `two fingers from the left edge slid right open the exit menu`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 40f, 400f)
        t.send(Action.POINTER_DOWN, 2, 40f, 400f, y1 = 420f)
        t.send(Action.MOVE, 2, 300f, 410f, y1 = 430f)
        assertEquals(listOf(Kind.EXIT_DIALOG), t.kinds)
    }

    @Test
    fun `edges keep working when the car keeps the top`() {
        val t = touch(carKeepsTop)
        t.send(Action.DOWN, 1, 1880f, 400f)
        t.send(Action.POINTER_DOWN, 2, 1880f, 400f, y1 = 420f)
        t.send(Action.MOVE, 2, 1600f, 410f, y1 = 430f)
        assertEquals(listOf(Kind.TOGGLE_FULLSCREEN), t.kinds)
    }

    @Test
    fun `an ordinary tap or drag is left alone`() {
        val t = touch(withDash)
        t.send(Action.DOWN, 1, 900f, 400f)
        t.send(Action.MOVE, 1, 960f, 300f)
        t.send(Action.UP, 1, 960f, 300f)
        assertTrue(t.kinds.isEmpty())
        assertFalse(t.consumedAny)
    }
}
