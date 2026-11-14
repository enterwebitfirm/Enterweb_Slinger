// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/firewall/FirewallHubScreen.kt ===
package com.enterwebslinger.network.ui.screens.firewall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.FirewallApplianceProfile
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallHubScreen(
    viewModel: FirewallViewModel,
    aiViewModel: AiAssistantViewModel? = null,
    onBack: (() -> Unit)? = null
) {
    val appliances by viewModel.savedAppliances.collectAsState()
    val selectedAppliance by viewModel.selectedAppliance.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val panOsMatch by viewModel.panOsMatchResult.collectAsState()
    val packetTracer by viewModel.packetTracerResult.collectAsState()
    val dropLogs by viewModel.dropLogs.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Multi-Vendor Firewall Hub",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("fw_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            },
            actions = {
                if (isLoading) {
                    IconButton(onClick = { viewModel.cancelActiveTask() }, modifier = Modifier.testTag("fw_cancel_button")) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyberCyan)
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.refreshDashboard() },
                        enabled = selectedAppliance != null,
                        modifier = Modifier.testTag("fw_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry", tint = CyberCyan)
                    }
                }
                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("fw_add_button")) {
                    Icon(Icons.Default.Add, contentDescription = "Add Firewall Appliance", tint = StatusEmerald)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        // Appliance Profile Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appliances.isEmpty()) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("fw_empty_add_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Sophos / FortiGate / PAN-OS / ASA / pfSense")
                }
            } else {
                appliances.forEach { fw ->
                    val isSelected = fw.id == selectedAppliance?.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) CardBackgroundElevated else DarkBackground,
                        border = BorderStroke(1.dp, if (isSelected) CyberCyan else BorderStrokeColor),
                        modifier = Modifier
                            .clickable { viewModel.selectAppliance(fw) }
                            .testTag("fw_chip_${fw.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = fw.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) CyberCyan else TextPrimary
                                )
                                Text(
                                    text = "${fw.vendor.shortCode} • ${fw.host}:${fw.apiPort}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    color = TextMuted
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { viewModel.deleteAppliance(fw) },
                                    modifier = Modifier.size(28.dp).testTag("fw_del_${fw.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Appliance", tint = StatusCrimson, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 12.dp,
            divider = {}
        ) {
            listOf("Overview & HA/WAN", "IPsec / XFRM", "Policy & Packet", "Drops & Backup").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { viewModel.setSelectedTab(idx) },
                    text = { Text(label) },
                    modifier = Modifier.testTag("fw_tab_$idx")
                )
            }
        }

        statusBanner?.let { msg ->
            Surface(
                color = CardBackgroundElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearStatusBanner() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        val currentFw = selectedAppliance
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentFw == null) {
                item {
                    EmptyFirewallStateCard(onAdd = { showAddDialog = true })
                }
            } else {
                when (selectedTab) {
                    0 -> item {
                        FirewallOverviewPanel(
                            appliance = currentFw,
                            snapshot = snapshot,
                            isLoading = isLoading,
                            onRefresh = { viewModel.refreshDashboard() },
                            aiViewModel = aiViewModel
                        )
                    }
                    1 -> item {
                        FirewallIpsecPanel(
                            appliance = currentFw,
                            snapshot = snapshot,
                            isLoading = isLoading,
                            onBounceSophosTunnel = { viewModel.bounceSophosIpsec(it) },
                            onControlFortiTunnel = { p1, p2, up -> viewModel.toggleFortiGateIpsec(p1, p2, up) },
                            aiViewModel = aiViewModel
                        )
                    }
                    2 -> item {
                        FirewallInteractivePolicyAndPacketPanel(
                            appliance = currentFw,
                            snapshot = snapshot,
                            panOsMatchResult = panOsMatch,
                            packetTracerResult = packetTracer,
                            isLoading = isLoading,
                            onRunPanOsMatch = { fz, tz, sIp, dIp, dPort, proto, app ->
                                viewModel.testPanOsPolicyMatch(fz, tz, sIp, dIp, dPort, proto, app)
                            },
                            onRunCiscoPacketTracer = { inIfc, proto, sIp, sPort, dIp, dPort ->
                                viewModel.runCiscoPacketTracer(inIfc, proto, sIp, sPort, dIp, dPort)
                            },
                            onRestartPfService = { svc -> viewModel.restartPfService(svc) },
                            aiViewModel = aiViewModel
                        )
                    }
                    3 -> item {
                        FirewallDropLogsAndBackupPanel(
                            appliance = currentFw,
                            dropLogs = dropLogs,
                            backupResult = backupResult,
                            isLoading = isLoading,
                            onFetchSophosDrops = { ip -> viewModel.fetchSophosDropPackets(ip) },
                            onTriggerSophosBackup = { viewModel.triggerSophosBackup() },
                            onDownloadFortiBackup = { viewModel.downloadFortiGateBackup() },
                            aiViewModel = aiViewModel
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFirewallApplianceDialog(
            onDismiss = { showAddDialog = false },
            onSave = { vendor, name, host, apiPort, sshPort, user, pass, token, selfSigned, extraCtx ->
                viewModel.saveAppliance(
                    vendor = vendor,
                    name = name,
                    host = host,
                    apiPort = apiPort,
                    sshPort = sshPort,
                    username = user,
                    password = pass,
                    apiToken = token,
                    allowSelfSignedTls = selfSigned,
                    extraContext = extraCtx
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyFirewallStateCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("fw_empty_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Enterprise Multi-Vendor Firewall NOC",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = "Connect directly over HTTPS REST/XML API or SSHv2 to Sophos XG/XGS, Fortinet FortiGate, Palo Alto PAN-OS, Cisco ASA/FTD, and pfSense/OPNsense appliances. Credentials are sealed in AndroidKeyStore AES-256-GCM.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fw_empty_cta"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Firewall Appliance", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AddFirewallApplianceDialog(
    onDismiss: () -> Unit,
    onSave: (
        vendor: FirewallVendor,
        name: String,
        host: String,
        apiPort: Int,
        sshPort: Int,
        username: String,
        password: String,
        apiToken: String,
        allowSelfSignedTls: Boolean,
        extraContext: String
    ) -> Unit
) {
    var vendor by remember { mutableStateOf(FirewallVendor.FORTINET_FORTIGATE) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var apiPortText by remember { mutableStateOf(vendor.defaultApiPort.toString()) }
    var sshPortText by remember { mutableStateOf(vendor.defaultSshPort.toString()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var extraContext by remember { mutableStateOf("") }
    var allowSelfSigned by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Firewall Appliance", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FirewallVendor.entries.forEach { v ->
                        FilterChip(
                            selected = vendor == v,
                            onClick = {
                                vendor = v
                                apiPortText = v.defaultApiPort.toString()
                                sshPortText = v.defaultSshPort.toString()
                            },
                            label = { Text(v.shortCode, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberCyan.copy(alpha = 0.2f), selectedLabelColor = CyberCyan)
                        )
                    }
                }
                Text(vendor.managementHint, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                FwField(name, { name = it }, "Display Name", "Core-GW-01")
                FwField(host, { host = it }, "Management IP / FQDN", "10.0.0.1")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FwField(apiPortText, { apiPortText = it }, "API Port", "443", Modifier.weight(1f))
                    FwField(sshPortText, { sshPortText = it }, "SSH Port", "22", Modifier.weight(1f))
                }
                FwField(username, { username = it }, "Username (SSH / XML API)", "admin")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Encrypted in Keystore)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = { Text("API Bearer Token / PAN-OS Key (Vaulted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                FwField(extraContext, { extraContext = it }, "VDOM / vsys / Context (Optional)", "root / vsys1")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trust Self-Signed Management Cert", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Switch(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        vendor,
                        name,
                        host,
                        apiPortText.toIntOrNull() ?: vendor.defaultApiPort,
                        sshPortText.toIntOrNull() ?: vendor.defaultSshPort,
                        username,
                        password,
                        apiToken,
                        allowSelfSigned,
                        extraContext
                    )
                },
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                modifier = Modifier.testTag("fw_dialog_save_btn")
            ) {
                Text("Save to Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = CardBackground
    )
}

@Composable
private fun FwField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan,
            unfocusedBorderColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
