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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChargingStatus
import com.example.data.model.DashboardSummary
import com.example.data.model.SyncMetadataState
import com.example.data.model.WeatherSnapshot
import com.example.data.model.WeatherStatus
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.EstimateDisclaimerBanner
import com.example.ui.components.EvFutureNoteBanner
import com.example.ui.components.SummaryMetricCard
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.WeatherCard
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.formatKwh
import com.example.ui.theme.ChargingAmber
import com.example.ui.theme.EnergyEmerald
import com.example.ui.theme.SkyWeather

@Composable
fun DashboardScreen(
    displayName: String,
    summary: DashboardSummary,
    syncState: SyncMetadataState,
    weatherStatus: WeatherStatus,
    onRefreshWeather: () -> Unit,
    onAddReadingClick: () -> Unit,
    onConfigureTariffClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = summary.latestReading
    val lastSavedWeather = latest?.let { r ->
        val temp = r.weatherTempC
        val cond = r.weatherCondition
        if (temp != null && cond != null) {
            WeatherSnapshot(
                temperatureC = temp,
                condition = cond,
                humidityPercent = (r.weatherHumidity ?: 0L).toInt(),
                windSpeedKmh = r.weatherWindKmh ?: 0.0,
                rainMm = r.weatherRainMm,
                weatherCode = 0,
                locationName = r.weatherLocationName ?: "Recorded Location",
                latitude = 0.0,
                longitude = 0.0
            )
        } else null
    }

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Energy Dashboard",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Welcome back, $displayName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SyncStatusBadge(syncState = syncState)
                }

                // Onboarding / Empty States
                if (summary.totalReadingCount == 0) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Start Monitoring Your Electricity",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Log your current electricity meter reading (kWh) to start tracking daily, weekly, and monthly consumption alongside EV charging and weather snapshots.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onAddReadingClick,
                                    modifier = Modifier.testTag("dashboard_add_reading_cta")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Log Reading")
                                }
                                if (!summary.hasConfiguredTariff) {
                                    OutlinedButton(onClick = onConfigureTariffClick) {
                                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Set Tariff")
                                    }
                                }
                            }
                        }
                    }
                } else if (!summary.hasConfiguredTariff) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, ChargingAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Configure Electricity Tariff",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.tariff_not_configured),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(onClick = onConfigureTariffClick) {
                                Text("Configure")
                            }
                        }
                    }
                }

                // Hero Latest Reading Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ElectricMeter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Latest Meter Reading",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ChargingStatusPill(
                                status = latest?.chargingStatusEnum ?: ChargingStatus.NOT_CHARGING
                            )
                        }

                        if (latest != null) {
                            Text(
                                text = "${"%,.2f".format(latest.readingKwh)} kWh",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Last recorded: ${formatEpochDateTime(latest.readingTimestampMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (latest.meterReset) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "Meter Reset",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ChargingAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "— kWh",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No meter readings recorded yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2x2 Summary Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryMetricCard(
                        title = "Today's Consumption",
                        value = formatKwh(summary.todayKwh),
                        subtitle = summary.todayIntervalNote ?: (
                            summary.estimatedTodayCost?.let {
                                "Est. ${formatCurrency(it, summary.activeCurrency)}"
                            } ?: "Recorded today"
                        ),
                        icon = Icons.Rounded.Today,
                        accentColor = EnergyEmerald,
                        badgeText = if (summary.todayIncludesMultiDayInterval) "Multi-day" else null,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Current Week",
                        value = formatKwh(summary.currentWeekKwh),
                        subtitle = "Mon–Sun recorded intervals",
                        icon = Icons.Rounded.DateRange,
                        accentColor = SkyWeather,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryMetricCard(
                        title = "Current Month",
                        value = formatKwh(summary.currentMonthKwh),
                        subtitle = "${summary.totalReadingCount} total readings logged",
                        icon = Icons.Rounded.CalendarMonth,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Est. Month Cost",
                        value = summary.estimatedMonthCost?.let {
                            formatCurrency(it, summary.activeCurrency)
                        } ?: "Not Set",
                        subtitle = if (summary.hasConfiguredTariff) {
                            "Estimate incl. monthly charges"
                        } else {
                            "Configure tariff in Settings"
                        },
                        icon = Icons.Rounded.Payments,
                        accentColor = ChargingAmber,
                        badgeText = if (summary.hasConfiguredTariff) "Estimate" else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Car Charging Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ElectricCar,
                                    contentDescription = null,
                                    tint = ChargingAmber
                                )
                                Text(
                                    text = "Car Charging Tracker",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            ChargingStatusPill(
                                status = latest?.chargingStatusEnum ?: ChargingStatus.NOT_CHARGING
                            )
                        }
                        EvFutureNoteBanner()
                    }
                }

                // Live Weather Card
                WeatherCard(
                    weatherStatus = weatherStatus,
                    lastSavedWeather = lastSavedWeather,
                    onRefreshClick = onRefreshWeather
                )

                if (summary.hasConfiguredTariff) {
                    EstimateDisclaimerBanner()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
