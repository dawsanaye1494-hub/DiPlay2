package com.shilapi.xcertplay.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers Apple CarPlay iAP2 SDP service record on the Android Bluetooth stack
 * using the standard CarPlay UUID: 00000000-0000-1000-8000-00805F9B34FB.
 *
 * This enables iPhones searching for Bluetooth accessories to discover the head unit
 * as a CarPlay-supported system, and accepts incoming RFCOMM connections from iOS.
 */
class BluetoothCarPlaySdpServer(
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
    private val serviceName: String = "WirelessCarPlay",
    val carplayUuid: UUID = CARPLAY_UUID,
    private val onClientConnected: (BluetoothSocket) -> Unit = {},
    private val logger: ((tag: String, msg: String, isError: Boolean) -> Unit)? = null,
) {

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) {
            log("SDP_SERVER", "BluetoothCarPlaySdpServer is already running", false)
            return true
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log("SDP_SERVER", "Bluetooth adapter is null or disabled", true)
            return false
        }

        return try {
            log("SDP_SERVER", "Registering CarPlay SDP record UUID=$carplayUuid name=$serviceName", false)
            serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, carplayUuid)
            isRunning.set(true)

            acceptThread = Thread({
                log("SDP_SERVER", "Listening for incoming CarPlay iAP2 RFCOMM connection...", false)
                while (isRunning.get()) {
                    val socket: BluetoothSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: IOException) {
                        if (!isRunning.get()) {
                            log("SDP_SERVER", "Server socket closed normally", false)
                        } else {
                            log("SDP_SERVER", "Socket accept failed: ${e.message}", true)
                        }
                        break
                    }

                    log("SDP_SERVER", "iPhone connected via Bluetooth SDP record: ${socket.remoteDevice?.address}", false)
                    try {
                        onClientConnected(socket)
                    } catch (e: Exception) {
                        log("SDP_SERVER", "Error in onClientConnected handler: ${e.message}", true)
                    }
                }
            }, "BT-CarPlay-SDP-Accept").apply { start() }

            true
        } catch (e: Exception) {
            log("SDP_SERVER", "Failed to register CarPlay SDP record: ${e.message}", true)
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            log("SDP_SERVER", "Error closing server socket: ${e.message}", false)
        }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        log("SDP_SERVER", "BluetoothCarPlaySdpServer stopped", false)
    }

    fun isListening(): Boolean = isRunning.get()

    private fun log(tag: String, msg: String, isError: Boolean) {
        if (logger != null) {
            logger.invoke(tag, msg, isError)
        } else {
            try {
                if (isError) Log.e(tag, msg) else Log.i(tag, msg)
            } catch (_: Throwable) {
                println("[$tag] $msg")
            }
        }
    }

    companion object {
        /** Apple Wireless CarPlay UUID registered in Bluetooth SDP */
        val CARPLAY_UUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
    }
}
