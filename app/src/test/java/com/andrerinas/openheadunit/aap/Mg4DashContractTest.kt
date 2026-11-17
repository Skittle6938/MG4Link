package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agreement between MG4Link and MG4Dash about gestures from the top of the screen. The names
 * are written out in full on purpose: MG4Dash uses the same ones, and changing one here without
 * changing it there would break the other app without any error.
 */
class Mg4DashContractTest {
    @Test
    fun `the names MG4Dash relies on`() {
        assertEquals("app.mg4dash", Mg4DashContract.MG4DASH_PACKAGE)
        assertEquals("app.mg4dash.LINK_IMMERSIVE", Mg4DashContract.ACTION_LINK_IMMERSIVE)
        assertEquals("app.mg4dash.SHORTCUTS_PULL", Mg4DashContract.ACTION_SHORTCUTS_PULL)
        assertEquals("mg4dash_shortcuts_strip", Mg4DashContract.GLOBAL_SHORTCUTS_STRIP)
        assertEquals("mg4link_car_pulldown", Mg4DashContract.GLOBAL_CAR_PULLDOWN)
        assertEquals("com.allgo.app.androidauto.USER_DISCONNECT", Mg4DashContract.ACTION_USER_DISCONNECT)
        assertEquals("com.allgo.app.androidauto.permission.USER_DISCONNECT", Mg4DashContract.PERMISSION_USER_DISCONNECT)
    }

    @Test
    fun `the car pull-down is published as 1 when on and 0 when off`() {
        assertEquals(1, Mg4DashContract.carPullDownValue(true))
        assertEquals(0, Mg4DashContract.carPullDownValue(false))
    }

    @Test
    fun `a disconnect request with a session disconnects`() {
        assertTrue(Mg4DashContract.shouldDisconnect(Mg4DashContract.ACTION_USER_DISCONNECT, sessionUp = true))
    }

    @Test
    fun `a disconnect request without a session is ignored`() {
        assertFalse(Mg4DashContract.shouldDisconnect(Mg4DashContract.ACTION_USER_DISCONNECT, sessionUp = false))
    }

    @Test
    fun `any other action is ignored`() {
        assertFalse(Mg4DashContract.shouldDisconnect("com.allgo.app.androidauto.SOMETHING", sessionUp = true))
        assertFalse(Mg4DashContract.shouldDisconnect(null, sessionUp = true))
    }
}
