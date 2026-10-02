package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiConnectionManagerTest {

    @Test
    fun `initial wifi state is 5GHz P2P`() {
        val states = WifiConnectionManager.WifiState.values()
        assertEquals(3, states.size)
        assertEquals(WifiConnectionManager.WifiState.P2P_5GHZ, WifiConnectionManager.WifiState.valueOf("P2P_5GHZ"))
        assertEquals(WifiConnectionManager.WifiState.P2P_2GHZ, WifiConnectionManager.WifiState.valueOf("P2P_2GHZ"))
        assertEquals(WifiConnectionManager.WifiState.SOFT_AP, WifiConnectionManager.WifiState.valueOf("SOFT_AP"))
    }
}
