// === FILE: app/src/main/java/com/enterwebslinger/network/ui/components/CanvasCharts.kt ===
package com.enterwebslinger.network.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.RssiDataPoint
import com.enterwebslinger.network.domain.model.WifiScanEntry
import com.enterwebslinger.network.ui.theme.Band24G
import com.enterwebslinger.network.ui.theme.Band5G
import com.enterwebslinger.network.ui.theme.Band6G
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time Wi-Fi RSSI (dBm) & Link Speed line chart over rolling 60-second window
 */
@Composable
fun RealtimeSignalChart(
    dataPoints: List<RssiDataPoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("signal_chart_container")
    ) {
        if (dataPoints.isEmpty() || dataPoints.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Awaiting RSSI telemetry samples...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize().testTag("signal_chart_canvas")) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f || dataPoints.size < 2) return@Canvas

                val gridDbm = listOf(-40, -60, -80, -100)
                gridDbm.forEach { dbm ->
                    val rawY = ((dbm - (-30f)) / (-100f - (-30f))) * h
                    val y = if (rawY.isNaN() || rawY.isInfinite()) 0f else rawY.coerceIn(0f, h)
                    drawLine(
                        color = BorderStrokeColor.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                val path = Path()
                val fillPath = Path()
                val stepX = (w / (dataPoints.size - 1).coerceAtLeast(1)).let {
                    if (it.isNaN() || it.isInfinite()) 0f else it
                }

                dataPoints.forEachIndexed { index, dp ->
                    val normY = ((dp.rssiDbm.toFloat() - (-30f)) / (-100f - (-30f))).coerceIn(0f, 1f)
                    val rawX = index * stepX
                    val rawY = normY * h
                    val x = if (rawX.isNaN() || rawX.isInfinite()) 0f else rawX.coerceIn(0f, w)
                    val y = if (rawY.isNaN() || rawY.isInfinite()) 0f else rawY.coerceIn(0f, h)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }

                    if (index == dataPoints.size - 1) {
                        fillPath.lineTo(x, h)
                        fillPath.close()

                        drawCircle(
                            color = CyberCyan,
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CyberCyan.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                drawPath(
                    path = path,
                    color = CyberCyan,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-60s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text("-30s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text("Now", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
            }
        }
    }
}

/**
 * High-precision Speedometer Gauge Canvas Component
 */
@Composable
fun SpeedometerGauge(
    currentSpeedMbps: Float,
    maxGaugeSpeed: Float = 500f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("speedometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas

            val center = Offset(size.width / 2, size.height / 2)
            val minDim = size.minDimension
            val radius = ((minDim / 2) - 20.dp.toPx()).coerceAtLeast(10f)

            val startAngle = 140f
            val sweepAngle = 260f

            drawArc(
                color = BorderStrokeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            val safeSpeed = if (currentSpeedMbps.isNaN() || currentSpeedMbps.isInfinite()) 0f else currentSpeedMbps.coerceAtLeast(0f)
            val safeMax = if (maxGaugeSpeed.isNaN() || maxGaugeSpeed.isInfinite() || maxGaugeSpeed <= 0f) 500f else maxGaugeSpeed
            val fraction = (safeSpeed / safeMax).coerceIn(0f, 1f)
            val activeSweep = (sweepAngle * fraction).let { if (it.isNaN() || it.isInfinite()) 0f else it }

            if (activeSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(CyberCyan, StatusEmerald, StatusAmber),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            val needleAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
            val rawNx = (center.x + (radius - 12.dp.toPx()) * cos(needleAngleRad)).toFloat()
            val rawNy = (center.y + (radius - 12.dp.toPx()) * sin(needleAngleRad)).toFloat()
            val nx = if (rawNx.isNaN() || rawNx.isInfinite()) center.x else rawNx
            val ny = if (rawNy.isNaN() || rawNy.isInfinite()) center.y else rawNy
            val needleEnd = Offset(nx, ny)

            drawLine(
                color = Color.White,
                start = center,
                end = needleEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = CyberCyan, radius = 7.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val displaySpeed = if (currentSpeedMbps.isNaN() || currentSpeedMbps.isInfinite()) 0.0f else currentSpeedMbps
            Text(
                text = String.format("%.1f", displaySpeed),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = TextPrimary
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.titleMedium,
                color = CyberCyan
            )
        }
    }
}

/**
 * Ping latency bar chart on Canvas
 */
@Composable
fun LatencyBarChart(
    rtts: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(CardBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("latency_bar_chart")
    ) {
        if (rtts.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val validRtts = rtts.filter { !it.isNaN() && it.isFinite() && it >= 0f }
        if (validRtts.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val maxRtt = (validRtts.maxOrNull() ?: 50f).coerceAtLeast(10f)
        val barCount = validRtts.size.coerceAtLeast(1)
        val barWidth = ((w / barCount) * 0.7f).coerceAtLeast(1f)
        val gap = ((w / barCount) * 0.3f).coerceAtLeast(0f)

        validRtts.forEachIndexed { i, rtt ->
            val barHeight = ((rtt / maxRtt).coerceIn(0.05f, 1f)) * h
            val x = (i * (barWidth + gap)).let { if (it.isNaN() || it.isInfinite()) 0f else it }
            val y = (h - barHeight).let { if (it.isNaN() || it.isInfinite()) 0f else it }

            val color = when {
                rtt < 35f -> StatusEmerald
                rtt < 90f -> StatusAmber
                else -> StatusCrimson
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}

/**
 * Wi-Fi Channel Spectrum Parabolic Curves Canvas (Ubiquiti WiFiman style)
 */
@Composable
fun WifiChannelGraph(
    entries: List<WifiScanEntry>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("wifi_channel_graph")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || entries.isEmpty()) return@Canvas

            val levels = listOf(-30, -50, -70, -90)
            levels.forEach { dbm ->
                val rawY = ((dbm - (-20f)) / (-100f - (-20f))) * h
                val y = if (rawY.isNaN() || rawY.isInfinite()) 0f else rawY.coerceIn(0f, h)
                drawLine(
                    color = BorderStrokeColor.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            entries.forEach { entry ->
                val channel = entry.channel.coerceIn(1, 165)
                val normX = if (entry.band == "2.4 GHz") {
                    (channel - 1).toFloat() / 13f
                } else {
                    ((channel - 36).toFloat() / (165 - 36).toFloat()).coerceIn(0f, 1f)
                }

                val centerX = (normX * w).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                val curveWidth = ((w * 0.18f).coerceAtLeast(40f)).let { if (it.isNaN() || it.isInfinite()) 40f else it }

                val rawPeakY = ((entry.rssiDbm.toFloat() - (-20f)) / (-100f - (-20f))).coerceIn(0.1f, 0.95f) * h
                val peakY = if (rawPeakY.isNaN() || rawPeakY.isInfinite()) h * 0.5f else rawPeakY

                val curveColor = when (entry.band) {
                    "2.4 GHz" -> Band24G
                    "5 GHz" -> Band5G
                    else -> Band6G
                }

                val curvePath = Path().apply {
                    moveTo(centerX - curveWidth / 2, h)
                    cubicTo(
                        centerX - curveWidth / 4, h,
                        centerX - curveWidth / 4, peakY,
                        centerX, peakY
                    )
                    cubicTo(
                        centerX + curveWidth / 4, peakY,
                        centerX + curveWidth / 4, h,
                        centerX + curveWidth / 2, h
                    )
                    close()
                }

                drawPath(
                    path = curvePath,
                    color = curveColor.copy(alpha = if (entry.isCurrentNetwork) 0.45f else 0.2f)
                )

                drawPath(
                    path = curvePath,
                    color = if (entry.isCurrentNetwork) CyberCyan else curveColor,
                    style = Stroke(width = if (entry.isCurrentNetwork) 3.dp.toPx() else 1.8.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ch 1", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("Ch 6", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("Ch 11", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("Ch 36+", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
