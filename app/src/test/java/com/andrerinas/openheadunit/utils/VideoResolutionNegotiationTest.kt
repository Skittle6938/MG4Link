package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoResolutionNegotiationTest {
    @Test
    fun `automatic 1080p offers a matching 720p profile`() {
        assertEquals(
            listOf(VideoResolution._1920x1080, VideoResolution._1280x720),
            HeadUnitScreenConfig.videoResolutionOptions(VideoResolution._1920x1080),
        )
        assertEquals(
            listOf(VideoResolution._1080x1920, VideoResolution._720x1280),
            HeadUnitScreenConfig.videoResolutionOptions(VideoResolution._1080x1920),
        )
    }

    @Test
    fun `a 720p cap offers only 720p`() {
        assertEquals(
            listOf(VideoResolution._1280x720),
            HeadUnitScreenConfig.videoResolutionOptions(VideoResolution._1280x720),
        )
    }

    @Test
    fun `phone choice updates projection dimensions for this session`() {
        val screen = HeadUnitScreenConfig
        val previousResolution = screen.negotiatedResolutionType
        val previousLogger = AppLog.LOGGER
        AppLog.LOGGER = object : AppLog.Logger {
            override fun println(priority: Int, tag: String, msg: String) = Unit
        }
        try {
            screen.selectVideoResolution(VideoResolution._1280x720, VideoResolution._1920x1080)
            assertEquals(1280, screen.getNegotiatedWidth())
            assertEquals(720, screen.getNegotiatedHeight())
            assertEquals(1920, screen.getAdvertisedTouchWidth())
            assertEquals(1080, screen.getAdvertisedTouchHeight())

            screen.unlockResolution()
            assertEquals(VideoResolution._1920x1080, screen.negotiatedResolutionType)
        } finally {
            screen.unlockResolution()
            screen.negotiatedResolutionType = previousResolution
            AppLog.LOGGER = previousLogger
        }
    }
}
