// === FILE: app/src/main/java/com/enterwebslinger/network/ui/components/ExplainResultButton.kt ===
package com.enterwebslinger.network.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary

@Composable
fun ExplainResultSection(
    diagnosisKey: String,
    contextTitle: String,
    rawResultText: String,
    aiViewModel: AiAssistantViewModel?,
    buttonTestTag: String,
    modifier: Modifier = Modifier
) {
    if (aiViewModel == null || rawResultText.isBlank()) return
    val map by aiViewModel.microDiagnosisMap.collectAsStateWithLifecycle()
    val loadingKeys by aiViewModel.microDiagnosisLoading.collectAsStateWithLifecycle()
    val isLoading = diagnosisKey in loadingKeys
    val explanation = map[diagnosisKey]

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { aiViewModel.explainTechnicalResult(diagnosisKey, contextTitle, rawResultText) },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(buttonTestTag),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.65f))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Engineer Analyzing...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explain This Result", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!explanation.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("${buttonTestTag}_card"),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Diagnosis: $contextTitle",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = CyberCyan
                            )
                        }
                        IconButton(
                            onClick = { aiViewModel.clearMicroDiagnosis(diagnosisKey) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss AI Explanation", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
