package com.shilapi.xcertplay.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectivityLogStoreTest {

    @Before
    fun setUp() {
        ConnectivityLogStore.clear()
    }

    @Test
    fun testDirectLoggingAndFiltering() {
        ConnectivityLogStore.logWifi("Wi-Fi P2P Group Owner created", isSuccess = true)
        ConnectivityLogStore.logBluetooth("RFCOMM connecting to iPhone", isSuccess = null)
        ConnectivityLogStore.logHandshake("iAP2 identification accepted", isSuccess = true)
        ConnectivityLogStore.log(ConnectivityCategory.BLUETOOTH, ConnectivityLevel.ERROR, "Bluetooth RFCOMM link severed")

        val allEntries = ConnectivityLogStore.getEntries(ConnectivityCategory.ALL)
        assertTrue(allEntries.size >= 4)

        val wifiEntries = ConnectivityLogStore.getEntries(ConnectivityCategory.WIFI)
        assertTrue(wifiEntries.any { it.message.contains("Wi-Fi P2P") })
        assertTrue(wifiEntries.all { it.category == ConnectivityCategory.WIFI })

        val btEntries = ConnectivityLogStore.getEntries(ConnectivityCategory.BLUETOOTH)
        assertTrue(btEntries.size >= 2)
        assertTrue(btEntries.any { it.level == ConnectivityLevel.ERROR })

        val handshakeEntries = ConnectivityLogStore.getEntries(ConnectivityCategory.IAP2_HANDSHAKE)
        assertEquals(1, handshakeEntries.size)
        assertEquals(ConnectivityLevel.SUCCESS, handshakeEntries[0].level)
    }

    @Test
    fun testLogFromTextCategorization() {
        ConnectivityLogStore.logFromText("wireless RFCOMM connecting address=AA:BB:CC:DD:EE:FF")
        ConnectivityLogStore.logFromText("wireless Bonjour services started mode=interface")
        ConnectivityLogStore.logFromText("iAP2 link synchronization established")
        ConnectivityLogStore.logFromText("CarPlay transport error: connection refused; reconnecting")

        val entries = ConnectivityLogStore.getEntries()

        val rfcommEntry = entries.find { it.message.contains("RFCOMM") }
        assertNotNull(rfcommEntry)
        assertEquals(ConnectivityCategory.BLUETOOTH, rfcommEntry!!.category)

        val bonjourEntry = entries.find { it.message.contains("Bonjour") }
        assertNotNull(bonjourEntry)
        assertEquals(ConnectivityCategory.WIFI, bonjourEntry!!.category)

        val iap2Entry = entries.find { it.message.contains("iAP2") }
        assertNotNull(iap2Entry)
        assertEquals(ConnectivityCategory.IAP2_HANDSHAKE, iap2Entry!!.category)
        assertEquals(ConnectivityLevel.SUCCESS, iap2Entry.level)

        val errEntry = entries.find { it.message.contains("transport error") }
        assertNotNull(errEntry)
        assertEquals(ConnectivityLevel.ERROR, errEntry!!.level)
    }

    @Test
    fun testRealtimeListenerNotification() {
        val captured = mutableListOf<ConnectivityLogEntry>()
        val unsubscribe = ConnectivityLogStore.addListener { entry ->
            captured.add(entry)
        }

        ConnectivityLogStore.logWifi("Autonomous Group formed", isSuccess = true)
        ConnectivityLogStore.logBluetooth("Paired iPhone found", isSuccess = true)

        assertEquals(2, captured.size)
        assertEquals("Autonomous Group formed", captured[0].message)
        assertEquals("Paired iPhone found", captured[1].message)

        unsubscribe()
        ConnectivityLogStore.logWifi("Another event after unsubscribe")
        assertEquals(2, captured.size) // Not notified
    }
}
