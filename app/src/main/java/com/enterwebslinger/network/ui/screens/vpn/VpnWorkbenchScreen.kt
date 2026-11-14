// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnWorkbenchScreen.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enterwebslinger.network.domain.model.CapturedPacketRecord
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.VpnStatus
import com.enterwebslinger.network.domain.model.VpnTelemetry
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnWorkbenchScreen(
    viewModel: VpnViewModel,
    aiViewModel: AiAssistantViewModel? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val diagnosticStatus by viewModel.diagnosticStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val pcapPackets by viewModel.pcapPackets.collectAsState()
    val pcapStats by viewModel.pcapStats.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val generatedKeyPair by viewModel.generatedKeyPair.collectAsState()
    val allowedIpsCalc by viewModel.allowedIpsCalc.collectAsState()
    val qrBitmap by viewModel.qrPreviewBitmap.collectAsState()
    val qrText by viewModel.qrPreviewText.collectAsState()
    val importedPkcs12 by viewModel.importedPkcs12.collectAsState()
    val sslProbeResult by viewModel.sslProbeResult.collectAsState()
    val ikeProbeResult by viewModel.ikeProbeResult.collectAsState()
    val mssCalc by viewModel.tunnelMssCalc.collectAsState()
    val vpnAuditReport by viewModel.vpnAuditReport.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showImportWgDialog by remember { mutableStateOf(false) }
    var showImportOvpnDialog by remember { mutableStateOf(false) }
    var showAddIkev2Dialog by remember { mutableStateOf(false) }
    var pendingTunnelProfile by remember { mutableStateOf<VpnProfile?>(null) }
    var pendingIkev2Consent by remember { mutableStateOf(false) }
    var pendingPcapStart by remember { mutableStateOf(false) }
    var inspectedPacket by remember { mutableStateOf<CapturedPacketRecord?>(null) }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when {
                pendingPcapStart -> {
                    pendingPcapStart = false
                    viewModel.startPcapCapture(context)
                }
                pendingIkev2Consent -> {
                    pendingIkev2Consent = false
                    viewModel.startProvisionedIkev2()
                }
                else -> {
                    val target = pendingTunnelProfile
                    pendingTunnelProfile = null
                    viewModel.startTunnel(target)
                }
            }
        } else {
            pendingPcapStart = false
            pendingIkev2Consent = false
            pendingTunnelProfile = null
            viewModel.onVpnPermissionDenied()
        }
    }

    fun launchLocalPcapWithConsent() {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingPcapStart = true
            pendingIkev2Consent = false
            pendingTunnelProfile = null
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            viewModel.startPcapCapture(context)
        }
    }

    fun launchTunnelWithConsent(profile: VpnProfile?) {
        val target = profile ?: selectedProfile
        if (target?.protocol == VpnProtocol.IKEV2_IPSEC) {
            viewModel.provisionIkev2Profile(target).onSuccess { consentIntent ->
                if (consentIntent != null) {
                    pendingIkev2Consent = true
                    vpnConsentLauncher.launch(consentIntent)
                } else {
                    viewModel.startProvisionedIkev2()
                }
            }
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                pendingTunnelProfile = target
                pendingIkev2Consent = false
                pendingPcapStart = false
                vpnConsentLauncher.launch(prepareIntent)
            } else {
                viewModel.startTunnel(target)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = "VPN Hub", tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enterprise VPN & IPsec Lab", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vpn_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = CardBackground, contentColor = CyberCyan, edgePadding = 12.dp, divider = {}) {
            Tab(selected = selectedTab == 0, onClick = { viewModel.setSelectedTab(0) }, text = { Text("Active Tunnel") }, modifier = Modifier.testTag("tab_vpn_tunnel"))
            Tab(selected = selectedTab == 1, onClick = { viewModel.setSelectedTab(1) }, text = { Text("Profiles (${profiles.size})") }, modifier = Modifier.testTag("tab_vpn_profiles"))
            Tab(selected = selectedTab == 2, onClick = { viewModel.setSelectedTab(2) }, text = { Text("IPsec & SSL Probes") }, modifier = Modifier.testTag("tab_vpn_probes"))
            Tab(selected = selectedTab == 3, onClick = { viewModel.setSelectedTab(3) }, text = { Text("Leak & Routes") }, modifier = Modifier.testTag("tab_vpn_diagnostics"))
            Tab(selected = selectedTab == 4, onClick = { viewModel.setSelectedTab(4) }, text = { Text("PCAP (${pcapPackets.size})") }, modifier = Modifier.testTag("tab_vpn_pcap"))
        }

        diagnosticStatus?.let { status ->
            Surface(
                color = CardBackgroundElevated,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearDiagnosticStatus() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (selectedTab) {
                0 -> {
                    item {
                        TunnelMasterCard(
                            telemetry = telemetry,
                            activeProfile = selectedProfile,
                            onToggleTunnel = {
                                if (telemetry.status == VpnStatus.CONNECTED || telemetry.status == VpnStatus.CONNECTING) {
                                    viewModel.stopTunnel()
                                } else {
                                    launchTunnelWithConsent(selectedProfile)
                                }
                            }
                        )
                    }
                    item {
                        ThroughputGaugeCard(
                            txBytesPerSec = telemetry.throughputOutBps,
                            rxBytesPerSec = telemetry.throughputInBps,
                            totalTxBytes = telemetry.bytesOut,
                            totalRxBytes = telemetry.bytesIn
                        )
                    }
                    item { TunnelDetailsCard(telemetry = telemetry, profile = selectedProfile) }
                }

                1 -> {
                    item {
                        VpnProfilesToolkitCard(
                            generatedKeyPair = generatedKeyPair,
                            allowedIpsCalc = allowedIpsCalc,
                            importedPkcs12 = importedPkcs12,
                            selectedProfile = selectedProfile,
                            onOpenImportWg = { showImportWgDialog = true },
                            onOpenImportOvpn = { showImportOvpnDialog = true },
                            onOpenAddIkev2 = { showAddIkev2Dialog = true },
                            onGenerateWgKeys = { viewModel.generateWireGuardKeyPair() },
                            onShowWgQr = { viewModel.showQrForSelectedWireGuard() },
                            onImportQrBitmap = { bmp -> viewModel.importWireGuardFromQrBitmap(bmp) },
                            onImportPkcs12Bytes = { bytes, pwd -> viewModel.importPkcs12Bytes(bytes, pwd) },
                            onCalculateAllowedIps = { inc, exc -> viewModel.calculateAllowedIps(inc, exc) },
                            onCreateWgFromBuilder = { name, priv, addr, dns, peerPub, psk, ep, allowed, ka ->
                                viewModel.createWireGuardProfileFromBuilder(name, priv, addr, dns, peerPub, psk, ep, allowed, ka)
                            },
                            onTuneKeepalive = { sec, allowed -> viewModel.tuneSelectedWireGuardKeepalive(sec, allowed) }
                        )
                    }
                    items(profiles) { profile ->
                        ProfileItemCard(
                            profile = profile,
                            isSelected = profile.id == selectedProfile?.id,
                            isTunnelActive = telemetry.status == VpnStatus.CONNECTED && profile.id == selectedProfile?.id,
                            onSelect = { viewModel.selectProfile(profile) },
                            onConnect = {
                                viewModel.selectProfile(profile)
                                launchTunnelWithConsent(profile)
                            }
                        )
                    }
                }

                2 -> item {
                    EnterpriseSslAndIpsecProbesTab(
                        isTesting = isTesting,
                        sslProbeResult = sslProbeResult,
                        ikeProbeResult = ikeProbeResult,
                        mssCalculation = mssCalc,
                        onProbeSslVpn = { vendor, host, port, selfSigned -> viewModel.probeEnterpriseSslVpn(vendor, host, port, selfSigned) },
                        onProbeIkev2 = { host, natT -> viewModel.probeIkev2Peer(host, natT) },
                        onCalculateMss = { encap, mtu, v6, natT -> viewModel.calculateTunnelMss(encap, mtu, v6, natT) },
                        aiViewModel = aiViewModel
                    )
                }

                3 -> item {
                    DeepLeakAndPerAppTab(
                        isTesting = isTesting,
                        auditReport = vpnAuditReport,
                        selectedProfile = selectedProfile,
                        installedApps = installedApps,
                        onRunFullAudit = { viewModel.runFullLeakAndRouteAudit() },
                        onLoadApps = { viewModel.loadInstalledApps() },
                        onToggleExcludedApp = { pkg -> viewModel.toggleExcludedApp(pkg) },
                        aiViewModel = aiViewModel
                    )
                }

                4 -> {
                    item {
                        PcapControlCard(
                            stats = pcapStats,
                            onStartCapture = { launchLocalPcapWithConsent() },
                            onStopCapture = { viewModel.stopPcapCapture(context) },
                            onClearPackets = { viewModel.clearPcapPackets() },
                            onExportPcap = {
                                viewModel.exportPcapFile().onSuccess { (file, uri) ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.tcpdump.pcap"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Enterweb Slinger Capture: ${file.name}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export .pcap to Wireshark / Email"))
                                }
                            }
                        )
                    }
                    items(pcapPackets, key = { it.id }) { packet ->
                        CapturedPacketItemCard(packet = packet, onClick = { inspectedPacket = packet })
                    }
                }
            }
        }
    }

    qrBitmap?.let { bmp -> QrPreviewDialog(bitmap = bmp, confText = qrText, onDismiss = { viewModel.dismissQrPreview() }) }
    inspectedPacket?.let { pkt -> PacketHexInspectorDialog(packet = pkt, onDismiss = { inspectedPacket = null }) }

    if (showImportWgDialog) {
        ImportTextConfigDialog(
            title = "Import WireGuard (.conf)",
            defaultName = "WireGuard-Tunnel",
            placeholder = "[Interface]\nPrivateKey = ...\nAddress = 10.0.0.2/32\n\n[Peer]\nPublicKey = ...\nEndpoint = host:51820\nAllowedIPs = 0.0.0.0/0",
            onDismiss = { showImportWgDialog = false },
            onImport = { name, text -> viewModel.importWireGuardConf(text, name); showImportWgDialog = false }
        )
    }

    if (showImportOvpnDialog) {
        ImportTextConfigDialog(
            title = "Import OpenVPN (.ovpn)",
            defaultName = "OpenVPN-Gateway",
            placeholder = "client\ndev tun\nproto udp\nremote host 1194\ncipher AES-256-GCM\nauth SHA256",
            onDismiss = { showImportOvpnDialog = false },
            onImport = { name, text -> viewModel.importOpenVpnConf(text, name); showImportOvpnDialog = false }
        )
    }

    if (showAddIkev2Dialog) {
        AddIkev2ProfileDialog(
            hasPkcs12Ready = importedPkcs12 != null,
            onDismiss = { showAddIkev2Dialog = false },
            onCreate = { name, server, remoteId, psk, user, pass, authMode ->
                viewModel.createIkev2Profile(name, server, remoteId, psk, user, pass, authMode)
                showAddIkev2Dialog = false
            }
        )
    }
}

@Composable
private fun TunnelMasterCard(telemetry: VpnTelemetry, activeProfile: VpnProfile?, onToggleTunnel: () -> Unit) {
    val isConnected = telemetry.status == VpnStatus.CONNECTED
    val isConnecting = telemetry.status == VpnStatus.CONNECTING
    val stateColor = when (telemetry.status) {
        VpnStatus.CONNECTED -> StatusConnected
        VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> StatusWarning
        VpnStatus.DISCONNECTED -> TextMuted
        VpnStatus.ERROR -> StatusError
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("vpn_master_card"), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(stateColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(telemetry.status.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = stateColor)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = CardBackgroundElevated) {
                    Text(activeProfile?.protocol?.name ?: "NO PROTOCOL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(activeProfile?.name ?: "No Profile Selected", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Text(if (activeProfile != null) "${activeProfile.serverAddress}:${activeProfile.port}" else "No profile endpoint", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)

            if (!telemetry.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(color = StatusError.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(telemetry.errorMessage, style = MaterialTheme.typography.bodySmall, color = StatusError, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onToggleTunnel,
                enabled = isConnected || isConnecting || activeProfile != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vpn_toggle_button"),
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) StatusError else CyberCyan, contentColor = if (isConnected) Color.White else Color(0xFF00363D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                when {
                    isConnecting -> { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF00363D)); Spacer(modifier = Modifier.width(8.dp)); Text("Negotiating Tunnel...", fontWeight = FontWeight.Bold) }
                    isConnected -> { Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Disconnect VPN", fontWeight = FontWeight.Bold) }
                    else -> { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Connect VPN", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ThroughputGaugeCard(txBytesPerSec: Long, rxBytesPerSec: Long, totalTxBytes: Long, totalRxBytes: Long) {
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(rxBytesPerSec, txBytesPerSec) {
        samples.add((rxBytesPerSec + txBytesPerSec).coerceAtLeast(0L).toFloat())
        if (samples.size > 30) samples.removeAt(0)
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("vpn_throughput_card"), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live Throughput & Traffic", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ThroughputMetric("DOWNLOAD (RX)", formatRate(rxBytesPerSec), formatBytes(totalRxBytes), CyberCyan)
                ThroughputMetric("UPLOAD (TX)", formatRate(txBytesPerSec), formatBytes(totalTxBytes), StatusConnected)
            }
            Spacer(modifier = Modifier.height(14.dp))
            val snapshot = samples.toList()
            Canvas(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(CardBackgroundElevated)) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1f)
                val baselineY = h * 0.9f
                if (snapshot.size < 2 || snapshot.all { it <= 0f }) {
                    drawLine(CyberCyan.copy(alpha = 0.45f), Offset(0f, baselineY), Offset(w, baselineY), 2.dp.toPx(), cap = StrokeCap.Round)
                    return@Canvas
                }
                val peak = (snapshot.maxOrNull() ?: 1024f).coerceAtLeast(1024f)
                val stepX = w / (snapshot.size - 1).coerceAtLeast(1)
                val path = Path()
                snapshot.forEachIndexed { idx, value ->
                    val x = (stepX * idx).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                    val norm = (value / peak).coerceIn(0f, 1f)
                    val y = (h * (0.9f - 0.75f * norm)).let { if (it.isNaN() || it.isInfinite()) baselineY else it }
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = CyberCyan.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun ThroughputMetric(label: String, currentRate: String, total: String, accentColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(currentRate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accentColor)
        Text("Total: $total", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
    }
}

@Composable
private fun TunnelDetailsCard(telemetry: VpnTelemetry, profile: VpnProfile?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Interface & Routing Parameters", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            ParameterRow("Assigned Virtual IP", telemetry.assignedIp.ifBlank { "Unassigned" })
            ParameterRow("Tunnel MTU", "${telemetry.mtu} bytes")
            ParameterRow("Active DNS Servers", telemetry.dnsServers.joinToString(", ").ifEmpty { "System Default / None" })
            ParameterRow("Connection Duration", if (telemetry.durationSeconds > 0) "${telemetry.durationSeconds}s" else "Inactive")
            ParameterRow("Split Tunnel Bypass", if (profile?.splitTunnelExcludedApps.isNullOrEmpty()) "Full Tunnel (All Apps)" else "${profile?.splitTunnelExcludedApps?.size} apps bypassed")
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold), color = TextPrimary)
    }
}

private fun formatRate(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB/s", kb)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
