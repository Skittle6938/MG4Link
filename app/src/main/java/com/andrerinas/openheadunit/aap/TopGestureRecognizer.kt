package com.andrerinas.openheadunit.aap

import kotlin.math.abs

/**
 * The touch gestures MG4Link recognises on the Android Auto screen, kept free of Android classes so
 * they can be tested. [AapProjectionActivity] feeds it each touch event and acts on what comes back.
 *
 *  - one finger from the very top, slid down: MG4Dash's shortcuts menu (relayed to MG4Dash; only when
 *    MG4Dash's strip is on, and not when the car's own pull-down is left the top of the screen);
 *  - two fingers from the top, slid down: back to the launcher (same condition on the top);
 *  - two fingers from the right edge, slid left: exit menu; from the left edge, slid right: full screen;
 *  - three fingers anywhere, slid down: disconnect.
 *
 * Coordinates are those of the activity's window, except [Sample.rawX] / [Sample.rawY] (the screen's):
 * with the car's status bar above the window (NAV_ONLY) the two differ by the bar's height.
 */
internal class TopGestureRecognizer {

    enum class Action { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP, CANCEL, OTHER }

    enum class Kind {
        MENU_PULL_BEGIN, MENU_PULL_MOVE, MENU_PULL_END, MENU_PULL_CANCEL,
        HOME, DISCONNECT, EXIT_DIALOG, TOGGLE_FULLSCREEN
    }

    data class Event(val kind: Kind, val dy: Float = 0f)

    /** One touch event: the first pointer's position (and the second's height), in the window and on screen. */
    class Sample(
        val action: Action,
        val pointerCount: Int,
        val x0: Float,
        val y0: Float,
        val y1: Float,
        val rawX: Float,
        val rawY: Float,
    )

    /**
     * @param usableWidth width of the window, for the right edge.
     * @param density screen density, for the one finger slide's length.
     * @param mg4DashStrip MG4Dash is there and its strip is on (the one finger slide goes to it).
     * @param topGestures the top of the screen is MG4Link's: false when the car's pull-down over
     *   Android Auto is switched on, which leaves the top of the screen to the car.
     */
    data class Env(val usableWidth: Int, val density: Float, val mg4DashStrip: Boolean, val topGestures: Boolean)

    class Result(val consumed: Boolean, val events: List<Event>)

    private var menuCandidate = false
    private var menuPulling = false
    private var menuDownX = 0f
    private var menuDownY = 0f

    private var edgeX = 0f
    private var edgeY = 0f
    private var potentialEdge = false
    private var potentialTop = false

    private var threeX = 0f
    private var threeY = 0f
    private var potentialThree = false

    fun onTouch(s: Sample, env: Env): Result {
        val events = ArrayList<Event>(1)
        if (menu(s, env, events)) return Result(true, events)
        val consumed = gestures(s, env, events)
        return Result(consumed, events)
    }

    /** One finger from the top 20 px, clearly down: the touch is taken from Android Auto. */
    private fun menu(s: Sample, env: Env, out: MutableList<Event>): Boolean {
        when (s.action) {
            Action.DOWN -> {
                menuPulling = false
                menuDownX = s.rawX; menuDownY = s.rawY
                menuCandidate = s.y0 <= edgePx(s) && env.mg4DashStrip && env.topGestures
            }
            Action.POINTER_DOWN -> {
                if (menuPulling) { out += Event(Kind.MENU_PULL_CANCEL); menuPulling = false }
                menuCandidate = false
            }
            Action.MOVE -> {
                val dy = s.rawY - menuDownY
                if (menuPulling) { out += Event(Kind.MENU_PULL_MOVE, dy); return true }
                val dx = s.rawX - menuDownX
                if (menuCandidate && s.pointerCount == 1 && dy > MENU_PULL_DP * env.density && abs(dx) < dy * 0.5f) {
                    menuPulling = true; menuCandidate = false
                    out += Event(Kind.MENU_PULL_BEGIN, dy)
                    return true
                }
            }
            Action.UP, Action.CANCEL -> {
                menuCandidate = false
                if (menuPulling) {
                    menuPulling = false
                    out += Event(Kind.MENU_PULL_END, s.rawY - menuDownY)
                    return true
                }
            }
            else -> {}
        }
        return menuPulling
    }

    /**
     * Height of the band the slide must start in, from the top of the window. 20 px when the window
     * starts at the top of the screen. With the car's status bar above it (an 80 px bar on the SWI173,
     * the window from y=80) a finger starting at the very top lands in the bar, which the car keeps,
     * and reaches the window lower than 20 px in: the band is then wider (MG4Dash #113).
     */
    private fun edgePx(s: Sample): Float {
        val windowTop = s.rawY - s.y0
        return if (windowTop > 1f) MENU_PULL_EDGE_BELOW_BAR_PX else MENU_PULL_EDGE_PX
    }

    private fun gestures(s: Sample, env: Env, out: MutableList<Event>): Boolean {
        if (s.pointerCount == 2) {
            potentialThree = false
            when (s.action) {
                Action.POINTER_DOWN -> {
                    edgeX = s.x0
                    edgeY = s.y0
                    potentialEdge = edgeX < EDGE_PX || edgeX > env.usableWidth - EDGE_PX
                    potentialTop = env.topGestures && edgeY < TOP_PX && s.y1 < TOP_PX
                }
                Action.MOVE -> {
                    if (potentialTop && s.y0 - edgeY > SWIPE_PX && abs(s.x0 - edgeX) < TOP_SIDE_PX) {
                        potentialTop = false
                        potentialEdge = false
                        out += Event(Kind.HOME)
                        return true
                    }
                    if (potentialEdge) {
                        val deltaX = s.x0 - edgeX
                        val deltaY = abs(s.y0 - edgeY)
                        if (deltaX > SWIPE_PX && deltaY < EDGE_SIDE_PX) {
                            potentialEdge = false
                            out += Event(Kind.EXIT_DIALOG)
                            return true
                        }
                        if (deltaX < -SWIPE_PX && deltaY < EDGE_SIDE_PX) {
                            potentialEdge = false
                            out += Event(Kind.TOGGLE_FULLSCREEN)
                            return true
                        }
                    }
                }
                else -> {}
            }
        }

        if (s.pointerCount >= 3) {
            potentialEdge = false
            potentialTop = false
            when (s.action) {
                Action.POINTER_DOWN -> if (s.pointerCount == 3) {
                    threeX = s.x0
                    threeY = s.y0
                    potentialThree = true
                }
                Action.MOVE -> if (potentialThree) {
                    val deltaY = s.y0 - threeY
                    val deltaX = abs(s.x0 - threeX)
                    if (deltaY > SWIPE_PX && deltaX < THREE_SIDE_PX) {
                        potentialThree = false
                        out += Event(Kind.DISCONNECT)
                        return true
                    }
                }
                Action.UP, Action.CANCEL -> potentialThree = false
                else -> {}
            }
        } else if (s.pointerCount < 3) {
            when (s.action) {
                Action.UP, Action.CANCEL, Action.POINTER_UP -> potentialThree = false
                else -> {}
            }
        }
        return false
    }

    companion object {
        const val MENU_PULL_EDGE_PX = 20f
        const val MENU_PULL_EDGE_BELOW_BAR_PX = 60f
        const val MENU_PULL_DP = 40
        const val EDGE_PX = 100
        const val TOP_PX = 100
        const val SWIPE_PX = 200
        const val TOP_SIDE_PX = 100
        const val EDGE_SIDE_PX = 100
        const val THREE_SIDE_PX = 150
    }
}
