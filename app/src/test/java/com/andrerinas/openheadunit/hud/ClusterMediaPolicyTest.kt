package com.andrerinas.openheadunit.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #76: when and where the playing track goes in the instrument cluster. */
class ClusterMediaPolicyTest {

    private fun visible(
        enabled: Boolean = true, track: Boolean = true, playing: Boolean = true, tab: Int? = 2,
        age: Long = 100, gear: Int? = 4, screen: Boolean = true,
    ) = ClusterMediaPolicy.visible(enabled, track, playing, tab, age, gear, screen)

    @Test fun `only on the media page, while music plays`() {
        assertTrue(visible())
        assertFalse(visible(enabled = false))
        assertFalse(visible(track = false))
        assertFalse(visible(playing = false))
        assertFalse(visible(tab = 1))
        assertFalse(visible(tab = null))
        assertFalse(visible(age = 2_000))
    }

    @Test fun `never in reverse or with the screen off`() {
        assertFalse(visible(gear = 2))
        assertTrue(visible(gear = null))
        assertFalse(visible(screen = false))
    }

    @Test fun `the free area right of the dials`() {
        assertArrayEquals(intArrayOf(482, 126, 260), ClusterMediaPolicy.bounds(800, 480))
        assertEquals(0, ClusterMediaPolicy.bounds(0, 0)[2])
    }

    @Test fun `progress moves on between the phone's updates, and stops when paused`() {
        assertEquals(12_500, ClusterMediaPolicy.positionMs(10_000, 1_000, 3_500, playing = true, durationMs = 200_000))
        assertEquals(10_000, ClusterMediaPolicy.positionMs(10_000, 1_000, 3_500, playing = false, durationMs = 200_000))
        assertEquals(200_000, ClusterMediaPolicy.positionMs(199_000, 0, 5_000, playing = true, durationMs = 200_000))
    }
}
