package com.shilapi.xcertplay

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver responsible for auto-starting DiPlay on:
 * - USB_DEVICE_ATTACHED (e.g. iPhone connected via USB)
 * - ACL_CONNECTED (e.g. iPhone connected via Bluetooth)
 * - BOOT_COMPLETED (Car head unit boot completed)
 */
open class USBReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "USBReceiver onReceive: action=$action")

        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            "android.hardware.usb.action.USB_DEVICE_ATTACHED" -> {
                val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                val vendorId = usbDevice?.vendorId
                Log.i(TAG, "USB device attached: vendorId=$vendorId, name=${usbDevice?.deviceName}")

                // Immediately mark wired mode to prevent Wi-Fi Direct conflict
                AirPlayPersistence.saveWirelessEnabled(context, false)

                val launchIntent = Intent(context, CarPlayHostActivity::class.java).apply {
                    this.action = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
                    usbDevice?.let { putExtra(UsbManager.EXTRA_DEVICE, it) }
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                runCatching {
                    context.startActivity(launchIntent)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to launch CarPlayHostActivity on USB attach", error)
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED,
            "android.bluetooth.device.action.ACL_CONNECTED" -> {
                val btDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                Log.i(TAG, "Bluetooth device connected: ${btDevice?.address}")

                val launchIntent = Intent(context, DiPlayActivity::class.java).apply {
                    this.action = "android.bluetooth.device.action.ACL_CONNECTED"
                    btDevice?.let { putExtra(BluetoothDevice.EXTRA_DEVICE, it) }
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                runCatching {
                    context.startActivity(launchIntent)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to launch DiPlayActivity on Bluetooth ACL connected", error)
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.BOOT_COMPLETED" -> {
                if (!AirPlayPersistence.loadAutoStartOnBoot(context)) {
                    Log.i(TAG, "Auto-start on boot is disabled by user settings")
                    return
                }
                val launchIntent = Intent(context, DiPlayActivity::class.java).apply {
                    this.action = Intent.ACTION_BOOT_COMPLETED
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                runCatching {
                    context.startActivity(launchIntent)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to launch DiPlayActivity on boot", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "USBReceiver"
    }
}
