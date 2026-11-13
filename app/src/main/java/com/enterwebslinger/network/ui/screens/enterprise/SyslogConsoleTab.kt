// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/SyslogConsoleTab.kt ===
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.enterwebslinger.network.domain.model.SyslogFacility
import com.enterwebslinger.network.domain.model.SyslogMessage
import com.enterwebslinger.network.domain.model.SyslogSeverity
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
fun SyslogConsoleTab(viewModel: EnterpriseViewModel) {
    val stats by viewModel.syslogStats.collectAsState()
    val messages by viewModel.filteredSyslogMessages.collectAsState()
    val searchQuery by viewModel.syslogSearchQuery.collectAsState()
    val severityFilter by viewModel.selectedSeverityFilter.collectAsState()
    val statusMsg by viewModel.syslogStatusMessage.collectAsState()

    var udpPortText by remember { mutableStateOf("1514") }
    var tcpPortText by remember { mutableStateOf("6514") }
    var showTestSender by remember { mutableStateOf(false) }
    var testHostText by remember { mutableStateOf("127.0.0.1") }
    var testMessageText by remember { mutableStateOf("") }
    var testSeverity by remember { mutableStateOf(SyslogSeverity.WARNING) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Syslog Service Controller Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("syslog_control_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = if (stats.isRunning) StatusConnected else CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Syslog Receiver (RFC 3164 / 5424)", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                                Text("UDP $udpPortText & TCP $tcpPortText", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearSyslogs() }, modifier = Modifier.size(48.dp)) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear logs", tint = TextMuted)
                            }
                            Switch(
                                checked = stats.isRunning,
                                onCheckedChange = {
                                    val udp = (udpPortText.toIntOrNull() ?: 1514).coerceAtLeast(1024)
                                    val tcp = (tcpPortText.toIntOrNull() ?: 6514).coerceAtLeast(1024)
                                    viewModel.toggleSyslogReceiver(udp, tcp)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00363D),
                                    checkedTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("syslog_receiver_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = udpPortText,
                            onValueChange = { if (!stats.isRunning) udpPortText = it },
                            enabled = !stats.isRunning,
                            label = { Text("UDP Port (≥1024)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = CardBackgroundElevated)
                        )
                        OutlinedTextField(
                            value = tcpPortText,
                            onValueChange = { if (!stats.isRunning) tcpPortText = it },
                            enabled = !stats.isRunning,
                            label = { Text("TCP Port (≥1024)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = CardBackgroundElevated)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeverityCounterBadge("Critical", stats.emergencyCount, Color(0xFFFF4433), modifier = Modifier.weight(1f))
                        SeverityCounterBadge("Errors", stats.errorCount, StatusError, modifier = Modifier.weight(1f))
                        SeverityCounterBadge("Warnings", stats.warningCount, StatusWarning, modifier = Modifier.weight(1f))
                        SeverityCounterBadge("Info", stats.infoCount, StatusConnected, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showTestSender = !showTestSender },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_test_syslog_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showTestSender) "Hide Generator" else "Syslog Test Dispatcher", fontSize = 11.sp)
                        }
                    }
                    if (!stats.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusError.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .testTag("syslog_error_banner")
                        ) {
                            Text(stats.errorMessage ?: "", color = StatusError, fontSize = 11.sp)
                        }
                    }
                    if (!statusMsg.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val isErr = statusMsg?.contains("failed", ignoreCase = true) == true
                        Text(
                            text = statusMsg ?: "",
                            color = if (isErr) StatusError else StatusConnected,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 2. Syslog Event Dispatcher (Expandable)
        if (showTestSender) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_sender_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Dispatch Test Syslog Datagram", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = testHostText,
                            onValueChange = { testHostText = it },
                            label = { Text("Destination Host") },
                            placeholder = { Text("127.0.0.1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = testMessageText,
                            onValueChange = { testMessageText = it },
                            label = { Text("Log Message Payload") },
                            placeholder = { Text("Enter RFC 5424 event message to transmit...") },
                            modifier = Modifier.fillMaxWidth().testTag("test_syslog_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(SyslogSeverity.CRITICAL, SyslogSeverity.ERROR, SyslogSeverity.WARNING, SyslogSeverity.INFORMATIONAL).forEach { sev ->
                                FilterChip(
                                    selected = testSeverity == sev,
                                    onClick = { testSeverity = sev },
                                    label = { Text(sev.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(sev.hexColor),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val udp = (udpPortText.toIntOrNull() ?: 1514).coerceAtLeast(1024)
                                viewModel.sendTestSyslog(
                                    host = testHostText,
                                    port = udp,
                                    isRfc5424 = true,
                                    facility = SyslogFacility.LOCAL0,
                                    severity = testSeverity,
                                    message = testMessageText
                                )
                            },
                            enabled = testHostText.isNotBlank() && testMessageText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_test_syslog_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Text("Transmit Datagram via UDP $udpPortText", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (statusMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(statusMsg ?: "", color = StatusConnected, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // 3. Search & Severity Filter Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSyslogSearchQuery(it) },
                        label = { Text("Filter logs by text / hostname...") },
                        modifier = Modifier.fillMaxWidth().testTag("syslog_search_input"),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSyslogSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = severityFilter == null,
                            onClick = { viewModel.setSeverityFilter(null) },
                            label = { Text("All (${stats.totalReceived})", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color(0xFF00363D)
                            )
                        )
                        listOf(
                            SyslogSeverity.EMERGENCY,
                            SyslogSeverity.ERROR,
                            SyslogSeverity.WARNING,
                            SyslogSeverity.INFORMATIONAL
                        ).forEach { sev ->
                            FilterChip(
                                selected = severityFilter == sev,
                                onClick = {
                                    if (severityFilter == sev) viewModel.setSeverityFilter(null) else viewModel.setSeverityFilter(sev)
                                },
                                label = { Text("≤ ${sev.label}", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(sev.hexColor),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Live Color-Coded Log Stream
        if (messages.isEmpty()) {
            item {
                Text(
                    text = if (stats.isRunning) "Listening for syslog messages on UDP $udpPortText & TCP $tcpPortText..." else "Receiver idle. Turn on switch to collect incoming logs.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        } else {
            items(messages) { msg ->
                SyslogLogCard(msg)
            }
        }
    }
}

@Composable
fun SeverityCounterBadge(label: String, count: Long, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CardBackgroundElevated, RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextMuted, fontSize = 9.sp)
            Text(count.toString(), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SyslogLogCard(msg: SyslogMessage) {
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(msg.timestamp))
    val sevColor = Color(msg.severity.hexColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1120), RoundedCornerShape(8.dp))
            .border(1.dp, sevColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(sevColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = msg.severity.label.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${msg.facility.label}.${msg.transport}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Text(timeStr, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.hostname, color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (msg.appName.isNotBlank()) {
                    Text(" [${msg.appName}${if (msg.procId.isNotBlank()) ":${msg.procId}" else ""}]", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = msg.messageContent,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            if (msg.structuredData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val sdString = msg.structuredData.entries.joinToString(" ") { "${it.key}=${it.value}" }
                Text("SD: $sdString", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
