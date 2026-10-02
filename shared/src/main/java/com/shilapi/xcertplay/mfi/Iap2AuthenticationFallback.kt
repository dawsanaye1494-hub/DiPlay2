package com.shilapi.xcertplay.mfi

import android.os.Handler
import android.os.Looper
import android.util.Log

enum class MfiMode {
    HARDWARE,
    SOFTWARE_EMULATION,
    BYPASS
}

data class MfiConfig(
    var mode: MfiMode = MfiMode.HARDWARE,
    var manufacturer: String = "Apple",
    var model: String = "CarPlay1,1",
)

/**
 * Handles iAP2 AuthenticationFailed (0xAA04) responses from the iPhone by switching
 * the MFi profile to Software Emulation / Bypass mode and initiating a connection retry.
 */
class Iap2AuthenticationFallback(
    val mfiConfig: MfiConfig = MfiConfig(),
    private val closeRfcommSocket: () -> Unit = {},
    private val startBluetoothHandshake: () -> Unit = {},
    private val scheduler: ((delayMillis: Long, action: () -> Unit) -> Unit)? = null,
    private val logger: ((tag: String, msg: String) -> Unit)? = null,
) {

    fun isAuthenticationFailed(payload: ByteArray): Boolean {
        if (payload.size < 2) return false
        // iAP2 AuthenticationFailed message ID is 0xAA04
        val messageId = ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
        return messageId == 0xaa04
    }

    fun handleIap2Response(payload: ByteArray) {
        if (isAuthenticationFailed(payload)) {
            logError("iAP2", "AuthenticationFailed received from iPhone. Triggering Fallback...")

            // 1. Current Session ကို ပိတ်ပါ
            closeRfcommSocket()

            // 2. MFi Profile ကို Software Emulation သို့ ပြောင်းပါ
            mfiConfig.mode = MfiMode.SOFTWARE_EMULATION
            mfiConfig.manufacturer = "Apple" // Generic Compatibility String
            mfiConfig.model = "CarPlay1,1"

            // 3. စက္ကန့်ဝက်ကြာမှ အသစ်ပြန်လည် Handshake စတင်ပါ
            if (scheduler != null) {
                scheduler.invoke(500L, startBluetoothHandshake)
            } else {
                try {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startBluetoothHandshake()
                    }, 500)
                } catch (_: Throwable) {
                    startBluetoothHandshake()
                }
            }
        }
    }

    private fun logError(tag: String, message: String) {
        if (logger != null) {
            logger.invoke(tag, message)
        } else {
            try {
                Log.e(tag, message)
            } catch (_: Throwable) {
                println("[$tag] $message")
            }
        }
    }
}
