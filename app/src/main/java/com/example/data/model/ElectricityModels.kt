package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.time.LocalDate

enum class ChargingStatus(val label: String, val firestoreValue: String) {
    CHARGING("Charging", "CHARGING"),
    NOT_CHARGING("Not Charging", "NOT_CHARGING"),
    FULLY_CHARGED("Fully Charged", "FULLY_CHARGED");

    companion object {
        fun fromRaw(raw: String?): ChargingStatus =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: NOT_CHARGING
    }
}

enum class AppThemeMode(val label: String, val firestoreValue: String) {
    SYSTEM("System Default", "SYSTEM"),
    LIGHT("Clean White", "LIGHT"),
    DARK_NAVY("Dark Navy", "DARK_NAVY");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: SYSTEM
    }
}

enum class AuditAction(val label: String, val firestoreValue: String) {
    CREATE("Created", "CREATE"),
    UPDATE("Edited", "UPDATE"),
    DELETE("Deleted", "DELETE"),
    METER_RESET("Meter Reset", "METER_RESET");

    companion object {
        fun fromRaw(raw: String?): AuditAction =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: UPDATE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = AppThemeMode.SYSTEM.firestoreValue,
    val weatherAutoFetch: Boolean = true,
    val manualLocationName: String? = null,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val defaultCurrency: String = "LKR",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MeterReading(
    @DocumentId val id: String = "",
    val userId: String = "",
    val readingKwh: Double = 0.0,
    val readingTimestampMs: Long = 0L,
    val chargingStatus: String = ChargingStatus.NOT_CHARGING.firestoreValue,
    val meterReset: Boolean = false,
    val previousMeterFinalKwh: Double? = null,
    val meterStartKwh: Double? = null,
    val weatherTempC: Double? = null,
    val weatherCondition: String? = null,
    val weatherHumidity: Long? = null,
    val weatherWindKmh: Double? = null,
    val weatherRainMm: Double? = null,
    val weatherLocationName: String? = null,
    val remarks: String? = null,
    val edited: Boolean = false,
    @ServerTimestamp val entryCreatedAt: Timestamp? = null,
    @ServerTimestamp val lastModifiedAt: Timestamp? = null
) {
    val chargingStatusEnum: ChargingStatus
        get() = ChargingStatus.fromRaw(chargingStatus)
}

@Keep
@IgnoreExtraProperties
data class TariffTier(
    val fromKwh: Double = 0.0,
    val toKwh: Double? = null,
    val ratePerKwh: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class TariffConfiguration(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val currency: String = "LKR",
    val ratePerKwh: Double = 0.0,
    val useTieredRates: Boolean = false,
    val tiers: List<TariffTier> = emptyList(),
    val fixedMonthlyCharge: Double = 0.0,
    val otherCharges: Double = 0.0,
    val effectiveFromEpochMs: Long = 0L,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReadingAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val readingId: String = "",
    val action: String = AuditAction.CREATE.firestoreValue,
    val previousReadingKwh: Double? = null,
    val newReadingKwh: Double? = null,
    val previousTimestampMs: Long? = null,
    val newTimestampMs: Long? = null,
    val reason: String = "",
    @ServerTimestamp val loggedAt: Timestamp? = null
) {
    val actionEnum: AuditAction
        get() = AuditAction.fromRaw(action)
}

data class WeatherSnapshot(
    val temperatureC: Double,
    val condition: String,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val rainMm: Double?,
    val weatherCode: Int,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

sealed interface WeatherStatus {
    data object Idle : WeatherStatus
    data object Loading : WeatherStatus
    data class Ready(val snapshot: WeatherSnapshot) : WeatherStatus
    data class Unavailable(val reason: WeatherUnavailableReason, val message: String) : WeatherStatus
}

enum class WeatherUnavailableReason {
    PERMISSION_DENIED,
    GPS_DISABLED,
    NO_INTERNET,
    API_FAILURE
}

data class SyncMetadataState(
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false,
    val pendingReadingCount: Int = 0,
    val lastSyncedAtMs: Long? = null
)

data class ConsumptionInterval(
    val reading: MeterReading,
    val previousReading: MeterReading?,
    val consumptionKwh: Double?,
    val startTimestampMs: Long?,
    val endTimestampMs: Long,
    val startDate: LocalDate?,
    val endDate: LocalDate,
    val spansMultipleDays: Boolean,
    val daySpanCount: Int,
    val estimatedEnergyCost: Double?,
    val currency: String,
    val appliedTariffName: String?
)

data class DailyConsumptionBucket(
    val date: LocalDate,
    val totalKwh: Double,
    val singleDayKwh: Double,
    val multiDayEndedKwh: Double,
    val hasMultiDayIntervalEndingHere: Boolean,
    val intervalDescriptions: List<String>,
    val estimatedCost: Double?,
    val currency: String,
    val chargingStatuses: List<ChargingStatus>,
    val readingCount: Int
)

data class PeriodAnalysisSummary(
    val totalKwh: Double,
    val estimatedEnergyCost: Double?,
    val fixedAndOtherCharges: Double?,
    val estimatedTotalCost: Double?,
    val currency: String,
    val dailyAverageKwh: Double,
    val highestDay: DailyConsumptionBucket?,
    val lowestDay: DailyConsumptionBucket?,
    val previousPeriodTotalKwh: Double?,
    val deltaPercentVsPrevious: Double?,
    val buckets: List<DailyConsumptionBucket>,
    val containsMultiDayIntervals: Boolean,
    val chargingCounts: Map<ChargingStatus, Int>
)

data class DashboardSummary(
    val latestReading: MeterReading?,
    val todayKwh: Double,
    val todayIncludesMultiDayInterval: Boolean,
    val todayIntervalNote: String?,
    val currentWeekKwh: Double,
    val currentMonthKwh: Double,
    val estimatedMonthCost: Double?,
    val estimatedTodayCost: Double?,
    val activeCurrency: String,
    val hasConfiguredTariff: Boolean,
    val totalCumulativeKwh: Double,
    val totalReadingCount: Int
)
