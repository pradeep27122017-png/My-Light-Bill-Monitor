package com.example.domain

import com.example.data.model.TariffConfiguration
import com.example.data.model.TariffTier
import kotlin.math.max
import kotlin.math.min

object TariffCalculator {

    /**
     * Resolves the historical or active tariff version applicable at [timestampMs].
     * Picks the latest tariff whose effectiveFromEpochMs <= timestampMs.
     * If all configured tariffs start after [timestampMs], falls back to the earliest tariff.
     */
    fun resolveTariffForTimestamp(
        tariffs: List<TariffConfiguration>,
        timestampMs: Long
    ): TariffConfiguration? {
        if (tariffs.isEmpty()) return null
        val sorted = tariffs.sortedBy { it.effectiveFromEpochMs }
        return sorted.lastOrNull { it.effectiveFromEpochMs <= timestampMs } ?: sorted.first()
    }

    /**
     * Computes only the energy charge (flat or slabbed) for [kwh] using [tariff].
     */
    fun computeEnergyCharge(
        kwh: Double,
        tariff: TariffConfiguration
    ): Double {
        if (kwh <= 0.0) return 0.0
        if (!tariff.useTieredRates || tariff.tiers.isEmpty()) {
            return kwh * tariff.ratePerKwh
        }
        return computeTieredEnergyCharge(kwh, tariff.tiers, tariff.ratePerKwh)
    }

    /**
     * Computes incremental energy charge when adding [deltaKwh] on top of [cumulativeMonthKwhBefore].
     * Ensures slab transitions during a billing month are accurately reflected per interval.
     */
    fun computeMarginalEnergyCharge(
        cumulativeMonthKwhBefore: Double,
        deltaKwh: Double,
        tariff: TariffConfiguration
    ): Double {
        if (deltaKwh <= 0.0) return 0.0
        if (!tariff.useTieredRates || tariff.tiers.isEmpty()) {
            return deltaKwh * tariff.ratePerKwh
        }
        val beforeCost = computeTieredEnergyCharge(cumulativeMonthKwhBefore, tariff.tiers, tariff.ratePerKwh)
        val afterCost = computeTieredEnergyCharge(cumulativeMonthKwhBefore + deltaKwh, tariff.tiers, tariff.ratePerKwh)
        return max(0.0, afterCost - beforeCost)
    }

    fun computeTieredEnergyCharge(
        totalKwh: Double,
        rawTiers: List<TariffTier>,
        fallbackRatePerKwh: Double
    ): Double {
        if (totalKwh <= 0.0) return 0.0
        val sortedTiers = rawTiers.sortedBy { it.fromKwh }
        var remaining = totalKwh
        var cost = 0.0
        var coveredUpTo = 0.0

        for (tier in sortedTiers) {
            val tierStart = max(tier.fromKwh, coveredUpTo)
            val tierEnd = tier.toKwh
            if (totalKwh <= tierStart) break

            val unitsInTier = if (tierEnd == null || tierEnd <= tierStart) {
                totalKwh - tierStart
            } else {
                min(totalKwh, tierEnd) - tierStart
            }

            if (unitsInTier > 0.0) {
                cost += unitsInTier * tier.ratePerKwh
                remaining -= unitsInTier
                coveredUpTo = tierStart + unitsInTier
            }
            if (remaining <= 1e-9) break
        }

        if (remaining > 1e-9) {
            cost += remaining * fallbackRatePerKwh
        }
        return cost
    }
}
