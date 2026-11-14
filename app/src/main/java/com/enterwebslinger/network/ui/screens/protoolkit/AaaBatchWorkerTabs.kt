// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/protoolkit/AaaBatchWorkerTabs.kt ===
package com.enterwebslinger.network.ui.screens.protoolkit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.DiffLineType
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AaaRadiusTacacsTab(viewModel: ProToolkitViewModel) {
    val radiusRes by viewModel.radiusResult.collectAsStateWithLifecycle()
    val tacacsRes by viewModel.tacacsResult.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingAaa.collectAsStateWithLifecycle()

    var serverIp by remember { mutableStateOf("") }
    var radiusPort by remember { mutableStateOf("1812") }
    var sharedSecret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tacacsPort by remember { mutableStateOf("49") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = BorderStrokeColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enterprise RADIUS (RFC 2865) Access-Request Tester",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Constructs raw binary Code 1 Access-Request packets with MD5 XOR obfuscated User-Password, NAS-IP-Address, and decodes VSAs (Type 26).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { serverIp = it },
                            label = { Text("RADIUS / TACACS+ Host") },
                            singleLine = true,
                            modifier = Modifier.weight(2f).testTag("input_aaa_server"),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = radiusPort,
                            onValueChange = { radiusPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_radius_port"),
                            colors = fieldColors
                        )
                    }
                    OutlinedTextField(
                        value = sharedSecret,
                        onValueChange = { sharedSecret = it },
                        label = { Text("Shared Secret") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_radius_secret"),
                        colors = fieldColors
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_radius_user"),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_radius_pass"),
                            colors = fieldColors
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.testRadius(
                                serverIp = serverIp,
                                port = radiusPort.toIntOrNull() ?: 1812,
                                secret = sharedSecret,
                                user = username,
                                pass = password
                            )
                        },
                        enabled = !isTesting && serverIp.isNotBlank() && sharedSecret.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_test_radius"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Send RADIUS Access-Request", fontWeight = FontWeight.Bold)
                    }

                    radiusRes?.let { r ->
                        Surface(color = CardBackgroundElevated, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${r.responseCode.label} (${r.rttMs} ms)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (r.isSuccess) StatusEmerald else StatusCrimson
                                )
                                if (r.replyMessage.isNotBlank()) {
                                    Text("Reply-Message: ${r.replyMessage}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                                r.attributes.forEach { attr ->
                                    Text("${attr.name}: ${attr.valueText}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                                }
                                r.errorMessage?.let { err ->
                                    Text(err, style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "TACACS+ Protocol Probe (TCP Port 49)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tacacsPort,
                            onValueChange = { tacacsPort = it },
                            label = { Text("TACACS+ Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_tacacs_port"),
                            colors = fieldColors
                        )
                        Button(
                            onClick = { viewModel.testTacacs(serverIp, tacacsPort.toIntOrNull() ?: 49) },
                            enabled = !isTesting && serverIp.isNotBlank(),
                            modifier = Modifier.weight(1.5f).heightIn(min = 48.dp).testTag("btn_probe_tacacs"),
                            colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan)
                        ) {
                            Text("Probe v12.0 Header", fontWeight = FontWeight.Bold)
                        }
                    }

                    tacacsRes?.let { t ->
                        Text(
                            text = t.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (t.isReachable) StatusEmerald else StatusCrimson
                        )
                        if (t.responseHex.isNotBlank()) {
                            Text("RX Hex: ${t.responseHex}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchSshAndTrafficTab(viewModel: ProToolkitViewModel) {
    val savedTargets by viewModel.savedSshTargets.collectAsStateWithLifecycle()
    val customTargets by viewModel.customSshTargets.collectAsStateWithLifecycle()
    val results by viewModel.sshBatchResults.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunningBatchSsh.collectAsStateWithLifecycle()
    val trafficSamples by viewModel.trafficSamples.collectAsStateWithLifecycle()
    val latestSample by viewModel.latestTrafficSample.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startTrafficStats()
    }

    var newHost by remember { mutableStateOf("") }
    var newPort by remember { mutableStateOf("22") }
    var newUser by remember { mutableStateOf("admin") }
    var newPass by remember { mutableStateOf("") }
    var batchCommand by remember { mutableStateOf("show version") }
    var goldenBaseline by remember { mutableStateOf("") }

    val allTargets = savedTargets + customTargets

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = BorderStrokeColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live TrafficStats Monitor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Real-Time Interface TrafficStats",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${latestSample.totalRxPps + latestSample.totalTxPps} pkt/s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = CyberCyan
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Total RX: ${formatBps(latestSample.totalRxBps)} • Mob: ${formatBps(latestSample.mobileRxBps)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = CyberCyan
                        )
                        Text(
                            text = "Total TX: ${formatBps(latestSample.totalTxBps)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = StatusEmerald
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                    ) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f || trafficSamples.size < 2) return@Canvas
                        val peak = (trafficSamples.maxOfOrNull { maxOf(it.totalRxBps, it.totalTxBps) } ?: 1024L).coerceAtLeast(1024L).toFloat()
                        val stepX = w / (trafficSamples.size - 1).coerceAtLeast(1)

                        val rxPath = Path()
                        val txPath = Path()
                        trafficSamples.forEachIndexed { i, s ->
                            val x = (stepX * i).let { if (it.isNaN()) 0f else it }
                            val rxNorm = (s.totalRxBps / peak).coerceIn(0f, 1f)
                            val txNorm = (s.totalTxBps / peak).coerceIn(0f, 1f)
                            val rxY = h * (0.9f - 0.78f * rxNorm)
                            val txY = h * (0.9f - 0.78f * txNorm)
                            if (i == 0) {
                                rxPath.moveTo(x, rxY)
                                txPath.moveTo(x, txY)
                            } else {
                                rxPath.lineTo(x, rxY)
                                txPath.lineTo(x, txY)
                            }
                        }
                        drawPath(rxPath, color = CyberCyan, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(txPath, color = StatusEmerald, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Multi-Host SSH Batch Runner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Multi-Host Concurrent SSH Batch Runner (${allTargets.size} Targets)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newHost,
                            onValueChange = { newHost = it },
                            label = { Text("SSH Host") },
                            singleLine = true,
                            modifier = Modifier.weight(2f).testTag("input_batch_ssh_host"),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = newPort,
                            onValueChange = { newPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newUser,
                            onValueChange = { newUser = it },
                            label = { Text("User") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.addCustomSshTarget(newHost, newHost, newPort.toIntOrNull() ?: 22, newUser, newPass)
                                newHost = ""
                            },
                            enabled = newHost.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_add_ssh_target")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add SSH Target", tint = CyberCyan)
                        }
                    }

                    customTargets.forEach { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${target.username}@${target.host}:${target.port}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            IconButton(onClick = { viewModel.removeCustomSshTarget(target.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove target", tint = StatusCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = batchCommand,
                        onValueChange = { batchCommand = it },
                        label = { Text("Batch Command") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_batch_ssh_cmd"),
                        colors = fieldColors
                    )

                    OutlinedTextField(
                        value = goldenBaseline,
                        onValueChange = { goldenBaseline = it },
                        label = { Text("Optional Golden Config Baseline (for Diff)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.runBatchSsh(batchCommand, goldenBaseline) },
                            enabled = !isRunning && batchCommand.isNotBlank() && allTargets.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_run_batch_ssh"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRunning) "Running..." else "Execute Across Hosts", fontWeight = FontWeight.Bold)
                        }

                        if (isRunning) {
                            OutlinedButton(
                                onClick = { viewModel.stopBatchSsh() },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_stop_batch_ssh")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop Batch SSH", tint = StatusCrimson)
                            }
                        }
                    }
                }
            }
        }

        items(results) { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, if (res.isSuccess) BorderStrokeColor else StatusCrimson),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${res.targetName} (${res.host})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text("${res.durationMs} ms • ${res.diffSummary}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = if (res.isSuccess) CyberCyan else StatusCrimson)
                    }
                    if (!res.isSuccess) {
                        Text(res.errorMessage ?: "Failed", style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                    } else {
                        res.diffLines.take(20).forEach { dl ->
                            val c = when (dl.type) {
                                DiffLineType.ADDED -> StatusEmerald
                                DiffLineType.REMOVED -> StatusCrimson
                                DiffLineType.UNCHANGED -> TextSecondary
                            }
                            Text(dl.text, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = c)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundAuditWorkerTab(viewModel: ProToolkitViewModel) {
    val context = LocalContext.current
    val cfg by viewModel.auditConfig.collectAsStateWithLifecycle()
    val summary by viewModel.lastAuditSummary.collectAsStateWithLifecycle()

    var enabled by remember(cfg.enabled) { mutableStateOf(cfg.enabled) }
    var intervalMin by remember(cfg.intervalMinutes) { mutableLongStateOf(cfg.intervalMinutes) }
    var trustedSsid by remember(cfg.trustedSsid) { mutableStateOf(cfg.trustedSsid) }
    var macWhitelist by remember(cfg.trustedMacWhitelist) { mutableStateOf(cfg.trustedMacWhitelist.joinToString(", ")) }
    var watchedHosts by remember(cfg.watchedHosts) { mutableStateOf(cfg.watchedHosts.joinToString(", ")) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = BorderStrokeColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scheduled Background Network Monitor",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newChecked ->
                                enabled = newChecked
                                if (newChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("switch_audit_worker_enabled")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(15L, 60L, 180L, 360L).forEach { mins ->
                            OutlinedButton(
                                onClick = { intervalMin = mins },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, if (intervalMin == mins) CyberCyan else BorderStrokeColor)
                            ) {
                                Text(
                                    text = if (mins < 60) "${mins}m" else "${mins / 60}h",
                                    fontSize = 11.sp,
                                    color = if (intervalMin == mins) CyberCyan else TextSecondary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = trustedSsid,
                        onValueChange = { trustedSsid = it },
                        label = { Text("Trusted SSID (blank = all networks)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_audit_trusted_ssid"),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = macWhitelist,
                        onValueChange = { macWhitelist = it },
                        label = { Text("Trusted MAC Whitelist (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_audit_mac_whitelist"),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = watchedHosts,
                        onValueChange = { watchedHosts = it },
                        label = { Text("Critical Watched Hosts (IP or IP:port, comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_audit_watched_hosts"),
                        colors = fieldColors
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                viewModel.updateAuditSchedule(context, enabled, intervalMin, trustedSsid, macWhitelist, watchedHosts)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_save_audit_schedule"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Text("Save Schedule", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerImmediateAudit(context) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_run_audit_now")
                        ) {
                            Text("Run Now", color = CyberCyan)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Latest Background Audit Report", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    val ts = if (summary.timestampMs > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(summary.timestampMs))
                    } else "Pending"
                    Text("Executed: $ts", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(summary.statusMessage, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                    if (summary.unknownMacsFound.isNotEmpty()) {
                        Text("Unknown MACs: ${summary.unknownMacsFound.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = StatusAmber)
                    }
                    if (summary.offlineHosts.isNotEmpty()) {
                        Text("Offline Hosts: ${summary.offlineHosts.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                    }
                }
            }
        }
    }
}

private fun formatBps(bps: Long): String {
    val kb = bps / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB/s", kb)
        else -> "$bps B/s"
    }
}
