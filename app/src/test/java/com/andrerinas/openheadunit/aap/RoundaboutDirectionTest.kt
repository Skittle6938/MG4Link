package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NavigationManeuver.NavigationType
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.NextEvent
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus.NextTurnDetail.Side
import com.andrerinas.openheadunit.contract.NavigationUpdateIntent
import org.junit.Assert.assertEquals
import org.junit.Test

/** #74: the roundabout exit angle and number count in the direction of traffic, so apps need it. */
class RoundaboutDirectionTest {

    private fun direction(type: NavigationType?, event: NextEvent? = null, side: Side? = null, rhd: Boolean = false) =
        AapNavigationHelper.roundaboutDirection(type, event, side, rhd)

    @Test fun `newer phones say it in the maneuver type`() {
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_CLOCKWISE, direction(NavigationType.ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_CLOCKWISE, direction(NavigationType.ROUNDABOUT_ENTER_AND_EXIT_CW))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_COUNTER_CLOCKWISE, direction(NavigationType.ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_COUNTER_CLOCKWISE, direction(NavigationType.ROUNDABOUT_ENTER_AND_EXIT_CCW))
    }

    @Test fun `older phones give the side of the traffic`() {
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_CLOCKWISE, direction(null, NextEvent.ROUNDABOUT_ENTER_AND_EXIT, Side.LEFT))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_COUNTER_CLOCKWISE, direction(null, NextEvent.ROUNDABOUT_ENTER_AND_EXIT, Side.RIGHT, rhd = true))
    }

    @Test fun `without a side, the steering side of the car stands in`() {
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_CLOCKWISE, direction(null, NextEvent.ROUNDABOUT_ENTER_AND_EXIT, Side.UNSPECIFIED, rhd = true))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_COUNTER_CLOCKWISE, direction(null, NextEvent.ROUNDABOUT_ENTER_AND_EXIT, null))
    }

    @Test fun `not a roundabout`() {
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_UNKNOWN, direction(NavigationType.TURN_NORMAL_RIGHT))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_UNKNOWN, direction(null, NextEvent.TURN, Side.LEFT))
        assertEquals(NavigationUpdateIntent.ROUNDABOUT_UNKNOWN, direction(null))
    }
}
