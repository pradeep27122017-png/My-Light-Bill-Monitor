package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ThemePreferenceStore
import com.example.data.model.AppThemeMode
import com.example.data.model.ChargingStatus
import com.example.data.model.ConsumptionInterval
import com.example.data.model.DashboardSummary
import com.example.data.model.MeterReading
import com.example.data.model.PeriodAnalysisSummary
import com.example.data.model.ReadingAuditLog
import com.example.data.model.SyncMetadataState
import com.example.data.model.TariffConfiguration
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.data.model.WeatherSnapshot
import com.example.data.model.WeatherStatus
import com.example.data.remote.GeocodingResultDto
import com.example.data.remote.WeatherRepository
import com.example.data.repository.EnergyRepository
import com.example.data.repository.ReadingsSnapshotResult
import com.example.domain.CsvExporter
import com.example.domain.EnergyCalculator
import com.example.domain.ReadingValidator
import com.example.domain.ValidationResult
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class EnergyViewModel(
    private val repository: EnergyRepository,
    private val weatherRepository: WeatherRepository,
    private val themeStore: ThemePreferenceStore,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedWeekStart = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val selectedWeekStart: StateFlow<LocalDate> = _selectedWeekStart.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    private val _weatherStatus = MutableStateFlow<WeatherStatus>(WeatherStatus.Idle)
    val weatherStatus: StateFlow<WeatherStatus> = _weatherStatus.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _citySearchResults = MutableStateFlow<List<GeocodingResultDto>>(emptyList())
    val citySearchResults: StateFlow<List<GeocodingResultDto>> = _citySearchResults.asStateFlow()

    val readingsState: StateFlow<UiState<ReadingsSnapshotResult>> =
        repository.observeReadingsWithSync(currentUserId)
            .map<ReadingsSnapshotResult, UiState<ReadingsSnapshotResult>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing readings", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load meter readings"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val tariffsState: StateFlow<UiState<List<TariffConfiguration>>> =
        repository.observeTariffs(currentUserId)
            .map<List<TariffConfiguration>, UiState<List<TariffConfiguration>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing tariffs", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load tariffs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val settingsState: StateFlow<UserSettings?> =
        repository.observeSettings(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing settings", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val userProfileState: StateFlow<UserProfile?> =
        repository.observeUserProfile(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing user profile", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val auditLogsState: StateFlow<List<ReadingAuditLog>> =
        repository.observeAuditLogs(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing audit logs", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val syncState: StateFlow<SyncMetadataState> = readingsState
        .map { state ->
            (state as? UiState.Success)?.data?.syncState ?: SyncMetadataState()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SyncMetadataState()
        )

    val intervalsState: StateFlow<List<ConsumptionInterval>> = combine(
        readingsState,
        tariffsState,
        settingsState
    ) { readingsUi, tariffsUi, settings ->
        val readings = (readingsUi as? UiState.Success)?.data?.readings.orEmpty()
        val tariffs = (tariffsUi as? UiState.Success)?.data.orEmpty()
        val currency = settings?.defaultCurrency ?: "LKR"
        EnergyCalculator.buildIntervals(readings, tariffs, currency)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        readingsState,
        tariffsState,
        settingsState
    ) { readingsUi, tariffsUi, settings ->
        val readings = (readingsUi as? UiState.Success)?.data?.readings.orEmpty()
        val tariffs = (tariffsUi as? UiState.Success)?.data.orEmpty()
        val currency = settings?.defaultCurrency ?: "LKR"
        EnergyCalculator.buildDashboardSummary(readings, tariffs, currency)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardSummary(
            latestReading = null,
            todayKwh = 0.0,
            todayIncludesMultiDayInterval = false,
            todayIntervalNote = null,
            currentWeekKwh = 0.0,
            currentMonthKwh = 0.0,
            estimatedMonthCost = null,
            estimatedTodayCost = null,
            activeCurrency = "LKR",
            hasConfiguredTariff = false,
            totalCumulativeKwh = 0.0,
            totalReadingCount = 0
        )
    )

    val weeklyAnalysis: StateFlow<PeriodAnalysisSummary?> = combine(
        readingsState,
        tariffsState,
        settingsState,
        selectedWeekStart
    ) { readingsUi, tariffsUi, settings, weekStart ->
        val readings = (readingsUi as? UiState.Success)?.data?.readings ?: return@combine null
        val tariffs = (tariffsUi as? UiState.Success)?.data.orEmpty()
        val currency = settings?.defaultCurrency ?: "LKR"
        EnergyCalculator.analyzeWeek(readings, tariffs, weekStart, currency)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null
    )

    val monthlyAnalysis: StateFlow<PeriodAnalysisSummary?> = combine(
        readingsState,
        tariffsState,
        settingsState,
        selectedYearMonth
    ) { readingsUi, tariffsUi, settings, ym ->
        val readings = (readingsUi as? UiState.Success)?.data?.readings ?: return@combine null
        val tariffs = (tariffsUi as? UiState.Success)?.data.orEmpty()
        val currency = settings?.defaultCurrency ?: "LKR"
        EnergyCalculator.analyzeMonth(readings, tariffs, ym, currency)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null
    )

    init {
        Firebase.auth.currentUser?.let { user ->
            viewModelScope.launch {
                repository.ensureUserProfile(user)
            }
        }
        viewModelScope.launch {
            settingsState.collect { settings ->
                if (settings != null) {
                    themeStore.setThemeMode(AppThemeMode.fromRaw(settings.themeMode))
                }
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun refreshWeather(preferGps: Boolean = true) {
        viewModelScope.launch {
            _weatherStatus.value = WeatherStatus.Loading
            val settings = settingsState.value
            val autoFetch = settings?.weatherAutoFetch ?: true
            val result = weatherRepository.fetchWeather(
                manualLatitude = settings?.manualLatitude,
                manualLongitude = settings?.manualLongitude,
                manualLocationName = settings?.manualLocationName,
                preferGps = preferGps && autoFetch
            )
            _weatherStatus.value = result
        }
    }

    fun searchWeatherCities(query: String) {
        viewModelScope.launch {
            val res = weatherRepository.searchLocations(query)
            _citySearchResults.value = res.getOrDefault(emptyList())
        }
    }

    fun clearCitySearchResults() {
        _citySearchResults.value = emptyList()
    }

    fun shiftWeek(deltaWeeks: Long) {
        _selectedWeekStart.value = _selectedWeekStart.value.plusWeeks(deltaWeeks)
    }

    fun resetToCurrentWeek() {
        _selectedWeekStart.value = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun shiftMonth(deltaMonths: Long) {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(deltaMonths)
    }

    fun resetToCurrentMonth() {
        _selectedYearMonth.value = YearMonth.now()
    }

    fun validateReadingInput(
        rawReadingInput: String,
        timestampMs: Long,
        editingReadingId: String? = null,
        meterResetConfirmed: Boolean = false,
        rawPreviousMeterFinalInput: String = "",
        rawNewMeterStartInput: String = "",
        remarks: String = ""
    ): ValidationResult {
        val currentReadings = (readingsState.value as? UiState.Success)?.data?.readings.orEmpty()
        return ReadingValidator.validate(
            rawReadingInput = rawReadingInput,
            timestampMs = timestampMs,
            existingReadings = currentReadings,
            editingReadingId = editingReadingId,
            meterResetConfirmed = meterResetConfirmed,
            rawPreviousMeterFinalInput = rawPreviousMeterFinalInput,
            rawNewMeterStartInput = rawNewMeterStartInput,
            remarks = remarks
        )
    }

    fun saveNewReading(
        readingKwh: Double,
        timestampMs: Long,
        chargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        weather: WeatherSnapshot?,
        remarks: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addMeterReading(
                readingKwh = readingKwh,
                readingTimestampMs = timestampMs,
                chargingStatus = chargingStatus,
                meterReset = meterReset,
                previousMeterFinalKwh = previousMeterFinalKwh,
                meterStartKwh = meterStartKwh,
                weather = weather,
                remarks = remarks
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Meter reading saved"
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = "Failed to save reading: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun updateExistingReading(
        original: MeterReading,
        newReadingKwh: Double,
        newTimestampMs: Long,
        newChargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        remarks: String?,
        editReason: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateMeterReading(
                original = original,
                newReadingKwh = newReadingKwh,
                newTimestampMs = newTimestampMs,
                newChargingStatus = newChargingStatus,
                meterReset = meterReset,
                previousMeterFinalKwh = previousMeterFinalKwh,
                meterStartKwh = meterStartKwh,
                remarks = remarks,
                editReason = editReason
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Reading updated with audit record"
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = "Update failed: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun deleteReading(reading: MeterReading, reason: String = "Deleted by user") {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteMeterReading(reading, reason)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Reading deleted & logged in audit history"
            }.onFailure { err ->
                _actionMessage.value = "Delete failed: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun saveTariff(tariff: TariffConfiguration, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveTariff(tariff)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Tariff configuration saved"
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = "Failed to save tariff: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun deleteTariff(tariffId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTariff(tariffId)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Tariff version removed"
            }.onFailure { err ->
                _actionMessage.value = "Failed to delete tariff: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themeStore.setThemeMode(mode)
            val current = settingsState.value
            repository.saveSettings(
                themeMode = mode,
                weatherAutoFetch = current?.weatherAutoFetch ?: true,
                manualLocationName = current?.manualLocationName,
                manualLatitude = current?.manualLatitude,
                manualLongitude = current?.manualLongitude,
                defaultCurrency = current?.defaultCurrency ?: "LKR"
            )
        }
    }

    fun saveWeatherAndGeneralSettings(
        weatherAutoFetch: Boolean,
        manualLocationName: String?,
        manualLatitude: Double?,
        manualLongitude: Double?,
        defaultCurrency: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val currentTheme = AppThemeMode.fromRaw(settingsState.value?.themeMode)
            val res = repository.saveSettings(
                themeMode = currentTheme,
                weatherAutoFetch = weatherAutoFetch,
                manualLocationName = manualLocationName,
                manualLatitude = manualLatitude,
                manualLongitude = manualLongitude,
                defaultCurrency = defaultCurrency
            )
            _isSaving.value = false
            res.onSuccess {
                _actionMessage.value = "Settings saved"
                refreshWeather(preferGps = weatherAutoFetch)
            }.onFailure { err ->
                _actionMessage.value = "Failed to save settings: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun checkSyncStatus() {
        viewModelScope.launch {
            val res = repository.waitForPendingWrites()
            res.onSuccess {
                _actionMessage.value = "All pending records synced with cloud"
            }.onFailure {
                _actionMessage.value = "Offline — pending entries will sync automatically when online"
            }
        }
    }

    fun buildCsvContent(): String = CsvExporter.toCsv(intervalsState.value)

    fun buildBackupJsonContent(): String {
        val tariffs = (tariffsState.value as? UiState.Success)?.data.orEmpty()
        return CsvExporter.toBackupJson(intervalsState.value, tariffs, auditLogsState.value)
    }

    private companion object {
        const val TAG = "EnergyVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
