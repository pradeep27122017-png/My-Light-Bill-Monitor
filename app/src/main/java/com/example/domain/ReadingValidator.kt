package com.example.domain

import com.example.data.model.MeterReading
import kotlin.math.abs

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class RequiresMeterResetConfirmation(
        val previousReading: MeterReading,
        val message: String
    ) : ValidationResult
    data class DuplicateDetected(
        val existingReading: MeterReading,
        val message: String
    ) : ValidationResult
    data class Error(val message: String) : ValidationResult
}

object ReadingValidator {

    private const val DUPLICATE_WINDOW_MS = 60_000L

    fun validate(
        rawReadingInput: String,
        timestampMs: Long,
        existingReadings: List<MeterReading>,
        editingReadingId: String? = null,
        meterResetConfirmed: Boolean = false,
        rawPreviousMeterFinalInput: String = "",
        rawNewMeterStartInput: String = "",
        remarks: String = ""
    ): ValidationResult {
        val trimmed = rawReadingInput.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("Meter reading (kWh) is required.")
        }
        val readingKwh = trimmed.toDoubleOrNull()
            ?: return ValidationResult.Error("Enter a valid numeric meter reading.")
        if (readingKwh.isNaN() || readingKwh.isInfinite()) {
            return ValidationResult.Error("Enter a finite numeric meter reading.")
        }
        if (readingKwh < 0.0) {
            return ValidationResult.Error("Meter reading cannot be negative.")
        }
        if (readingKwh > 10_000_000.0) {
            return ValidationResult.Error("Meter reading exceeds maximum supported range (10,000,000 kWh).")
        }
        if (timestampMs <= 0L) {
            return ValidationResult.Error("Please select a valid reading date and time.")
        }

        val otherReadings = existingReadings
            .filter { it.id != editingReadingId }
            .sortedBy { it.readingTimestampMs }

        // Duplicate detection: identical kWh and timestamp within 60 seconds, or exact timestamp match
        val duplicate = otherReadings.firstOrNull { other ->
            val timeDiff = abs(other.readingTimestampMs - timestampMs)
            (timeDiff <= DUPLICATE_WINDOW_MS && abs(other.readingKwh - readingKwh) < 0.0001) ||
                timeDiff == 0L
        }
        if (duplicate != null) {
            return ValidationResult.DuplicateDetected(
                existingReading = duplicate,
                message = "A reading at this timestamp (${duplicate.readingKwh} kWh) already exists. Edit the existing entry or adjust the timestamp."
            )
        }

        val previousReading = otherReadings.lastOrNull { it.readingTimestampMs < timestampMs }
        val nextReading = otherReadings.firstOrNull { it.readingTimestampMs > timestampMs }

        if (previousReading != null && readingKwh < previousReading.readingKwh) {
            if (!meterResetConfirmed) {
                return ValidationResult.RequiresMeterResetConfirmation(
                    previousReading = previousReading,
                    message = "Reading ($readingKwh kWh) is lower than the previous reading (${previousReading.readingKwh} kWh). Enable Meter Reset / Replacement if the meter rolled over or was replaced."
                )
            }
            if (remarks.trim().length < 3) {
                return ValidationResult.Error("Please add a brief reason in Remarks explaining the meter reset or replacement.")
            }
            if (rawPreviousMeterFinalInput.isNotBlank()) {
                val finalOld = rawPreviousMeterFinalInput.trim().toDoubleOrNull()
                    ?: return ValidationResult.Error("Final reading of old meter must be a valid number.")
                if (finalOld < previousReading.readingKwh) {
                    return ValidationResult.Error("Old meter's final reading (${finalOld} kWh) cannot be lower than ${previousReading.readingKwh} kWh.")
                }
            }
            if (rawNewMeterStartInput.isNotBlank()) {
                val startNew = rawNewMeterStartInput.trim().toDoubleOrNull()
                    ?: return ValidationResult.Error("New meter start reading must be a valid number.")
                if (startNew < 0.0 || startNew > readingKwh) {
                    return ValidationResult.Error("New meter baseline cannot exceed the current reading (${readingKwh} kWh).")
                }
            }
        }

        if (nextReading != null && !nextReading.meterReset && readingKwh > nextReading.readingKwh) {
            return ValidationResult.Error(
                "Reading ($readingKwh kWh) cannot exceed the subsequent recorded reading (${nextReading.readingKwh} kWh)."
            )
        }

        return ValidationResult.Valid
    }
}
