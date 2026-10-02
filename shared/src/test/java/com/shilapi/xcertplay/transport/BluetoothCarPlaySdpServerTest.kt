package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

class BluetoothCarPlaySdpServerTest {

    @Test
    fun `standard CarPlay UUID matches Apple specification`() {
        val expected = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
        assertEquals(expected, BluetoothCarPlaySdpServer.CARPLAY_UUID)
    }

    @Test
    fun `server fails gracefully when bluetooth adapter is null`() {
        val server = BluetoothCarPlaySdpServer(
            bluetoothAdapter = null,
            onClientConnected = {},
        )
        assertFalse(server.start())
        assertFalse(server.isListening())
        server.stop()
    }
}
