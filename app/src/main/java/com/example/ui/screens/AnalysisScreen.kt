package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ChargingStatus
import com.example.data.model.PeriodAnalysisSummary
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.EstimateDisclaimerBanner
import com.example.ui.components.EvFutureNoteBanner
import com.example.ui.components.InteractiveConsumptionBarChart
import com.example.ui.components.SummaryMetricCard
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatKwh
import com.example.ui.theme.ChargingAmber
import com.example.ui.theme.EnergyEmerald
import com.example.ui.theme.SkyWeather
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun AnalysisScreen(
    selectedWeekStart: LocalDate,
    selectedYearMonth: YearMonth,
    weeklySummary: PeriodAnalysisSummary?,
    monthlySummary: PeriodAnalysisSummary?,
    onShiftWeek: (Long) -> Unit,
    onResetWeek: () -> Unit,
    onShiftMonth: (Long) -> Unit,
    onResetMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Weekly, 1 = Monthly
    var showCostOnChart by remember { mutableStateOf(false) }

    val activeSummary = if (selectedTab == 0) weeklySummary else monthlySummary

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Energy Analysis",
                    style = MaterialTheme.typography.headlineMedium
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("analysis_weekly_tab")
                    ) {
                        Text("Weekly")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("analysis_monthly_tab")
                    ) {
                        Text("Monthly")
                    }
                }

                // Period Navigation Header
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (selectedTab == 0) onShiftWeek(-1) else onShiftMonth(-1) }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous period")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val title = if (selectedTab == 0) {
                                val fmt = DateTimeFormatter.ofPattern("MMM dd")
                                "${selectedWeekStart.format(fmt)} – ${selectedWeekStart.plusDays(6).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"
                            } else {
                                selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            }
                            Text(text = title, style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = { if (selectedTab == 0) onResetWeek() else onResetMonth() }
                            ) {
                                Text(if (selectedTab == 0) "Jump to This Week" else "Jump to This Month")
                            }
                        }

                        IconButton(
                            onClick = { if (selectedTab == 0) onShiftWeek(1) else onShiftMonth(1) }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next period")
                        }
                    }
                }

                if (activeSummary == null || activeSummary.totalKwh == 0.0 && activeSummary.buckets.all { it.readingCount == 0 }) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No readings in this ${if (selectedTab == 0) "week" else "month"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Log at least two consecutive meter readings to calculate interval consumption and charts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Chart Card
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "Daily Consumption (Week)" else "Daily Consumption (Month)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !showCostOnChart,
                                        onClick = { showCostOnChart = false },
                                        label = { Text("kWh") }
                                    )
                                    FilterChip(
                                        selected = showCostOnChart,
                                        onClick = { showCostOnChart = true },
                                        label = { Text(activeSummary.currency) }
                                    )
                                }
                            }

                            InteractiveConsumptionBarChart(
                                buckets = activeSummary.buckets,
                                showCostMode = showCostOnChart,
                                isWeekly = selectedTab == 0
                            )

                            if (activeSummary.containsMultiDayIntervals) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = "Amber bars indicate multi-day intervals ending on that date (not exact single-day consumption).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Key Metrics Grid
                    val comparisonText = when {
                        activeSummary.previousPeriodTotalKwh == null -> "No prior period data"
                        activeSummary.deltaPercentVsPrevious == null -> "Prior: ${formatKwh(activeSummary.previousPeriodTotalKwh)}"
                        else -> {
                            val sign = if (activeSummary.deltaPercentVsPrevious >= 0) "+" else ""
                            "$sign${"%.1f".format(activeSummary.deltaPercentVsPrevious)}% vs prior (${formatKwh(activeSummary.previousPeriodTotalKwh)})"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricCard(
                            title = if (selectedTab == 0) "Weekly Total" else "Monthly Total",
                            value = formatKwh(activeSummary.totalKwh),
                            subtitle = comparisonText,
                            icon = Icons.Rounded.Bolt,
                            accentColor = EnergyEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Daily Average",
                            value = formatKwh(activeSummary.dailyAverageKwh),
                            subtitle = "Averaged across elapsed reading days",
                            icon = Icons.Rounded.TrendingUp,
                            accentColor = SkyWeather,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricCard(
                            title = if (selectedTab == 0) "Est. Weekly Cost" else "Est. Monthly Cost",
                            value = activeSummary.estimatedTotalCost?.let {
                                formatCurrency(it, activeSummary.currency)
                            } ?: "Tariff Not Set",
                            subtitle = if (selectedTab == 1 && activeSummary.fixedAndOtherCharges != null) {
                                "Includes ${formatCurrency(activeSummary.fixedAndOtherCharges, activeSummary.currency)} fixed/other"
                            } else {
                                "Energy estimate"
                            },
                            icon = Icons.Rounded.Payments,
                            accentColor = ChargingAmber,
                            badgeText = if (activeSummary.estimatedTotalCost != null) "Estimate" else null,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedTab == 1) {
                            SummaryMetricCard(
                                title = "Highest Usage Day",
                                value = activeSummary.highestDay?.let { formatKwh(it.totalKwh) } ?: "—",
                                subtitle = activeSummary.highestDay?.let {
                                    "${it.date.format(DateTimeFormatter.ofPattern("MMM dd"))}${if (it.hasMultiDayIntervalEndingHere) " (Multi-day)" else ""}"
                                } ?: "No consumption recorded",
                                icon = Icons.Rounded.ArrowUpward,
                                accentColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedTab == 1) {
                        SummaryMetricCard(
                            title = "Lowest Usage Day",
                            value = activeSummary.lowestDay?.let { formatKwh(it.totalKwh) } ?: "—",
                            subtitle = activeSummary.lowestDay?.let {
                                "${it.date.format(DateTimeFormatter.ofPattern("MMM dd"))}${if (it.hasMultiDayIntervalEndingHere) " (Multi-day)" else ""}"
                            } ?: "No consumption recorded",
                            icon = Icons.Rounded.ArrowDownward,
                            accentColor = EnergyEmerald,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Car Charging History in Period
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.ElectricCar, contentDescription = null, tint = ChargingAmber)
                                Text(
                                    text = "Car Charging Status History",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChargingStatus.entries.forEach { status ->
                                    val count = activeSummary.chargingCounts[status] ?: 0
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ChargingStatusPill(status = status)
                                        Text(
                                            text = "$count logs",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            EvFutureNoteBanner()
                        }
                    }

                    EstimateDisclaimerBanner()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
