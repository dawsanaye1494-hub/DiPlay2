package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.HandlerThread
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Wi-Fi connection setup with automatic fallback:
 * 5GHz Wi-Fi Direct -> 2.4GHz Wi-Fi Direct -> LocalOnlyHotspot (SoftAP)
 */
class WifiConnectionManager(
    private val context: Context,
    private val diagnostic: (String) -> Unit = {},
) : WirelessHotspotManager {

    enum class WifiState { P2P_5GHZ, P2P_2GHZ, SOFT_AP }

    private val appContext = context.applicationContext
    private val p2pManager = appContext.getSystemService(WifiP2pManager::class.java)

    private var currentState = WifiState.P2P_5GHZ
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var handlerThread: HandlerThread? = null
    private var localHotspotManager: LocalOnlyHotspotManager? = null
    private val closed = AtomicBoolean(false)

    @Volatile
    private var activeHotspotInfo: WirelessHotspotInfo? = null

    fun getCurrentState(): WifiState = currentState

    /**
     * Starts the Wi-Fi connection fallback sequence asynchronously.
     */
    fun startWifiService(onResult: (Boolean, WirelessHotspotInfo?) -> Unit = { _, _ -> }) {
        if (closed.get()) {
            onResult(false, null)
            return
        }

        when (currentState) {
            WifiState.P2P_5GHZ -> {
                diagnostic("Attempting Wi-Fi P2P 5GHz group creation...")
                createP2pGroup(frequencyBand = 5) { success ->
                    if (success) {
                        onResult(true, activeHotspotInfo)
                    } else {
                        diagnostic("5GHz Wi-Fi P2P failed. Falling back to 2.4GHz...")
                        currentState = WifiState.P2P_2GHZ
                        startWifiService(onResult)
                    }
                }
            }
            WifiState.P2P_2GHZ -> {
                diagnostic("Attempting Wi-Fi P2P 2.4GHz group creation...")
                createP2pGroup(frequencyBand = 2) { success ->
                    if (success) {
                        onResult(true, activeHotspotInfo)
                    } else {
                        diagnostic("2.4GHz Wi-Fi P2P failed. Falling back to SoftAP...")
                        currentState = WifiState.SOFT_AP
                        startWifiService(onResult)
                    }
                }
            }
            WifiState.SOFT_AP -> {
                diagnostic("Attempting LocalOnlyHotspot (SoftAP) fallback...")
                startLocalHotspotFallback { success, info ->
                    onResult(success, info)
                }
            }
        }
    }

    /**
     * Creates a Wi-Fi Direct (P2P) group specifying the target frequency band.
     * frequencyBand: 5 for 5GHz (5180MHz), 2 for 2.4GHz (2437MHz).
     */
    private fun createP2pGroup(frequencyBand: Int, callback: (Boolean) -> Unit) {
        val manager = p2pManager
        if (manager == null) {
            callback(false)
            return
        }

        try {
            val thread = HandlerThread("WifiConnMgr-P2P").apply { start() }
            handlerThread = thread
            val channel = manager.initialize(appContext, thread.looper, null)
            p2pChannel = channel

            val randomPassphrase = generateRandomPassphrase()
            val randomSsid = "DiPlay-P2P-${(1000..9999).random()}"
            val frequencyMHz = if (frequencyBand == 5) 5180 else 2437

            val actionListener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    diagnostic("P2P createGroup call accepted for band $frequencyBand ($frequencyMHz MHz)")
                    requestP2pGroupInfo(channel) { group ->
                        val ssid = group?.networkName ?: randomSsid
                        val passphrase = group?.passphrase ?: randomPassphrase
                        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && group != null) group.frequency else frequencyMHz
                        val ch = wifiFrequencyMhzToChannel(freq) ?: (if (frequencyBand == 5) 36 else 6)

                        activeHotspotInfo = WirelessHotspotInfo(
                            ssid = ssid,
                            passphrase = passphrase,
                            security = Iap2WirelessSecurity.WPA_WPA2,
                            channel = ch,
                            frequencyMHz = freq,
                            bssid = group?.owner?.deviceAddress,
                            interfaceName = null,
                            hostAddress = null,
                            bandLabel = if (frequencyBand == 5) "5 GHz" else "2.4 GHz",
                            backend = WirelessHotspotBackend.WIFI_P2P,
                        )
                        callback(true)
                    }
                }

                override fun onFailure(reason: Int) {
                    val reasonStr = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P unsupported"
                        WifiP2pManager.BUSY -> "Framework busy"
                        WifiP2pManager.ERROR -> "Internal error"
                        else -> "Reason code $reason"
                    }
                    diagnostic("P2P createGroup failed for $frequencyBand GHz: $reasonStr")
                    callback(false)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = WifiP2pConfig.Builder()
                    .setNetworkName(randomSsid)
                    .setPassphrase(randomPassphrase)
                    .setGroupOperatingFrequency(frequencyMHz)
                    .build()
                manager.createGroup(channel, config, actionListener)
            } else {
                manager.createGroup(channel, actionListener)
            }
        } catch (e: Exception) {
            diagnostic("Exception in createP2pGroup ($frequencyBand GHz): ${e.message}")
            callback(false)
        }
    }

    private fun requestP2pGroupInfo(channel: WifiP2pManager.Channel, callback: (WifiP2pGroup?) -> Unit) {
        val manager = p2pManager ?: return callback(null)
        try {
            manager.requestGroupInfo(channel) { group ->
                callback(group)
            }
        } catch (e: Exception) {
            callback(null)
        }
    }

    /**
     * LocalOnlyHotspot (SoftAP) fallback API call.
     */
    private fun startLocalHotspotFallback(callback: (Boolean, WirelessHotspotInfo?) -> Unit) {
        try {
            val hotspotMgr = LocalOnlyHotspotManager(appContext, diagnostic)
            localHotspotManager = hotspotMgr
            Thread {
                try {
                    val info = hotspotMgr.start(30_000L)
                    activeHotspotInfo = info
                    diagnostic("LocalOnlyHotspot started successfully: ${info.ssid}")
                    callback(true, info)
                } catch (e: Exception) {
                    diagnostic("LocalOnlyHotspot fallback failed: ${e.message}")
                    callback(false, null)
                }
            }.apply {
                name = "WifiConnMgr-SoftAP"
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            diagnostic("LocalOnlyHotspot exception: ${e.message}")
            callback(false, null)
        }
    }

    /**
     * Synchronous implementation of WirelessHotspotManager interface.
     */
    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        val latch = CountDownLatch(1)
        var resultInfo: WirelessHotspotInfo? = null
        var resultError: Exception? = null

        startWifiService { success, info ->
            if (success && info != null) {
                resultInfo = info
            } else {
                resultError = IOException("Wi-Fi connection setup failed through all fallback states (5GHz -> 2.4GHz -> SoftAP)")
            }
            latch.countDown()
        }

        if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            close()
            throw IOException("Wi-Fi connection manager timed out after $timeoutMillis ms")
        }

        resultError?.let { throw it }
        return resultInfo ?: throw IOException("Wi-Fi connection manager returned no hotspot info")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            p2pChannel?.let { ch ->
                p2pManager?.removeGroup(ch, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ch.close()
                }
            }
        } catch (_: Exception) {}

        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {}

        try {
            localHotspotManager?.close()
        } catch (_: Exception) {}
    }

    private fun generateRandomPassphrase(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sr = SecureRandom()
        return (1..12).map { chars[sr.nextInt(chars.length)] }.joinToString("")
    }
}
