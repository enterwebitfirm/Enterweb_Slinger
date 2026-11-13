// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/ai/AiAssistantScreen.kt ===
package com.enterwebslinger.network.ui.screens.ai

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.ChatMessage
import com.enterwebslinger.network.ui.components.AiAuditCard
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auditState by viewModel.auditState.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeToolExecuting.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Audit current Wi-Fi signal and interference",
        "Measure gateway latency & DNS response",
        "Inspect discovered LAN hosts & open ports",
        "Check active NetFlow/SNMP counters"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("ai_assistant_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("ai_screen_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CyberCyan
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Engineer",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "DIAGNOSTICS ENGINEER",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CyberCyan,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "AI Network Assistant",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AiAuditCard(
            auditState = auditState,
            onRunAudit = { viewModel.runNetworkHealthAudit() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickPrompts.forEach { prompt ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.sendChatMessage(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardBackgroundElevated,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = BorderStrokeColor,
                        enabled = true,
                        selected = false
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeTool != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Executing tool: $activeTool()...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = StatusAmber
                )
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(color = StatusAmber, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("ai_screen_messages_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                AssistantMessageBubble(message = msg)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask AI to inspect live telemetry or run diagnostics...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_screen_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackgroundElevated,
                    unfocusedContainerColor = CardBackgroundElevated,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val clean = inputText.trim()
                    if (clean.isNotBlank()) {
                        viewModel.sendChatMessage(clean)
                        inputText = ""
                    }
                },
                enabled = !isLoading && inputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (!isLoading && inputText.isNotBlank()) CyberCyan else CardBackgroundElevated,
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("ai_screen_send_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send prompt",
                        tint = if (inputText.isNotBlank()) Color(0xFF00363D) else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .background(
                    color = if (isUser) CyberCyan.copy(alpha = 0.16f) else CardBackground,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) CyberCyan.copy(alpha = 0.4f) else BorderStrokeColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (isUser) "You" else "Network Engineer",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) CyberCyan else Color(0xFF10B981)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                lineHeight = 19.sp
            )
        }
    }
}
