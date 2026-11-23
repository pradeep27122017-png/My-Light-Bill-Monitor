package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ChargingStatus
import com.example.data.model.MeterReading
import com.example.data.model.WeatherSnapshot
import com.example.data.model.WeatherStatus
import com.example.data.model.WeatherUnavailableReason
import com.example.domain.ValidationResult
import com.example.ui.components.formatEpochDateTime
import com.example.ui.theme.ChargingAmber
import com.example.ui.theme.EnergyEmerald
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    editingReading: MeterReading?,
    latestExistingReading: MeterReading?,
    weatherStatus: WeatherStatus,
    isSaving: Boolean,
    onRefreshWeather: () -> Unit,
    onValidate: (
        rawReading: String,
        timestampMs: Long,
        editingId: String?,
        meterResetConfirmed: Boolean,
        rawPrevFinal: String,
        rawNewStart: String,
        remarks: String
    ) -> ValidationResult,
    onSaveNew: (
        readingKwh: Double,
        timestampMs: Long,
        chargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        weather: WeatherSnapshot?,
        remarks: String?
    ) -> Unit,
    onUpdateExisting: (
        original: MeterReading,
        newReadingKwh: Double,
        newTimestampMs: Long,
        newChargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        remarks: String?,
        editReason: String
    ) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }

    val initialTimestamp = editingReading?.readingTimestampMs ?: System.currentTimeMillis()
    var selectedDate by remember(editingReading?.id) {
        mutableStateOf(Instant.ofEpochMilli(initialTimestamp).atZone(zoneId).toLocalDate())
    }
    var selectedTime by remember(editingReading?.id) {
        mutableStateOf(Instant.ofEpochMilli(initialTimestamp).atZone(zoneId).toLocalTime().withSecond(0).withNano(0))
    }

    var readingInput by remember(editingReading?.id) {
        mutableStateOf(editingReading?.readingKwh?.toString() ?: "")
    }
    var chargingStatus by remember(editingReading?.id) {
        mutableStateOf(editingReading?.chargingStatusEnum ?: ChargingStatus.NOT_CHARGING)
    }
    var remarksInput by remember(editingReading?.id) {
        mutableStateOf(editingReading?.remarks ?: "")
    }
    var editReasonInput by remember(editingReading?.id) {
        mutableStateOf("")
    }

    var isMeterReset by remember(editingReading?.id) {
        mutableStateOf(editingReading?.meterReset ?: false)
    }
    var prevMeterFinalInput by remember(editingReading?.id) {
        mutableStateOf(editingReading?.previousMeterFinalKwh?.toString() ?: "")
    }
    var newMeterStartInput by remember(editingReading?.id) {
        mutableStateOf(editingReading?.meterStartKwh?.toString() ?: "0")
    }

    var attachWeatherSnapshot by remember(editingReading?.id) {
        mutableStateOf(editingReading == null)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var resetPromptDialog by remember { mutableStateOf<ValidationResult.RequiresMeterResetConfirmation?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshWeather()
    }

    LaunchedEffect(editingReading?.id) {
        if (editingReading == null && weatherStatus is WeatherStatus.Idle) {
            onRefreshWeather()
        }
    }

    val combinedTimestampMs = remember(selectedDate, selectedTime) {
        LocalDateTime.of(selectedDate, selectedTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (editingReading != null) "Edit Meter Reading" else "Add Meter Reading",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (editingReading != null) {
                            "Changes create a permanent audit record"
                        } else {
                            "Record kWh, EV charging status & weather snapshot"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (editingReading != null) {
                    TextButton(onClick = onCancelEdit) {
                        Text("Cancel Edit")
                    }
                }
            }

            if (editingReading != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val createdStr = editingReading.entryCreatedAt?.toDate()?.time?.let(::formatEpochDateTime) ?: "Pending sync"
                        val modStr = editingReading.lastModifiedAt?.toDate()?.time?.let(::formatEpochDateTime) ?: "Pending sync"
                        Text("Original entry time: $createdStr", style = MaterialTheme.typography.bodySmall)
                        Text("Last modified time: $modStr", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (latestExistingReading != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Previous reading: ${"%,.2f".format(latestExistingReading.readingKwh)} kWh",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatEpochDateTime(latestExistingReading.readingTimestampMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Meter Reading Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = readingInput,
                        onValueChange = {
                            readingInput = it
                            validationError = null
                        },
                        label = { Text("Meter Reading (kWh) *") },
                        placeholder = { Text("e.g. 12468.5") },
                        leadingIcon = { Icon(Icons.Rounded.ElectricMeter, contentDescription = null) },
                        suffix = { Text("kWh") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meter_reading_input")
                    )

                    // Date & Time Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("reading_date_button")
                        ) {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                        }

                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("reading_time_button")
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }

                    // Car Charging Status Dropdown
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = chargingStatus.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Car Charging Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("charging_status_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            ChargingStatus.entries.forEach { statusOption ->
                                DropdownMenuItem(
                                    text = { Text(statusOption.label) },
                                    onClick = {
                                        chargingStatus = statusOption
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Controlled Meter Reset / Replacement
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = null,
                                        tint = ChargingAmber
                                    )
                                    Column {
                                        Text(
                                            text = "Controlled Meter Reset / Replacement",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Enable only if the meter was replaced or rolled over",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isMeterReset,
                                    onCheckedChange = {
                                        isMeterReset = it
                                        validationError = null
                                    },
                                    modifier = Modifier.testTag("meter_reset_switch")
                                )
                            }

                            AnimatedVisibility(visible = isMeterReset) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = prevMeterFinalInput,
                                        onValueChange = { prevMeterFinalInput = it },
                                        label = { Text("Old Meter Final Reading (kWh, optional)") },
                                        placeholder = { Text("e.g. ${latestExistingReading?.readingKwh ?: 12472}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newMeterStartInput,
                                        onValueChange = { newMeterStartInput = it },
                                        label = { Text("New Meter Baseline (kWh)") },
                                        placeholder = { Text("0") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarksInput,
                        onValueChange = {
                            remarksInput = it
                            validationError = null
                        },
                        label = {
                            Text(if (isMeterReset) "Remarks / Meter Reset Reason *" else "Remarks (Optional)")
                        },
                        placeholder = { Text("e.g. Monthly check, EV plugged in overnight") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remarks_input")
                    )

                    if (editingReading != null) {
                        OutlinedTextField(
                            value = editReasonInput,
                            onValueChange = { editReasonInput = it },
                            label = { Text("Audit Reason for Edit") },
                            placeholder = { Text("e.g. Fixed typo in decimal digit") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Weather Snapshot Card
            if (editingReading == null) {
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weather Snapshot",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onRefreshWeather) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh weather snapshot")
                                }
                                Switch(
                                    checked = attachWeatherSnapshot,
                                    onCheckedChange = { attachWeatherSnapshot = it }
                                )
                            }
                        }

                        when (weatherStatus) {
                            is WeatherStatus.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Fetching current weather...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is WeatherStatus.Ready -> {
                                val w = weatherStatus.snapshot
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EnergyEmerald, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "${"%.1f".format(w.temperatureC)}°C • ${w.condition} • ${w.humidityPercent}% RH • ${w.locationName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            is WeatherStatus.Unavailable -> {
                                Text(
                                    text = weatherStatus.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                when (weatherStatus.reason) {
                                    WeatherUnavailableReason.PERMISSION_DENIED -> {
                                        OutlinedButton(onClick = {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }) {
                                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Grant Location Permission")
                                        }
                                    }
                                    WeatherUnavailableReason.GPS_DISABLED -> {
                                        OutlinedButton(onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                            }
                                        }) {
                                            Text("Open Location Settings")
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            WeatherStatus.Idle -> {
                                Text(
                                    text = "Weather will be attached automatically if available.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val result = onValidate(
                        readingInput,
                        combinedTimestampMs,
                        editingReading?.id,
                        isMeterReset,
                        prevMeterFinalInput,
                        newMeterStartInput,
                        remarksInput
                    )
                    when (result) {
                        ValidationResult.Valid -> {
                            val kwh = readingInput.trim().toDouble()
                            val prevFinal = prevMeterFinalInput.trim().toDoubleOrNull()
                            val newStart = if (isMeterReset) (newMeterStartInput.trim().toDoubleOrNull() ?: 0.0) else null
                            if (editingReading == null) {
                                val weather = if (attachWeatherSnapshot && weatherStatus is WeatherStatus.Ready) {
                                    weatherStatus.snapshot
                                } else null
                                onSaveNew(
                                    kwh,
                                    combinedTimestampMs,
                                    chargingStatus,
                                    isMeterReset,
                                    if (isMeterReset) prevFinal else null,
                                    newStart,
                                    weather,
                                    remarksInput.trim().ifEmpty { null }
                                )
                            } else {
                                onUpdateExisting(
                                    editingReading,
                                    kwh,
                                    combinedTimestampMs,
                                    chargingStatus,
                                    isMeterReset,
                                    if (isMeterReset) prevFinal else null,
                                    newStart,
                                    remarksInput.trim().ifEmpty { null },
                                    editReasonInput.trim().ifEmpty { "Edited meter reading" }
                                )
                            }
                        }
                        is ValidationResult.RequiresMeterResetConfirmation -> {
                            resetPromptDialog = result
                        }
                        is ValidationResult.DuplicateDetected -> {
                            validationError = result.message
                        }
                        is ValidationResult.Error -> {
                            validationError = result.message
                        }
                    }
                },
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_reading_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (editingReading != null) "Update Reading" else "Save Meter Reading")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reading Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    resetPromptDialog?.let { prompt ->
        AlertDialog(
            onDismissRequest = { resetPromptDialog = null },
            title = { Text("Lower Meter Reading Detected") },
            text = {
                Text(
                    "${prompt.message}\n\nIf the utility meter was replaced or rolled over, enable Controlled Meter Reset / Replacement and enter a brief explanation in Remarks."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isMeterReset = true
                    prevMeterFinalInput = prompt.previousReading.readingKwh.toString()
                    resetPromptDialog = null
                }) {
                    Text("Enable Meter Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetPromptDialog = null }) {
                    Text("Edit Reading")
                }
            }
        )
    }
}
