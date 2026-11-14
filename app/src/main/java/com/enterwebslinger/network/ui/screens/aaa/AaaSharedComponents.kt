// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/aaa/AaaSharedComponents.kt ===
package com.enterwebslinger.network.ui.screens.aaa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.RadiusWorkbenchResult
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary

@Composable
internal fun RadiusResultCard(
    title: String,
    res: RadiusWorkbenchResult,
    aiViewModel: AiAssistantViewModel?,
    aiKey: String
) {
    val badgeColor = when {
        res.isSuccess -> StatusEmerald
        res.errorMessage != null -> StatusCrimson
        else -> StatusAmber
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("${res.responsePacketCode.label} • ${res.rttMs} ms", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = badgeColor)
            Text(
                "Response-Auth: ${if (res.responseAuthenticatorValid) "VERIFIED" else "UNVERIFIED"} | Msg-Auth (80): ${if (!res.messageAuthenticatorPresent) "ABSENT" else if (res.messageAuthenticatorValid) "HMAC-MD5 VALID" else "INVALID"}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (res.responseAuthenticatorValid) StatusEmerald else StatusAmber
            )
            if (res.dot1xSummary.vlanId.isNotBlank() || res.dot1xSummary.mikrotikRateLimit.isNotBlank()) {
                Text(
                    "802.1X Policy: VLAN=${res.dot1xSummary.vlanId.ifBlank { "--" }} Rate=${res.dot1xSummary.mikrotikRateLimit.ifBlank { "--" }} Group=${res.dot1xSummary.mikrotikGroup.ifBlank { res.dot1xSummary.fortinetGroup.ifBlank { "--" } }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
            }
            if (res.errorCauseCode != null) {
                Text("Error-Cause (101): ${res.errorCauseCode} - ${res.errorCauseText}", style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
            }
            res.attributes.forEach { a ->
                Text("${a.name}: ${a.valueText}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
            }
            res.decodedVsas.forEach { v ->
                Surface(color = DarkBackground, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${v.vendorName} • ${v.attributeName} = ${v.decodedValue}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = CyberCyan,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            res.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusCrimson) }

            val rawSummary = buildString {
                appendLine("Server ${res.serverIp}:${res.port} | Req=${res.requestCode.label} | Resp=${res.responsePacketCode.label} | RTT=${res.rttMs}ms")
                appendLine("RespAuthValid=${res.responseAuthenticatorValid}, MsgAuthValid=${res.messageAuthenticatorValid}")
                res.attributes.forEach { appendLine("${it.name}: ${it.valueText}") }
                res.decodedVsas.forEach { appendLine("VSA ${it.vendorName} ${it.attributeName}: ${it.decodedValue}") }
                res.errorMessage?.let { appendLine("Error: $it") }
            }
            ExplainResultSection(
                diagnosisKey = aiKey,
                contextTitle = "RADIUS Result (${res.responsePacketCode.label})",
                rawResultText = rawSummary,
                aiViewModel = aiViewModel,
                buttonTestTag = "explain_${aiKey}_btn"
            )
        }
    }
}

@Composable
internal fun AaaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan,
            unfocusedBorderColor = BorderStrokeColor,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
