package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BluetoothRfcommConnectorTest {

    @Test
    fun `max retries reached triggers state reset and callback`() {
        val sleptDelays = mutableListOf<Long>()
        val logs = mutableListOf<String>()
        val latch = CountDownLatch(1)

        val connector = BluetoothRfcommConnector(
            bluetoothDevice = null, // Will trigger error path
            maxRetries = 3,
            onMaxRetriesReached = { latch.countDown() },
            sleepProvider = { sleptDelays.add(it) },
            logger = { tag, msg, _ -> logs.add("[$tag] $msg") },
        )

        connector.connectRfcommWithRetry("AA:BB:CC:DD:EE:FF")
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(0, connector.retryCount)
    }
}
