// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnWorkbenchScreen.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.app.Activity
import android.net.VpnService
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.DnsLeakResult
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.VpnStatus
import com.enterwebslinger.network.domain.model.VpnTelemetry
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
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val diagnosticStatus by viewModel.diagnosticStatus.collectAsState()
    val dnsLeakResult by viewModel.dnsLeakResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

    var showImportWgDialog by remember { mutableStateOf(false) }
    var showImportOvpnDialog by remember { mutableStateOf(false) }
    var showAddIkev2Dialog by remember { mutableStateOf(false) }
    var pendingTunnelProfile by remember { mutableStateOf<VpnProfile?>(null) }
    var pendingIkev2Consent by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Profiles, 2: Diagnostics & Leak Test

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingIkev2Consent) {
                pendingIkev2Consent = false
                viewModel.startProvisionedIkev2()
            } else {
                val target = pendingTunnelProfile
                pendingTunnelProfile = null
                viewModel.startTunnel(target)
            }
        } else {
            pendingIkev2Consent = false
            pendingTunnelProfile = null
            viewModel.onVpnPermissionDenied()
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
                vpnConsentLauncher.launch(prepareIntent)
            } else {
                viewModel.startTunnel(target)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "VPN Hub",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Multi-Protocol VPN & Tunneling",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("vpn_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 16.dp,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Active Tunnel") },
                modifier = Modifier.testTag("tab_vpn_tunnel")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Profiles (${profiles.size})") },
                modifier = Modifier.testTag("tab_vpn_profiles")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Diagnostics & Leak Test") },
                modifier = Modifier.testTag("tab_vpn_diagnostics")
            )
        }

        // Live Diagnostic Banner if active
        diagnosticStatus?.let { status ->
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
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearDiagnosticStatus() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

                    item {
                        TunnelDetailsCard(
                            telemetry = telemetry,
                            profile = selectedProfile
                        )
                    }
                }

                1 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Tunnel Configurations",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showImportWgDialog = true },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_wg_button")
                                ) {
                                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WG .conf", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { showImportOvpnDialog = true },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_ovpn_button")
                                ) {
                                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("OpenVPN", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { showAddIkev2Dialog = true },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("add_ikev2_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("IKEv2", fontSize = 11.sp)
                                }
                            }
                        }
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

                2 -> {
                    item {
                        DnsLeakTestCard(
                            leakResult = dnsLeakResult,
                            isTesting = isTesting,
                            onRunTest = { viewModel.runDnsLeakTest() }
                        )
                    }

                    item {
                        ProtocolHandshakeTesterCard(
                            isTesting = isTesting,
                            onTestSstp = { host, port -> viewModel.testSstp(host, port) },
                            onTestIpsec = { host, port -> viewModel.testIpsec(host, port) }
                        )
                    }
                }
            }
        }
    }

    // --- Import WireGuard Dialog ---
    if (showImportWgDialog) {
        ImportTextConfigDialog(
            title = "Import WireGuard (.conf)",
            defaultName = "WireGuard-Tunnel",
            placeholder = """[Interface]
PrivateKey = <base64-private-key>
Address = <interface-cidr>

[Peer]
PublicKey = <base64-peer-public-key>
Endpoint = <host:port>
AllowedIPs = 0.0.0.0/0""",
            onDismiss = { showImportWgDialog = false },
            onImport = { name, text ->
                viewModel.importWireGuardConf(text, name)
                showImportWgDialog = false
            }
        )
    }

    // --- Import OpenVPN Dialog ---
    if (showImportOvpnDialog) {
        ImportTextConfigDialog(
            title = "Import OpenVPN (.ovpn)",
            defaultName = "OpenVPN-Gateway",
            placeholder = """client
dev tun
proto udp
remote <host> <port>
cipher AES-256-GCM""",
            onDismiss = { showImportOvpnDialog = false },
            onImport = { name, text ->
                viewModel.importOpenVpnConf(text, name)
                showImportOvpnDialog = false
            }
        )
    }

    // --- Add IKEv2 Profile Dialog ---
    if (showAddIkev2Dialog) {
        AddIkev2ProfileDialog(
            onDismiss = { showAddIkev2Dialog = false },
            onCreate = { name, server, remoteId, psk, user, pass ->
                viewModel.createIkev2Profile(name, server, remoteId, psk, user, pass)
                showAddIkev2Dialog = false
            }
        )
    }
}

@Composable
private fun TunnelMasterCard(
    telemetry: VpnTelemetry,
    activeProfile: VpnProfile?,
    onToggleTunnel: () -> Unit
) {
    val isConnected = telemetry.status == VpnStatus.CONNECTED
    val isConnecting = telemetry.status == VpnStatus.CONNECTING

    val stateColor = when (telemetry.status) {
        VpnStatus.CONNECTED -> StatusConnected
        VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> StatusWarning
        VpnStatus.DISCONNECTED -> TextMuted
        VpnStatus.ERROR -> StatusError
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_master_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(stateColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = telemetry.status.name,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = stateColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CardBackgroundElevated
                ) {
                    Text(
                        text = activeProfile?.protocol?.name ?: "NO PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = activeProfile?.name ?: "No Profile Selected",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Text(
                text = if (activeProfile != null) "${activeProfile.serverAddress}:${activeProfile.port}" else "No profile endpoint",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary
            )

            if (!telemetry.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = StatusError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = telemetry.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onToggleTunnel,
                enabled = isConnected || isConnecting || activeProfile != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("vpn_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) StatusError else CyberCyan,
                    contentColor = if (isConnected) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Negotiating Tunnel...", fontWeight = FontWeight.Bold)
                } else if (isConnected) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect VPN", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect VPN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThroughputGaugeCard(
    txBytesPerSec: Long,
    rxBytesPerSec: Long,
    totalTxBytes: Long,
    totalRxBytes: Long
) {
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(rxBytesPerSec, txBytesPerSec) {
        val totalRate = (rxBytesPerSec + txBytesPerSec).coerceAtLeast(0L).toFloat()
        samples.add(totalRate)
        if (samples.size > 30) {
            samples.removeAt(0)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_throughput_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Throughput & Traffic",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ThroughputMetric(
                    label = "DOWNLOAD (RX)",
                    currentRate = formatRate(rxBytesPerSec),
                    total = formatBytes(totalRxBytes),
                    accentColor = CyberCyan
                )

                ThroughputMetric(
                    label = "UPLOAD (TX)",
                    currentRate = formatRate(txBytesPerSec),
                    total = formatBytes(totalTxBytes),
                    accentColor = StatusConnected
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val snapshot = samples.toList()
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBackgroundElevated)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 1f
                )

                val baselineY = h * 0.9f
                if (snapshot.size < 2 || snapshot.all { it <= 0f }) {
                    drawLine(
                        color = CyberCyan.copy(alpha = 0.45f),
                        start = Offset(0f, baselineY),
                        end = Offset(w, baselineY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
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

                drawPath(
                    path = path,
                    color = CyberCyan.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun ThroughputMetric(
    label: String,
    currentRate: String,
    total: String,
    accentColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = currentRate,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )
        Text(
            text = "Total: $total",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary
        )
    }
}

@Composable
private fun TunnelDetailsCard(
    telemetry: VpnTelemetry,
    profile: VpnProfile?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Interface & Routing Parameters",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold), color = TextPrimary)
    }
}

@Composable
private fun ProfileItemCard(
    profile: VpnProfile,
    isSelected: Boolean,
    isTunnelActive: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBackgroundElevated else CardBackground
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyberCyan) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground
                    ) {
                        Text(
                            text = profile.protocol.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${profile.serverAddress}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }

            Button(
                onClick = onConnect,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("connect_profile_${profile.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTunnelActive) StatusError else CyberCyan,
                    contentColor = if (isTunnelActive) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isTunnelActive) "Active" else "Select & Use", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DnsLeakTestCard(
    leakResult: DnsLeakResult?,
    isTesting: Boolean,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dns_leak_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DNS Leak & Privacy Audit",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onRunTest,
                    enabled = !isTesting,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("run_dns_leak_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Audit", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (leakResult != null) {
                val isLeak = leakResult.isLeakDetected
                val bannerColor = if (isLeak) StatusError else StatusConnected
                val bannerIcon = if (isLeak) Icons.Default.LockOpen else Icons.Default.Lock

                Surface(
                    color = bannerColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isLeak) "LEAK DETECTED" else "TUNNEL SECURE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = bannerColor
                            )
                            Text(
                                text = leakResult.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tested Resolvers: ${leakResult.testedDnsServers.joinToString(", ")} (${leakResult.responseTimeMs}ms RTT)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "Query random subdomains across multiple resolvers to verify whether local ISP resolvers intercept DNS traffic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ProtocolHandshakeTesterCard(
    isTesting: Boolean,
    onTestSstp: (String, Int) -> Unit,
    onTestIpsec: (String, Int) -> Unit
) {
    var hostInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("protocol_tester_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Protocol Handshake Probes",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Target Host / Gateway IP") },
                placeholder = { Text("e.g. vpn.company.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vpn_probe_host_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onTestSstp(hostInput.trim(), 443) },
                    enabled = !isTesting && hostInput.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("test_sstp_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SSTP (443)", fontSize = 12.sp)
                }

                Button(
                    onClick = { onTestIpsec(hostInput.trim(), 500) },
                    enabled = !isTesting && hostInput.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("test_ikev2_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("IKEv2 (500)", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportTextConfigDialog(
    title: String,
    defaultName: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onImport: (name: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf("") }
    var configText by remember { mutableStateOf("") }
    var fileLoadError by remember { mutableStateOf<String?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && profileName.isBlank()) {
                        val rawName = cursor.getString(0) ?: ""
                        profileName = rawName.substringBeforeLast(".")
                    }
                }
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Unable to open selected file stream")
            }.fold(
                onSuccess = { loaded ->
                    configText = loaded
                    fileLoadError = null
                },
                onFailure = { err ->
                    fileLoadError = err.message ?: "Failed to read selected file"
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vpn_browse_conf_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse .conf / .ovpn File", fontSize = 12.sp)
                }
                fileLoadError?.let { err ->
                    Text(text = err, style = MaterialTheme.typography.labelSmall, color = StatusError)
                }
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text(defaultName, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    label = { Text("Paste Configuration File") },
                    placeholder = { Text(placeholder, fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(profileName.ifBlank { defaultName }, configText) },
                enabled = configText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardBackground
    )
}

@Composable
private fun AddIkev2ProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        serverAddress: String,
        remoteId: String,
        psk: String,
        username: String,
        password: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var remoteId by remember { mutableStateOf("") }
    var psk by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provision IKEv2 / IPsec Profile", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("Corporate IKEv2", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server Host / IP") },
                    placeholder = { Text("vpn.company.com", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = remoteId,
                    onValueChange = { remoteId = it },
                    label = { Text("Remote Identity (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = psk,
                    onValueChange = { psk = it },
                    label = { Text("Pre-Shared Key (if PSK mode)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("EAP Username (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("EAP Password (Optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.ifBlank { "IKEv2-${server.trim()}" },
                        server.trim(),
                        remoteId.trim(),
                        psk,
                        username.trim(),
                        password
                    )
                },
                enabled = server.isNotBlank() && (psk.isNotBlank() || username.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardBackground
    )
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
