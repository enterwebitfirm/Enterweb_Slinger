// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/NetworkTopologyGraph.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enterwebslinger.network.domain.model.TopologyGraphData
import com.enterwebslinger.network.domain.model.TopologyNode
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning

@Composable
fun NetworkTopologyGraph(
    graphData: TopologyGraphData,
    onNodeSelected: (TopologyNode) -> Unit = {},
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
) {
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(graphData) {
                    detectTapGestures { tapOffset ->
                        val width = size.width
                        val height = size.height
                        val tappedNode = graphData.nodes.firstOrNull { node ->
                            val nx = node.xRatio * width
                            val ny = node.yRatio * height
                            val dx = tapOffset.x - nx
                            val dy = tapOffset.y - ny
                            (dx * dx + dy * dy) <= (28f * 28f)
                        }
                        if (tappedNode != null) {
                            selectedNodeId = tappedNode.id
                            onNodeSelected(tappedNode)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f || graphData.nodes.isEmpty()) return@Canvas

            val centerX = width * 0.5f
            val centerY = height * 0.5f
            val maxRadius = (width.coerceAtMost(height) * 0.42f).coerceAtLeast(10f)

            drawCircle(
                color = Color(0xFF1E293B),
                radius = maxRadius * 0.5f,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = maxRadius,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )

            val nodeMap = graphData.nodes.associateBy { it.id }
            for (link in graphData.links) {
                val source = nodeMap[link.sourceId]
                val target = nodeMap[link.targetId]
                if (source != null && target != null) {
                    val sx = (source.xRatio * width).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                    val sy = (source.yRatio * height).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                    val tx = (target.xRatio * width).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                    val ty = (target.yRatio * height).let { if (it.isNaN() || it.isInfinite()) 0f else it }
                    val startOffset = Offset(sx, sy)
                    val endOffset = Offset(tx, ty)

                    val linkColor = when {
                        link.latencyMs < 20 -> StatusConnected.copy(alpha = 0.6f)
                        link.latencyMs < 80 -> StatusWarning.copy(alpha = 0.6f)
                        else -> StatusError.copy(alpha = 0.6f)
                    }

                    drawLine(
                        color = linkColor,
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            for (node in graphData.nodes) {
                val rawNx = node.xRatio * width
                val rawNy = node.yRatio * height
                val nx = if (rawNx.isNaN() || rawNx.isInfinite()) 0f else rawNx
                val ny = if (rawNy.isNaN() || rawNy.isInfinite()) 0f else rawNy
                val isSelected = node.id == selectedNodeId

                val (nodeColor, radius) = when {
                    node.isGateway -> Pair(CyberCyan, 20.dp.toPx())
                    node.isLocalDevice -> Pair(Color(0xFF38BDF8), 16.dp.toPx())
                    node.openPortCount > 5 -> Pair(StatusWarning, 15.dp.toPx())
                    else -> Pair(StatusConnected, 14.dp.toPx())
                }

                drawCircle(
                    color = nodeColor.copy(alpha = if (isSelected) 0.5f else 0.25f),
                    radius = radius + if (isSelected) 10.dp.toPx() else 6.dp.toPx(),
                    center = Offset(nx, ny)
                )

                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = radius,
                    center = Offset(nx, ny)
                )
                drawCircle(
                    color = nodeColor,
                    radius = radius * 0.7f,
                    center = Offset(nx, ny)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = node.isGateway || isSelected
                    }
                    val labelY = ny + radius + 32f
                    drawText(node.label.take(16), nx, labelY, paint)

                    val subPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val subText = if (node.isGateway) "GW (${node.latencyMs}ms)" else "${node.openPortCount} ports"
                    drawText(subText, nx, labelY + 26f, subPaint)
                }
            }
        }
    }
}
