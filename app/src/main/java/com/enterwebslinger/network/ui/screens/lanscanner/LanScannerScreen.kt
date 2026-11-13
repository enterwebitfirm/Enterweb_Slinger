// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/lanscanner/LanScannerScreen.kt ===
package com.enterwebslinger.network.ui.screens.lanscanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.ui.components.ErrorBannerCard
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.StatusPurple
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScannerScreen(
    viewModel: LanScannerViewModel,
    aiViewModel: AiAssistantViewModel,
    onNavigateToPortScan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val scanUiState by viewModel.scanUiState.collectAsStateWithLifecycle()
    val devices by viewModel.filteredDevices.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val nicknameFeedback by viewModel.nicknameFeedback.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("lan_scanner_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LAN DISCOVERY ENGINE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CyberCyan,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "Network Devices",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.exportReport(context, formatAsJson = false) },
                    modifier = Modifier.size(48.dp).testTag("share_lan_report_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share Report", tint = CyberCyan)
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) StatusCrimson else CyberCyan,
                        contentColor = if (isScanning) Color.White else Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp).testTag("lan_scan_action_button")
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isScanning) "Stop" else "Scan Subnet")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isScanning) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = CyberCyan,
                    trackColor = CardBackgroundElevated
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        val scanError = scanUiState as? UiState.Error
        if (scanError != null) {
            ErrorBannerCard(
                message = scanError.message,
                onDismiss = { viewModel.dismissError() },
                onRetry = { viewModel.startScan() },
                testTagPrefix = "lan_error_banner"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = if (isScanning) CyberCyan else TextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Filter by IP, Hostname, MAC, or Vendor...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lan_search_textfield"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = BorderStrokeColor
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isScanning) "Discovering active network nodes..." else "No devices discovered yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Scan Subnet' to broadcast ICMP, TCP, mDNS, SSDP, and ARP discovery probes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lan_devices_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.ip }) { device ->
                    DeviceListItem(
                        device = device,
                        onClick = { viewModel.selectDevice(device) }
                    )
                }
            }
        }
    }

    selectedDevice?.let { device ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectDevice(null) },
            sheetState = sheetState,
            containerColor = CardBackground,
            modifier = Modifier.testTag("device_detail_bottom_sheet")
        ) {
            DeviceDetailSheetContent(
                device = device,
                aiViewModel = aiViewModel,
                nicknameFeedback = nicknameFeedback,
                onSaveNickname = { identifier, name, type ->
                    viewModel.saveDeviceNickname(identifier, name, type)
                },
                onScanPorts = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        viewModel.selectDevice(null)
                        onNavigateToPortScan(device.ip)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceListItem(
    device: DiscoveredDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_item_${device.ip.replace(".", "_")}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon: ImageVector = when (device.deviceType) {
                "Router" -> Icons.Default.Router
                "Phone" -> Icons.Default.Smartphone
                "PC / NAS", "PC / Samba" -> Icons.Default.Computer
                else -> Icons.Default.Devices
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(CardBackgroundElevated, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayName = device.customLabel ?: device.hostname
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Text(
                        text = "${device.responseTimeMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (device.responseTimeMs < 20) StatusEmerald else StatusAmber,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan
                        )
                    )
                    Text(
                        text = device.vendor,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                if (device.services.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        device.services.forEach { tag ->
                            Surface(
                                color = StatusPurple.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = StatusPurple,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDetailSheetContent(
    device: DiscoveredDevice,
    aiViewModel: AiAssistantViewModel,
    nicknameFeedback: String?,
    onSaveNickname: (identifier: String, name: String, type: String) -> Unit,
    onScanPorts: () -> Unit
) {
    var nicknameInput by remember(device.ip) { mutableStateOf(device.customLabel ?: "") }
    val microDiagnosisMap by aiViewModel.microDiagnosisMap.collectAsStateWithLifecycle()
    val microDiagnosisLoading by aiViewModel.microDiagnosisLoading.collectAsStateWithLifecycle()

    val deviceKey = "device_${device.ip}"
    val isAuditing = microDiagnosisLoading.contains(deviceKey)
    val auditResult = microDiagnosisMap[deviceKey]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (device.deviceType) {
                        "Router" -> Icons.Default.Router
                        "Phone" -> Icons.Default.Smartphone
                        "PC / NAS", "PC / Samba" -> Icons.Default.Computer
                        else -> Icons.Default.Devices
                    },
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.customLabel ?: device.hostname,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = device.vendor,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = nicknameInput,
                onValueChange = { nicknameInput = it },
                label = { Text("Custom Nickname") },
                placeholder = { Text("Enter device label") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackgroundElevated,
                    unfocusedContainerColor = CardBackgroundElevated,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val key = if (device.mac != "Unavailable" && device.mac.isNotBlank()) device.mac else device.ip
                    onSaveNickname(key, nicknameInput, device.deviceType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_device_nickname_button")
            ) {
                Text("Save")
            }
        }

        if (!nicknameFeedback.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nicknameFeedback,
                style = MaterialTheme.typography.labelSmall,
                color = if (nicknameFeedback.startsWith("Failed")) StatusCrimson else StatusEmerald
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                DetailRow(label = "IP Address", value = device.ip, isMono = true)
                DetailRow(label = "MAC Address", value = device.mac, isMono = true)
                DetailRow(label = "Latency", value = "${device.responseTimeMs} ms")
                DetailRow(label = "Device Type", value = device.deviceType)
                if (device.openPorts.isNotEmpty()) {
                    DetailRow(label = "Probed Ports", value = device.openPorts.joinToString(", "))
                }
                device.serverHeader?.let {
                    DetailRow(label = "Server Info", value = it)
                }
            }
        }

        if (device.services.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Discovered Protocols & Tags", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                device.services.forEach { s ->
                    Surface(
                        color = StatusPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = s,
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (auditResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.4f)))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Assessment",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = CyberCyan)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = auditResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onScanPorts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("scan_ports_for_device_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Scan Open Ports on This Device", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { aiViewModel.auditDeviceSecurity(device) },
                enabled = !isAuditing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("audit_device_security_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CyberCyan
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                if (isAuditing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyberCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Auditing Device Security...", fontSize = 13.sp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "AI Security Audit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            color = TextPrimary
        )
    }
}
