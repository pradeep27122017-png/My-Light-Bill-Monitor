package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyConsumptionBucket
import com.example.ui.theme.ChargingAmber
import com.example.ui.theme.EnergyEmerald
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun InteractiveConsumptionBarChart(
    buckets: List<DailyConsumptionBucket>,
    showCostMode: Boolean,
    isWeekly: Boolean,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No interval data available for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedIndex by remember(buckets, showCostMode) {
        val lastActive = buckets.indexOfLast { it.totalKwh > 0.0 }
        mutableIntStateOf(if (lastActive >= 0) lastActive else 0)
    }

    val values = buckets.map { bucket ->
        if (showCostMode) (bucket.estimatedCost ?: 0.0) else bucket.totalKwh
    }
    val maxVal = max(1.0, values.maxOrNull() ?: 1.0)

    val primaryColor = MaterialTheme.colorScheme.primary
    val multiDayColor = ChargingAmber
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val selectedBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 450),
        label = "chartBarsAnim"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selected Bar Tooltip Summary
        val selected = buckets.getOrNull(selectedIndex)
        if (selected != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selected.date.format(DateTimeFormatter.ofPattern("EEE, MMM dd")),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${formatKwh(selected.totalKwh)} • ${
                                selected.estimatedCost?.let { formatCurrency(it, selected.currency) } ?: "No tariff"
                            }",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (selected.hasMultiDayIntervalEndingHere) {
                        Text(
                            text = "Multi-day reading interval ended on this date (not exact single-day usage):",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChargingAmber
                        )
                    }
                    if (selected.intervalDescriptions.isNotEmpty()) {
                        selected.intervalDescriptions.forEach { desc ->
                            Text(
                                text = "• $desc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No interval recorded ending on this day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Canvas Bar Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            if (buckets.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / buckets.size
                                val tapped = (offset.x / slotWidth).toInt().coerceIn(0, buckets.lastIndex)
                                selectedIndex = tapped
                            }
                        }
                    }
            ) {
                val chartHeight = size.height - 20f
                val chartWidth = size.width
                val slotWidth = chartWidth / buckets.size.toFloat()
                val barWidth = (slotWidth * 0.62f).coerceAtLeast(4f).coerceAtMost(42f)

                // Horizontal dashed gridlines (3 levels)
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val y = chartHeight * (1f - fraction) + 8f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                buckets.forEachIndexed { idx, bucket ->
                    val valRatio = ((values[idx] / maxVal).toFloat()).coerceIn(0f, 1f) * animProgress
                    val barHeight = (chartHeight - 12f) * valRatio
                    val slotLeft = idx * slotWidth
                    val barLeft = slotLeft + (slotWidth - barWidth) / 2f
                    val barTop = chartHeight - barHeight + 8f

                    if (idx == selectedIndex) {
                        drawRoundRect(
                            color = selectedBgColor,
                            topLeft = Offset(slotLeft + 2f, 4f),
                            size = Size(slotWidth - 4f, chartHeight + 8f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }

                    val barColor = when {
                        bucket.hasMultiDayIntervalEndingHere -> multiDayColor
                        else -> primaryColor
                    }

                    if (barHeight > 1f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                        )
                    } else {
                        // Subtle baseline tick
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(barLeft, chartHeight + 4f),
                            size = Size(barWidth, 4f),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }
            }
        }

        // X-axis Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isWeekly) {
                buckets.forEach { b ->
                    Text(
                        text = b.date.format(DateTimeFormatter.ofPattern("EEE")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val milestones = listOf(0, buckets.size / 4, buckets.size / 2, (buckets.size * 3) / 4, buckets.lastIndex)
                    .distinct()
                milestones.forEach { idx ->
                    Text(
                        text = "${buckets[idx].date.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = EnergyEmerald, label = "Single-day reading")
            LegendItem(color = ChargingAmber, label = "Multi-day interval ended")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
