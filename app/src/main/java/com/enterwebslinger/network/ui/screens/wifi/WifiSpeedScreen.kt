// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/wifi/WifiSpeedScreen.kt ===
package com.enterwebslinger.network.ui.screens.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.ChannelCongestionInfo
import com.enterwebslinger.network.domain.model.SpeedPhase
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.ui.components.ErrorBannerCard
import com.enterwebslinger.network.ui.components.SpeedometerGauge
import com.enterwebslinger.network.ui.components.WifiChannelGraph
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun WifiSpeedScreen(
    viewModel: WifiSpeedViewModel,
    aiViewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Speed Test", "Wi-Fi Analyzer")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("wifi_speed_screen")
    ) {
        Text(
            text = "PERFORMANCE & SPECTRUM",
            style = MaterialTheme.typography.labelMedium.copy(
                color = CyberCyan,
                letterSpacing = 1.5.sp
            )
        )
        Text(
            text = "Wi-Fi & Speed",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyberCyan
                )
            },
            divider = { Spacer(modifier = Modifier.height(1.dp).background(BorderStrokeColor)) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderStrokeColor, RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.setSelectedTab(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == index) CyberCyan else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("speed_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> SpeedTestTab(viewModel)
            1 -> WifiAnalyzerTab(viewModel, aiViewModel)
        }
    }
}

@Composable
private fun SpeedTestTab(viewModel: WifiSpeedViewModel) {
    val state by viewModel.speedState.collectAsStateWithLifecycle()
    val speedUiState by viewModel.speedUiState.collectAsStateWithLifecycle()
    val isRunning = state.phase != SpeedPhase.IDLE && state.phase != SpeedPhase.COMPLETE && state.phase != SpeedPhase.ERROR

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SpeedometerGauge(
            currentSpeedMbps = if (isRunning) state.currentRateMbps else state.downloadSpeedMbps,
            maxGaugeSpeed = 300f
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.phase == SpeedPhase.ERROR) StatusCrimson else CyberCyan
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isRunning) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        val speedErr = speedUiState as? UiState.Error
        if (speedErr != null) {
            ErrorBannerCard(
                message = speedErr.message,
                onDismiss = { viewModel.dismissSpeedError() },
                onRetry = { viewModel.startSpeedTest() },
                testTagPrefix = "speed_error_banner"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (isRunning) viewModel.stopSpeedTest() else viewModel.startSpeedTest()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) StatusCrimson else CyberCyan,
                contentColor = if (isRunning) Color.White else Color(0xFF00363D)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("speed_test_action_button")
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "Cancel Test" else "Start Speed Test",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpeedCard(
                title = "Download",
                value = String.format("%.1f", state.downloadSpeedMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowDownward,
                accentColor = CyberCyan,
                modifier = Modifier.weight(1f)
            )
            SpeedCard(
                title = "Upload",
                value = String.format("%.1f", state.uploadSpeedMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowUpward,
                accentColor = StatusEmerald,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpeedCard(
                title = "Unloaded Ping",
                value = String.format("%.0f", state.unloadedPingMs),
                unit = "ms",
                icon = Icons.Default.Timer,
                accentColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            SpeedCard(
                title = "Loaded Ping",
                value = String.format("%.0f", state.loadedPingMs),
                unit = "ms",
                icon = Icons.Default.NetworkPing,
                accentColor = CyberCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        SpeedCard(
            title = "Bufferbloat Delta (Loaded - Unloaded)",
            value = "+${String.format("%.0f", state.bufferbloatDeltaMs)}",
            unit = "ms",
            icon = Icons.Default.Speed,
            accentColor = when {
                state.bufferbloatDeltaMs < 15 -> StatusEmerald
                state.bufferbloatDeltaMs < 50 -> StatusAmber
                else -> StatusCrimson
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SpeedCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun WifiAnalyzerTab(
    viewModel: WifiSpeedViewModel,
    aiViewModel: AiAssistantViewModel
) {
    val wifiList by viewModel.filteredWifiList.collectAsStateWithLifecycle()
    val selectedBand by viewModel.selectedBandFilter.collectAsStateWithLifecycle()
    val wifiUiState by viewModel.wifiUiState.collectAsStateWithLifecycle()
    val channelCongestion by viewModel.channelCongestion.collectAsStateWithLifecycle()
    val microDiagnosisMap by aiViewModel.microDiagnosisMap.collectAsStateWithLifecycle()
    val microDiagnosisLoading by aiViewModel.microDiagnosisLoading.collectAsStateWithLifecycle()

    val isScanningWifi = wifiUiState is UiState.Loading
    val isAnalyzingChannel = microDiagnosisLoading.contains("wifi_channel")
    val channelRecommendation = microDiagnosisMap["wifi_channel"]

    val bands = listOf("All", "2.4 GHz", "5 GHz", "6 GHz")
    val filteredCongestion = if (selectedBand == "All") {
        channelCongestion
    } else {
        channelCongestion.filter { it.band == selectedBand }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wifi_ap_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bands.forEach { band ->
                        FilterChip(
                            selected = selectedBand == band,
                            onClick = { viewModel.setSelectedBandFilter(band) },
                            label = { Text(band, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            ),
                            modifier = Modifier.testTag("wifi_band_chip_${band.replace(" ", "_")}")
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isScanningWifi) viewModel.cancelWifiScan() else viewModel.refreshWifiScan()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_wifi_scan_button")
                ) {
                    Icon(
                        imageVector = if (isScanningWifi) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isScanningWifi) "Cancel Scan" else "Refresh Scan",
                        tint = if (isScanningWifi) StatusCrimson else CyberCyan
                    )
                }
            }

            if (isScanningWifi) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = CyberCyan,
                    trackColor = CardBackgroundElevated
                )
            }

            val wifiErr = wifiUiState as? UiState.Error
            if (wifiErr != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorBannerCard(
                    message = wifiErr.message,
                    onDismiss = { viewModel.dismissWifiError() },
                    onRetry = { viewModel.refreshWifiScan() },
                    testTagPrefix = "wifi_error_banner"
                )
            }
        }

        item {
            Text(
                text = "Channel Spectrum Density",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            WifiChannelGraph(entries = wifiList)
        }

        if (filteredCongestion.isNotEmpty()) {
            item {
                ChannelCongestionCard(congestionList = filteredCongestion)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.35f)))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Channel Optimization",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = CyberCyan)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val currentAp = wifiList.firstOrNull { it.isCurrentNetwork }
                                val currentCh = currentAp?.channel ?: 6
                                val currentB = currentAp?.band ?: "2.4 GHz"
                                aiViewModel.recommendWifiChannel(currentCh, currentB, wifiList)
                            },
                            enabled = !isAnalyzingChannel && wifiList.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                            modifier = Modifier.height(48.dp).testTag("optimize_wifi_channel_button")
                        ) {
                            if (isAnalyzingChannel) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = CyberCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Analyzing...", fontSize = 11.sp)
                            } else {
                                Text(text = "Recommend", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (channelRecommendation != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = channelRecommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Detected Access Points (${wifiList.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        if (wifiList.isEmpty() && !isScanningWifi) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "No nearby access points found. Tap refresh to run an OS Wi-Fi scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(wifiList) { ap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (ap.isCurrentNetwork) CyberCyan.copy(alpha = 0.2f) else CardBackgroundElevated,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (ap.isCurrentNetwork) CyberCyan else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ap.ssid,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (ap.isCurrentNetwork) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = StatusEmerald.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "CONNECTED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = StatusEmerald,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "BSSID: ${ap.bssid} • Ch ${ap.channel} (${ap.band} • ${ap.channelWidthMhz} MHz)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${ap.rssiDbm} dBm",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        ap.rssiDbm >= -65 -> StatusEmerald
                                        ap.rssiDbm >= -80 -> StatusAmber
                                        else -> StatusCrimson
                                    }
                                )
                            )
                            Text(
                                text = ap.security.substringBefore("[").ifBlank { "WPA2" },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCongestionCard(congestionList: List<ChannelCongestionInfo>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_congestion_card"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Channel Congestion & Best Channels",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            congestionList.take(8).forEach { info ->
                val barColor = when {
                    info.congestionPercent < 30 -> StatusEmerald
                    info.congestionPercent < 65 -> StatusAmber
                    else -> StatusCrimson
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ch ${info.channel} (${info.band})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextPrimary
                            )
                            if (info.isRecommended) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = StatusEmerald.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "RECOMMENDED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = StatusEmerald,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${info.apCount} APs • ${info.congestionPercent}% load",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (info.congestionPercent / 100f).coerceIn(0.03f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = barColor,
                        trackColor = CardBackgroundElevated
                    )
                }
            }
        }
    }
}
