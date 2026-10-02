package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ConnectionDashboardStateTest {

    @Test
    fun testAllOperationalState() {
        val btActive = SubsystemConnectionState(
            title = "Bluetooth Connection",
            categoryLabel = "Discovery",
            isPrimaryActive = true,
            summaryText = "Bluetooth ready",
            indicators = listOf(
                StatusIndicatorItem("Radio", "On", true),
                StatusIndicatorItem("Hardware MAC", "AA:BB:CC:DD:EE:FF", true),
            ),
            actionLabel = "Settings",
            settingsAction = "android.settings.BLUETOOTH_SETTINGS",
        )

        val wifiActive = SubsystemConnectionState(
            title = "Wi-Fi & P2P Direct",
            categoryLabel = "Transport",
            isPrimaryActive = true,
            summaryText = "Wi-Fi ready",
            indicators = listOf(
                StatusIndicatorItem("Radio", "Enabled", true),
                StatusIndicatorItem("P2P", "Ready", true),
            ),
            actionLabel = "Settings",
            settingsAction = "android.settings.WIFI_SETTINGS",
        )

        val adbActive = SubsystemConnectionState(
            title = "ADB & USB Debugging",
            categoryLabel = "Developer",
            isPrimaryActive = true,
            summaryText = "ADB ready",
            indicators = listOf(
                StatusIndicatorItem("USB Link", "Connected", true),
                StatusIndicatorItem("Port 5555", "Listening", true),
            ),
            actionLabel = "Developer",
            settingsAction = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        )

        val state = ConnectionDashboardState(
            bluetooth = btActive,
            wifi = wifiActive,
            adb = adbActive,
            timestamp = "12:00:00",
        )

        assertTrue(state.allOperational)
        assertEquals(3, state.totalActive)
        assertEquals(0, state.disconnectedCount)
    }

    @Test
    fun testDisconnectedStateDetection() {
        val btDisconnected = SubsystemConnectionState(
            title = "Bluetooth Connection",
            categoryLabel = "Discovery",
            isPrimaryActive = false,
            summaryText = "Bluetooth off",
            indicators = listOf(
                StatusIndicatorItem("Radio", "Turned Off", false),
            ),
            actionLabel = "Settings",
            settingsAction = "android.settings.BLUETOOTH_SETTINGS",
        )

        val wifiActive = SubsystemConnectionState(
            title = "Wi-Fi & P2P Direct",
            categoryLabel = "Transport",
            isPrimaryActive = true,
            summaryText = "Wi-Fi ready",
            indicators = listOf(
                StatusIndicatorItem("Radio", "Enabled", true),
            ),
            actionLabel = "Settings",
            settingsAction = "android.settings.WIFI_SETTINGS",
        )

        val adbDisconnected = SubsystemConnectionState(
            title = "ADB & USB Debugging",
            categoryLabel = "Developer",
            isPrimaryActive = false,
            summaryText = "ADB disconnected",
            indicators = listOf(
                StatusIndicatorItem("USB Link", "Disconnected", false),
            ),
            actionLabel = "Developer",
            settingsAction = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        )

        val state = ConnectionDashboardState(
            bluetooth = btDisconnected,
            wifi = wifiActive,
            adb = adbDisconnected,
            timestamp = "12:00:00",
        )

        assertFalse(state.allOperational)
        assertEquals(1, state.totalActive)
        assertEquals(2, state.disconnectedCount)
    }
}
