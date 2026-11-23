package com.example.domain

import com.example.data.model.ConsumptionInterval
import com.example.data.model.ReadingAuditLog
import com.example.data.model.TariffConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toCsv(
        intervals: List<ConsumptionInterval>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Date,Time,Meter Reading (kWh),Consumption Since Previous (kWh),Multi-Day Span,Interval Start Date,Estimated Cost,Currency,Car Charging Status,Weather Condition,Temp (C),Humidity (%),Wind (km/h),Rain (mm),Remarks,Meter Reset,Edited,Entry Created At,Last Modified At"
        )

        for (item in intervals.sortedByDescending { it.endTimestampMs }) {
            val zdt = Instant.ofEpochMilli(item.endTimestampMs).atZone(zoneId)
            val r = item.reading
            val createdIso = r.entryCreatedAt?.toDate()?.toInstant()?.atZone(zoneId)?.format(isoFmt) ?: ""
            val modifiedIso = r.lastModifiedAt?.toDate()?.toInstant()?.atZone(zoneId)?.format(isoFmt) ?: ""

            val row = listOf(
                dateFmt.format(zdt),
                timeFmt.format(zdt),
                "%.2f".format(r.readingKwh),
                item.consumptionKwh?.let { "%.2f".format(it) } ?: "",
                if (item.spansMultipleDays) "Yes (${item.daySpanCount} days)" else "No",
                item.startDate?.toString() ?: "",
                item.estimatedEnergyCost?.let { "%.2f".format(it) } ?: "",
                item.currency,
                r.chargingStatusEnum.label,
                r.weatherCondition ?: "",
                r.weatherTempC?.let { "%.1f".format(it) } ?: "",
                r.weatherHumidity?.toString() ?: "",
                r.weatherWindKmh?.let { "%.1f".format(it) } ?: "",
                r.weatherRainMm?.let { "%.1f".format(it) } ?: "",
                r.remarks ?: "",
                if (r.meterReset) "Yes" else "No",
                if (r.edited) "Yes" else "No",
                createdIso,
                modifiedIso
            ).joinToString(",") { escapeCsv(it) }

            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun toBackupJson(
        intervals: List<ConsumptionInterval>,
        tariffs: List<TariffConfiguration>,
        auditLogs: List<ReadingAuditLog>
    ): String {
        val root = JSONObject()
        root.put("exportedAt", Instant.now().toString())
        root.put("app", "Electricity Monitor")

        val readingsArr = JSONArray()
        intervals.forEach { item ->
            val r = item.reading
            readingsArr.put(JSONObject().apply {
                put("id", r.id)
                put("readingKwh", r.readingKwh)
                put("readingTimestampMs", r.readingTimestampMs)
                put("consumptionSincePreviousKwh", item.consumptionKwh)
                put("estimatedEnergyCost", item.estimatedEnergyCost)
                put("currency", item.currency)
                put("chargingStatus", r.chargingStatus)
                put("meterReset", r.meterReset)
                put("previousMeterFinalKwh", r.previousMeterFinalKwh)
                put("weatherTempC", r.weatherTempC)
                put("weatherCondition", r.weatherCondition)
                put("weatherHumidity", r.weatherHumidity)
                put("weatherWindKmh", r.weatherWindKmh)
                put("weatherRainMm", r.weatherRainMm)
                put("remarks", r.remarks)
                put("edited", r.edited)
            })
        }
        root.put("readings", readingsArr)

        val tariffsArr = JSONArray()
        tariffs.forEach { t ->
            val tiersArr = JSONArray()
            t.tiers.forEach { tier ->
                tiersArr.put(JSONObject().apply {
                    put("fromKwh", tier.fromKwh)
                    put("toKwh", tier.toKwh)
                    put("ratePerKwh", tier.ratePerKwh)
                })
            }
            tariffsArr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("currency", t.currency)
                put("ratePerKwh", t.ratePerKwh)
                put("useTieredRates", t.useTieredRates)
                put("tiers", tiersArr)
                put("fixedMonthlyCharge", t.fixedMonthlyCharge)
                put("otherCharges", t.otherCharges)
                put("effectiveFromEpochMs", t.effectiveFromEpochMs)
                put("notes", t.notes)
            })
        }
        root.put("tariffs", tariffsArr)

        val logsArr = JSONArray()
        auditLogs.forEach { l ->
            logsArr.put(JSONObject().apply {
                put("id", l.id)
                put("readingId", l.readingId)
                put("action", l.action)
                put("previousReadingKwh", l.previousReadingKwh)
                put("newReadingKwh", l.newReadingKwh)
                put("reason", l.reason)
            })
        }
        root.put("auditLogs", logsArr)

        return root.toString(2)
    }

    private fun escapeCsv(value: String): String {
        val sanitized = if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
            "'$value"
        } else {
            value
        }
        return if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }
}
