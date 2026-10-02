package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodecFallbackManagerTest {

    @Test
    fun `initVideoCodecWithFallback falls back to 720p 30fps on 1080p failure`() {
        val logList = mutableListOf<String>()
        var attemptCount = 0

        val fallbackManager = VideoCodecFallbackManager(
            codecCreator = {
                attemptCount++
                if (attemptCount == 1) {
                    throw RuntimeException("1080p 60fps unsupported by hardware decoder")
                }
                // Return dummy object or succeed on second attempt (720p)
                throw RuntimeException("720p failed too") // trigger software decoder check
            },
            logger = { tag, msg, _ -> logList.add("[$tag] $msg") },
        )

        fallbackManager.initVideoCodecWithFallback(surface = null, width = 1920, height = 1080, fps = 60)

        assertEquals(2, attemptCount)
        assertTrue(fallbackManager.isSoftwareDecoderRequired)
        assertTrue(logList.any { it.contains("Falling back to 720p 30fps") })
        assertTrue(logList.any { it.contains("Software decoder required") })
    }
}
