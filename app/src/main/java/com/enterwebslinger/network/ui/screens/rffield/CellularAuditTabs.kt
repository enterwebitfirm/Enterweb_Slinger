// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/rffield/CellularAuditTabs.kt ===
package com.enterwebslinger.network.ui.screens.rffield

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.WifiAuditSeverity
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
fun CellularRfTelemetryTab(viewModel: RfFieldViewModel) {
    val state by viewModel.cellularState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isCellularLoading.collectAsStateWithLifecycle()

    val phonePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCellularTelemetry()
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasCellularPermissions()) {
            viewModel.refreshCellularTelemetry()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CellTower, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${state.operatorName} (${state.mcc}/${state.mnc})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${state.radioType.label} • ${state.networkTypeLabel} • ${state.voiceServiceState}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberCyan
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (viewModel.hasCellularPermissions()) {
                                    viewModel.refreshCellularTelemetry()
                                } else {
                                    phonePermLauncher.launch(
                                        arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION)
                                    )
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_refresh_cellular"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    state.errorMessage?.let { err ->
                        Surface(
                            color = StatusAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusAmber,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CellMetricTile("RSRP", state.rsrpDbm?.let { "$it dBm" } ?: "--", Modifier.weight(1f))
                        CellMetricTile("RSRQ", state.rsrqDb?.let { "$it dB" } ?: "--", Modifier.weight(1f))
                        CellMetricTile("SINR", state.sinrDb?.let { "$it dB" } ?: "--", Modifier.weight(1f))
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CellKvRow("Band / Frequency", state.bandLabel)
                            CellKvRow("NCI / Cell Identity", if (state.cellIdentity >= 0) state.cellIdentity.toString() else "--")
                            CellKvRow("PCI / PSC", if (state.pci >= 0) state.pci.toString() else "--")
                            CellKvRow("TAC / LAC", if (state.tacOrLac >= 0) state.tacOrLac.toString() else "--")
                            CellKvRow("NR-ARFCN / EARFCN", if (state.arfcn >= 0) state.arfcn.toString() else "--")
                            CellKvRow("Timing Advance", state.timingAdvance?.toString() ?: "--")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Neighboring Cells (${state.neighbors.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        items(state.neighbors) { neighbor ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${neighbor.radioType.label} • PCI ${neighbor.pciOrPsc} • ${neighbor.bandLabel}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "ARFCN: ${neighbor.arfcnOrEarfcn} • RSRQ: ${neighbor.rsrqOrEcNoDb ?: "--"} dB",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "${neighbor.rsrpOrRscpDbm} dBm",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = if (neighbor.rsrpOrRscpDbm >= -95) StatusEmerald else if (neighbor.rsrpOrRscpDbm >= -110) StatusAmber else StatusCrimson
                    )
                }
            }
        }
    }
}

@Composable
private fun CellMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CardBackgroundElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = CyberCyan)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun CellKvRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
    }
}

@Composable
fun OnvifAndEvilTwinTab(viewModel: RfFieldViewModel) {
    val cameras by viewModel.onvifCameras.collectAsStateWithLifecycle()
    val isScanningCameras by viewModel.isScanningCameras.collectAsStateWithLifecycle()
    val evilTwinFindings by viewModel.evilTwinFindings.collectAsStateWithLifecycle()
    val isAuditingEvilTwin by viewModel.isAuditingEvilTwin.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ONVIF Camera Discovery Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ONVIF & RTSP Camera Discovery",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "WS-Discovery (239.255.255.250:3702) + TCP 554/8000/8080/37777",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.scanOnvifCameras() },
                            enabled = !isScanningCameras,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_scan_onvif"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            if (isScanningCameras) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                            } else {
                                Text("Discover", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        items(cameras, key = { it.ip }) { cam ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cam.hardwareModel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text("${cam.responseTimeMs} ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = StatusEmerald)
                    }
                    Text("IP: ${cam.ip} • Ports: ${cam.openPorts.joinToString(", ").ifEmpty { "UDP 3702" }}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                    Text("Endpoint: ${cam.endpointUrl}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    if (cam.rtspUrls.isNotEmpty()) {
                        Text("RTSP: ${cam.rtspUrls.first()}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = StatusAmber)
                    }
                }
            }
        }

        // Evil Twin Wi-Fi Security Auditor Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = StatusAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Evil Twin & Rogue AP Security Auditor",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Detects duplicate SSIDs with unknown BSSIDs, WPA3->WPA2/Open downgrades, and overlapping hidden APs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runEvilTwinAudit() },
                            enabled = !isAuditingEvilTwin,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_run_evil_twin"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.Black)
                        ) {
                            if (isAuditingEvilTwin) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            } else {
                                Text("Run Wi-Fi Security Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.baselineCurrentConnectedAp() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_trust_current_ap")
                        ) {
                            Text("Trust Current AP", fontSize = 11.sp, color = CyberCyan)
                        }
                    }
                }
            }
        }

        items(evilTwinFindings) { finding ->
            val sevColor = when (finding.severity) {
                WifiAuditSeverity.HIGH -> StatusCrimson
                WifiAuditSeverity.MEDIUM -> StatusAmber
                WifiAuditSeverity.INFO -> CyberCyan
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, sevColor.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(finding.findingType.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = sevColor)
                        Text("Ch ${finding.channel} • ${finding.rssiDbm} dBm", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    }
                    Text("SSID: ${finding.ssid} | BSSID: ${finding.bssid} | Sec: ${finding.securityCapabilities}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                    Text(finding.reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}
