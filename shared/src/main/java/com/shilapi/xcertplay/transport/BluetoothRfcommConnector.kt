package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

/**
 * Manages Bluetooth RFCOMM socket connection with exponential backoff retries.
 */
class BluetoothRfcommConnector(
    private val bluetoothDevice: BluetoothDevice? = null,
    private val carplayUuid: UUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb"),
    private val maxRetries: Int = 3,
    private val startHandshake: (BluetoothSocket) -> Unit = {},
    private val onMaxRetriesReached: () -> Unit = {},
    private val sleepProvider: (Long) -> Unit = { Thread.sleep(it) },
    private val logger: ((tag: String, msg: String, isError: Boolean) -> Unit)? = null,
) {

    @Volatile
    var retryCount = 0
        private set

    fun connectRfcommWithRetry(deviceAddress: String) {
        Thread({
            val device = bluetoothDevice
            if (device == null) {
                logMessage("BT_RFCOMM", "BluetoothDevice is null for address $deviceAddress", isError = true)
                onMaxRetriesReached()
                return@Thread
            }

            try {
                val socket = device.createRfcommSocketToServiceRecord(carplayUuid)
                socket.connect()
                retryCount = 0 // အောင်မြင်ပါက Retry Counter ကို Reset လုပ်ပါ
                logMessage("BT_RFCOMM", "RFCOMM connected successfully to $deviceAddress", isError = false)
                startHandshake(socket)
            } catch (e: IOException) {
                retryCount++
                if (retryCount <= maxRetries) {
                    val delay = retryCount * 1000L // 1s, 2s, 3s စသဖြင့် ကြာချိန်တိုးသွားမည်
                    logMessage(
                        "BT_RFCOMM",
                        "Connection failed. Retrying ($retryCount/$maxRetries) in ${delay}ms...",
                        isError = false,
                    )
                    sleepProvider(delay)
                    connectRfcommWithRetry(deviceAddress)
                } else {
                    logMessage(
                        "BT_RFCOMM",
                        "Max retries reached. Resetting Bluetooth adapter state.",
                        isError = true,
                    )
                    retryCount = 0
                    onMaxRetriesReached()
                }
            }
        }, "BT-RFCOMM-RetryThread").start()
    }

    private fun logMessage(tag: String, msg: String, isError: Boolean) {
        if (logger != null) {
            logger.invoke(tag, msg, isError)
        } else {
            try {
                if (isError) {
                    Log.e(tag, msg)
                } else {
                    Log.w(tag, msg)
                }
            } catch (_: Throwable) {
                println("[$tag] $msg")
            }
        }
    }
}
