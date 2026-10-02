package com.shilapi.xcertplay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.shilapi.xcertplay.host.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.shilapi.xcertplay.log.ConnectivityCategory
import com.shilapi.xcertplay.log.ConnectivityLevel
import com.shilapi.xcertplay.log.ConnectivityLogEntry
import com.shilapi.xcertplay.log.ConnectivityLogStore
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
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.gac.GACHycanConfiguration
import com.shilapi.xcertplay.hud.BydAdbAccess
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
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
 * Diagnostic & troubleshooting activity designed for Android head units (e.g. GAC Hycan Z03, BYD, generic AAOS).
 * Displays real-time hardware status, connectivity health (Bluetooth, Wi-Fi, ADB), and installation guidance.
 */
class SystemDiagnosticsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XcertplayTheme {
                var selectedTab by remember { mutableStateOf(0) }
                Scaffold(
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF080E1A))
                                .padding(top = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = getString(R.string.system_troubleshoot_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFF1F5F9),
                                    )
                                    Text(
                                        text = getString(R.string.system_requirements_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                    )
                                }
                                OutlinedButton(
                                    onClick = { finish() },
                                    modifier = Modifier.testTag("diagnostics_back_button"),
                                ) {
                                    Text(text = "Back", color = Color(0xFF38BDF8))
                                }
                            }

                            ScrollableTabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color(0xFF080E1A),
                                contentColor = Color(0xFF38BDF8),
                                edgePadding = 16.dp,
                                modifier = Modifier.fillMaxWidth().testTag("diagnostics_tab_row"),
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    modifier = Modifier.testTag("tab_status_visualizer"),
                                    text = {
                                        Text(
                                            text = getString(R.string.tab_visualizer),
                                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (selectedTab == 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        )
                                    },
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    modifier = Modifier.testTag("tab_live_performance"),
                                    text = {
                                        Text(
                                            text = getString(R.string.tab_live_dashboard),
                                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (selectedTab == 1) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        )
                                    },
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    modifier = Modifier.testTag("tab_detailed_specs"),
                                    text = {
                                        Text(
                                            text = getString(R.string.tab_detailed_diagnostics),
                                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (selectedTab == 2) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        )
                                    },
                                )
                            }
                        }
                    },
                    containerColor = Color(0xFF080E1A),
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (selectedTab) {
                            0 -> ConnectionStatusScreen(onBack = { finish() }, showTopBar = false)
                            1 -> LiveDiagnosticDashboardScreen(onBack = { finish() }, showTopBar = false)
                            else -> SystemDiagnosticsScreen(onBack = { finish() }, showTopBar = false)
                        }
                    }
                }
            }
        }
    }
}

enum class CheckStatus {
    READY,
    ATTENTION,
    FAILED
}

data class SystemRequirementsInfo(
    val osVersion: String,
    val apiLevel: Int,
    val isApiSupported: Boolean,
    val manufacturer: String,
    val model: String,
    val board: String,
    val displayMetrics: String,
    val isGacPlatform: Boolean,
    val micPermission: Boolean,
    val locationPermission: Boolean,
    val bluetoothPermission: Boolean,
    val overlayPermission: Boolean,
)

data class BluetoothStatusInfo(
    val isSupported: Boolean,
    val isEnabled: Boolean,
    val hardwareMac: String,
    val isDummyMac: Boolean,
    val pairedDeviceCount: Int,
    val status: CheckStatus,
    val statusSummary: String,
)

data class WifiStatusInfo(
    val isEnabled: Boolean,
    val isP2pSupported: Boolean,
    val hardwareMac: String,
    val ipAddress: String,
    val hotspotMode: String,
    val status: CheckStatus,
    val statusSummary: String,
)

data class AdbStatusInfo(
    val isUsbConnected: Boolean,
    val isLocalPortOpen: Boolean,
    val clusterAdbState: String,
    val status: CheckStatus,
    val statusSummary: String,
    val troubleshootingTip: String,
)

data class DiagnosticsReport(
    val system: SystemRequirementsInfo,
    val bluetooth: BluetoothStatusInfo,
    val wifi: WifiStatusInfo,
    val adb: AdbStatusInfo,
    val timestamp: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDiagnosticsScreen(
    onBack: () -> Unit,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<DiagnosticsReport?>(null) }

    fun refreshDiagnostics() {
        scope.launch {
            isLoading = true
            val gathered = withContext(Dispatchers.IO) {
                gatherDiagnostics(context)
            }
            report = gathered
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("system_diagnostics_screen"),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = context.getString(R.string.system_troubleshoot_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = context.getString(R.string.system_requirements_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.padding(end = 8.dp).testTag("diagnostics_back_button"),
                        ) {
                            Text("Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header / Action Controls
                item {
                    HeaderActionCard(
                        isLoading = isLoading,
                        onRefresh = { refreshDiagnostics() },
                        onCopyReport = {
                            report?.let { rep ->
                                val text = formatReportText(rep)
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.diagnostics_report_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }

                report?.let { rep ->
                    // Card 1: System & Hardware Environment
                    item {
                        SystemHardwareCard(rep.system)
                    }

                    // Card 2: Bluetooth Subsystem
                    item {
                        BluetoothCard(
                            info = rep.bluetooth,
                            onOpenSettings = {
                                openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
                            },
                        )
                    }

                    // Card 3: Wi-Fi & P2P Direct
                    item {
                        WifiCard(
                            info = rep.wifi,
                            onOpenSettings = {
                                openSettings(context, Settings.ACTION_WIFI_SETTINGS)
                            },
                        )
                    }

                    // Card 4: ADB & USB Debugging
                    item {
                        AdbCard(
                            info = rep.adb,
                            onOpenDevSettings = {
                                openSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            },
                        )
                    }

                    // Card 5: Real-Time Connectivity Events Log
                    item {
                        RealTimeConnectivityLogCard()
                    }

                    // Card 6: Comprehensive Troubleshooting Guide
                    item {
                        TroubleshootingGuideCard()
                    }
                } ?: run {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
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
private fun HeaderActionCard(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onCopyReport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("header_action_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Diagnostics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Verify head unit wireless, audio, and developer capabilities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyReport,
                    modifier = Modifier.testTag("copy_report_button"),
                ) {
                    Text("Copy Report")
                }
                Button(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_diagnostics_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun SystemHardwareCard(info: SystemRequirementsInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("system_hardware_card"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "System & Hardware Requirements",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                StatusChip(
                    status = if (info.isApiSupported) CheckStatus.READY else CheckStatus.ATTENTION,
                    label = if (info.isApiSupported) "COMPATIBLE" else "API < 26",
                )
            }

            InfoRow(label = "Android Version", value = "Android ${info.osVersion} (API ${info.apiLevel})")
            InfoRow(label = "Hardware Model", value = "${info.manufacturer} ${info.model}")
            InfoRow(label = "Board / Chipset", value = info.board)
            InfoRow(label = "Display & Density", value = info.displayMetrics)
            InfoRow(label = "Vehicle Profile", value = if (info.isGacPlatform) "GAC Hycan Z03 (Optimized)" else "Standard Head Unit")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runtime Permissions",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionBadge(name = "Microphone", granted = info.micPermission)
                PermissionBadge(name = "Location", granted = info.locationPermission)
                PermissionBadge(name = "Nearby Devices", granted = info.bluetoothPermission)
                PermissionBadge(name = "Display Over Apps", granted = info.overlayPermission)
            }
            if (!info.overlayPermission) {
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    },
                    modifier = Modifier.padding(top = 4.dp).testTag("grant_overlay_permission_button"),
                ) {
                    Text("Grant Display Over Other Apps Permission")
                }
            }
        }
    }
}

@Composable
private fun BluetoothCard(
    info: BluetoothStatusInfo,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("bluetooth_diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bluetooth Subsystem",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                StatusChip(status = info.status, label = info.status.name)
            }

            Text(
                text = info.statusSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            InfoRow(
                label = "Bluetooth Hardware",
                value = when {
                    !info.isSupported -> "Not Available"
                    info.isEnabled -> "Enabled & Active"
                    else -> "Disabled in Settings"
                },
            )
            InfoRow(
                label = "Real Hardware MAC",
                value = info.hardwareMac + if (info.isDummyMac) " (Dummy 02:00... blocked)" else "",
                isMonospace = true,
            )
            InfoRow(label = "Paired Devices", value = "${info.pairedDeviceCount} paired device(s)")

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().testTag("open_bluetooth_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("Open Bluetooth Settings")
            }
        }
    }
}

@Composable
private fun WifiCard(
    info: WifiStatusInfo,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("wifi_diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Wi-Fi & Wi-Fi Direct (P2P)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                StatusChip(status = info.status, label = info.status.name)
            }

            Text(
                text = info.statusSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            InfoRow(label = "Wi-Fi Radio", value = if (info.isEnabled) "Enabled" else "Turned Off")
            InfoRow(label = "Wi-Fi Direct P2P", value = if (info.isP2pSupported) "Autonomous Group Ready" else "Unsupported")
            InfoRow(label = "Interface MAC", value = info.hardwareMac, isMonospace = true)
            InfoRow(label = "Active IP Address", value = info.ipAddress, isMonospace = true)
            InfoRow(label = "Configured Hotspot Mode", value = info.hotspotMode)

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().testTag("open_wifi_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("Open Wi-Fi Settings")
            }
        }
    }
}

@Composable
private fun AdbCard(
    info: AdbStatusInfo,
    onOpenDevSettings: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("adb_diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ADB & USB Connectivity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                StatusChip(status = info.status, label = info.status.name)
            }

            Text(
                text = info.statusSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            InfoRow(
                label = "USB Cable Connection",
                value = if (info.isUsbConnected) "Connected to Host PC" else "Disconnected / No Data Link",
            )
            InfoRow(
                label = "Local ADB Port (5555)",
                value = if (info.isLocalPortOpen) "Listening / Reachable" else "Not Listening (Offline)",
            )
            InfoRow(label = "Cluster / HUD Access", value = info.clusterAdbState)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = info.troubleshootingTip,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Button(
                onClick = onOpenDevSettings,
                modifier = Modifier.fillMaxWidth().testTag("open_developer_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("Open Developer Options")
            }
        }
    }
}

@Composable
private fun RealTimeConnectivityLogCard() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedCategory by remember { mutableStateOf(ConnectivityCategory.ALL) }
    var entries by remember { mutableStateOf(ConnectivityLogStore.getEntries(selectedCategory)) }
    var isPaused by remember { mutableStateOf(false) }

    DisposableEffect(selectedCategory) {
        entries = ConnectivityLogStore.getEntries(selectedCategory)
        val unsubscribe = ConnectivityLogStore.addListener {
            if (!isPaused) {
                entries = ConnectivityLogStore.getEntries(selectedCategory)
            }
        }
        onDispose {
            unsubscribe()
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("realtime_connectivity_log_card"),
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
                Text(
                    text = context.getString(R.string.connectivity_logs_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                StatusChip(
                    status = if (isPaused) CheckStatus.ATTENTION else CheckStatus.READY,
                    label = if (isPaused) "PAUSED" else "LIVE",
                )
            }

            Text(
                text = context.getString(R.string.connectivity_logs_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val filters = listOf(
                    ConnectivityCategory.ALL to "All",
                    ConnectivityCategory.WIFI to "Wi-Fi",
                    ConnectivityCategory.BLUETOOTH to "Bluetooth",
                    ConnectivityCategory.IAP2_HANDSHAKE to "Handshake",
                )
                filters.forEach { (cat, label) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            entries = ConnectivityLogStore.getEntries(cat)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Controls: Pause, Clear, Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        isPaused = !isPaused
                        if (!isPaused) entries = ConnectivityLogStore.getEntries(selectedCategory)
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (isPaused) "Resume" else "Pause", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        ConnectivityLogStore.clear()
                        entries = ConnectivityLogStore.getEntries(selectedCategory)
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Clear", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val text = entries.joinToString("\n") { it.toFormattedString() }
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, context.getString(R.string.connectivity_logs_copied), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Copy", fontSize = 12.sp)
                }
            }

            // Terminal View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF080E1A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = context.getString(R.string.connectivity_logs_empty),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF64748B),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEach { entry ->
                            val levelColor = when (entry.level) {
                                ConnectivityLevel.SUCCESS -> Color(0xFF10B981)
                                ConnectivityLevel.ERROR -> Color(0xFFEF4444)
                                ConnectivityLevel.WARNING -> Color(0xFFF59E0B)
                                ConnectivityLevel.INFO -> Color(0xFF38BDF8)
                            }
                            val catColor = when (entry.category) {
                                ConnectivityCategory.BLUETOOTH -> Color(0xFF818CF8)
                                ConnectivityCategory.WIFI -> Color(0xFF34D399)
                                ConnectivityCategory.IAP2_HANDSHAKE -> Color(0xFFF472B6)
                                ConnectivityCategory.AIRPLAY -> Color(0xFFA78BFA)
                                ConnectivityCategory.SYSTEM -> Color(0xFF94A3B8)
                                ConnectivityCategory.ALL -> Color(0xFF94A3B8)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = entry.formattedTime,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B),
                                )
                                Text(
                                    text = "[${entry.level.name.take(3)}]",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = levelColor,
                                )
                                Text(
                                    text = "[${entry.category.name}]",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = catColor,
                                )
                                Text(
                                    text = entry.message + if (entry.details != null) " (${entry.details})" else "",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("troubleshooting_guide_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Installation & Connection Troubleshooting",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            TroubleshootStep(
                number = "1",
                title = "AI Studio 'Install via USB' button disabled in browser?",
                description = "On your computer, open Command Prompt or Terminal and run:\n  adb kill-server\nThis releases the PC's exclusive ADB lock so Chrome/Edge WebUSB can detect the device.",
            )

            TroubleshootStep(
                number = "2",
                title = "Direct APK Installation via ADB",
                description = "If ADB is active on your PC, you can install directly via command line:\n  adb install -r app-debug.apk",
            )

            TroubleshootStep(
                number = "3",
                title = "Wireless CarPlay Connection Requirements",
                description = "Ensure both Bluetooth and Wi-Fi are switched ON on this head unit. On Android 8-12, Location permission is mandatory for scanning Wi-Fi Direct peers.",
            )

            TroubleshootStep(
                number = "4",
                title = "GAC Hycan Z03 & Renesas Head Units",
                description = "The app includes autonomous Wi-Fi Direct group owner fallback for Android 8.1 and genuine Bluetooth MAC address resolution for handoffs.",
            )
        }
    }
}

@Composable
private fun TroubleshootStep(
    number: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}

@Composable
private fun StatusChip(status: CheckStatus, label: String) {
    val (bgColor, textColor) = when (status) {
        CheckStatus.READY -> Color(0xFF1B5E20) to Color(0xFFE8F5E9)
        CheckStatus.ATTENTION -> Color(0xFFE65100) to Color(0xFFFFF3E0)
        CheckStatus.FAILED -> Color(0xFFB71C1C) to Color(0xFFFFEBEE)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionBadge(name: String, granted: Boolean) {
    Surface(
        color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = "$name: ${if (granted) "OK" else "Missing"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun gatherDiagnostics(context: Context): DiagnosticsReport {
    val systemInfo = gatherSystemInfo(context)
    val bluetoothInfo = gatherBluetoothInfo(context)
    val wifiInfo = gatherWifiInfo(context)
    val adbInfo = gatherAdbInfo(context)

    return DiagnosticsReport(
        system = systemInfo,
        bluetooth = bluetoothInfo,
        wifi = wifiInfo,
        adb = adbInfo,
        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
    )
}

private fun gatherSystemInfo(context: Context): SystemRequirementsInfo {
    val displayMetrics = context.resources.displayMetrics
    val widthPx = displayMetrics.widthPixels
    val heightPx = displayMetrics.heightPixels
    val densityDpi = displayMetrics.densityDpi

    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasBt = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

    val hasOverlay = Settings.canDrawOverlays(context)

    return SystemRequirementsInfo(
        osVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        isApiSupported = Build.VERSION.SDK_INT >= 26,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        board = "${Build.BOARD} (${Build.HARDWARE})",
        displayMetrics = "${widthPx}x${heightPx} @ ${densityDpi}dpi",
        isGacPlatform = GACHycanConfiguration.isGacHycan(),
        micPermission = hasMic,
        locationPermission = hasLoc,
        bluetoothPermission = hasBt,
        overlayPermission = hasOverlay,
    )
}

private fun gatherBluetoothInfo(context: Context): BluetoothStatusInfo {
    val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
    if (adapter == null) {
        return BluetoothStatusInfo(
            isSupported = false,
            isEnabled = false,
            hardwareMac = "None",
            isDummyMac = false,
            pairedDeviceCount = 0,
            status = CheckStatus.FAILED,
            statusSummary = "Bluetooth hardware not detected on this head unit.",
        )
    }

    val isEnabled = adapter.isEnabled
    val realMac = GACHycanConfiguration.resolveBluetoothAddress(context) ?: "Unavailable"
    val isDummy = realMac.equals("02:00:00:00:00:00", ignoreCase = true)
    val pairedCount = runCatching { adapter.bondedDevices?.size ?: 0 }.getOrDefault(0)

    val (status, summary) = when {
        !isEnabled -> CheckStatus.ATTENTION to "Bluetooth is turned off. Turn it on in system settings to connect iPhone."
        isDummy -> CheckStatus.ATTENTION to "System returned Android 8 dummy MAC (02:00:00:00:00:00). Hardware MAC resolution required."
        else -> CheckStatus.READY to "Bluetooth hardware ready. Hardware MAC resolved for CarPlay wireless announcements."
    }

    return BluetoothStatusInfo(
        isSupported = true,
        isEnabled = isEnabled,
        hardwareMac = realMac,
        isDummyMac = isDummy,
        pairedDeviceCount = pairedCount,
        status = status,
        statusSummary = summary,
    )
}

private fun gatherWifiInfo(context: Context): WifiStatusInfo {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val isEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
    val p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    val isP2pSupported = p2pManager != null

    val wifiMac = GACHycanConfiguration.resolveWifiMacAddress()
    val activeIp = resolveLocalIp()

    val (status, summary) = when {
        !isEnabled -> CheckStatus.ATTENTION to "Wi-Fi is turned off. Turn it on so Wi-Fi Direct or hotspot can function."
        activeIp == "None" -> CheckStatus.ATTENTION to "Wi-Fi Direct idle. Ready to create autonomous Group Owner upon connection."
        else -> CheckStatus.READY to "Wi-Fi adapter active. Network interface bound."
    }

    return WifiStatusInfo(
        isEnabled = isEnabled,
        isP2pSupported = isP2pSupported,
        hardwareMac = wifiMac,
        ipAddress = activeIp,
        hotspotMode = "Wi-Fi Direct (P2P Group Owner)",
        status = status,
        statusSummary = summary,
    )
}

private fun gatherAdbInfo(context: Context): AdbStatusInfo {
    // 1. Check USB connection via BatteryManager intent
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
        context.registerReceiver(null, filter)
    }
    val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    val isUsb = chargePlug == BatteryManager.BATTERY_PLUGGED_USB

    // 2. Check local ADB port 5555
    var isPort5555Open = false
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 5555), 250)
            isPort5555Open = true
        }
    } catch (_: Exception) {
        isPort5555Open = false
    }

    // 3. Check cluster ADB status
    val clusterStatus = runCatching {
        val result = BydAdbAccess.check(context, mayAsk = false)
        result.state.name
    }.getOrDefault("Not applicable")

    val (status, summary) = when {
        isPort5555Open -> CheckStatus.READY to "Local ADB service is running on port 5555."
        isUsb -> CheckStatus.READY to "USB connection detected. Ready for host communication."
        else -> CheckStatus.ATTENTION to "No active USB or network ADB session detected."
    }

    val tip = if (isUsb) {
        "USB cable connected. If 'Install via USB' is disabled in your PC browser, run 'adb kill-server' in cmd on your computer to release the locked USB handle."
    } else {
        "To connect your computer: plug a USB data cable into the head unit's primary USB port and enable USB Debugging in Developer Options."
    }

    return AdbStatusInfo(
        isUsbConnected = isUsb,
        isLocalPortOpen = isPort5555Open,
        clusterAdbState = clusterStatus,
        status = status,
        statusSummary = summary,
        troubleshootingTip = tip,
    )
}

private fun resolveLocalIp(): String {
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

private fun openSettings(context: Context, action: String) {
    runCatching {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Could not open system settings", Toast.LENGTH_SHORT).show()
    }
}

private fun formatReportText(report: DiagnosticsReport): String = buildString {
    appendLine("=== Head Unit System & Diagnostics Report ===")
    appendLine("Generated: ${report.timestamp}")
    appendLine()
    appendLine("[System Requirements]")
    appendLine("OS: Android ${report.system.osVersion} (API ${report.system.apiLevel})")
    appendLine("Device: ${report.system.manufacturer} ${report.system.model}")
    appendLine("Board: ${report.system.board}")
    appendLine("Display: ${report.system.displayMetrics}")
    appendLine("GAC Hycan Profile: ${report.system.isGacPlatform}")
    appendLine("Permissions: Mic=${report.system.micPermission}, Loc=${report.system.locationPermission}, BT=${report.system.bluetoothPermission}")
    appendLine()
    appendLine("[Bluetooth]")
    appendLine("Supported: ${report.bluetooth.isSupported}, Enabled: ${report.bluetooth.isEnabled}")
    appendLine("Hardware MAC: ${report.bluetooth.hardwareMac} (Dummy: ${report.bluetooth.isDummyMac})")
    appendLine("Paired Devices: ${report.bluetooth.pairedDeviceCount}")
    appendLine("Status: ${report.bluetooth.statusSummary}")
    appendLine()
    appendLine("[Wi-Fi & P2P Direct]")
    appendLine("Enabled: ${report.wifi.isEnabled}, P2P Support: ${report.wifi.isP2pSupported}")
    appendLine("Interface MAC: ${report.wifi.hardwareMac}")
    appendLine("IP Address: ${report.wifi.ipAddress}")
    appendLine("Status: ${report.wifi.statusSummary}")
    appendLine()
    appendLine("[ADB & USB]")
    appendLine("USB Connected: ${report.adb.isUsbConnected}")
    appendLine("Local Port 5555: ${report.adb.isLocalPortOpen}")
    appendLine("Cluster ADB: ${report.adb.clusterAdbState}")
    appendLine("Status: ${report.adb.statusSummary}")
}
