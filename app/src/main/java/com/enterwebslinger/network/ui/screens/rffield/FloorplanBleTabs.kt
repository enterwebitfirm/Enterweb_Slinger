// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/rffield/FloorplanBleTabs.kt ===
package com.enterwebslinger.network.ui.screens.rffield

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FloorplanHeatmapTab(viewModel: RfFieldViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pins by viewModel.floorplanPins.collectAsStateWithLifecycle()
    val sample by viewModel.liveWifiSample.collectAsStateWithLifecycle()
    val isWalking by viewModel.isStepWalkActive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshLiveWifiSample()
    }

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
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${sample.ssid} (${sample.bssid})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Live RSSI: ${sample.rssiDbm} dBm • PHY: ${sample.linkSpeedMbps} Mbps • ${sample.frequencyMhz} MHz",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (sample.rssiDbm >= -65) StatusEmerald else if (sample.rssiDbm >= -78) StatusAmber else StatusCrimson
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.refreshLiveWifiSample() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_rf_sample_refresh")
                        ) {
                            Text("Sample", fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "Tap anywhere on the architectural survey grid to drop a live RSSI measurement pin and compute an IDW heatmap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (size.width > 0 && size.height > 0) {
                                        viewModel.dropPinAt(
                                            offset.x / size.width.toFloat(),
                                            offset.y / size.height.toFloat()
                                        )
                                    }
                                }
                            }
                            .testTag("floorplan_canvas")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@Canvas

                            // Render IDW Heatmap cells if at least 1 pin exists
                            if (pins.isNotEmpty()) {
                                val cols = 20
                                val rows = 14
                                val cellW = w / cols
                                val cellH = h / rows
                                for (r in 0 until rows) {
                                    val yNorm = (r + 0.5f) / rows
                                    for (c in 0 until cols) {
                                        val xNorm = (c + 0.5f) / cols
                                        val estRssi = viewModel.interpolateIdw(xNorm, yNorm)
                                        drawRect(
                                            color = rssiToComposeColor(estRssi).copy(alpha = 0.48f),
                                            topLeft = Offset(c * cellW, r * cellH),
                                            size = Size(cellW + 1f, cellH + 1f)
                                        )
                                    }
                                }
                            }

                            // Architectural grid lines
                            for (c in 1 until 10) {
                                val x = w * (c / 10f)
                                drawLine(BorderStrokeColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                            }
                            for (r in 1 until 8) {
                                val y = h * (r / 8f)
                                drawLine(BorderStrokeColor.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                            }

                            // Survey Pins
                            pins.forEach { pin ->
                                val cx = pin.xRatio.coerceIn(0f, 1f) * w
                                val cy = pin.yRatio.coerceIn(0f, 1f) * h
                                val color = rssiToComposeColor(pin.rssiDbm.toFloat())
                                drawCircle(color = color, radius = 9.dp.toPx(), center = Offset(cx, cy))
                                drawCircle(
                                    color = Color.White,
                                    radius = 9.dp.toPx(),
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        if (pins.isEmpty()) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                Text("Tap grid to drop RF survey pin", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleStepWalkMode() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_step_walk_toggle"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWalking) StatusAmber else CardBackgroundElevated,
                                contentColor = if (isWalking) Color.Black else CyberCyan
                            )
                        ) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isWalking) "Stop Walk" else "Step Walk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.undoLastPin() },
                            enabled = pins.isNotEmpty(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_undo_pin")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo Pin", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearFloorplanPins() },
                            enabled = pins.isNotEmpty(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_clear_pins")
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear Pins", tint = StatusCrimson, modifier = Modifier.size(16.dp))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.exportFloorplanPng().onSuccess { (file, uri) ->
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Enterweb Floorplan RF Heatmap (${file.name})")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Heatmap PNG"))
                                    }
                                }
                            },
                            enabled = pins.isNotEmpty(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_export_heatmap"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(pins.reversed(), key = { it.id }) { pin ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${pin.ssid} (${pin.bssid})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Grid (${String.format(Locale.US, "%.2f", pin.xRatio)}, ${String.format(Locale.US, "%.2f", pin.yRatio)}) • ${pin.linkSpeedMbps} Mbps",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                    Surface(
                        color = rssiToComposeColor(pin.rssiDbm.toFloat()).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${pin.rssiDbm} dBm",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = rssiToComposeColor(pin.rssiDbm.toFloat()),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BleScannerTab(viewModel: RfFieldViewModel) {
    val bleDevices by viewModel.bleDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
    val bleError by viewModel.bleError.collectAsStateWithLifecycle()

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.startBleScan()
        }
    }

    fun requestBleAndScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (viewModel.hasBlePermission()) {
            viewModel.startBleScan()
        } else {
            blePermissionLauncher.launch(perms)
        }
    }

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
                border = BorderStroke(1.dp, if (isScanning) StatusEmerald else BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Bluetooth Low Energy (BLE) Beacon Scanner",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Inspects advertising service UUIDs, TX power, connectable flags, and log-distance proximity estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    bleError?.let { err ->
                        Surface(
                            color = StatusCrimson.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusCrimson,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (isScanning) viewModel.stopBleScan() else requestBleAndScan() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_ble_scan_toggle"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) StatusCrimson else CyberCyan,
                                contentColor = if (isScanning) Color.White else Color(0xFF00363D)
                            )
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScanning) "Stop BLE Scan" else "Start BLE Scan", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearBleDevices() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("btn_ble_clear")
                        ) {
                            Text("Clear", color = TextSecondary)
                        }
                    }
                }
            }
        }

        items(bleDevices, key = { it.address }) { dev ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dev.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${dev.rssiDbm} dBm (~${String.format(Locale.US, "%.1f", dev.estimatedDistanceMeters)} m)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = if (dev.rssiDbm >= -65) StatusEmerald else if (dev.rssiDbm >= -82) StatusAmber else StatusCrimson
                        )
                    }
                    Text(
                        text = "MAC: ${dev.address} • TX: ${dev.txPowerDbm?.let { "$it dBm" } ?: "N/A"} • ${if (dev.isConnectable) "Connectable" else "Beacon-Only"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                    if (dev.serviceUuids.isNotEmpty()) {
                        Text(
                            text = "UUIDs: ${dev.serviceUuids.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = CyberCyan
                        )
                    }
                    if (dev.manufacturerDataHex.isNotBlank()) {
                        Text(
                            text = "Mfg: ${dev.manufacturerDataHex}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

private fun rssiToComposeColor(rssiDbm: Float): Color {
    return when {
        rssiDbm >= -60f -> StatusEmerald
        rssiDbm >= -76f -> StatusAmber
        else -> StatusCrimson
    }
}
