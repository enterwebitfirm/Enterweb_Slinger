// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/SnmpMonitorTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.enterwebslinger.network.domain.model.SnmpAuthProtocol
import com.enterwebslinger.network.domain.model.SnmpInterfaceEntry
import com.enterwebslinger.network.domain.model.SnmpPrivProtocol
import com.enterwebslinger.network.domain.model.SnmpSecurityLevel
import com.enterwebslinger.network.domain.model.SnmpTrapRecord
import com.enterwebslinger.network.domain.model.SnmpVersion
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnmpMonitorTab(viewModel: EnterpriseViewModel) {
    val config by viewModel.snmpConfig.collectAsState()
    val isLoading by viewModel.snmpLoading.collectAsState()
    val mibSummary by viewModel.snmpMibSummary.collectAsState()
    val lastResult by viewModel.snmpLastQueryResult.collectAsState()
    val walkResults by viewModel.snmpWalkResults.collectAsState()
    val statusMsg by viewModel.snmpStatusMessage.collectAsState()
    val traps by viewModel.trapRecords.collectAsState()
    val isTrapRunning by viewModel.isTrapReceiverRunning.collectAsState()
    val trapErrorMsg by viewModel.trapErrorMessage.collectAsState()

    var customOid by remember { mutableStateOf("1.3.6.1.2.1.1.1.0") }
    var trapPortText by remember { mutableStateOf("1162") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SNMP Target Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("snmp_config_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Router, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SNMP Agent Target",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = config.host,
                            onValueChange = { viewModel.updateSnmpConfig(config.copy(host = it)) },
                            label = { Text("Agent Host / IP") },
                            placeholder = { Text("e.g. 192.0.2.1") },
                            modifier = Modifier.weight(2f).testTag("snmp_host_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = config.port.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: 161
                                viewModel.updateSnmpConfig(config.copy(port = p))
                            },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f).testTag("snmp_port_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                        Text("Ver: ", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary), modifier = Modifier.align(Alignment.CenterVertically))
                        SnmpVersion.entries.forEach { v ->
                            FilterChip(
                                selected = config.version == v,
                                onClick = { viewModel.updateSnmpConfig(config.copy(version = v)) },
                                label = { Text(v.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = Color(0xFF00363D)
                                ),
                                modifier = Modifier.testTag("snmp_ver_${v.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (config.version != SnmpVersion.V3) {
                        OutlinedTextField(
                            value = config.community,
                            onValueChange = { viewModel.updateSnmpConfig(config.copy(community = it)) },
                            label = { Text("Community String") },
                            modifier = Modifier.fillMaxWidth().testTag("snmp_community_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("SNMPv3 USM Credentials", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = config.securityName,
                                onValueChange = { viewModel.updateSnmpConfig(config.copy(securityName = it)) },
                                label = { Text("Security Name (USM User)") },
                                placeholder = { Text("e.g. snmpv3admin") },
                                modifier = Modifier.fillMaxWidth().testTag("snmp_v3_user_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = CardBackground
                                ),
                                singleLine = true
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SnmpSecurityLevel.entries.forEach { lvl ->
                                    FilterChip(
                                        selected = config.securityLevel == lvl,
                                        onClick = {
                                            val updatedAuth = if (lvl == SnmpSecurityLevel.NO_AUTH_NO_PRIV) SnmpAuthProtocol.NONE
                                            else if (config.authProtocol == SnmpAuthProtocol.NONE) SnmpAuthProtocol.SHA1 else config.authProtocol
                                            val updatedPriv = if (lvl != SnmpSecurityLevel.AUTH_PRIV) SnmpPrivProtocol.NONE
                                            else if (config.privProtocol == SnmpPrivProtocol.NONE) SnmpPrivProtocol.AES128 else config.privProtocol
                                            viewModel.updateSnmpConfig(
                                                config.copy(
                                                    securityLevel = lvl,
                                                    authProtocol = updatedAuth,
                                                    privProtocol = updatedPriv
                                                )
                                            )
                                        },
                                        label = { Text(lvl.label, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan,
                                            selectedLabelColor = Color(0xFF00363D)
                                        )
                                    )
                                }
                            }

                            if (config.securityLevel != SnmpSecurityLevel.NO_AUTH_NO_PRIV) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(SnmpAuthProtocol.MD5, SnmpAuthProtocol.SHA1, SnmpAuthProtocol.SHA256).forEach { auth ->
                                        FilterChip(
                                            selected = config.authProtocol == auth,
                                            onClick = { viewModel.updateSnmpConfig(config.copy(authProtocol = auth)) },
                                            label = { Text("Auth: ${auth.name}", fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = StatusConnected,
                                                selectedLabelColor = Color(0xFF003314)
                                            )
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = config.authPassword,
                                    onValueChange = { viewModel.updateSnmpConfig(config.copy(authPassword = it)) },
                                    label = { Text("Auth Passphrase (≥8 chars)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth().testTag("snmp_v3_auth_pass_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = CardBackground
                                    ),
                                    singleLine = true
                                )
                            }

                            if (config.securityLevel == SnmpSecurityLevel.AUTH_PRIV) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(SnmpPrivProtocol.DES, SnmpPrivProtocol.AES128, SnmpPrivProtocol.AES256).forEach { priv ->
                                        FilterChip(
                                            selected = config.privProtocol == priv,
                                            onClick = { viewModel.updateSnmpConfig(config.copy(privProtocol = priv)) },
                                            label = { Text("Priv: ${priv.name}", fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = StatusWarning,
                                                selectedLabelColor = Color(0xFF1F1600)
                                            )
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = config.privPassword,
                                    onValueChange = { viewModel.updateSnmpConfig(config.copy(privPassword = it)) },
                                    label = { Text("Privacy Passphrase (≥8 chars)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth().testTag("snmp_v3_priv_pass_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = CardBackground
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.querySnmpMibSummary() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("snmp_query_mib_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            enabled = !isLoading && config.host.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Poll MIB-II", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.walkSnmpSubtree("1.3.6.1.2.1.2.2.1.2") },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("snmp_walk_button"),
                            enabled = !isLoading && config.host.isNotBlank()
                        ) {
                            Icon(imageVector = Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Walk ifTable", fontSize = 12.sp)
                        }
                    }

                    if (statusMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isErr = statusMsg?.contains("failed", ignoreCase = true) == true ||
                            statusMsg?.contains("error", ignoreCase = true) == true
                        Text(
                            text = statusMsg ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isErr) StatusError else CyberCyan,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        // 2. Custom OID Query Bar & Standard MIB-II Presets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("snmp_oid_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OID Browser & Single GET",
                        style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customOid,
                            onValueChange = { customOid = it },
                            label = { Text("Object Identifier (OID)") },
                            modifier = Modifier.weight(1f).testTag("snmp_oid_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.querySnmpOid(customOid) },
                            enabled = !isLoading && config.host.isNotBlank() && customOid.isNotBlank(),
                            modifier = Modifier.size(48.dp).background(CyberCyan, shape = CircleShape).testTag("snmp_get_button")
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Query OID", tint = Color(0xFF00363D))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "sysDescr" to "1.3.6.1.2.1.1.1.0",
                            "sysUpTime" to "1.3.6.1.2.1.1.3.0",
                            "sysName" to "1.3.6.1.2.1.1.5.0",
                            "hrCpuLoad" to "1.3.6.1.2.1.25.3.3.1.2.1",
                            "ifInOctets.1" to "1.3.6.1.2.1.2.2.1.10.1"
                        )
                        presets.forEach { (name, oid) ->
                            FilterChip(
                                selected = customOid == oid,
                                onClick = {
                                    customOid = oid
                                    if (config.host.isNotBlank()) {
                                        viewModel.querySnmpOid(oid)
                                    }
                                },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = Color(0xFF00363D)
                                )
                            )
                        }
                    }

                    if (lastResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Response (RTT: ${lastResult?.responseTimeMs}ms):", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                lastResult?.varBinds?.forEach { vb ->
                                    Text("${vb.oid} = [${vb.syntax}] ${vb.value}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    if (walkResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Walk Results (${walkResults.size}):", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                walkResults.take(20).forEach { vb ->
                                    Text("${vb.oid} = ${vb.value}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. MIB-II Device Summary Card
        if (mibSummary != null) {
            item {
                val sum = mibSummary!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("snmp_mib_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = StatusConnected)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sum.sysName, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(sum.sysDescr, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Uptime", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Text(sum.sysUpTime, style = MaterialTheme.typography.bodyMedium.copy(color = CyberCyan, fontWeight = FontWeight.SemiBold))
                            }
                            Column {
                                Text("Contact", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Text(sum.sysContact, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                            }
                            Column {
                                Text("CPU Load", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Text("${sum.cpuUtilization ?: 0}%", style = MaterialTheme.typography.bodyMedium.copy(color = StatusConnected, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        // 4. Live Interface Table
        if (mibSummary?.interfaces?.isNotEmpty() == true) {
            item {
                Text(
                    text = "Interface Counters & Bandwidth",
                    style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                )
            }
            items(mibSummary!!.interfaces) { iface ->
                InterfaceCard(iface)
            }
        }

        // 5. SNMP Trap Receiver Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("snmp_trap_panel")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = if (isTrapRunning) StatusConnected else StatusWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("SNMP Trap Receiver", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                                Text("Unprivileged UDP port $trapPortText", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearTraps() }, modifier = Modifier.size(48.dp)) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Traps", tint = TextMuted)
                            }
                            Switch(
                                checked = isTrapRunning,
                                onCheckedChange = {
                                    val port = (trapPortText.toIntOrNull() ?: 1162).coerceAtLeast(1024)
                                    viewModel.toggleTrapReceiver(port)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00363D),
                                    checkedTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("snmp_trap_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trapPortText,
                        onValueChange = { if (!isTrapRunning) trapPortText = it },
                        enabled = !isTrapRunning,
                        label = { Text("Trap UDP Listen Port (1024-65535)") },
                        modifier = Modifier.fillMaxWidth().testTag("snmp_trap_port_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        )
                    )

                    if (!trapErrorMsg.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = trapErrorMsg ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = StatusError, fontSize = 11.sp),
                            modifier = Modifier.testTag("snmp_trap_error_text")
                        )
                    }

                    if (traps.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isTrapRunning) "Listening for incoming traps on UDP $trapPortText..." else "Trap receiver is idle.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Recent Received Traps (${traps.size}):", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        traps.take(10).forEach { trap ->
                            TrapItem(trap)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InterfaceCard(iface: SnmpInterfaceEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("iface_card_${iface.index}")
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
                            .background(if (iface.operStatus == "UP") StatusConnected else StatusError, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(iface.name, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(${iface.type})", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp))
                }
                Text("${iface.speedMbps} Mbps", style = MaterialTheme.typography.bodySmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("RX: ${formatBandwidth(iface.inOctetsRateBps)}", color = StatusConnected, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Total: ${iface.inOctets / (1024 * 1024)} MB", color = TextMuted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("TX: ${formatBandwidth(iface.outOctetsRateBps)}", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Total: ${iface.outOctets / (1024 * 1024)} MB", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun TrapItem(trap: SnmpTrapRecord) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(trap.timestamp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .border(1.dp, CardBackgroundElevated, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${trap.sourceIp}:${trap.sourcePort}", color = StatusWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(timeStr, color = TextMuted, fontSize = 10.sp)
            }
            Text("Trap OID: ${trap.trapOid}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (trap.varBinds.isNotEmpty()) {
                trap.varBinds.take(3).forEach { vb ->
                    Text("  ${vb.oid} = ${vb.value}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun formatBandwidth(bps: Long): String {
    return when {
        bps >= 1_000_000_000 -> "%.2f Gbps".format(bps / 1_000_000_000.0)
        bps >= 1_000_000 -> "%.2f Mbps".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.1f kbps".format(bps / 1_000.0)
        else -> "$bps bps"
    }
}
