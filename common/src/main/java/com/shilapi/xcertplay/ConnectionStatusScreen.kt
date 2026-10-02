package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shilapi.xcertplay.gac.GACHycanConfiguration
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydAdbAccess
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data representation of an individual sub-check indicator.
 */
data class StatusIndicatorItem(
    val title: String,
    val detail: String,
    val isActive: Boolean,
)

/**
 * Visual status information for a primary connection subsystem.
 */
data class SubsystemConnectionState(
    val title: String,
    val categoryLabel: String,
    val isPrimaryActive: Boolean,
    val summaryText: String,
    val indicators: List<StatusIndicatorItem>,
    val actionLabel: String,
    val settingsAction: String,
)

/**
 * Overall connectivity state.
 */
data class ConnectionDashboardState(
    val bluetooth: SubsystemConnectionState,
    val wifi: SubsystemConnectionState,
    val adb: SubsystemConnectionState,
    val timestamp: String,
) {
    val totalActive: Int
        get() = (if (bluetooth.isPrimaryActive) 1 else 0) +
            (if (wifi.isPrimaryActive) 1 else 0) +
            (if (adb.isPrimaryActive) 1 else 0)

    val allOperational: Boolean
        get() = totalActive == 3

    val disconnectedCount: Int
        get() = 3 - totalActive
}

// Design Palette: Bold High-Contrast Green & Red Indicators
private val ActiveEmerald = Color(0xFF10B981)
private val ActiveEmeraldDark = Color(0xFF064E3B)
private val ActiveEmeraldBorder = Color(0xFF059669)
private val ActiveText = Color(0xFFA7F3D0)

private val DisconnectedRed = Color(0xFFEF4444)
private val DisconnectedRedDark = Color(0xFF450A0A)
private val DisconnectedRedBorder = Color(0xFFDC2626)
private val DisconnectedText = Color(0xFFFECACA)

private val TerminalBackground = Color(0xFF080E1A)
private val CardSurfaceDark = Color(0xFF131C2E)
private val BorderDefault = Color(0xFF1E293B)

/**
 * Modern Jetpack Compose Screen visualizing the live status of Bluetooth, Wi-Fi, and ADB connections
 * using clear, high-contrast green/red indicators for intuitive head unit troubleshooting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf<ConnectionDashboardState?>(null) }

    fun refreshState() {
        scope.launch {
            isRefreshing = true
            val updated = withContext(Dispatchers.IO) {
                queryConnectionDashboard(context)
            }
            state = updated
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("connection_status_screen"),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = context.getString(R.string.connection_visualizer_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF1F5F9),
                            )
                            Text(
                                text = context.getString(R.string.connection_visualizer_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.padding(end = 8.dp).testTag("close_status_button"),
                        ) {
                            Text("Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TerminalBackground,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBackground),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state?.let { dashState ->
                    // 1. Master System Health Banner
                    item {
                        MasterHealthBanner(
                            state = dashState,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshState() },
                            onCopyReport = {
                                val report = formatDashboardReport(dashState)
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.diagnostics_report_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }

                    // 1b. Display Over Other Apps (Overlay) Permission Card
                    item {
                        OverlayPermissionCard(context = context)
                    }

                    // 2. Bluetooth Connection Card
                    item {
                        ConnectionStatusCard(
                            subsystem = dashState.bluetooth,
                            cardTestTag = "bluetooth_status_card",
                            onActionClick = {
                                openSystemSettings(context, dashState.bluetooth.settingsAction)
                            },
                        )
                    }

                    // 3. Wi-Fi & P2P Direct Connection Card
                    item {
                        ConnectionStatusCard(
                            subsystem = dashState.wifi,
                            cardTestTag = "wifi_status_card",
                            onActionClick = {
                                openSystemSettings(context, dashState.wifi.settingsAction)
                            },
                        )
                    }

                    // 4. ADB & USB Debugging Connection Card
                    item {
                        ConnectionStatusCard(
                            subsystem = dashState.adb,
                            cardTestTag = "adb_status_card",
                            onActionClick = {
                                openSystemSettings(context, dashState.adb.settingsAction)
                            },
                        )
                    }

                    // 5. Quick Troubleshooting Tips
                    item {
                        QuickTroubleshootNotice(state = dashState)
                    }
                } ?: run {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(color = ActiveEmerald)
                                Text(
                                    text = "Scanning Bluetooth, Wi-Fi & ADB interfaces...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun OverlayPermissionCard(context: Context) {
    val hasOverlay = Settings.canDrawOverlays(context)
    val cardBg = if (hasOverlay) ActiveEmeraldDark.copy(alpha = 0.25f) else DisconnectedRedDark.copy(alpha = 0.35f)
    val borderColor = if (hasOverlay) ActiveEmeraldBorder else DisconnectedRedBorder
    val titleColor = if (hasOverlay) ActiveText else DisconnectedText

    Card(
        modifier = Modifier.fillMaxWidth().testTag("overlay_permission_card"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Display Over Other Apps (Overlay)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        text = if (hasOverlay) {
                            "Permission Granted · Ready to project over head unit launcher & navigation apps"
                        } else {
                            "Setup Needed · Grant permission so DiPlay can render CarPlay overlay windows"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = titleColor,
                    )
                }
            }

            if (!hasOverlay) {
                Button(
                    onClick = {
                        openSystemSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DisconnectedRedBorder),
                    modifier = Modifier.testTag("grant_overlay_permission_button"),
                ) {
                    Text("Grant Display Over Other Apps Permission", color = Color.White)
                }
            }
        }
    }
}

/**
 * Top Master Health Banner providing instant green/red status at a glance.
 */
@Composable
private fun MasterHealthBanner(
    state: ConnectionDashboardState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCopyReport: () -> Unit,
) {
    val context = LocalContext.current
    val isAllOperational = state.allOperational

    val bannerBg by animateColorAsState(
        targetValue = if (isAllOperational) ActiveEmeraldDark else DisconnectedRedDark,
        animationSpec = tween(400),
        label = "bannerBg",
    )
    val bannerBorder by animateColorAsState(
        targetValue = if (isAllOperational) ActiveEmeraldBorder else DisconnectedRedBorder,
        animationSpec = tween(400),
        label = "bannerBorder",
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("master_health_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        border = BorderStroke(1.dp, bannerBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isAllOperational) ActiveEmerald else DisconnectedRed),
                    )
                    Column {
                        Text(
                            text = if (isAllOperational) {
                                context.getString(R.string.status_all_operational)
                            } else {
                                context.getString(R.string.status_issues_detected)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isAllOperational) ActiveText else DisconnectedText,
                        )
                        Text(
                            text = if (isAllOperational) {
                                context.getString(R.string.status_all_operational_desc)
                            } else {
                                context.getString(R.string.status_issues_detected_desc, state.disconnectedCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopyReport,
                        modifier = Modifier.testTag("copy_report_button"),
                    ) {
                        Text("Copy", color = Color(0xFFF1F5F9))
                    }
                    Button(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("refresh_status_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAllOperational) ActiveEmerald else DisconnectedRed,
                        ),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

/**
 * Dedicated visual status tile for one subsystem (Bluetooth, Wi-Fi, or ADB)
 * using prominent Green (Active) or Red (Disconnected) styling.
 */
@Composable
private fun ConnectionStatusCard(
    subsystem: SubsystemConnectionState,
    cardTestTag: String,
    onActionClick: () -> Unit,
) {
    val isActive = subsystem.isPrimaryActive

    val cardBorder = if (isActive) ActiveEmeraldBorder else DisconnectedRedBorder
    val badgeBg = if (isActive) ActiveEmerald else DisconnectedRed
    val badgeText = if (isActive) "ACTIVE" else "DISCONNECTED"

    Card(
        modifier = Modifier.fillMaxWidth().testTag(cardTestTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row: Subsystem Name + Large Glow Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = subsystem.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        text = subsystem.categoryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                }

                // Large Visual Badge
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                        Text(
                            text = badgeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }

            // Summary Explanation
            Text(
                text = subsystem.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Color(0xFFCBD5E1) else Color(0xFFFCA5A5),
            )

            // Sub-indicator Checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1324), RoundedCornerShape(10.dp))
                    .border(1.dp, BorderDefault, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subsystem.indicators.forEach { item ->
                    IndicatorRow(item = item)
                }
            }

            // Action Button
            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth().testTag("${cardTestTag}_action_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF1E293B) else DisconnectedRed,
                    contentColor = Color.White,
                ),
            ) {
                Text(subsystem.actionLabel)
            }
        }
    }
}

/**
 * Individual checklist line item with green or red indicator dot.
 */
@Composable
private fun IndicatorRow(item: StatusIndicatorItem) {
    val dotColor = if (item.isActive) ActiveEmerald else DisconnectedRed
    val textColor = if (item.isActive) Color(0xFFF1F5F9) else Color(0xFFEF4444)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFE2E8F0),
            )
        }
        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            color = textColor,
        )
    }
}

/**
 * Contextual troubleshooting card providing targeted steps based on which connections are disconnected.
 */
@Composable
private fun QuickTroubleshootNotice(state: ConnectionDashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("troubleshoot_notice_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Troubleshooting Diagnostic Tips",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF8FAFC),
            )

            if (!state.adb.isPrimaryActive) {
                TroubleshootBullet(
                    text = "ADB Disconnected: If PC browser says 'Install via USB' is disabled, run 'adb kill-server' in cmd on your PC to release the locked USB handle.",
                )
            }
            if (!state.bluetooth.isPrimaryActive) {
                TroubleshootBullet(
                    text = "Bluetooth Inactive: Turn on Bluetooth in system settings. Wireless CarPlay requires Bluetooth for initial handshake and discovery announcement.",
                )
            }
            if (!state.wifi.isPrimaryActive) {
                TroubleshootBullet(
                    text = "Wi-Fi Disconnected: Wi-Fi radio must be enabled to establish the high-speed P2P Direct or hotspot connection for video and audio streaming.",
                )
            }
            if (state.allOperational) {
                TroubleshootBullet(
                    text = "All systems active. Head unit is ready to accept wired USB or wireless AirPlay / CarPlay connections.",
                )
            }
        }
    }
}

@Composable
private fun TroubleshootBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "•", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun queryConnectionDashboard(context: Context): ConnectionDashboardState {
    val btState = queryBluetoothSubsystem(context)
    val wifiState = queryWifiSubsystem(context)
    val adbState = queryAdbSubsystem(context)

    return ConnectionDashboardState(
        bluetooth = btState,
        wifi = wifiState,
        adb = adbState,
        timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    )
}

private fun queryBluetoothSubsystem(context: Context): SubsystemConnectionState {
    val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
    val isSupported = adapter != null
    val isEnabled = adapter?.isEnabled == true
    val realMac = GACHycanConfiguration.resolveBluetoothAddress(context) ?: "Unavailable"
    val isDummy = realMac.equals("02:00:00:00:00:00", ignoreCase = true)
    val pairedCount = runCatching { adapter?.bondedDevices?.size ?: 0 }.getOrDefault(0)

    val isPrimaryActive = isSupported && isEnabled && !isDummy

    val indicators = listOf(
        StatusIndicatorItem(
            title = "Bluetooth Radio",
            detail = if (isEnabled) "Turned On" else "Turned Off",
            isActive = isEnabled,
        ),
        StatusIndicatorItem(
            title = "Hardware MAC Address",
            detail = if (!isDummy && realMac != "Unavailable") realMac else "02:00... (Dummy)",
            isActive = !isDummy && realMac != "Unavailable",
        ),
        StatusIndicatorItem(
            title = "Paired Devices",
            detail = if (pairedCount > 0) "$pairedCount paired device(s)" else "None paired",
            isActive = pairedCount > 0,
        ),
        StatusIndicatorItem(
            title = "RFCOMM Wireless Handoff",
            detail = if (isEnabled) "Listening & Ready" else "Service Stopped",
            isActive = isEnabled,
        ),
    )

    val summary = when {
        !isSupported -> "Bluetooth hardware is not present on this head unit."
        !isEnabled -> "Bluetooth radio is turned off in head unit settings."
        isDummy -> "Android 8 dummy MAC returned; authentic MAC resolution required."
        else -> "Bluetooth link active. Ready for wireless CarPlay announcement."
    }

    return SubsystemConnectionState(
        title = "Bluetooth Connection",
        categoryLabel = "Wireless Discovery & iAP2 Handoff",
        isPrimaryActive = isPrimaryActive,
        summaryText = summary,
        indicators = indicators,
        actionLabel = "Open Bluetooth Settings",
        settingsAction = Settings.ACTION_BLUETOOTH_SETTINGS,
    )
}

private fun queryWifiSubsystem(context: Context): SubsystemConnectionState {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val isEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
    val p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    val isP2pSupported = p2pManager != null

    val wifiMac = GACHycanConfiguration.resolveWifiMacAddress()
    val activeIp = resolveLocalIpv4()
    val isIpBound = activeIp != "None"

    val isPrimaryActive = isEnabled && isP2pSupported

    val indicators = listOf(
        StatusIndicatorItem(
            title = "Wi-Fi Radio",
            detail = if (isEnabled) "Enabled" else "Turned Off",
            isActive = isEnabled,
        ),
        StatusIndicatorItem(
            title = "Wi-Fi Direct (P2P)",
            detail = if (isP2pSupported) "Autonomous Group Ready" else "Unsupported",
            isActive = isP2pSupported,
        ),
        StatusIndicatorItem(
            title = "Interface MAC",
            detail = wifiMac,
            isActive = wifiMac != "02:00:00:00:00:00" && wifiMac.isNotBlank(),
        ),
        StatusIndicatorItem(
            title = "IP Network Address",
            detail = activeIp,
            isActive = isIpBound,
        ),
    )

    val summary = when {
        !isEnabled -> "Wi-Fi radio is disabled. Turn Wi-Fi on to allow CarPlay streaming."
        !isP2pSupported -> "Wi-Fi Direct P2P subsystem is not supported by this OS build."
        else -> "Wi-Fi subsystem ready. Autonomous Group Owner available."
    }

    return SubsystemConnectionState(
        title = "Wi-Fi & P2P Direct",
        categoryLabel = "High-Speed Video & Audio Transport",
        isPrimaryActive = isPrimaryActive,
        summaryText = summary,
        indicators = indicators,
        actionLabel = "Open Wi-Fi Settings",
        settingsAction = Settings.ACTION_WIFI_SETTINGS,
    )
}

private fun queryAdbSubsystem(context: Context): SubsystemConnectionState {
    // 1. USB Connection check
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
        context.registerReceiver(null, filter)
    }
    val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    val isUsb = chargePlug == BatteryManager.BATTERY_PLUGGED_USB

    // 2. Local ADB port 5555 check
    var isPort5555Open = false
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 5555), 250)
            isPort5555Open = true
        }
    } catch (_: Exception) {
        isPort5555Open = false
    }

    // 3. Cluster / HUD ADB State
    val clusterResult = runCatching {
        BydAdbAccess.check(context, mayAsk = false)
    }.getOrNull()
    val isClusterReady = clusterResult?.state == BydAdbAccess.State.READY

    val isPrimaryActive = isUsb || isPort5555Open || isClusterReady

    val indicators = listOf(
        StatusIndicatorItem(
            title = "USB Host Cable Link",
            detail = if (isUsb) "Connected to Host PC" else "Disconnected",
            isActive = isUsb,
        ),
        StatusIndicatorItem(
            title = "Local ADB Port (5555)",
            detail = if (isPort5555Open) "Listening (5555)" else "Offline",
            isActive = isPort5555Open,
        ),
        StatusIndicatorItem(
            title = "Developer Mode Access",
            detail = if (isUsb || isPort5555Open) "Active" else "Idle",
            isActive = isUsb || isPort5555Open,
        ),
        StatusIndicatorItem(
            title = "Cluster HUD ADB Bridge",
            detail = clusterResult?.state?.name ?: "Idle / Not Applicable",
            isActive = isClusterReady,
        ),
    )

    val summary = when {
        isPort5555Open -> "Local ADB network port 5555 is listening and ready."
        isUsb -> "USB cable connected to computer. Ready for ADB commands."
        else -> "No active ADB or USB connection detected. Plug USB cable or enable developer mode."
    }

    return SubsystemConnectionState(
        title = "ADB & USB Debugging",
        categoryLabel = "Developer Bridge & Installation Transport",
        isPrimaryActive = isPrimaryActive,
        summaryText = summary,
        indicators = indicators,
        actionLabel = "Open Developer Options",
        settingsAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
    )
}

private fun resolveLocalIpv4(): String {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            val addresses = Collections.list(iface.inetAddresses)
            for (addr in addresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress ?: continue
                    if (host != "127.0.0.1") return host
                }
            }
        }
    } catch (_: Exception) {}
    return "None"
}

private fun openSystemSettings(context: Context, action: String) {
    runCatching {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDashboardReport(state: ConnectionDashboardState): String = buildString {
    appendLine("=== Connectivity Status Visualizer Report ===")
    appendLine("Timestamp: ${state.timestamp}")
    appendLine("Overall Status: ${if (state.allOperational) "ALL OPERATIONAL" else "ISSUES DETECTED (${state.disconnectedCount} Disconnected)"}")
    appendLine()

    listOf(state.bluetooth, state.wifi, state.adb).forEach { sub ->
        appendLine("[${sub.title}] - ${if (sub.isPrimaryActive) "ACTIVE" else "DISCONNECTED"}")
        appendLine("Summary: ${sub.summaryText}")
        sub.indicators.forEach { ind ->
            appendLine("  • ${ind.title}: ${ind.detail} (${if (ind.isActive) "ACTIVE" else "DISCONNECTED"})")
        }
        appendLine()
    }
}
