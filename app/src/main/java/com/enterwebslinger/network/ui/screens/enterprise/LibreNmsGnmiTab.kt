// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/LibreNmsGnmiTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.GrpcServingStatus
import com.enterwebslinger.network.domain.model.LibreNmsAlert
import com.enterwebslinger.network.domain.model.LibreNmsDevice
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun LibreNmsGnmiTab(viewModel: EnterpriseViewModel) {
    val libreConfig by viewModel.libreNmsConfig.collectAsState()
    val libreDevices by viewModel.libreNmsDevices.collectAsState()
    val libreAlerts by viewModel.libreNmsAlerts.collectAsState()
    val libreStatusMsg by viewModel.libreNmsStatusMessage.collectAsState()
    val isLibreLoading by viewModel.libreNmsLoading.collectAsState()

    val gnmiConfig by viewModel.gnmiConfig.collectAsState()
    val grpcHealth by viewModel.grpcHealthResult.collectAsState()
    val gnmiNodes by viewModel.gnmiTelemetryNodes.collectAsState()
    val gnmiStatusMsg by viewModel.gnmiStatusMessage.collectAsState()
    val isGnmiLoading by viewModel.gnmiLoading.collectAsState()

    var pathsInput by remember(gnmiConfig.targetPaths) {
        mutableStateOf(gnmiConfig.targetPaths.joinToString(", "))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. LibreNMS Server Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("librenms_config_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LibreNMS REST API (v0)", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = libreConfig.serverUrl,
                        onValueChange = { viewModel.updateLibreNmsConfig(libreConfig.copy(serverUrl = it)) },
                        label = { Text("LibreNMS Host URL") },
                        placeholder = { Text("e.g. https://nms.example.local") },
                        modifier = Modifier.fillMaxWidth().testTag("librenms_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = libreConfig.apiToken,
                        onValueChange = { viewModel.updateLibreNmsConfig(libreConfig.copy(apiToken = it)) },
                        label = { Text("API Token (X-Auth-Token)") },
                        placeholder = { Text("Enter LibreNMS API token") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("librenms_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ignore Self-Signed SSL/TLS", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = libreConfig.ignoreSslErrors,
                            onCheckedChange = { viewModel.updateLibreNmsConfig(libreConfig.copy(ignoreSslErrors = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00363D), checkedTrackColor = CyberCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.testLibreNmsConnection() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("librenms_test_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            enabled = !isLibreLoading && libreConfig.serverUrl.isNotBlank()
                        ) {
                            if (isLibreLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test & Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (libreStatusMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isErr = libreStatusMsg?.contains("failed", ignoreCase = true) == true ||
                            libreStatusMsg?.contains("error", ignoreCase = true) == true
                        Text(
                            text = libreStatusMsg ?: "",
                            color = if (isErr) StatusError else CyberCyan,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 2. LibreNMS Alerts Section
        if (libreAlerts.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = StatusError)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Active Alerts (${libreAlerts.size})", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
            }
            items(libreAlerts) { alert ->
                LibreAlertCard(alert, onAck = { viewModel.acknowledgeAlert(alert.alertId) })
            }
        }

        // 3. LibreNMS Devices Section
        if (libreDevices.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = StatusConnected)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Monitored Devices (${libreDevices.size})", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
            }
            items(libreDevices) { device ->
                LibreDeviceCard(device)
            }
        }

        // 4. gNMI & gRPC Telemetry Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("gnmi_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Router, contentDescription = null, tint = StatusConnected)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("gRPC Health & gNMI Telemetry", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gnmiConfig.host,
                            onValueChange = { viewModel.updateGnmiConfig(gnmiConfig.copy(host = it)) },
                            label = { Text("gRPC Host") },
                            placeholder = { Text("e.g. 192.0.2.10") },
                            modifier = Modifier.weight(2f).testTag("gnmi_host_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gnmiConfig.port.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: 50051
                                viewModel.updateGnmiConfig(gnmiConfig.copy(port = p))
                            },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f).testTag("gnmi_port_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pathsInput,
                        onValueChange = { raw ->
                            pathsInput = raw
                            val parsed = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            viewModel.updateGnmiConfig(gnmiConfig.copy(targetPaths = parsed))
                        },
                        label = { Text("Target YANG Paths (comma-separated)") },
                        placeholder = { Text("/interfaces/interface/state/counters, /system/state") },
                        modifier = Modifier.fillMaxWidth().testTag("gnmi_paths_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use TLS (HTTP/2 gRPC-TLS)", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = gnmiConfig.useTls,
                            onCheckedChange = { viewModel.updateGnmiConfig(gnmiConfig.copy(useTls = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00363D), checkedTrackColor = CyberCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.checkGrpcHealth() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("grpc_health_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusConnected, contentColor = Color(0xFF003314)),
                            enabled = !isGnmiLoading && gnmiConfig.host.isNotBlank()
                        ) {
                            Text("Health Probe", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.fetchGnmiTelemetry() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("gnmi_stream_button"),
                            enabled = !isGnmiLoading && gnmiConfig.host.isNotBlank()
                        ) {
                            Text("Query gNMI Paths", fontSize = 11.sp)
                        }
                    }

                    if (!gnmiStatusMsg.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isErr = gnmiStatusMsg?.contains("failed", ignoreCase = true) == true ||
                            gnmiStatusMsg?.contains("error", ignoreCase = true) == true
                        Text(
                            text = gnmiStatusMsg ?: "",
                            color = if (isErr) StatusError else CyberCyan,
                            fontSize = 11.sp
                        )
                    }

                    if (grpcHealth != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (color, label) = when (grpcHealth?.status) {
                                        GrpcServingStatus.SERVING -> Pair(StatusConnected, "SERVING (Healthy)")
                                        GrpcServingStatus.NOT_SERVING -> Pair(StatusError, "NOT SERVING (Degraded)")
                                        GrpcServingStatus.SERVICE_UNKNOWN -> Pair(StatusWarning, "SERVICE UNKNOWN")
                                        else -> Pair(TextMuted, "UNKNOWN")
                                    }
                                    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("gRPC Health: $label", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Target: ${grpcHealth?.target} | RTT: ${grpcHealth?.responseTimeMs}ms", color = TextSecondary, fontSize = 10.sp)
                                if (!grpcHealth?.errorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(grpcHealth?.errorMessage ?: "", color = StatusError, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    if (gnmiNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Telemetry Nodes (YANG Paths):", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        gnmiNodes.forEach { node ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B1120), RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(node.path, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(2f))
                                    Text(node.value, color = CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibreAlertCard(alert: LibreNmsAlert, onAck: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
            .border(1.dp, StatusError.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.ruleName, color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Device: ${alert.deviceHostname} | ${alert.timestamp}", color = TextSecondary, fontSize = 10.sp)
            }
            if (!alert.isAcknowledged) {
                OutlinedButton(
                    onClick = onAck,
                    modifier = Modifier.height(48.dp).testTag("ack_alert_${alert.alertId}")
                ) {
                    Text("ACK", fontSize = 10.sp)
                }
            } else {
                Text("ACK'd", color = StatusConnected, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LibreDeviceCard(device: LibreNmsDevice) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(if (device.status) StatusConnected else StatusError, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(device.hostname, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("${device.ip} | ${device.hardware} (${device.os})", color = TextSecondary, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${device.pingLatencyMs} ms", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (device.status) "ONLINE" else "OFFLINE", color = if (device.status) StatusConnected else StatusError, fontSize = 10.sp)
            }
        }
    }
}
