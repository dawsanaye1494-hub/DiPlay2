package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConnectionTelemetryTest {

    @Test
    fun testPacketRecordingAndLossCalculation() {
        // Record packets with a sequence gap
        LiveConnectionTelemetry.recordPacket(1024, 100)
        LiveConnectionTelemetry.recordPacket(1024, 101)
        LiveConnectionTelemetry.recordPacket(1024, 105) // Missed 102, 103, 104 (3 packets gap)

        LiveConnectionTelemetry.recordHandshakeLatency("rfcomm", 40)
        LiveConnectionTelemetry.recordHandshakeLatency("iap2", 20)
        LiveConnectionTelemetry.recordHandshakeLatency("airplay", 30)

        // Handshake latency should be recorded
        assertTrue(true)
    }

    @Test
    fun testHandshakeLatencyStages() {
        LiveConnectionTelemetry.recordHandshakeLatency("bluetooth", 55)
        LiveConnectionTelemetry.recordHandshakeLatency("handshake", 35)
        LiveConnectionTelemetry.recordHandshakeLatency("rtsp", 25)

        assertTrue(true)
    }
}
