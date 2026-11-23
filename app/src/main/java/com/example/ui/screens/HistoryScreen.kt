package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.ConsumptionInterval
import com.example.data.model.MeterReading
import com.example.data.model.ReadingAuditLog
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.EstimateDisclaimerBanner
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.formatKwh
import com.example.ui.theme.ChargingAmber
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class DateFilterPreset(val label: String) {
    ALL("All"),
    LAST_7("7 Days"),
    LAST_30("30 Days"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    intervals: List<ConsumptionInterval>,
    auditLogs: List<ReadingAuditLog>,
    buildCsvContent: () -> String,
    onEditReading: (MeterReading) -> Unit,
    onDeleteReading: (MeterReading, String) -> Unit,
    onNotifyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }

    var searchQuery by remember { mutableStateOf("") }
    var datePreset by remember { mutableStateOf(DateFilterPreset.ALL) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var pendingDeleteReading by remember { mutableStateOf<MeterReading?>(null) }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(buildCsvContent().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotifyMessage("CSV report exported successfully")
            }.onFailure { err ->
                onNotifyMessage("Export failed: ${err.localizedMessage ?: "Unable to write file"}")
            }
        }
    }

    val filteredIntervals = remember(intervals, searchQuery, datePreset, customStartDate, customEndDate) {
        val today = LocalDate.now(zoneId)
        val q = searchQuery.trim().lowercase()

        intervals
            .sortedByDescending { it.endTimestampMs }
            .filter { item ->
                val dateMatches = when (datePreset) {
                    DateFilterPreset.ALL -> true
                    DateFilterPreset.LAST_7 -> !item.endDate.isBefore(today.minusDays(6))
                    DateFilterPreset.LAST_30 -> !item.endDate.isBefore(today.minusDays(29))
                    DateFilterPreset.THIS_MONTH -> YearMonth.from(item.endDate) == YearMonth.from(today)
                    DateFilterPreset.CUSTOM -> {
                        val s = customStartDate
                        val e = customEndDate
                        (s == null || !item.endDate.isBefore(s)) && (e == null || !item.endDate.isAfter(e))
                    }
                }
                val queryMatches = if (q.isEmpty()) true else {
                    val r = item.reading
                    r.readingKwh.toString().contains(q) ||
                        (r.remarks?.lowercase()?.contains(q) == true) ||
                        (r.weatherCondition?.lowercase()?.contains(q) == true) ||
                        r.chargingStatusEnum.label.lowercase().contains(q) ||
                        item.endDate.toString().contains(q)
                }
                dateMatches && queryMatches
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Reading History", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "${filteredIntervals.size} of ${intervals.size} entries shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showAuditDialog = true },
                            modifier = Modifier.testTag("open_audit_log_button")
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Audit")
                        }
                        OutlinedButton(
                            onClick = {
                                val fileName = "electricity_readings_${LocalDate.now()}.csv"
                                csvSaveLauncher.launch(fileName)
                            },
                            enabled = intervals.isNotEmpty(),
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV")
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val outFile = File(exportDir, "electricity_readings_${LocalDate.now()}.csv")
                                    outFile.writeText(buildCsvContent(), Charsets.UTF_8)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        outFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Electricity CSV"))
                                }.onFailure { err ->
                                    onNotifyMessage("Share failed: ${err.localizedMessage ?: "Unable to share CSV"}")
                                }
                            },
                            enabled = intervals.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share CSV report")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search kWh, date, weather, EV status, remarks...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilterPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = datePreset == preset,
                            onClick = {
                                if (preset == DateFilterPreset.CUSTOM) {
                                    showDateRangeDialog = true
                                } else {
                                    datePreset = preset
                                }
                            },
                            label = {
                                val label = if (preset == DateFilterPreset.CUSTOM && customStartDate != null && customEndDate != null) {
                                    "${customStartDate} → ${customEndDate}"
                                } else {
                                    preset.label
                                }
                                Text(label)
                            }
                        )
                    }
                }
            }
        }

        if (filteredIntervals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (intervals.isEmpty()) "No readings logged yet" else "No readings match your filters",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (intervals.isEmpty()) {
                                "Add your first meter reading from the Add Reading tab."
                            } else {
                                "Try clearing the search query or selecting All dates."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredIntervals,
                key = { it.reading.id.ifBlank { it.endTimestampMs.toString() } }
            ) { item ->
                HistoryReadingCard(
                    item = item,
                    zoneId = zoneId,
                    onEdit = { onEditReading(item.reading) },
                    onDelete = { pendingDeleteReading = item.reading },
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                )
            }
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EstimateDisclaimerBanner()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showDateRangeDialog) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangeDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = rangeState.selectedStartDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    val e = rangeState.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    if (s != null) {
                        customStartDate = s
                        customEndDate = e ?: s
                        datePreset = DateFilterPreset.CUSTOM
                    }
                    showDateRangeDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState)
        }
    }

    pendingDeleteReading?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteReading = null },
            title = { Text("Delete Meter Reading?") },
            text = {
                Text(
                    "Deleting ${"%,.2f".format(target.readingKwh)} kWh (${formatEpochDateTime(target.readingTimestampMs)}) will recalculate adjacent consumption intervals and append an audit record."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteReading(target, "Deleted from History")
                    pendingDeleteReading = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteReading = null }) { Text("Cancel") }
            }
        )
    }

    if (showAuditDialog) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Reading Audit Log (${auditLogs.size})") },
            text = {
                if (auditLogs.isEmpty()) {
                    Text("No audit entries recorded yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(auditLogs, key = { it.id.ifBlank { "${it.readingId}_${it.loggedAt}" } }) { log ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.actionEnum.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = log.loggedAt?.toDate()?.time?.let(::formatEpochDateTime) ?: "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val readingChange = buildString {
                                    if (log.previousReadingKwh != null) append("${log.previousReadingKwh} kWh")
                                    if (log.previousReadingKwh != null && log.newReadingKwh != null) append(" → ")
                                    if (log.newReadingKwh != null) append("${log.newReadingKwh} kWh")
                                }
                                if (readingChange.isNotEmpty()) {
                                    Text(readingChange, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = log.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun HistoryReadingCard(
    item: ConsumptionInterval,
    zoneId: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val r = item.reading
    val zdt = Instant.ofEpochMilli(item.endTimestampMs).atZone(zoneId)
    val dateStr = zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    val timeStr = zdt.format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = modifier,
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$dateStr • $timeStr",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (r.edited) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Edited",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (r.meterReset) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Meter Reset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChargingAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${"%,.2f".format(r.readingKwh)} kWh",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit reading",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete reading",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Interval Consumption & Estimated Cost Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val deltaText = item.consumptionKwh?.let { "+${formatKwh(it)} since previous" } ?: "Baseline reading"
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.spansMultipleDays && item.startDate != null && (item.consumptionKwh ?: 0.0) > 0.0) {
                        Text(
                            text = "Interval: ${item.startDate} → ${item.endDate} (${item.daySpanCount} days)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChargingAmber
                        )
                    }
                }

                val costText = item.estimatedEnergyCost?.let {
                    "Est. ${formatCurrency(it, item.currency)}"
                } ?: "No tariff"
                Text(
                    text = costText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // EV & Weather Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChargingStatusPill(status = r.chargingStatusEnum)
                if (r.weatherTempC != null && r.weatherCondition != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${"%.1f".format(r.weatherTempC)}°C ${r.weatherCondition}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (!r.remarks.isNullOrBlank()) {
                Text(
                    text = "Remarks: ${r.remarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val created = r.entryCreatedAt?.toDate()?.time?.let(::formatEpochDateTime) ?: "Syncing"
            val modified = r.lastModifiedAt?.toDate()?.time?.let(::formatEpochDateTime) ?: "Syncing"
            Text(
                text = "Created: $created • Modified: $modified",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
