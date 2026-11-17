package com.andrerinas.openheadunit.aap

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog

/**
 * What MG4Link and MG4Dash say to each other about gestures from the top of the screen. The two
 * apps are independent, and each works without the other; these names are the whole agreement, so
 * a test pins them (a renamed one would silently break the other app).
 */
object Mg4DashContract {
    const val MG4DASH_PACKAGE = "app.mg4dash"

    // MG4Link to MG4Dash.
    const val ACTION_LINK_IMMERSIVE = "app.mg4dash.LINK_IMMERSIVE"
    const val ACTION_SHORTCUTS_PULL = "app.mg4dash.SHORTCUTS_PULL"

    /**
     * 1 while the car's pull-down over Android Auto is switched on (the top of the screen is left to
     * the car: MG4Dash makes its strip transparent to touches), 0 otherwise. Global setting, kept up
     * to date when the option changes and at start.
     */
    const val GLOBAL_CAR_PULLDOWN = "mg4link_car_pulldown"

    // MG4Dash to MG4Link.
    /** MG4Dash's strip: 1 while it is on. Read here, written by MG4Dash. */
    const val GLOBAL_SHORTCUTS_STRIP = "mg4dash_shortcuts_strip"

    /**
     * Explicit broadcast MG4Dash sends when three fingers slid down from the top of the screen, over
     * the car's status bar where MG4Link receives no touch. Sent to this package, protected by
     * [PERMISSION_USER_DISCONNECT] (signature). Ignored when no session is up.
     */
    const val ACTION_USER_DISCONNECT = "com.allgo.app.androidauto.USER_DISCONNECT"
    const val PERMISSION_USER_DISCONNECT = "com.allgo.app.androidauto.permission.USER_DISCONNECT"

    fun carPullDownValue(enabled: Boolean): Int = if (enabled) 1 else 0

    /** Whether a received broadcast makes MG4Link disconnect: the right action, and a session to end. */
    fun shouldDisconnect(action: String?, sessionUp: Boolean): Boolean =
        action == ACTION_USER_DISCONNECT && sessionUp

    fun publishCarPullDown(context: Context, enabled: Boolean) {
        try {
            android.provider.Settings.Global.putInt(context.contentResolver, GLOBAL_CAR_PULLDOWN, carPullDownValue(enabled))
        } catch (e: Exception) {
            // Not a platform-signed build: MG4Dash then simply never sees the setting.
            AppLog.w("Car pull-down state for MG4 Dash: ${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }
}
