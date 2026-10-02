package com.shilapi.xcertplay.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class ConnectivityCategory {
    ALL,
    BLUETOOTH,
    WIFI,
    IAP2_HANDSHAKE,
    AIRPLAY,
    SYSTEM
}

enum class ConnectivityLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class ConnectivityLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val category: ConnectivityCategory,
    val level: ConnectivityLevel,
    val message: String,
    val details: String? = null,
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMillis))

    fun toFormattedString(): String {
        val levelTag = when (level) {
            ConnectivityLevel.INFO -> "[INFO]"
            ConnectivityLevel.SUCCESS -> "[OK]"
            ConnectivityLevel.WARNING -> "[WARN]"
            ConnectivityLevel.ERROR -> "[ERR]"
        }
        val catTag = when (category) {
            ConnectivityCategory.BLUETOOTH -> "[BT]"
            ConnectivityCategory.WIFI -> "[WIFI]"
            ConnectivityCategory.IAP2_HANDSHAKE -> "[HANDSHAKE]"
            ConnectivityCategory.AIRPLAY -> "[AIRPLAY]"
            ConnectivityCategory.SYSTEM -> "[SYS]"
            ConnectivityCategory.ALL -> "[LOG]"
        }
        return "$formattedTime $levelTag $catTag $message" + if (details != null) " ($details)" else ""
    }
}

/**
 * Thread-safe, bounded in-memory log store capturing real-time connectivity events
 * (Wi-Fi P2P/Hotspot negotiation, Bluetooth RFCOMM, iAP2 handshake, and AirPlay states).
 */
object ConnectivityLogStore {
    private const val MAX_ENTRIES = 250
    private var sequenceId = 0L
    private val entries = ArrayList<ConnectivityLogEntry>()
    private val listeners = CopyOnWriteArrayList<(ConnectivityLogEntry) -> Unit>()
    private val lock = Any()

    init {
        log(
            ConnectivityCategory.SYSTEM,
            ConnectivityLevel.INFO,
            "Connectivity event logger initialized",
        )
    }

    fun log(
        category: ConnectivityCategory,
        level: ConnectivityLevel,
        message: String,
        details: String? = null,
    ) {
        val entry = synchronized(lock) {
            val item = ConnectivityLogEntry(
                id = ++sequenceId,
                timestampMillis = System.currentTimeMillis(),
                category = category,
                level = level,
                message = message,
                details = details,
            )
            entries.add(item)
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            item
        }
        listeners.forEach { listener ->
            try {
                listener(entry)
            } catch (_: Throwable) {}
        }
    }

    fun logWifi(message: String, isSuccess: Boolean? = null) {
        val level = when (isSuccess) {
            true -> ConnectivityLevel.SUCCESS
            false -> ConnectivityLevel.ERROR
            null -> if (message.contains("fail", ignoreCase = true) || message.contains("error", ignoreCase = true)) {
                ConnectivityLevel.ERROR
            } else if (message.contains("success", ignoreCase = true) || message.contains("ready", ignoreCase = true) || message.contains("started", ignoreCase = true)) {
                ConnectivityLevel.SUCCESS
            } else {
                ConnectivityLevel.INFO
            }
        }
        log(ConnectivityCategory.WIFI, level, message)
    }

    fun logBluetooth(message: String, isSuccess: Boolean? = null) {
        val level = when (isSuccess) {
            true -> ConnectivityLevel.SUCCESS
            false -> ConnectivityLevel.ERROR
            null -> if (message.contains("fail", ignoreCase = true) || message.contains("error", ignoreCase = true)) {
                ConnectivityLevel.ERROR
            } else if (message.contains("connected", ignoreCase = true) || message.contains("ready", ignoreCase = true)) {
                ConnectivityLevel.SUCCESS
            } else {
                ConnectivityLevel.INFO
            }
        }
        log(ConnectivityCategory.BLUETOOTH, level, message)
    }

    fun logHandshake(message: String, isSuccess: Boolean? = null) {
        val level = when (isSuccess) {
            true -> ConnectivityLevel.SUCCESS
            false -> ConnectivityLevel.ERROR
            null -> if (message.contains("fail", ignoreCase = true) || message.contains("reject", ignoreCase = true) || message.contains("error", ignoreCase = true)) {
                ConnectivityLevel.ERROR
            } else if (message.contains("accepted", ignoreCase = true) || message.contains("established", ignoreCase = true) || message.contains("success", ignoreCase = true)) {
                ConnectivityLevel.SUCCESS
            } else {
                ConnectivityLevel.INFO
            }
        }
        log(ConnectivityCategory.IAP2_HANDSHAKE, level, message)
    }

    fun getEntries(category: ConnectivityCategory = ConnectivityCategory.ALL): List<ConnectivityLogEntry> {
        return synchronized(lock) {
            if (category == ConnectivityCategory.ALL) {
                ArrayList(entries)
            } else {
                entries.filter { it.category == category }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
        log(ConnectivityCategory.SYSTEM, ConnectivityLevel.INFO, "Log buffer cleared")
    }

    fun addListener(listener: (ConnectivityLogEntry) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Parses raw debug strings from controllers and classifies them into structured connectivity events.
     */
    fun logFromText(text: String) {
        if (text.isBlank()) return
        val lower = text.lowercase(Locale.ROOT)

        val level = when {
            lower.contains("failed") || lower.contains("error") || lower.contains("rejected") || lower.contains("exception") -> ConnectivityLevel.ERROR
            lower.contains("stale") || lower.contains("warn") || lower.contains("timeout") || lower.contains("interrupted") || lower.contains("retry") -> ConnectivityLevel.WARNING
            lower.contains("success") || lower.contains("connected") || lower.contains("ready") || lower.contains("active") || lower.contains("accepted") || lower.contains("established") -> ConnectivityLevel.SUCCESS
            else -> ConnectivityLevel.INFO
        }

        val category = when {
            lower.contains("iap2") || lower.contains("handshake") || lower.contains("identification") || lower.contains("csm") || lower.contains("accessory") -> ConnectivityCategory.IAP2_HANDSHAKE
            lower.contains("rfcomm") || lower.contains("bluetooth") || lower.contains("bdaddr") || lower.contains("bt ") || lower.contains("nearby") -> ConnectivityCategory.BLUETOOTH
            lower.contains("p2p") || lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("hotspot") || lower.contains("bonjour") || lower.contains("wlan") || lower.contains("group owner") -> ConnectivityCategory.WIFI
            lower.contains("airplay") || lower.contains("carplay") || lower.contains("h.264") || lower.contains("hevc") || lower.contains("audio") || lower.contains("stream") -> ConnectivityCategory.AIRPLAY
            else -> ConnectivityCategory.SYSTEM
        }

        log(category, level, text.trim())
    }
}
