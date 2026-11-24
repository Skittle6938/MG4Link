package com.andrerinas.openheadunit.aap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideMarginsTest {
    private val screen = 1920

    @After fun reset() = SideMargins.clear()

    @Test fun `a request reserves its side`() {
        assertTrue(SideMargins.request("chargepilot", SideMargins.Side.RIGHT, 480, screen))
        assertEquals(0 to 480, SideMargins.effective(screen))
    }

    @Test fun `the widest request on a side wins and 0 only withdraws its own`() {
        SideMargins.request("a", SideMargins.Side.RIGHT, 480, screen)
        SideMargins.request("b", SideMargins.Side.RIGHT, 300, screen)
        assertEquals(0 to 480, SideMargins.effective(screen))
        SideMargins.request("a", SideMargins.Side.RIGHT, 0, screen)
        assertEquals(0 to 300, SideMargins.effective(screen))
    }

    @Test fun `left and right are independent`() {
        SideMargins.request("a", SideMargins.Side.LEFT, 200, screen)
        SideMargins.request("b", SideMargins.Side.RIGHT, 480, screen)
        assertEquals(200 to 480, SideMargins.effective(screen))
    }

    @Test fun `no side takes more than a third of the screen`() {
        SideMargins.request("a", SideMargins.Side.RIGHT, 1500, screen)
        assertEquals(0 to 640, SideMargins.effective(screen))
    }

    @Test fun `repeating the same request changes nothing`() {
        SideMargins.request("a", SideMargins.Side.RIGHT, 480, screen)
        assertFalse(SideMargins.request("a", SideMargins.Side.RIGHT, 480, screen))
    }

    @Test fun `screen pixels convert to the video's width`() {
        assertEquals(480, SideMargins.toVideoPx(480, 1920, 1920))
        assertEquals(320, SideMargins.toVideoPx(480, 1920, 1280))
        assertEquals(0, SideMargins.toVideoPx(480, 0, 1280))
    }
}
