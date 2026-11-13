// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/security/SecurityScreen.kt ===
package com.enterwebslinger.network.ui.screens.security

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.HostWatcherTarget
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.ui.components.ErrorBannerCard
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
fun SecurityScreen(
    viewModel: SecurityViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Port Scanner", "Wake-on-LAN", "Host Watcher")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("security_screen")
    ) {
        Text(
            text = "SECURITY & PORTS",
            style = MaterialTheme.typography.labelMedium.copy(
                color = CyberCyan,
                letterSpacing = 1.5.sp
            )
        )
        Text(
            text = "Security Tools",
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
                    modifier = Modifier.testTag("sec_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> PortScannerTab(viewModel)
            1 -> WakeOnLanTab(viewModel)
            2 -> HostWatcherTab(viewModel)
        }
    }
}

@Composable
private fun PortScannerTab(viewModel: SecurityViewModel) {
    val target by viewModel.portTarget.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val customStart by viewModel.customPortStart.collectAsStateWithLifecycle()
    val customEnd by viewModel.customPortEnd.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningPorts.collectAsStateWithLifecycle()
    val progress by viewModel.portScanProgress.collectAsStateWithLifecycle()
    val portResults by viewModel.portResults.collectAsStateWithLifecycle()
    val portUiState by viewModel.portScanUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val presets = listOf("Common Top 100", "Web & Admin", "Custom Range")

    val openHttpPortInBrowser: (Int) -> Unit = { port ->
        val cleanTarget = target.trim()
        val proto = if (port == 443 || port == 8443) "https" else "http"
        val url = "$proto://$cleanTarget:$port"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure { err ->
            Toast.makeText(context, "Unable to open $url (${err.message})", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { viewModel.setPortTarget(it) },
                label = { Text("Target Host / IP") },
                placeholder = { Text("e.g. gateway IP or hostname") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("port_target_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (isScanning) viewModel.stopPortScan() else viewModel.startPortScan()
                },
                enabled = isScanning || target.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) StatusCrimson else CyberCyan,
                    contentColor = if (isScanning) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("port_scan_action_button")
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Radar,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isScanning) "Stop" else "Scan")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { viewModel.setSelectedPreset(preset) },
                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("port_preset_${preset.replace(" ", "_")}")
                )
            }
        }

        if (selectedPreset == "Custom Range") {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customStart,
                    onValueChange = { viewModel.setCustomPortRange(it, customEnd) },
                    label = { Text("Start Port (1-65535)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_port_start_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderStrokeColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = customEnd,
                    onValueChange = { viewModel.setCustomPortRange(customStart, it) },
                    label = { Text("End Port (1-65535)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_port_end_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderStrokeColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        val portErr = portUiState as? UiState.Error
        if (portErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = portErr.message,
                onDismiss = { viewModel.dismissPortScanError() },
                onRetry = { viewModel.startPortScan() },
                testTagPrefix = "port_scan_error_banner"
            )
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scanned ${(progress * 100).toInt()}% • Found ${portResults.size} open ports",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (portResults.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Enter a target IP or hostname and tap 'Scan' to probe open TCP ports & service banners.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("open_ports_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(portResults, key = { it.port }) { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (p.isHttp) Modifier.clickable { openHttpPortInBrowser(p.port) } else Modifier
                            )
                            .testTag("port_item_${p.port}"),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(StatusEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Port ${p.port}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = p.serviceName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                if (p.isHttp) {
                                    Button(
                                        onClick = { openHttpPortInBrowser(p.port) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberCyan.copy(alpha = 0.15f),
                                            contentColor = CyberCyan
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_web_port_${p.port}")
                                    ) {
                                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Web", fontSize = 12.sp)
                                    }
                                } else {
                                    Surface(
                                        color = StatusEmerald.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "OPEN",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = StatusEmerald,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            val banner = p.banner
                            if (!banner.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = CardBackgroundElevated,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = banner,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeOnLanTab(viewModel: SecurityViewModel) {
    val mac by viewModel.wolMacAddress.collectAsStateWithLifecycle()
    val deviceName by viewModel.wolDeviceName.collectAsStateWithLifecycle()
    val broadcastIp by viewModel.wolBroadcastIp.collectAsStateWithLifecycle()
    val status by viewModel.wolStatus.collectAsStateWithLifecycle()
    val wolUiState by viewModel.wolUiState.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.savedWolProfiles.collectAsStateWithLifecycle()

    val isSending = wolUiState is UiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Broadcast Magic Packet (WoL)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sends a 102-byte UDP packet (6x 0xFF followed by 16 repetitions of MAC) to 255.255.255.255 and your active subnet broadcast IP on UDP ports 7 and 9.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { viewModel.setWolDeviceName(it) },
                    label = { Text("Profile / Device Name (Optional)") },
                    placeholder = { Text("e.g. Workstation PC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wol_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackgroundElevated,
                        unfocusedContainerColor = CardBackgroundElevated,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderStrokeColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = mac,
                    onValueChange = { viewModel.setWolMacAddress(it) },
                    label = { Text("Target MAC Address") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wol_mac_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackgroundElevated,
                        unfocusedContainerColor = CardBackgroundElevated,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderStrokeColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = broadcastIp,
                    onValueChange = { viewModel.setWolBroadcastIp(it) },
                    label = { Text("Custom Subnet Broadcast IP (Optional)") },
                    placeholder = { Text("e.g. 192.168.1.255", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wol_broadcast_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackgroundElevated,
                        unfocusedContainerColor = CardBackgroundElevated,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderStrokeColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.triggerWakeOnLan() },
                        enabled = !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wol_broadcast_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF00363D),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sending...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Broadcast WoL", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.saveCurrentWolProfile() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wol_save_profile_button")
                    ) {
                        Text("Save Profile")
                    }
                }

                val wolErr = wolUiState as? UiState.Error
                if (wolErr != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ErrorBannerCard(
                        message = wolErr.message,
                        onDismiss = { viewModel.dismissWolStatus() },
                        onRetry = { viewModel.triggerWakeOnLan() },
                        testTagPrefix = "wol_error_banner"
                    )
                } else if (!status.isNullOrBlank()) {
                    val isFail = status!!.contains("Failed", ignoreCase = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isFail) {
                        ErrorBannerCard(
                            message = status!!,
                            onDismiss = { viewModel.dismissWolStatus() },
                            testTagPrefix = "wol_status_error_banner"
                        )
                    } else {
                        Surface(
                            color = StatusEmerald.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StatusEmerald.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = status!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissWolStatus() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss Status",
                                        tint = StatusEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (savedProfiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Saved Profiles (${savedProfiles.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            savedProfiles.forEach { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("wol_profile_${profile.id}"),
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectWolProfile(profile) }
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (profile.ipAddress.isNotBlank()) "${profile.macAddress} • ${profile.ipAddress}" else profile.macAddress,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberCyan
                                )
                            )
                        }

                        IconButton(
                            onClick = { viewModel.deleteWolProfile(profile.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Profile", tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostWatcherTab(viewModel: SecurityViewModel) {
    val watchedHosts by viewModel.watchedHosts.collectAsStateWithLifecycle()
    val isWatcherRunning by viewModel.isWatcherRunning.collectAsStateWithLifecycle()
    val watcherUiState by viewModel.watcherUiState.collectAsStateWithLifecycle()
    val newHost by viewModel.newHostAddress.collectAsStateWithLifecycle()
    val newLabel by viewModel.newHostLabel.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "Add Host to Monitor",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedButton(
                        onClick = {
                            if (isWatcherRunning) viewModel.stopWatcher() else viewModel.startWatcher()
                        },
                        enabled = isWatcherRunning || watchedHosts.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isWatcherRunning) StatusCrimson else StatusEmerald),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isWatcherRunning) StatusCrimson else StatusEmerald
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_watcher_button")
                    ) {
                        Icon(
                            imageVector = if (isWatcherRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isWatcherRunning) "Pause Polling" else "Resume Polling", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newHost,
                        onValueChange = { viewModel.setNewHostAddress(it) },
                        label = { Text("Host / IP") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("watcher_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBackgroundElevated,
                            unfocusedContainerColor = CardBackgroundElevated,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderStrokeColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { viewModel.setNewHostLabel(it) },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("watcher_label_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBackgroundElevated,
                            unfocusedContainerColor = CardBackgroundElevated,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderStrokeColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.addWatchedHost() },
                    enabled = newHost.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_watcher_host_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Continuous Watcher")
                }
            }
        }

        val watcherErr = watcherUiState as? UiState.Error
        if (watcherErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = watcherErr.message,
                onDismiss = { viewModel.dismissWatcherError() },
                onRetry = { viewModel.startWatcher() },
                testTagPrefix = "watcher_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (watchedHosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Add critical gateways, servers, or IoT hosts to monitor availability & TCP/ICMP latency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("host_watcher_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchedHosts, key = { it.id }) { target ->
                    WatchedHostCard(
                        target = target,
                        onDelete = { viewModel.removeWatchedHost(target.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedHostCard(
    target: HostWatcherTarget,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (target.isUp) StatusEmerald else StatusCrimson, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = target.host,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format("%.0f%% Uptime", target.uptimePercent),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (target.uptimePercent > 95) StatusEmerald else StatusAmber
                            )
                        )
                        Text(
                            text = "${target.lastLatencyMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val paddedHistory = target.recentHistory.takeLast(20)
                paddedHistory.forEach { success ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                color = if (success) StatusEmerald else StatusCrimson,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
