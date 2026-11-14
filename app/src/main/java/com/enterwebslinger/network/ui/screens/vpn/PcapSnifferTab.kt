// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/PcapSnifferTab.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.CapturedPacketRecord
import com.enterwebslinger.network.domain.model.PcapCaptureStats
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.StatusPurple
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun PcapControlCard(
    stats: PcapCaptureStats,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onClearPackets: () -> Unit,
    onExportPcap: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pcap_control_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, if (stats.isCapturing) StatusEmerald else BorderStrokeColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loopback TUN PCAP Sniffer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Captures L3/L4 IPv4/IPv6 headers into standard libpcap (0xA1B2C3D4, LINKTYPE_RAW) with protected DNS forwarding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (stats.isCapturing) StatusEmerald.copy(alpha = 0.2f) else CardBackgroundElevated
                ) {
                    Text(
                        text = if (stats.isCapturing) "CAPTURING" else "IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (stats.isCapturing) StatusEmerald else TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PcapStatChip("Packets", stats.packetCount.toString(), CyberCyan, Modifier.weight(1f))
                PcapStatChip("TCP", stats.tcpCount.toString(), StatusEmerald, Modifier.weight(1f))
                PcapStatChip("UDP/DNS", stats.udpCount.toString(), StatusAmber, Modifier.weight(1f))
                PcapStatChip("ICMP", stats.icmpCount.toString(), StatusPurple, Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { if (stats.isCapturing) onStopCapture() else onStartCapture() },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_pcap_toggle"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stats.isCapturing) StatusCrimson else CyberCyan,
                        contentColor = if (stats.isCapturing) Color.White else Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (stats.isCapturing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (stats.isCapturing) "Stop Capture" else "Start PCAP", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onClearPackets,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_pcap_clear"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Packets", tint = TextSecondary)
                }

                OutlinedButton(
                    onClick = onExportPcap,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_pcap_export"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Wireshark .pcap", fontSize = 11.sp, color = CyberCyan)
                }
            }

            stats.activeFilePath?.let { path ->
                Text(
                    text = "Output: ${path.substringAfterLast("/")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun PcapStatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CardBackgroundElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
        }
    }
}

@Composable
fun CapturedPacketItemCard(
    packet: CapturedPacketRecord,
    onClick: () -> Unit
) {
    val protoColor = when (packet.protocolName) {
        "TCP" -> StatusEmerald
        "DNS" -> CyberCyan
        "UDP" -> StatusAmber
        "ICMP", "ICMPv6" -> StatusPurple
        else -> TextSecondary
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pcap_pkt_${packet.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = protoColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "IPv${packet.ipVersion} ${packet.protocolName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = protoColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "#${packet.id}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextMuted
                    )
                }
                Text(
                    text = "${packet.capturedLength} B",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }

            Text(
                text = packet.summary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextPrimary
            )

            if (packet.payloadPreviewAscii.isNotBlank()) {
                Text(
                    text = packet.payloadPreviewAscii,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PacketHexInspectorDialog(
    packet: CapturedPacketRecord,
    onDismiss: () -> Unit
) {
    val hexDump = remember(packet.id) {
        NetworkByteUtils.bytesToHex(packet.rawIpBytes.take(256).toByteArray(), " ")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Packet #${packet.id} (${packet.protocolName}, ${packet.capturedLength} B)",
                color = CyberCyan,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = packet.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary
                )
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = hexDump,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        color = TextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyberCyan)
            }
        },
        containerColor = CardBackground
    )
}
