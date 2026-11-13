// === FILE: app/src/main/java/com/enterwebslinger/network/ui/components/AiAuditCard.kt ===
package com.enterwebslinger.network.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.AiAuditFinding
import com.enterwebslinger.network.domain.model.AiAuditReport
import com.enterwebslinger.network.ui.screens.ai.AuditUiState
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun AiAuditCard(
    auditState: AuditUiState,
    onRunAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFindingsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_audit_card"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.5f)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Network Engineer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Live Telemetry Reasoning Engine",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CyberCyan
                        )
                    }
                }

                if (auditState is AuditUiState.Success) {
                    IconButton(
                        onClick = { shareAuditReport(context, auditState.report) },
                        modifier = Modifier.testTag("share_audit_report_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Audit", tint = CyberCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (auditState) {
                is AuditUiState.Idle -> {
                    Text(
                        text = "Run a complete one-tap diagnostic audit across Wi-Fi RF, gateway latency, bufferbloat, and LAN security risks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRunAudit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_ai_audit_button")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run AI Network Audit", fontWeight = FontWeight.Bold)
                    }
                }

                is AuditUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing PHY, bufferbloat, and security telemetry...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                }

                is AuditUiState.Error -> {
                    Text(
                        text = "Audit Error: ${auditState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusCrimson
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRunAudit,
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Audit", color = TextPrimary)
                    }
                }

                is AuditUiState.Success -> {
                    val report = auditState.report
                    val scoreColor = when {
                        report.healthScore >= 80 -> StatusEmerald
                        report.healthScore >= 50 -> StatusAmber
                        else -> StatusCrimson
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HealthScoreMiniGauge(score = report.healthScore, color = scoreColor)

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = when (report.severity) {
                                        "OPTIMAL" -> StatusEmerald.copy(alpha = 0.2f)
                                        "WARNING" -> StatusAmber.copy(alpha = 0.2f)
                                        else -> StatusCrimson.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = report.severity,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = when (report.severity) {
                                            "OPTIMAL" -> StatusEmerald
                                            "WARNING" -> StatusAmber
                                            else -> StatusCrimson
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    color = CardBackgroundElevated,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = report.primaryBottleneck.replace("_", " "),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.executiveSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFindingsExpanded = !isFindingsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Key Findings & Actionable Fixes (${report.findings.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CyberCyan
                        )
                        Icon(
                            imageVector = if (isFindingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyberCyan
                        )
                    }

                    AnimatedVisibility(visible = isFindingsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            report.findings.forEach { finding ->
                                FindingItemCard(finding = finding)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onRunAudit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Audit Network")
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthScoreMiniGauge(score: Int, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = score.toFloat() / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "health_gauge_anim"
    )

    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val strokeWidth = 8.dp.toPx()

            drawArc(
                color = CardBackgroundElevated,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )

            val sweep = (animatedProgress * 360f).let { if (it.isNaN() || it.isInfinite()) 0f else it }
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = TextPrimary
            )
            Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
        }
    }
}

@Composable
private fun FindingItemCard(finding: AiAuditFinding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            if (finding.technicalEvidence.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Evidence: ${finding.technicalEvidence}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.plainEnglishExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (finding.recommendedFix.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Fix: ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = StatusEmerald
                    )
                    Text(
                        text = finding.recommendedFix,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

private fun shareAuditReport(context: Context, report: AiAuditReport) {
    val content = buildString {
        appendLine("# Enterweb Slinger - AI Network Health Audit")
        appendLine("Health Score: **${report.healthScore}/100** | Severity: **${report.severity}**")
        appendLine("Primary Bottleneck: `${report.primaryBottleneck}`")
        appendLine()
        appendLine("### Executive Summary")
        appendLine(report.executiveSummary)
        appendLine()
        appendLine("### Detailed Findings & Recommended Fixes")
        report.findings.forEachIndexed { i, f ->
            appendLine("#### ${i + 1}. ${f.title}")
            appendLine("- **Evidence**: `${f.technicalEvidence}`")
            appendLine("- **Explanation**: ${f.plainEnglishExplanation}")
            appendLine("- **Recommended Fix**: ${f.recommendedFix}")
            appendLine()
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Enterweb Slinger - AI Network Health Audit")
        putExtra(Intent.EXTRA_TEXT, content)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share AI Audit Report"))
}
