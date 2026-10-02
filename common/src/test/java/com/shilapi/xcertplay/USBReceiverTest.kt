package com.shilapi.xcertplay

import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class USBReceiverTest {

    @Test
    fun receiverInstantiatesSuccessfully() {
        val receiver = USBReceiver()
        assertNotNull(receiver)
    }

    @Test
    fun receiverHandlesNullActionGracefully() {
        val receiver = USBReceiver()
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent() // action is null
        receiver.onReceive(context, intent)
    }

    @Test
    fun receiverHandlesUnknownActionGracefully() {
        val receiver = USBReceiver()
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent("com.example.UNKNOWN_ACTION")
        receiver.onReceive(context, intent)
    }
}
