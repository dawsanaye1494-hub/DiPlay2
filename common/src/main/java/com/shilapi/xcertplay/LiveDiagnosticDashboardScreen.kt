package com.shilapi.xcertplay

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.ConnectionQuality
import com.shilapi.xcertplay.network.LiveConnectionMetrics
import com.shilapi.xcertplay.network.LiveConnectionTelemetry
import com.shilapi.xcertplay.network.TelemetryTransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TerminalBackground = Color(0xFF080E1A)
private val CardSurfaceDark = Color(0xFF111A2C)
private val BorderDefault = Color(0xFF1E293B)

private val EmeraldAccent = Color(0xFF10B981)
private val SkyAccent = Color(0xFF38BDF8)
private val AmberAccent = Color(0xFFF59E0B)
private val RedAccent = Color(0xFFEF4444)

/**
 * Real-time diagnostic dashboard monitoring live signal strength, handshake latency,
 * and packet loss for the active Wi-Fi or USB connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDiagnosticDashboardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isProbing by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf<LiveConnectionMetrics?>(null) }

    // Live polling loop updating every 1000ms
    LaunchedEffect(Unit) {
        while (isActive) {
            val updated = withContext(Dispatchers.IO) {
                LiveConnectionTelemetry.queryMetrics(context, runSocketProbe = false)
            }
            metrics = updated
            delay(1000)
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("diagnostic_dashboard_screen"),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = context.getString(R.string.dashboard_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF1F5F9),
                            )
                            Text(
                                text = context.getString(R.string.dashboard_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.padding(end = 8.dp).testTag("dashboard_back_button"),
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
                metrics?.let { m ->
                    // 1. Hero Summary Quality Banner
                    item {
                        QualityHeroBanner(
                            metrics = m,
                            isProbing = isProbing,
                            onRunProbe = {
                                scope.launch {
                                    isProbing = true
                                    val probed = withContext(Dispatchers.IO) {
                                        LiveConnectionTelemetry.queryMetrics(context, runSocketProbe = true)
                                    }
                                    metrics = probed
                                    isProbing = false
                                    Toast.makeText(context, "Latency probe completed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCopyMetrics = {
                                val report = formatTelemetryReport(m)
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, context.getString(R.string.diagnostics_report_copied), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    // 2. Metric Card 1: Signal Strength & Physical Link
                    item {
                        SignalStrengthMetricCard(metrics = m)
                    }

                    // 3. Metric Card 2: Handshake Latency Breakdown
                    item {
                        HandshakeLatencyMetricCard(metrics = m)
                    }

                    // 4. Metric Card 3: Packet Loss & Stream Health
                    item {
                        PacketLossMetricCard(metrics = m)
                    }

                    // 5. Live Latency Waveform Graph
                    item {
                        LiveWaveformCard(latencyHistory = m.latencyHistory)
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
                                CircularProgressIndicator(color = EmeraldAccent)
                                Text(
                                    text = "Connecting to telemetry sensors...",
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
private fun QualityHeroBanner(
    metrics: LiveConnectionMetrics,
    isProbing: Boolean,
    onRunProbe: () -> Unit,
    onCopyMetrics: () -> Unit,
) {
    val context = LocalContext.current
    val qualityColor = when (metrics.quality) {
        ConnectionQuality.EXCELLENT -> EmeraldAccent
        ConnectionQuality.GOOD -> SkyAccent
        ConnectionQuality.FAIR -> AmberAccent
        ConnectionQuality.POOR -> RedAccent
    }

    val qualityLabel = when (metrics.quality) {
        ConnectionQuality.EXCELLENT -> context.getString(R.string.connection_quality_excellent)
        ConnectionQuality.GOOD -> context.getString(R.string.connection_quality_good)
        ConnectionQuality.FAIR -> context.getString(R.string.connection_quality_fair)
        ConnectionQuality.POOR -> context.getString(R.string.connection_quality_poor)
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("telemetry_hero_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.6f)),
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
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(qualityColor),
                        )
                        Text(
                            text = "LIVE LINK QUALITY: $qualityLabel",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = qualityColor,
                        )
                    }
                    Text(
                        text = "Transport: ${metrics.channelLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopyMetrics,
                        modifier = Modifier.testTag("copy_metrics_button"),
                    ) {
                        Text("Copy", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onRunProbe,
                        enabled = !isProbing,
                        modifier = Modifier.testTag("run_probe_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyAccent),
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Probe RTT", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Signal strength, RSSI meter, and frequency channel card.
 */
@Composable
private fun SignalStrengthMetricCard(metrics: LiveConnectionMetrics) {
    val progress by animateFloatAsState(
        targetValue = metrics.signalLevelPercent / 100f,
        animationSpec = tween(500),
        label = "signalProgress",
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("signal_meter_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Signal Strength & Link Speed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF8FAFC),
                )
                Text(
                    text = "${metrics.signalLevelPercent}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (metrics.signalLevelPercent >= 75) EmeraldAccent else AmberAccent,
                )
            }

            // Animated Visual Progress Meter
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (metrics.signalLevelPercent >= 75) EmeraldAccent else AmberAccent,
                trackColor = Color(0xFF1E293B),
            )

            // Metrics grid
            val dbm = metrics.signalDbm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricPill(
                    label = "Raw RSSI",
                    value = dbm?.let { "$it dBm" } ?: "0 dBm (Copper)",
                    color = if (dbm != null && dbm < -75) RedAccent else EmeraldAccent,
                )
                MetricPill(
                    label = "Link Bandwidth",
                    value = "${metrics.linkSpeedMbps} Mbps",
                    color = SkyAccent,
                )
                MetricPill(
                    label = "Channel / Band",
                    value = if (metrics.transportType == TelemetryTransportType.USB_WIRED) "Wired USB" else "${metrics.wifiFrequencyMhz} MHz",
                    color = Color(0xFFA78BFA),
                )
            }
        }
    }
}

/**
 * Handshake Latency breakdown (RFCOMM, iAP2, AirPlay RTSP).
 */
@Composable
private fun HandshakeLatencyMetricCard(metrics: LiveConnectionMetrics) {
    val latencyColor = when {
        metrics.handshakeLatencyMs < 40 -> EmeraldAccent
        metrics.handshakeLatencyMs < 80 -> SkyAccent
        metrics.handshakeLatencyMs < 120 -> AmberAccent
        else -> RedAccent
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("latency_meter_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Handshake & Round-Trip Latency",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        text = "Phase timing for Bluetooth RFCOMM, iAP2 and AirPlay",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                }

                Text(
                    text = "${metrics.handshakeLatencyMs} ms",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = latencyColor,
                )
            }

            // Phase Breakdown Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A101D), RoundedCornerShape(10.dp))
                    .border(1.dp, BorderDefault, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LatencyPhaseRow("1. Bluetooth RFCOMM Connection", "${metrics.rfcommLatencyMs} ms")
                LatencyPhaseRow("2. iAP2 Link Synchronization (SYN/ACK)", "${metrics.iap2LatencyMs} ms")
                LatencyPhaseRow("3. AirPlay RTSP & Cryptographic Setup", "${metrics.airplayLatencyMs} ms")
            }
        }
    }
}

@Composable
private fun LatencyPhaseRow(phase: String, duration: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = phase,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1),
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
            color = SkyAccent,
        )
    }
}

/**
 * Packet Loss and Video/Audio stream integrity monitor card.
 */
@Composable
private fun PacketLossMetricCard(metrics: LiveConnectionMetrics) {
    val isZeroLoss = metrics.packetLossPercent == 0.0f
    val lossColor = if (isZeroLoss) EmeraldAccent else RedAccent

    Card(
        modifier = Modifier.fillMaxWidth().testTag("packet_loss_meter_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Packet Loss & Stream Integrity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        text = "RTP frame gaps, jitter, and transport drop rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                }

                Surface(
                    color = if (isZeroLoss) EmeraldAccent.copy(alpha = 0.2f) else RedAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, lossColor),
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.2f", metrics.packetLossPercent)}% LOSS",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = lossColor,
                    )
                }
            }

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricPill(
                    label = "Total Packets",
                    value = "${metrics.totalPackets}",
                    color = Color(0xFFE2E8F0),
                )
                MetricPill(
                    label = "Sequence Gaps",
                    value = "${metrics.gapPackets}",
                    color = if (metrics.gapPackets == 0L) EmeraldAccent else RedAccent,
                )
                MetricPill(
                    label = "Audio Jitter",
                    value = "${metrics.jitterMs} ms",
                    color = SkyAccent,
                )
                MetricPill(
                    label = "Throughput",
                    value = "${(metrics.throughputKbps / 1024f).toInt()} MB/s",
                    color = Color(0xFFA78BFA),
                )
            }
        }
    }
}

/**
 * Rolling waveform / sparkline graph showing live latency stability over time.
 */
@Composable
private fun LiveWaveformCard(latencyHistory: List<Float>) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("waveform_graph_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Live Latency Waveform (Last 20s)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF8FAFC),
                )
                Text(
                    text = "Target: <50ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAccent,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color(0xFF090F1C), RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (latencyHistory.size >= 2) {
                        val maxVal = 100f
                        val minVal = 0f
                        val w = size.width
                        val h = size.height

                        val stepX = w / (latencyHistory.size - 1)
                        val path = Path()

                        latencyHistory.forEachIndexed { i, lat ->
                            val normalizedY = h - ((lat.coerceIn(minVal, maxVal) - minVal) / (maxVal - minVal)) * h
                            val x = i * stepX
                            if (i == 0) {
                                path.moveTo(x, normalizedY)
                            } else {
                                path.lineTo(x, normalizedY)
                            }
                        }

                        // Draw target baseline (50ms line)
                        val targetY = h - (50f / maxVal) * h
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(0f, targetY),
                            end = Offset(w, targetY),
                            strokeWidth = 1.dp.toPx(),
                        )

                        // Draw live sparkline
                        drawPath(
                            path = path,
                            color = SkyAccent,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(Color(0xFF0A101D), RoundedCornerShape(8.dp))
            .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

private fun formatTelemetryReport(m: LiveConnectionMetrics): String = buildString {
    appendLine("=== Live Connection Diagnostic Report ===")
    appendLine("Transport: ${m.channelLabel}")
    appendLine("Quality: ${m.quality.name}")
    appendLine("Signal Strength: ${m.signalDbm?.let { "$it dBm" } ?: "Wired"} (${m.signalLevelPercent}%)")
    appendLine("Link Bandwidth: ${m.linkSpeedMbps} Mbps")
    appendLine("Handshake Latency: ${m.handshakeLatencyMs} ms")
    appendLine("  - RFCOMM: ${m.rfcommLatencyMs} ms")
    appendLine("  - iAP2: ${m.iap2LatencyMs} ms")
    appendLine("  - AirPlay RTSP: ${m.airplayLatencyMs} ms")
    appendLine("Packet Loss: ${String.format(java.util.Locale.US, "%.2f", m.packetLossPercent)}%")
    appendLine("Total Packets: ${m.totalPackets}")
    appendLine("Sequence Gaps: ${m.gapPackets}")
    appendLine("Jitter: ${m.jitterMs} ms")
    appendLine("Throughput: ${m.throughputKbps} Kbps")
}
