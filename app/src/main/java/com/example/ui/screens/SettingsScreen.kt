package com.example.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.AppThemeMode
import com.example.data.model.SyncMetadataState
import com.example.data.model.TariffConfiguration
import com.example.data.model.TariffTier
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.data.remote.GeocodingResultDto
import com.example.ui.components.EstimateDisclaimerBanner
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.formatCurrency
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    userEmail: String?,
    themeMode: AppThemeMode,
    settings: UserSettings?,
    tariffs: List<TariffConfiguration>,
    syncState: SyncMetadataState,
    citySearchResults: List<GeocodingResultDto>,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onSaveTariff: (TariffConfiguration) -> Unit,
    onDeleteTariff: (String) -> Unit,
    onSaveWeatherSettings: (
        weatherAutoFetch: Boolean,
        manualLocationName: String?,
        manualLatitude: Double?,
        manualLongitude: Double?,
        defaultCurrency: String
    ) -> Unit,
    onSearchCity: (String) -> Unit,
    onClearCitySearch: () -> Unit,
    onCheckSync: () -> Unit,
    buildCsvContent: () -> String,
    buildBackupJsonContent: () -> String,
    onNotifyMessage: (String) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showTariffDialog by remember { mutableStateOf(false) }
    var editingTariff by remember { mutableStateOf<TariffConfiguration?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    var autoFetchGps by remember(settings?.weatherAutoFetch) {
        mutableStateOf(settings?.weatherAutoFetch ?: true)
    }
    var defaultCurrency by remember(settings?.defaultCurrency) {
        mutableStateOf(settings?.defaultCurrency ?: "LKR")
    }
    var cityQuery by remember { mutableStateOf("") }
    var manualCityName by remember(settings?.manualLocationName) {
        mutableStateOf(settings?.manualLocationName)
    }
    var manualLat by remember(settings?.manualLatitude) {
        mutableStateOf(settings?.manualLatitude)
    }
    var manualLon by remember(settings?.manualLongitude) {
        mutableStateOf(settings?.manualLongitude)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotifyMessage(
            if (granted) "Location permission granted" else "Location permission denied. You can set a manual city below."
        )
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(buildCsvContent().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess { onNotifyMessage("CSV exported") }
                .onFailure { onNotifyMessage("CSV export failed: ${it.localizedMessage}") }
        }
    }

    val jsonBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(buildBackupJsonContent().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess { onNotifyMessage("JSON backup exported") }
                .onFailure { onNotifyMessage("Backup failed: ${it.localizedMessage}") }
        }
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
                Text("Settings", style = MaterialTheme.typography.headlineMedium)

                // 1. User Profile Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName?.ifBlank { "Electricity Monitor User" }
                                    ?: "Electricity Monitor User",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = userProfile?.email ?: userEmail ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Synced across devices via Google Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 2. Theme Selector (White & Dark Navy)
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
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val tag = when (mode) {
                                    AppThemeMode.LIGHT -> "theme_light_chip"
                                    AppThemeMode.DARK_NAVY -> "theme_dark_navy_chip"
                                    AppThemeMode.SYSTEM -> "theme_system_chip"
                                }
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }
                    }
                }

                // 3. Electricity Tariff Configurations (Historical Versions Supported)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Electricity Tariff Settings", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Historical tariff versions preserve past reports",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    editingTariff = null
                                    showTariffDialog = true
                                },
                                modifier = Modifier.testTag("add_tariff_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (tariffs.isEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "No tariffs configured yet. Add your utility rate per kWh (or slab tiers) and monthly fixed charges in LKR.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                )
                            }
                        } else {
                            tariffs.forEachIndexed { idx, tariff ->
                                val effectiveDateStr = Instant.ofEpochMilli(tariff.effectiveFromEpochMs)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(tariff.name, style = MaterialTheme.typography.titleSmall)
                                                if (idx == 0) {
                                                    Surface(
                                                        shape = MaterialTheme.shapes.small,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "Latest",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            val rateLabel = if (tariff.useTieredRates) {
                                                "${tariff.tiers.size} slab tiers • fallback ${formatCurrency(tariff.ratePerKwh, tariff.currency)}/kWh"
                                            } else {
                                                "${formatCurrency(tariff.ratePerKwh, tariff.currency)} / kWh"
                                            }
                                            Text(rateLabel, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = "Effective: $effectiveDateStr • Fixed: ${formatCurrency(tariff.fixedMonthlyCharge + tariff.otherCharges, tariff.currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                editingTariff = tariff
                                                showTariffDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit tariff")
                                            }
                                            IconButton(onClick = { onDeleteTariff(tariff.id) }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete tariff", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        EstimateDisclaimerBanner()
                    }
                }

                // 4. Weather & Regional Settings
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Weather Location & Currency", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-fetch GPS Weather", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Automatically capture Open-Meteo weather with readings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoFetchGps,
                                onCheckedChange = { autoFetchGps = it }
                            )
                        }

                        OutlinedButton(
                            onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Request / Verify Device Location Permission")
                        }

                        OutlinedTextField(
                            value = cityQuery,
                            onValueChange = {
                                cityQuery = it
                                onSearchCity(it)
                            },
                            label = { Text("Fallback City (when GPS disabled)") },
                            placeholder = { Text(manualCityName ?: "Search city, e.g. Colombo") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (citySearchResults.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    citySearchResults.forEach { city ->
                                        val label = listOfNotNull(city.name, city.admin1, city.country).joinToString(", ")
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    manualCityName = label
                                                    manualLat = city.latitude
                                                    manualLon = city.longitude
                                                    cityQuery = ""
                                                    onClearCitySearch()
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        if (!manualCityName.isNullOrBlank()) {
                            Text(
                                text = "Saved fallback location: $manualCityName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = defaultCurrency,
                            onValueChange = { defaultCurrency = it.uppercase().take(6) },
                            label = { Text("Default Display Currency") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                onSaveWeatherSettings(
                                    autoFetchGps,
                                    manualCityName,
                                    manualLat,
                                    manualLon,
                                    defaultCurrency.ifBlank { "LKR" }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Location & Currency Settings")
                        }
                    }
                }

                // 5. Data Synchronization & Export / Backup
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Sync & Backup", style = MaterialTheme.typography.titleMedium)
                            }
                            SyncStatusBadge(syncState = syncState)
                        }

                        OutlinedButton(
                            onClick = onCheckSync,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verify Pending Cloud Writes")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { csvExportLauncher.launch("electricity_readings_${LocalDate.now()}.csv") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save CSV")
                            }
                            OutlinedButton(
                                onClick = { jsonBackupLauncher.launch("electricity_backup_${LocalDate.now()}.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("JSON Backup")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val file = File(dir, "electricity_backup_${LocalDate.now()}.json")
                                    file.writeText(buildBackupJsonContent(), Charsets.UTF_8)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share JSON Backup"))
                                }.onFailure { onNotifyMessage("Share failed: ${it.localizedMessage}") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Backup Archive")
                        }
                    }
                }

                // 6. Account Settings
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Your account session is authenticated via Google Sign-In and protected by per-user Firestore security rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showSignOutConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showTariffDialog) {
        TariffEditorDialog(
            initial = editingTariff,
            defaultCurrency = defaultCurrency.ifBlank { "LKR" },
            onDismiss = { showTariffDialog = false },
            onSave = { tariff ->
                onSaveTariff(tariff)
                showTariffDialog = false
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Any synced readings remain safely stored in your cloud account.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    onSignOutClick()
                }) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TariffEditorDialog(
    initial: TariffConfiguration?,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (TariffConfiguration) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var ratePerKwh by remember { mutableStateOf(initial?.ratePerKwh?.toString() ?: "") }
    var useTiered by remember { mutableStateOf(initial?.useTieredRates ?: false) }
    var fixedCharge by remember { mutableStateOf(initial?.fixedMonthlyCharge?.toString() ?: "0") }
    var otherCharge by remember { mutableStateOf(initial?.otherCharges?.toString() ?: "0") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var effectiveEpochMs by remember {
        mutableLongStateOf(initial?.effectiveFromEpochMs ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val editableTiers = remember {
        mutableStateListOf<Triple<String, String, String>>().apply {
            if (initial != null && initial.tiers.isNotEmpty()) {
                initial.tiers.forEach { t ->
                    add(Triple(t.fromKwh.toString(), t.toKwh?.toString() ?: "", t.ratePerKwh.toString()))
                }
            } else {
                add(Triple("0", "60", ""))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Tariff Version" else "Edit Tariff") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tariff Plan Name *") },
                    placeholder = { Text("e.g. Home Utility Plan 2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(6) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f)
                    )
                    OutlinedTextField(
                        value = ratePerKwh,
                        onValueChange = { ratePerKwh = it },
                        label = { Text(if (useTiered) "Fallback Rate/kWh" else "Rate / kWh *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Tiered / Slab Rates", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = useTiered, onCheckedChange = { useTiered = it })
                }

                if (useTiered) {
                    editableTiers.forEachIndexed { idx, tier ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = tier.first,
                                onValueChange = { editableTiers[idx] = tier.copy(first = it) },
                                label = { Text("From") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tier.second,
                                onValueChange = { editableTiers[idx] = tier.copy(second = it) },
                                label = { Text("To") },
                                placeholder = { Text("∞") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tier.third,
                                onValueChange = { editableTiers[idx] = tier.copy(third = it) },
                                label = { Text("Rate") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (editableTiers.size > 1) {
                                IconButton(onClick = { editableTiers.removeAt(idx) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove slab")
                                }
                            }
                        }
                    }
                    if (editableTiers.size < 10) {
                        TextButton(onClick = {
                            val nextFrom = editableTiers.lastOrNull()?.second?.takeIf { it.isNotBlank() } ?: ""
                            editableTiers.add(Triple(nextFrom, "", ""))
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Slab Tier")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fixedCharge,
                        onValueChange = { fixedCharge = it },
                        label = { Text("Fixed Monthly") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = otherCharge,
                        onValueChange = { otherCharge = it },
                        label = { Text("Other Charges") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                val effDate = Instant.ofEpochMilli(effectiveEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Effective From: ${effDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val baseRate = ratePerKwh.trim().toDoubleOrNull()
                if (name.trim().isEmpty()) {
                    validationError = "Please enter a tariff plan name."
                    return@TextButton
                }
                if (baseRate == null || baseRate < 0.0) {
                    validationError = "Enter a valid non-negative rate per kWh."
                    return@TextButton
                }
                val parsedTiers = if (useTiered) {
                    editableTiers.mapNotNull { t ->
                        val f = t.first.trim().toDoubleOrNull()
                        val r = t.third.trim().toDoubleOrNull()
                        val to = t.second.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                        if (f != null && r != null && r >= 0.0) TariffTier(f, to, r) else null
                    }
                } else emptyList()

                if (useTiered && parsedTiers.isEmpty()) {
                    validationError = "Add at least one valid slab tier with rate."
                    return@TextButton
                }

                onSave(
                    TariffConfiguration(
                        id = initial?.id ?: "",
                        userId = initial?.userId ?: "",
                        name = name.trim(),
                        currency = currency.ifBlank { "LKR" },
                        ratePerKwh = baseRate,
                        useTieredRates = useTiered,
                        tiers = parsedTiers,
                        fixedMonthlyCharge = fixedCharge.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        otherCharges = otherCharge.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        effectiveFromEpochMs = effectiveEpochMs,
                        notes = notes.trim().ifEmpty { null }
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = effectiveEpochMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        effectiveEpochMs = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}
