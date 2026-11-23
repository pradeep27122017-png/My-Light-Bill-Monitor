package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.ThemePreferenceStore
import com.example.data.model.AppThemeMode
import com.example.data.model.MeterReading
import com.example.data.remote.WeatherRepository
import com.example.data.repository.EnergyRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AddReadingScreen
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricityMonitorTheme
import com.example.ui.viewmodel.EnergyViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class AppTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    ADD_READING("add_reading", "Add", Icons.Rounded.AddCircle, Icons.Outlined.AddCircleOutline),
    ANALYSIS("analysis", "Analysis", Icons.Rounded.Analytics, Icons.Outlined.Analytics),
    HISTORY("history", "History", Icons.Rounded.History, Icons.Outlined.History),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { ThemePreferenceStore(context.applicationContext) }
            val themeMode by themeStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)

            ElectricityMonitorTheme(themeMode = themeMode) {
                AppContent(themeStore = themeStore, themeMode = themeMode)
            }
        }
    }
}

@Composable
fun AppContent(
    themeStore: ThemePreferenceStore,
    themeMode: AppThemeMode
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: "User",
                userEmail = user.email,
                themeStore = themeStore,
                themeMode = themeMode
            )
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    themeStore: ThemePreferenceStore,
    themeMode: AppThemeMode
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: EnergyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                EnergyViewModel(
                    repository = EnergyRepository(db),
                    weatherRepository = WeatherRepository(app),
                    themeStore = themeStore,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val readingsUi by viewModel.readingsState.collectAsStateWithLifecycle()
    val tariffsUi by viewModel.tariffsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val intervals by viewModel.intervalsState.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val weeklySummary by viewModel.weeklyAnalysis.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlyAnalysis.collectAsStateWithLifecycle()
    val selectedWeekStart by viewModel.selectedWeekStart.collectAsStateWithLifecycle()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val weatherStatus by viewModel.weatherStatus.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val citySearchResults by viewModel.citySearchResults.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }
    var editingReading by remember { mutableStateOf<MeterReading?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshWeather(preferGps = true)
    }

    // Request coarse location permission politely on first authenticated launch for weather snapshots
    LaunchedEffect(currentUserId) {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCoarse) {
            viewModel.refreshWeather(preferGps = true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(readingsUi, tariffsUi) {
        val errMsg = (readingsUi as? UiState.Error)?.message ?: (tariffsUi as? UiState.Error)?.message
        if (errMsg != null) {
            snackbarHostState.showSnackbar(errMsg)
        }
    }

    BackHandler(enabled = editingReading != null || currentTab != AppTab.DASHBOARD) {
        if (editingReading != null) {
            editingReading = null
            currentTab = AppTab.HISTORY
        } else {
            currentTab = AppTab.DASHBOARD
        }
    }

    val tariffs = (tariffsUi as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab != AppTab.ADD_READING) editingReading = null
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab != AppTab.ADD_READING) editingReading = null
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            displayName = userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                            summary = dashboardSummary,
                            syncState = syncState,
                            weatherStatus = weatherStatus,
                            onRefreshWeather = { viewModel.refreshWeather(preferGps = true) },
                            onAddReadingClick = {
                                editingReading = null
                                currentTab = AppTab.ADD_READING
                            },
                            onConfigureTariffClick = {
                                currentTab = AppTab.SETTINGS
                            }
                        )
                        AppTab.ADD_READING -> AddReadingScreen(
                            editingReading = editingReading,
                            latestExistingReading = dashboardSummary.latestReading,
                            weatherStatus = weatherStatus,
                            isSaving = isSaving,
                            onRefreshWeather = { viewModel.refreshWeather(preferGps = true) },
                            onValidate = viewModel::validateReadingInput,
                            onSaveNew = { kwh, ts, status, reset, prevFinal, newStart, weather, remarks ->
                                viewModel.saveNewReading(
                                    readingKwh = kwh,
                                    timestampMs = ts,
                                    chargingStatus = status,
                                    meterReset = reset,
                                    previousMeterFinalKwh = prevFinal,
                                    meterStartKwh = newStart,
                                    weather = weather,
                                    remarks = remarks,
                                    onSuccess = { currentTab = AppTab.DASHBOARD }
                                )
                            },
                            onUpdateExisting = { orig, kwh, ts, status, reset, prevFinal, newStart, remarks, reason ->
                                viewModel.updateExistingReading(
                                    original = orig,
                                    newReadingKwh = kwh,
                                    newTimestampMs = ts,
                                    newChargingStatus = status,
                                    meterReset = reset,
                                    previousMeterFinalKwh = prevFinal,
                                    meterStartKwh = newStart,
                                    remarks = remarks,
                                    editReason = reason,
                                    onSuccess = {
                                        editingReading = null
                                        currentTab = AppTab.HISTORY
                                    }
                                )
                            },
                            onCancelEdit = {
                                editingReading = null
                                currentTab = AppTab.HISTORY
                            }
                        )
                        AppTab.ANALYSIS -> AnalysisScreen(
                            selectedWeekStart = selectedWeekStart,
                            selectedYearMonth = selectedYearMonth,
                            weeklySummary = weeklySummary,
                            monthlySummary = monthlySummary,
                            onShiftWeek = viewModel::shiftWeek,
                            onResetWeek = viewModel::resetToCurrentWeek,
                            onShiftMonth = viewModel::shiftMonth,
                            onResetMonth = viewModel::resetToCurrentMonth
                        )
                        AppTab.HISTORY -> HistoryScreen(
                            intervals = intervals,
                            auditLogs = auditLogs,
                            buildCsvContent = viewModel::buildCsvContent,
                            onEditReading = { reading ->
                                editingReading = reading
                                currentTab = AppTab.ADD_READING
                            },
                            onDeleteReading = viewModel::deleteReading,
                            onNotifyMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            userProfile = userProfile,
                            userEmail = userEmail,
                            themeMode = themeMode,
                            settings = settings,
                            tariffs = tariffs,
                            syncState = syncState,
                            citySearchResults = citySearchResults,
                            onThemeModeChange = viewModel::updateThemeMode,
                            onSaveTariff = { tariff -> viewModel.saveTariff(tariff) },
                            onDeleteTariff = viewModel::deleteTariff,
                            onSaveWeatherSettings = viewModel::saveWeatherAndGeneralSettings,
                            onSearchCity = viewModel::searchWeatherCities,
                            onClearCitySearch = viewModel::clearCitySearchResults,
                            onCheckSync = viewModel::checkSyncStatus,
                            buildCsvContent = viewModel::buildCsvContent,
                            buildBackupJsonContent = viewModel::buildBackupJsonContent,
                            onNotifyMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onSignOutClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
