package com.example

import com.example.data.model.ChargingStatus
import com.example.data.model.MeterReading
import com.example.data.model.TariffConfiguration
import com.example.data.model.TariffTier
import com.example.domain.CsvExporter
import com.example.domain.EnergyCalculator
import com.example.domain.ReadingValidator
import com.example.domain.TariffCalculator
import com.example.domain.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class EnergyDomainTest {

    private val zone = ZoneId.of("UTC")

    private fun epoch(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `consecutive readings calculate exact consumption and detect multi-day span`() {
        val r1 = MeterReading(id = "r1", readingKwh = 12450.0, readingTimestampMs = epoch(2026, 11, 1, 8))
        val r2 = MeterReading(id = "r2", readingKwh = 12468.0, readingTimestampMs = epoch(2026, 11, 1, 20))
        val r3 = MeterReading(id = "r3", readingKwh = 12510.0, readingTimestampMs = epoch(2026, 11, 5, 9))

        val intervals = EnergyCalculator.buildIntervals(listOf(r3, r1, r2), emptyList(), "LKR", zone)
        assertEquals(3, intervals.size)

        assertNull(intervals[0].consumptionKwh)
        assertEquals(18.0, intervals[1].consumptionKwh!!, 0.001)
        assertFalse(intervals[1].spansMultipleDays)

        assertEquals(42.0, intervals[2].consumptionKwh!!, 0.001)
        assertTrue(intervals[2].spansMultipleDays)
        assertEquals(LocalDate.of(2026, 11, 1), intervals[2].startDate)
        assertEquals(LocalDate.of(2026, 11, 5), intervals[2].endDate)
        assertEquals(5, intervals[2].daySpanCount)
    }

    @Test
    fun `controlled meter reset handles old meter final and new meter baseline accurately`() {
        val r1 = MeterReading(id = "r1", readingKwh = 9980.0, readingTimestampMs = epoch(2026, 11, 1))
        val r2 = MeterReading(
            id = "r2",
            readingKwh = 15.0,
            readingTimestampMs = epoch(2026, 11, 3),
            meterReset = true,
            previousMeterFinalKwh = 10000.0,
            meterStartKwh = 0.0
        )
        val r3 = MeterReading(id = "r3", readingKwh = 35.0, readingTimestampMs = epoch(2026, 11, 4))

        val intervals = EnergyCalculator.buildIntervals(listOf(r1, r2, r3), emptyList(), "LKR", zone)
        // Old meter used 10000 - 9980 = 20 kWh, new meter used 15 - 0 = 15 kWh -> total 35 kWh
        assertEquals(35.0, intervals[1].consumptionKwh!!, 0.001)
        // Subsequent reading: 35 - 15 = 20 kWh
        assertEquals(20.0, intervals[2].consumptionKwh!!, 0.001)
    }

    @Test
    fun `historical tariff versions preserve past period costs when new tariff added`() {
        val oldTariff = TariffConfiguration(
            id = "t1",
            name = "Old Plan",
            currency = "LKR",
            ratePerKwh = 20.0,
            fixedMonthlyCharge = 300.0,
            otherCharges = 50.0,
            effectiveFromEpochMs = epoch(2026, 10, 1, 0)
        )
        val newTariff = TariffConfiguration(
            id = "t2",
            name = "New Plan",
            currency = "LKR",
            ratePerKwh = 40.0,
            fixedMonthlyCharge = 500.0,
            otherCharges = 100.0,
            effectiveFromEpochMs = epoch(2026, 11, 1, 0)
        )

        val readings = listOf(
            MeterReading(id = "oct1", readingKwh = 100.0, readingTimestampMs = epoch(2026, 10, 10)),
            MeterReading(id = "oct2", readingKwh = 150.0, readingTimestampMs = epoch(2026, 10, 20)), // 50 kWh @ 20 = 1000
            MeterReading(id = "nov1", readingKwh = 200.0, readingTimestampMs = epoch(2026, 11, 10))  // 50 kWh @ 40 = 2000
        )

        val octReport = EnergyCalculator.analyzeMonth(readings, listOf(oldTariff, newTariff), YearMonth.of(2026, 10), "LKR", zone)
        val novReport = EnergyCalculator.analyzeMonth(readings, listOf(oldTariff, newTariff), YearMonth.of(2026, 11), "LKR", zone)

        assertEquals(1000.0, octReport.estimatedEnergyCost!!, 0.001)
        assertEquals(1350.0, octReport.estimatedTotalCost!!, 0.001)

        assertEquals(2000.0, novReport.estimatedEnergyCost!!, 0.001)
        assertEquals(2600.0, novReport.estimatedTotalCost!!, 0.001)
    }

    @Test
    fun `tiered slab tariff calculates progressive blocks accurately`() {
        val tiered = TariffConfiguration(
            id = "t_slab",
            name = "Slab Plan",
            currency = "LKR",
            ratePerKwh = 50.0,
            useTieredRates = true,
            tiers = listOf(
                TariffTier(fromKwh = 0.0, toKwh = 60.0, ratePerKwh = 10.0),
                TariffTier(fromKwh = 60.0, toKwh = 90.0, ratePerKwh = 20.0),
                TariffTier(fromKwh = 90.0, toKwh = null, ratePerKwh = 35.0)
            )
        )

        // 100 kWh = 60*10 + 30*20 + 10*35 = 600 + 600 + 350 = 1550
        val cost = TariffCalculator.computeEnergyCharge(100.0, tiered)
        assertEquals(1550.0, cost, 0.001)
    }

    @Test
    fun `validator rejects empty negative duplicate and unconfirmed lower readings`() {
        val existing = listOf(
            MeterReading(id = "r1", readingKwh = 500.0, readingTimestampMs = epoch(2026, 11, 20, 12))
        )

        assertTrue(ReadingValidator.validate("", epoch(2026, 11, 21), existing) is ValidationResult.Error)
        assertTrue(ReadingValidator.validate("-10", epoch(2026, 11, 21), existing) is ValidationResult.Error)
        assertTrue(
            ReadingValidator.validate("500.0", epoch(2026, 11, 20, 12), existing) is ValidationResult.DuplicateDetected
        )
        assertTrue(
            ReadingValidator.validate("10.0", epoch(2026, 11, 21), existing) is ValidationResult.RequiresMeterResetConfirmation
        )
        assertTrue(
            ReadingValidator.validate(
                rawReadingInput = "10.0",
                timestampMs = epoch(2026, 11, 21),
                existingReadings = existing,
                meterResetConfirmed = true,
                remarks = "Replaced faulty meter"
            ) is ValidationResult.Valid
        )
    }

    @Test
    fun `csv export neutralizes formula injection and formats intervals`() {
        val r1 = MeterReading(id = "r1", readingKwh = 100.0, readingTimestampMs = epoch(2026, 11, 1), chargingStatus = ChargingStatus.CHARGING.firestoreValue, remarks = "=CMD()")
        val intervals = EnergyCalculator.buildIntervals(listOf(r1), emptyList(), "LKR", zone)
        val csv = CsvExporter.toCsv(intervals, zone)
        assertNotNull(csv)
        assertTrue(csv.contains("'=CMD()"))
        assertTrue(csv.contains("Charging"))
    }
}
