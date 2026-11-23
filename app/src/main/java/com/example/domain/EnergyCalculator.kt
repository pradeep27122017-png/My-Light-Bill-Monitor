package com.example.domain

import com.example.data.model.ChargingStatus
import com.example.data.model.ConsumptionInterval
import com.example.data.model.DailyConsumptionBucket
import com.example.data.model.DashboardSummary
import com.example.data.model.MeterReading
import com.example.data.model.PeriodAnalysisSummary
import com.example.data.model.TariffConfiguration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

object EnergyCalculator {

    fun toLocalDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    /**
     * Converts chronological meter readings into non-overlapping consumption intervals.
     * Supports controlled meter resets/replacements without generating false negative spikes.
     */
    fun buildIntervals(
        readings: List<MeterReading>,
        tariffs: List<TariffConfiguration>,
        defaultCurrency: String = "LKR",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ConsumptionInterval> {
        if (readings.isEmpty()) return emptyList()

        val sorted = readings.sortedWith(
            compareBy<MeterReading> { it.readingTimestampMs }
                .thenBy { it.entryCreatedAt?.toDate()?.time ?: 0L }
                .thenBy { it.id }
        )

        val monthlyCumulativeKwh = mutableMapOf<YearMonth, Double>()
        val intervals = mutableListOf<ConsumptionInterval>()

        for (i in sorted.indices) {
            val current = sorted[i]
            val endDate = toLocalDate(current.readingTimestampMs, zoneId)
            val endMonth = YearMonth.from(endDate)

            if (i == 0) {
                val tariff = TariffCalculator.resolveTariffForTimestamp(tariffs, current.readingTimestampMs)
                intervals.add(
                    ConsumptionInterval(
                        reading = current,
                        previousReading = null,
                        consumptionKwh = null,
                        startTimestampMs = null,
                        endTimestampMs = current.readingTimestampMs,
                        startDate = null,
                        endDate = endDate,
                        spansMultipleDays = false,
                        daySpanCount = 1,
                        estimatedEnergyCost = null,
                        currency = tariff?.currency ?: defaultCurrency,
                        appliedTariffName = tariff?.name
                    )
                )
                continue
            }

            val prev = sorted[i - 1]
            val startDate = toLocalDate(prev.readingTimestampMs, zoneId)
            val daySpan = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            val spansMultipleDays = startDate != endDate

            val kwh = if (current.meterReset) {
                val oldMeterDelta = if (current.previousMeterFinalKwh != null && current.previousMeterFinalKwh >= prev.readingKwh) {
                    current.previousMeterFinalKwh - prev.readingKwh
                } else {
                    0.0
                }
                val newMeterBaseline = current.meterStartKwh ?: current.readingKwh
                val newMeterDelta = max(0.0, current.readingKwh - newMeterBaseline)
                oldMeterDelta + newMeterDelta
            } else {
                max(0.0, current.readingKwh - prev.readingKwh)
            }

            val tariff = TariffCalculator.resolveTariffForTimestamp(tariffs, current.readingTimestampMs)
            val priorMonthKwh = monthlyCumulativeKwh[endMonth] ?: 0.0
            val estEnergyCost = tariff?.let {
                TariffCalculator.computeMarginalEnergyCharge(priorMonthKwh, kwh, it)
            }
            monthlyCumulativeKwh[endMonth] = priorMonthKwh + kwh

            intervals.add(
                ConsumptionInterval(
                    reading = current,
                    previousReading = prev,
                    consumptionKwh = kwh,
                    startTimestampMs = prev.readingTimestampMs,
                    endTimestampMs = current.readingTimestampMs,
                    startDate = startDate,
                    endDate = endDate,
                    spansMultipleDays = spansMultipleDays,
                    daySpanCount = max(1, daySpan),
                    estimatedEnergyCost = estEnergyCost,
                    currency = tariff?.currency ?: defaultCurrency,
                    appliedTariffName = tariff?.name
                )
            )
        }

        return intervals
    }

    fun buildDashboardSummary(
        readings: List<MeterReading>,
        tariffs: List<TariffConfiguration>,
        defaultCurrency: String = "LKR",
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DashboardSummary {
        val intervals = buildIntervals(readings, tariffs, defaultCurrency, zoneId)
        val latest = intervals.lastOrNull()?.reading
        val activeTariff = tariffs.maxByOrNull { it.effectiveFromEpochMs }
        val currency = activeTariff?.currency ?: defaultCurrency

        val todayIntervals = intervals.filter { it.endDate == today && it.consumptionKwh != null }
        val todayKwh = todayIntervals.sumOf { it.consumptionKwh ?: 0.0 }
        val todayHasMultiDay = todayIntervals.any { it.spansMultipleDays }
        val todayNote = todayIntervals.firstOrNull { it.spansMultipleDays }?.let { interval ->
            "Includes multi-day interval (${interval.startDate} → ${interval.endDate}, ${interval.daySpanCount} days)"
        }

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekIntervals = intervals.filter { !it.endDate.isBefore(weekStart) && !it.endDate.isAfter(weekEnd) && it.consumptionKwh != null }
        val weekKwh = weekIntervals.sumOf { it.consumptionKwh ?: 0.0 }

        val currentYm = YearMonth.from(today)
        val monthSummary = analyzeMonth(readings, tariffs, currentYm, defaultCurrency, zoneId)

        val todayCost = if (activeTariff != null) {
            val sumToday = todayIntervals.mapNotNull { it.estimatedEnergyCost }
            if (todayIntervals.isEmpty()) 0.0 else sumToday.sum()
        } else null

        val totalCumulative = intervals.sumOf { it.consumptionKwh ?: 0.0 }

        return DashboardSummary(
            latestReading = latest,
            todayKwh = todayKwh,
            todayIncludesMultiDayInterval = todayHasMultiDay,
            todayIntervalNote = todayNote,
            currentWeekKwh = weekKwh,
            currentMonthKwh = monthSummary.totalKwh,
            estimatedMonthCost = monthSummary.estimatedTotalCost,
            estimatedTodayCost = todayCost,
            activeCurrency = currency,
            hasConfiguredTariff = tariffs.isNotEmpty(),
            totalCumulativeKwh = totalCumulative,
            totalReadingCount = readings.size
        )
    }

    fun analyzeWeek(
        readings: List<MeterReading>,
        tariffs: List<TariffConfiguration>,
        weekStart: LocalDate,
        defaultCurrency: String = "LKR",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeriodAnalysisSummary {
        val intervals = buildIntervals(readings, tariffs, defaultCurrency, zoneId)
        val weekEnd = weekStart.plusDays(6)
        val prevWeekStart = weekStart.minusWeeks(1)
        val prevWeekEnd = weekStart.minusDays(1)

        val currentSummary = buildPeriodSummary(
            intervals = intervals,
            tariffs = tariffs,
            startDate = weekStart,
            endDate = weekEnd,
            includeMonthlyFixedCharges = false,
            defaultCurrency = defaultCurrency,
            zoneId = zoneId
        )

        val prevKwh = intervals
            .filter { !it.endDate.isBefore(prevWeekStart) && !it.endDate.isAfter(prevWeekEnd) }
            .sumOf { it.consumptionKwh ?: 0.0 }
        val hasPrevReadings = intervals.any { !it.endDate.isBefore(prevWeekStart) && !it.endDate.isAfter(prevWeekEnd) && it.consumptionKwh != null }

        val deltaPercent = if (hasPrevReadings && prevKwh > 0.0) {
            ((currentSummary.totalKwh - prevKwh) / prevKwh) * 100.0
        } else null

        return currentSummary.copy(
            previousPeriodTotalKwh = if (hasPrevReadings) prevKwh else null,
            deltaPercentVsPrevious = deltaPercent
        )
    }

    fun analyzeMonth(
        readings: List<MeterReading>,
        tariffs: List<TariffConfiguration>,
        yearMonth: YearMonth,
        defaultCurrency: String = "LKR",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeriodAnalysisSummary {
        val intervals = buildIntervals(readings, tariffs, defaultCurrency, zoneId)
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val prevYm = yearMonth.minusMonths(1)
        val prevStart = prevYm.atDay(1)
        val prevEnd = prevYm.atEndOfMonth()

        val currentSummary = buildPeriodSummary(
            intervals = intervals,
            tariffs = tariffs,
            startDate = startDate,
            endDate = endDate,
            includeMonthlyFixedCharges = true,
            defaultCurrency = defaultCurrency,
            zoneId = zoneId
        )

        val prevKwh = intervals
            .filter { !it.endDate.isBefore(prevStart) && !it.endDate.isAfter(prevEnd) }
            .sumOf { it.consumptionKwh ?: 0.0 }
        val hasPrevReadings = intervals.any { !it.endDate.isBefore(prevStart) && !it.endDate.isAfter(prevEnd) && it.consumptionKwh != null }

        val deltaPercent = if (hasPrevReadings && prevKwh > 0.0) {
            ((currentSummary.totalKwh - prevKwh) / prevKwh) * 100.0
        } else null

        return currentSummary.copy(
            previousPeriodTotalKwh = if (hasPrevReadings) prevKwh else null,
            deltaPercentVsPrevious = deltaPercent
        )
    }

    private fun buildPeriodSummary(
        intervals: List<ConsumptionInterval>,
        tariffs: List<TariffConfiguration>,
        startDate: LocalDate,
        endDate: LocalDate,
        includeMonthlyFixedCharges: Boolean,
        defaultCurrency: String,
        zoneId: ZoneId
    ): PeriodAnalysisSummary {
        val periodIntervals = intervals.filter {
            !it.endDate.isBefore(startDate) && !it.endDate.isAfter(endDate)
        }
        val byDate = periodIntervals.groupBy { it.endDate }

        val buckets = mutableListOf<DailyConsumptionBucket>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            val dayItems = byDate[cursor].orEmpty()
            val singleKwh = dayItems.filter { !it.spansMultipleDays }.sumOf { it.consumptionKwh ?: 0.0 }
            val multiKwh = dayItems.filter { it.spansMultipleDays }.sumOf { it.consumptionKwh ?: 0.0 }
            val totalDayKwh = singleKwh + multiKwh
            val dayCostParts = dayItems.mapNotNull { it.estimatedEnergyCost }
            val dayCurrency = dayItems.lastOrNull()?.currency
                ?: TariffCalculator.resolveTariffForTimestamp(
                    tariffs,
                    cursor.atStartOfDay(zoneId).toInstant().toEpochMilli()
                )?.currency
                ?: defaultCurrency

            val descriptions = dayItems.mapNotNull { interval ->
                val kwh = interval.consumptionKwh ?: return@mapNotNull null
                if (interval.spansMultipleDays && interval.startDate != null) {
                    "${interval.startDate} → ${interval.endDate} (${interval.daySpanCount}d): ${"%.2f".format(kwh)} kWh"
                } else {
                    "Single-day reading: ${"%.2f".format(kwh)} kWh"
                }
            }

            buckets.add(
                DailyConsumptionBucket(
                    date = cursor,
                    totalKwh = totalDayKwh,
                    singleDayKwh = singleKwh,
                    multiDayEndedKwh = multiKwh,
                    hasMultiDayIntervalEndingHere = dayItems.any { it.spansMultipleDays && (it.consumptionKwh ?: 0.0) > 0.0 },
                    intervalDescriptions = descriptions,
                    estimatedCost = if (dayCostParts.isNotEmpty()) dayCostParts.sum() else if (tariffs.isNotEmpty()) 0.0 else null,
                    currency = dayCurrency,
                    chargingStatuses = dayItems.map { it.reading.chargingStatusEnum },
                    readingCount = dayItems.size
                )
            )
            cursor = cursor.plusDays(1)
        }

        val totalKwh = buckets.sumOf { it.totalKwh }
        val activeBucketsWithConsumption = buckets.filter { it.readingCount > 0 && it.totalKwh > 0.0 }
        val highestDay = activeBucketsWithConsumption.maxByOrNull { it.totalKwh }
        val lowestDay = activeBucketsWithConsumption.minByOrNull { it.totalKwh }

        // Average daily consumption across elapsed days with valid intervals (or days in period with readings)
        val firstIntervalStart = periodIntervals.mapNotNull { it.startDate }.minOrNull()
        val lastIntervalEnd = periodIntervals.filter { it.consumptionKwh != null }.maxOfOrNull { it.endDate }
        val elapsedDays = if (firstIntervalStart != null && lastIntervalEnd != null) {
            val effectiveStart = if (firstIntervalStart.isBefore(startDate)) firstIntervalStart else startDate
            max(1L, ChronoUnit.DAYS.between(effectiveStart, lastIntervalEnd) + 1L).toDouble()
        } else {
            max(1, buckets.size).toDouble()
        }
        val dailyAverageKwh = if (totalKwh > 0.0) totalKwh / elapsedDays else 0.0

        val periodEndMs = endDate.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
        val periodTariff = TariffCalculator.resolveTariffForTimestamp(tariffs, periodEndMs)
        val currency = periodTariff?.currency ?: defaultCurrency

        val energyCost = if (periodTariff != null) {
            val summed = periodIntervals.mapNotNull { it.estimatedEnergyCost }.sum()
            summed
        } else null

        val fixedCharges = if (includeMonthlyFixedCharges && periodTariff != null && (totalKwh > 0.0 || periodIntervals.isNotEmpty())) {
            periodTariff.fixedMonthlyCharge + periodTariff.otherCharges
        } else if (includeMonthlyFixedCharges && periodTariff != null) {
            0.0
        } else {
            null
        }

        val totalCost = if (energyCost != null) {
            energyCost + (fixedCharges ?: 0.0)
        } else null

        val chargingCounts = ChargingStatus.entries.associateWith { status ->
            periodIntervals.count { it.reading.chargingStatusEnum == status }
        }

        return PeriodAnalysisSummary(
            totalKwh = totalKwh,
            estimatedEnergyCost = energyCost,
            fixedAndOtherCharges = fixedCharges,
            estimatedTotalCost = totalCost,
            currency = currency,
            dailyAverageKwh = dailyAverageKwh,
            highestDay = highestDay,
            lowestDay = lowestDay,
            previousPeriodTotalKwh = null,
            deltaPercentVsPrevious = null,
            buckets = buckets,
            containsMultiDayIntervals = periodIntervals.any { it.spansMultipleDays && (it.consumptionKwh ?: 0.0) > 0.0 },
            chargingCounts = chargingCounts
        )
    }
}
