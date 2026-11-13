// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/CoreProtocolsTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.enterwebslinger.network.domain.model.DhcpServerOffer
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
import java.util.Locale

@Composable
fun CoreProtocolsTab(viewModel: EnterpriseViewModel) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
            .testTag("core_protocols_tab")
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            modifier = Modifier.fillMaxWidth().testTag("core_protocols_subtabs")
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("DHCP Rogue Scan", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_dhcp_rogue")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("ToD (37) & NTP", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_tod_ntp")
            )
            Tab(
                selected = subTab == 2,
                onClick = { subTab = 2 },
                text = { Text("SMTP Diag (25)", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_smtp_diag")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (subTab) {
                0 -> DhcpRogueScannerView(viewModel)
                1 -> TodAndNtpView(viewModel)
                2 -> SmtpDiagnosticsView(viewModel)
            }
        }
    }
}

@Composable
fun DhcpRogueScannerView(viewModel: EnterpriseViewModel) {
    val offers by viewModel.dhcpOffers.collectAsState()
    val isScanning by viewModel.isDhcpScanning.collectAsState()
    val dhcpDiagnostic by viewModel.dhcpDiagnostic.collectAsState()
    var authorizedGw by remember { mutableStateOf("") }
    var useInform by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dhcp_rogue_view")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "DHCP Rogue Server & Lease Inspector (RFC 2131)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Broadcasts a raw UDP 67/68 DHCPDISCOVER or DHCPINFORM frame with magic cookie 0x63825363.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = authorizedGw,
                    onValueChange = { authorizedGw = it },
                    label = { Text("Authorized Gateway IP (Optional override)") },
                    placeholder = { Text("Leave blank to auto-use active network gateway") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dhcp_auth_gw_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use DHCPINFORM (Non-lease probe)", color = TextPrimary, fontSize = 12.sp)
                    Switch(
                        checked = useInform,
                        onCheckedChange = { useInform = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.runDhcpRogueScan(authorizedGw.takeIf { it.isNotBlank() }, useInform = useInform) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dhcp_discover_button"),
                    enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isScanning) "Broadcasting ${if (useInform) "DHCPINFORM" else "DHCPDISCOVER"}..." else "Broadcast ${if (useInform) "DHCP INFORM" else "DHCP DISCOVER"} (UDP 67/68)",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!dhcpDiagnostic.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dhcpDiagnostic ?: "",
                        color = if (offers.isEmpty()) StatusWarning else StatusConnected,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (offers.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(dhcpDiagnostic ?: "No DHCP servers discovered yet. Press Broadcast to scan.", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).testTag("dhcp_offers_list")) {
                items(offers) { offer ->
                    DhcpOfferCard(offer)
                }
            }
        }
    }
}

@Composable
fun DhcpOfferCard(offer: DhcpServerOffer) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("dhcp_offer_${offer.serverIp}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (offer.isRogue) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (offer.isRogue) StatusError else StatusConnected,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DHCP Server: ${offer.serverIp}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = if (offer.isRogue) "ROGUE SERVER!" else "AUTHORIZED",
                    color = if (offer.isRogue) StatusError else StatusConnected,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("• Offered IP: ${offer.offeredIp}", color = CyberCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("• Subnet Mask (Opt 1): ${offer.subnetMask}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("• Routers / Gateways (Opt 3): ${offer.routers.joinToString(", ")}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("• DNS Servers (Opt 6): ${offer.dnsServers.joinToString(", ").ifBlank { "None" }}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("• Domain Name (Opt 15): ${offer.domainName}", color = TextSecondary, fontSize = 11.sp)
                Text("• Lease Time (Opt 51): ${offer.leaseTimeSeconds} seconds (${offer.leaseTimeSeconds / 3600}h)", color = TextSecondary, fontSize = 11.sp)
                if (offer.tftpServer != "N/A") {
                    Text("• TFTP Server (Opt 66): ${offer.tftpServer}", color = TextSecondary, fontSize = 11.sp)
                }
                if (offer.voipTftpServer != "N/A") {
                    Text("• VoIP Cisco TFTP (Opt 150): ${offer.voipTftpServer}", color = TextSecondary, fontSize = 11.sp)
                }
                Text("• Response Latency: ${offer.responseTimeMs} ms", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun TodAndNtpView(viewModel: EnterpriseViewModel) {
    var todHost by remember { mutableStateOf("") }
    var todPort by remember { mutableStateOf("37") }
    var todUseUdp by remember { mutableStateOf(false) }
    val todResult by viewModel.todResult.collectAsState()
    val isTodLoading by viewModel.isTodLoading.collectAsState()

    var ntpHost by remember { mutableStateOf("") }
    val ntpResult by viewModel.ntpResult.collectAsState()
    val isNtpLoading by viewModel.isNtpLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("tod_ntp_view")
    ) {
        // Time of Day (RFC 868)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Time of Day Protocol (RFC 868 - Port 37)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Receives a raw 32-bit unsigned binary integer (seconds since Jan 1 1900 00:00:00 UTC) and computes epoch offset against Unix 1970.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = todHost,
                        onValueChange = { todHost = it },
                        label = { Text("ToD Host") },
                        placeholder = { Text("e.g. time.example.org") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("tod_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = todPort,
                        onValueChange = { todPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("tod_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transport: ${if (todUseUdp) "UDP Datagram" else "TCP Stream"}", color = TextPrimary, fontSize = 12.sp)
                    Switch(
                        checked = todUseUdp,
                        onCheckedChange = { todUseUdp = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = todPort.toIntOrNull() ?: 37
                        viewModel.queryTimeOfDay(todHost, p, useUdp = todUseUdp)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tod_query_button"),
                    enabled = !isTodLoading && todHost.isNotBlank()
                ) {
                    if (isTodLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Query ToD (${if (todUseUdp) "UDP" else "TCP"} $todPort)", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                todResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (res.isSuccess) {
                        Text("32-bit Raw Seconds: ${res.raw32BitSeconds}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Epoch Difference: -2,208,988,800 sec", color = TextMuted, fontSize = 10.sp)
                        Text("Decoded UTC: ${res.formattedUtcTime}", color = StatusConnected, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Clock Drift: ${res.clockOffsetMs} ms (RTT: ${res.latencyMs} ms)", color = CyberCyan, fontSize = 11.sp)
                    } else {
                        Text("Error: ${res.errorMessage}", color = StatusError, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SNTP (RFC 4330)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SNTP Network Time Protocol (RFC 4330 - Port 123)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "UDP 123 telemetry query. Computes clock stratum, leap indicator, reference identifier, round-trip delay, and microsecond offset.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ntpHost,
                    onValueChange = { ntpHost = it },
                    label = { Text("NTP Server") },
                    placeholder = { Text("e.g. pool.ntp.org") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ntp_host_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.querySntp(ntpHost) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ntp_query_button"),
                    enabled = !isNtpLoading && ntpHost.isNotBlank()
                ) {
                    if (isNtpLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Query SNTP (UDP 123)", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                ntpResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (res.isSuccess) {
                        Text("Stratum: ${res.stratum} (Leap: ${res.leapIndicator}, Ref: ${res.referenceId})", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Transmit Time: ${res.transmitTimeFormatted}", color = StatusConnected, fontSize = 11.sp)
                        Text("Round-Trip Delay: ${String.format(Locale.US, "%.2f", res.roundTripDelayMs)} ms", color = CyberCyan, fontSize = 11.sp)
                        Text("Clock Offset: ${String.format(Locale.US, "%.2f", res.clockOffsetMs)} ms", color = if (kotlin.math.abs(res.clockOffsetMs) > 1000) StatusWarning else StatusConnected, fontSize = 11.sp)
                    } else {
                        Text("Error: ${res.errorMessage}", color = StatusError, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SmtpDiagnosticsView(viewModel: EnterpriseViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("587") }
    var heloHost by remember { mutableStateOf("") }
    var testRelay by remember { mutableStateOf(true) }

    val result by viewModel.smtpResult.collectAsState()
    val isLoading by viewModel.isSmtpLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("smtp_diagnostics_view")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SMTP Mail Server & Relay Diagnostics (RFC 5321 / 3207)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Banner grabbing, EHLO capability enumeration, STARTTLS encryption verification, and open-relay vulnerability testing.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("SMTP Host") },
                        placeholder = { Text("e.g. mail.example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("smtp_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port (25/587/465)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("smtp_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = heloHost,
                    onValueChange = { heloHost = it },
                    label = { Text("EHLO Client Domain") },
                    placeholder = { Text("e.g. localhost") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("smtp_helo_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Test Open Relay (MAIL FROM / RCPT TO)", color = TextPrimary, fontSize = 12.sp)
                    Switch(
                        checked = testRelay,
                        onCheckedChange = { testRelay = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = port.toIntOrNull() ?: 25
                        viewModel.runSmtpDiagnostics(host, p, testRelay, heloHost.ifBlank { "localhost" })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("smtp_audit_button"),
                    enabled = !isLoading && host.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Execute SMTP Audit", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        result?.let { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("smtp_result_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (res.isSuccess) "SMTP Audit Completed" else "SMTP Audit Failed",
                            color = if (res.isSuccess) StatusConnected else StatusError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text("${res.transactionLatencyMs} ms", color = TextMuted, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Banner: ${res.banner}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (res.startTlsSupported) "✓ STARTTLS Supported" else "✗ No STARTTLS",
                            color = if (res.startTlsSupported) StatusConnected else StatusWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (res.openRelayVulnerable) "⚠ OPEN RELAY VULNERABLE!" else "✓ Open Relay Rejected (Secure)",
                            color = if (res.openRelayVulnerable) StatusError else StatusConnected,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Protocol Transcript:", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF0A0F14), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            res.transcriptLogs.forEach { log ->
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = when {
                                        log.startsWith(">>") -> CyberCyan
                                        log.startsWith("<<") -> TextPrimary
                                        log.startsWith("[WARNING") || log.contains("VULNERABLE") -> StatusError
                                        log.startsWith("[PASSED") -> StatusConnected
                                        else -> TextMuted
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
