package com.shilapi.xcertplay.mfi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2AuthenticationFallbackTest {

    @Test
    fun `isAuthenticationFailed identifies 0xAA04 payload`() {
        val fallback = Iap2AuthenticationFallback()
        val authFailedPayload = byteArrayOf(0xaa.toByte(), 0x04.toByte(), 0x00, 0x01)
        val normalPayload = byteArrayOf(0xaa.toByte(), 0x05.toByte(), 0x00, 0x01)

        assertTrue(fallback.isAuthenticationFailed(authFailedPayload))
        assertFalse(fallback.isAuthenticationFailed(normalPayload))
        assertFalse(fallback.isAuthenticationFailed(byteArrayOf(0x01)))
    }

    @Test
    fun `handleIap2Response switches config on authentication failure`() {
        var socketClosed = false
        var handshakeStarted = false
        val fallback = Iap2AuthenticationFallback(
            closeRfcommSocket = { socketClosed = true },
            startBluetoothHandshake = { handshakeStarted = true },
            scheduler = { _, action -> action() },
        )

        val authFailedPayload = byteArrayOf(0xaa.toByte(), 0x04.toByte())
        fallback.handleIap2Response(authFailedPayload)

        assertTrue(socketClosed)
        assertTrue(handshakeStarted)
        assertEquals(MfiMode.SOFTWARE_EMULATION, fallback.mfiConfig.mode)
        assertEquals("Apple", fallback.mfiConfig.manufacturer)
        assertEquals("CarPlay1,1", fallback.mfiConfig.model)
    }
}
