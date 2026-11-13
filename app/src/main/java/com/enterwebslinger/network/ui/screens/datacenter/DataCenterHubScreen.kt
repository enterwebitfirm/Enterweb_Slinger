// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/datacenter/DataCenterHubScreen.kt ===
package com.enterwebslinger.network.ui.screens.datacenter

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.BootDevice
import com.enterwebslinger.network.domain.model.EndpointRegistrationStatus
import com.enterwebslinger.network.domain.model.HypervisorType
import com.enterwebslinger.network.domain.model.PowerAction
import com.enterwebslinger.network.domain.model.ServerPowerState
import com.enterwebslinger.network.domain.model.VmPowerState
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.DividerColor
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCenterHubScreen(
    viewModel: DataCenterViewModel,
    onBack: (() -> Unit)? = null
) {
    val selectedSection by viewModel.selectedSection.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("datacenter_hub_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Data Center & Hypervisors",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "DMTF Redfish, IPMI v2.0, VMware, oVirt, Hyper-V & Cisco UC",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CyberCyan,
                            fontSize = 10.sp
                        )
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("dc_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        TabRow(
            selectedTabIndex = selectedSection.coerceIn(0, 2),
            containerColor = CardBackground,
            contentColor = CyberCyan,
            modifier = Modifier.fillMaxWidth().testTag("datacenter_tab_row")
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = { viewModel.setSelectedSection(0) },
                text = { Text("Bare-Metal OOB", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Dns, contentDescription = "Bare-Metal OOB", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_bare_metal_oob")
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { viewModel.setSelectedSection(1) },
                text = { Text("Hypervisors & VMs", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Computer, contentDescription = "Hypervisors and VMs", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_hypervisors_vms")
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { viewModel.setSelectedSection(2) },
                text = { Text("Cisco UC & VoIP", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Phone, contentDescription = "Cisco UC and VoIP", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_cisco_uc")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSection) {
                0 -> BareMetalOobSection(viewModel)
                1 -> HypervisorsAndVmsSection(viewModel)
                2 -> CiscoUcSection(viewModel)
            }
        }
    }
}

// =========================================================================
// 1. Bare Metal Out-of-Band (Redfish & IPMI v2.0)
// =========================================================================

@Composable
fun BareMetalOobSection(viewModel: DataCenterViewModel) {
    var bmcHost by remember { mutableStateOf("") }
    var httpsPort by remember { mutableStateOf("443") }
    var ipmiPort by remember { mutableStateOf("623") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var systemId by remember { mutableStateOf("1") }
    var ignoreSslErrors by remember { mutableStateOf(true) }

    val redfishSummary by viewModel.redfishSummary.collectAsState()
    val asfResult by viewModel.asfResult.collectAsState()
    val ipmiAuthCapsResult by viewModel.ipmiAuthCapsResult.collectAsState()
    val isLoading by viewModel.isOobLoading.collectAsState()
    val statusMsg by viewModel.oobStatusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .testTag("oob_section")
    ) {
        // Target Connection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "BMC Target Configuration (iDRAC, iLO, XClarity, Supermicro)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = bmcHost,
                        onValueChange = { bmcHost = it },
                        label = { Text("BMC Host / IP") },
                        placeholder = { Text("e.g. idrac.example.local") },
                        modifier = Modifier.weight(1.8f).testTag("bmc_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = httpsPort,
                        onValueChange = { httpsPort = it },
                        label = { Text("HTTPS") },
                        modifier = Modifier.weight(0.8f).testTag("bmc_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = ipmiPort,
                        onValueChange = { ipmiPort = it },
                        label = { Text("IPMI") },
                        modifier = Modifier.weight(0.8f).testTag("bmc_ipmi_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("BMC Username") },
                        placeholder = { Text("e.g. Administrator") },
                        modifier = Modifier.weight(1f).testTag("bmc_user_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("bmc_pass_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = systemId,
                        onValueChange = { systemId = it },
                        label = { Text("Sys ID") },
                        placeholder = { Text("1") },
                        modifier = Modifier.weight(0.75f).testTag("bmc_system_id_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ignore Self-Signed BMC TLS Certificates",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Switch(
                        checked = ignoreSslErrors,
                        onCheckedChange = { ignoreSslErrors = it },
                        modifier = Modifier.testTag("bmc_ignore_tls_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val p = httpsPort.toIntOrNull() ?: 443
                        viewModel.fetchRedfishSummary(bmcHost, p, user, pass, systemId, ignoreSslErrors)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync_redfish_button"),
                    enabled = !isLoading && bmcHost.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Sync Redfish Telemetry (HTTPS)", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val p = ipmiPort.toIntOrNull() ?: 623
                            viewModel.pingAsfPresence(bmcHost, p)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ping_asf_button"),
                        enabled = !isLoading && bmcHost.isNotBlank()
                    ) {
                        Text("ASF Ping (UDP $ipmiPort)", color = CyberCyan, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val p = ipmiPort.toIntOrNull() ?: 623
                            viewModel.queryIpmiAuthCapabilities(bmcHost, p)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ipmi_auth_caps_button"),
                        enabled = !isLoading && bmcHost.isNotBlank()
                    ) {
                        Text("IPMI Auth Caps", color = CyberCyan, fontSize = 11.sp)
                    }
                }

                if (statusMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isErr = statusMsg?.contains("Error", true) == true ||
                        statusMsg?.contains("Failed", true) == true ||
                        statusMsg?.contains("unreachable", true) == true
                    Text(
                        text = statusMsg ?: "",
                        color = if (isErr) StatusError else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ASF Ping Result
        asfResult?.let { asf ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("asf_result_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (asf.isAlive) StatusConnected else StatusError, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IPMI ASF Presence: ${asf.oemType}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "IANA Enterprise: ${asf.ianaEnterpriseId} | RTT: ${asf.responseTimeMs} ms",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (asf.rawHex.isNotBlank()) {
                        Text(text = asf.rawHex.take(24) + "...", color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        ipmiAuthCapsResult?.let { caps ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("ipmi_auth_caps_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "IPMI v1.5 / RMCP+ Channel Auth Capabilities",
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = caps,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Redfish Hardware Summary Card
        redfishSummary?.let { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("redfish_summary_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${s.manufacturer} ${s.model}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Service Tag / Serial: ${s.serialNumber} | BIOS: ${s.biosVersion}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        val powerColor = when (s.powerState) {
                            ServerPowerState.ON -> StatusConnected
                            ServerPowerState.OFF -> StatusError
                            else -> StatusWarning
                        }
                        Box(
                            modifier = Modifier
                                .background(powerColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = s.powerState.name,
                                color = powerColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Out-of-Band Power Action Buttons
                    Text("Out-of-Band Power Operations (ResetType)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.executePowerAction(bmcHost, p, user, pass, systemId, PowerAction.ON, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("power_on_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Power On", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.executePowerAction(bmcHost, p, user, pass, systemId, PowerAction.GRACEFUL_SHUTDOWN, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("graceful_shutdown_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Graceful Shutdown", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.executePowerAction(bmcHost, p, user, pass, systemId, PowerAction.POWER_CYCLE, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("power_cycle_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Power Cycle", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.executePowerAction(bmcHost, p, user, pass, systemId, PowerAction.FORCE_OFF, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("force_off_button")
                        ) {
                            Text("Force Off", color = StatusError, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // One-Time Boot Override
                    Text("One-Time Boot Override", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.setBootOverride(bmcHost, p, user, pass, systemId, BootDevice.PXE, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Boot to PXE Network", fontSize = 11.sp, color = CyberCyan)
                        }
                        OutlinedButton(
                            onClick = {
                                val p = httpsPort.toIntOrNull() ?: 443
                                viewModel.setBootOverride(bmcHost, p, user, pass, systemId, BootDevice.BIOS_SETUP, ignoreSslErrors)
                            },
                            enabled = bmcHost.isNotBlank() && !isLoading,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Boot to BIOS Setup", fontSize = 11.sp, color = CyberCyan)
                        }
                    }

                    // Thermal Sensors
                    if (s.thermalSensors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Thermal Sensors (Chassis / Thermal)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        s.thermalSensors.take(6).forEach { sensor ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Thermostat, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = sensor.name, color = TextPrimary, fontSize = 11.sp)
                                }
                                Text(
                                    text = "${sensor.currentCelsius}°C (Crit: ${sensor.criticalCelsius}°C)",
                                    color = if (sensor.currentCelsius > 75) StatusWarning else StatusConnected,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Fans
                    if (s.fanSensors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Fan Tachometers", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.fanSensors.take(8).forEach { fan ->
                                Box(
                                    modifier = Modifier.background(DarkBackground, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${fan.name}: ${fan.rpm} RPM", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    // System Event Log (SEL)
                    if (s.selEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("System Event Log (SEL Entries)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        s.selEntries.take(4).forEach { sel ->
                            Text(
                                text = "[${sel.timestamp}] [${sel.severity}] ${sel.message}",
                                color = if (sel.severity.contains("Critical", true)) StatusError else TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. Hypervisors & Cloud Virtualization (VMware, RHEV/oVirt, Hyper-V, VBox)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HypervisorsAndVmsSection(viewModel: DataCenterViewModel) {
    var hypervisor by remember { mutableStateOf(HypervisorType.VMWARE_VCENTER_REST) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var wqlQuery by remember { mutableStateOf("") }
    var winRmUseTls by remember { mutableStateOf(false) }
    var expandedHyp by remember { mutableStateOf(false) }

    val vms by viewModel.vms.collectAsState()
    val hosts by viewModel.hosts.collectAsState()
    val winRmResult by viewModel.winRmResult.collectAsState()
    val isLoading by viewModel.isVirtLoading.collectAsState()
    val statusMsg by viewModel.virtStatusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .testTag("hypervisors_section")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Hypervisor & Cluster Management Engine",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedHyp,
                    onExpandedChange = { expandedHyp = !expandedHyp }
                ) {
                    OutlinedTextField(
                        value = hypervisor.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hypervisor Connector") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedHyp) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedHyp,
                        onDismissRequest = { expandedHyp = false }
                    ) {
                        HypervisorType.entries.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h.displayName) },
                                onClick = {
                                    hypervisor = h
                                    port = h.defaultPort.toString()
                                    winRmUseTls = (h.defaultPort == 5986)
                                    expandedHyp = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Hypervisor Host / IP") },
                        placeholder = { Text("e.g. vcenter.example.local") },
                        modifier = Modifier.weight(2f).testTag("virt_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).testTag("virt_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username") },
                        placeholder = { Text("e.g. admin@domain") },
                        modifier = Modifier.weight(1f).testTag("virt_user_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("virt_pass_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                if (hypervisor == HypervisorType.MS_HYPERV_WINRM) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = wqlQuery,
                        onValueChange = { wqlQuery = it },
                        label = { Text("WQL / WMI Query") },
                        placeholder = { Text("e.g. SELECT ElementName, EnabledState FROM Msvm_ComputerSystem") },
                        modifier = Modifier.fillMaxWidth().testTag("virt_wql_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Use HTTPS / TLS (WinRM Port 5986)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Switch(
                            checked = winRmUseTls,
                            onCheckedChange = { enabled ->
                                winRmUseTls = enabled
                                if (enabled && port == "5985") port = "5986"
                                else if (!enabled && port == "5986") port = "5985"
                            },
                            modifier = Modifier.testTag("winrm_tls_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = port.toIntOrNull() ?: hypervisor.defaultPort
                        when (hypervisor) {
                            HypervisorType.VMWARE_VCENTER_REST -> viewModel.fetchVcenterData(host, p, user, pass)
                            HypervisorType.VMWARE_ESXI_SOAP -> viewModel.queryEsxiSoap(host, p)
                            HypervisorType.RH_OVIRT -> viewModel.fetchOvirtData(host, p, user, pass)
                            HypervisorType.MS_HYPERV_WINRM -> viewModel.executeWinRmQuery(host, p, user, pass, wqlQuery, useTls = winRmUseTls)
                            HypervisorType.ORACLE_VBOX -> viewModel.queryVirtualBox(host, p, user, pass)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("virt_connect_button"),
                    enabled = !isLoading && host.isNotBlank() &&
                        (hypervisor != HypervisorType.MS_HYPERV_WINRM || wqlQuery.isNotBlank())
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Connect & Query Hypervisor API", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                if (statusMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isErr = statusMsg?.contains("Error", true) == true || statusMsg?.contains("failed", true) == true
                    Text(text = statusMsg ?: "", color = if (isErr) StatusError else TextMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ESXi Hosts (if any)
        if (hosts.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("hosts_list_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ESXi Cluster Hosts (${hosts.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    hosts.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(h.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${h.model} (${h.status})", color = TextMuted, fontSize = 10.sp)
                            }
                            Text(
                                text = "CPU: ${(h.cpuPercent * 100).toInt()}% | RAM: ${(h.ramPercent * 100).toInt()}%",
                                color = StatusConnected,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Virtual Machines List
        if (vms.isNotEmpty()) {
            Text("Virtual Machine Inventory (${vms.size} VMs)", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            vms.forEach { vm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("vm_card_${vm.id}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(vm.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${vm.cpuCount} vCPU | ${vm.memoryMb} MB RAM | ${vm.guestOs}", color = TextMuted, fontSize = 10.sp)
                            }

                            val pColor = when (vm.powerState) {
                                VmPowerState.POWERED_ON -> StatusConnected
                                VmPowerState.POWERED_OFF -> StatusError
                                VmPowerState.SUSPENDED -> StatusWarning
                                else -> TextMuted
                            }
                            Text(
                                text = vm.powerState.name,
                                color = pColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (vm.powerState != VmPowerState.POWERED_ON) {
                                OutlinedButton(
                                    onClick = {
                                        val p = port.toIntOrNull() ?: 443
                                        viewModel.controlVcenterVm(host, p, user, pass, vm.id, "start")
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Start", fontSize = 11.sp, color = StatusConnected)
                                }
                            }
                            if (vm.powerState == VmPowerState.POWERED_ON) {
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        val p = port.toIntOrNull() ?: 443
                                        viewModel.controlVcenterVm(host, p, user, pass, vm.id, "stop")
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Stop", fontSize = 11.sp, color = StatusError)
                                }
                            }
                        }
                    }
                }
            }
        }

        // WinRM WMI Query Output
        winRmResult?.let { res ->
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("winrm_result_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("WinRM / WS-Management Result (${res.executionTimeMs} ms)", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color(0xFF0A0F14), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (res.isSuccess) res.stdout else res.stderr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (res.isSuccess) TextPrimary else StatusError
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. Cisco Unified Communications & Collaboration
// =========================================================================

@Composable
fun CiscoUcSection(viewModel: DataCenterViewModel) {
    var cucmHost by remember { mutableStateOf("") }
    var cucmPort by remember { mutableStateOf("8443") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val endpoints by viewModel.phoneEndpoints.collectAsState()
    val sipTrunks by viewModel.sipTrunks.collectAsState()
    val callMos by viewModel.callQualityMetrics.collectAsState()
    val dsps by viewModel.gatewayDsps.collectAsState()
    val isLoading by viewModel.isUcLoading.collectAsState()
    val statusMsg by viewModel.ucStatusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .testTag("cisco_uc_section")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Cisco Prime Collaboration & CUCM Telemetry",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Query voice/video endpoints, SIP trunks, real-time call MOS scores, and Voice Gateway DSP utilization.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cucmHost,
                        onValueChange = { cucmHost = it },
                        label = { Text("CUCM / Prime Host") },
                        placeholder = { Text("e.g. uc-pub.example.local") },
                        modifier = Modifier.weight(2f).testTag("cucm_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = cucmPort,
                        onValueChange = { cucmPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).testTag("cucm_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("AXL / API User") },
                        placeholder = { Text("e.g. axl-service") },
                        modifier = Modifier.weight(1f).testTag("cucm_user_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("cucm_pass_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = cucmPort.toIntOrNull() ?: 8443
                        viewModel.loadDefaultUcTelemetry(cucmHost, p, user, pass)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync_cucm_button"),
                    enabled = !isLoading && cucmHost.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Synchronize Cisco UC Telemetry", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                if (statusMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isErr = statusMsg?.contains("Error", true) == true
                    Text(
                        text = statusMsg ?: "",
                        color = if (isErr) StatusError else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Call Quality & MOS Scores
        if (callMos.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("call_mos_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Active Call Quality & MOS Telemetry", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    callMos.forEach { call ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${call.caller} -> ${call.callee}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Jitter: ${call.jitterMs}ms | Latency: ${call.latencyMs}ms | Loss: ${call.packetLossPercent}% (${call.codec})", color = TextMuted, fontSize = 10.sp)
                            }
                            Text(
                                text = "MOS ${call.mosScore}",
                                color = if (call.isGoodQuality) StatusConnected else StatusWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Voice Gateway DSP Channels
        if (dsps.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("dsp_util_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Voice Gateway DSP Utilization (PVDM4)", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    dsps.forEach { dsp ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dsp.gatewayName, color = TextPrimary, fontSize = 11.sp)
                                Text("${dsp.activeChannels} / ${dsp.totalChannels} ch (${(dsp.utilizationPercent * 100).toInt()}%)", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { dsp.utilizationPercent },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = if (dsp.utilizationPercent > 0.8f) StatusWarning else StatusConnected,
                                trackColor = DarkBackground
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // SIP Trunks
        if (sipTrunks.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("sip_trunks_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("SIP Trunks (${sipTrunks.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    sipTrunks.forEach { trunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(trunk.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${trunk.destinationAddress}:${trunk.port} (${trunk.serviceState})", color = TextMuted, fontSize = 10.sp)
                            }
                            Text(
                                text = "${trunk.status} (${trunk.activeCallCount} calls)",
                                color = if (trunk.status.contains("In Service")) StatusConnected else StatusError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Phone & Video Endpoints
        if (endpoints.isNotEmpty()) {
            Text("Registered Phone & Video Endpoints (${endpoints.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            endpoints.forEach { ep ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("endpoint_${ep.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ep.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ext: ${ep.directoryNumber}", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${ep.model} | User: ${ep.primaryUser} | IP: ${ep.ipAddress}", color = TextMuted, fontSize = 10.sp)
                        }

                        val regColor = when (ep.status) {
                            EndpointRegistrationStatus.REGISTERED -> StatusConnected
                            EndpointRegistrationStatus.UNREGISTERED -> StatusError
                            else -> StatusWarning
                        }
                        Text(
                            text = ep.status.name,
                            color = regColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
