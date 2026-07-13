package dev.dwm.liftlog.domain

const val KCAL_PER_KG = 7700.0

data class DayWeight(val epochDay: Long, val kg: Double)
data class DayIntake(val epochDay: Long, val kcal: Double)

data class TdeeResult(
    val trendWeightKg: Double,
    val weeklyDeltaKg: Double,
    val tdeeKcal: Double,
    val targetKcal: Double,
)

/** Exponential moving average over daily weights; gaps carry the previous trend forward. */
fun trendWeights(weights: List<DayWeight>, alpha: Double = 0.25): List<DayWeight> {
    if (weights.isEmpty()) return emptyList()
    val sorted = weights.sortedBy { it.epochDay }
    var trend = sorted.first().kg
    return sorted.map { w ->
        trend += alpha * (w.kg - trend)
        DayWeight(w.epochDay, trend)
    }
}

/**
 * MacroFactor-style adherence-neutral TDEE:
 * observed energy balance = avg intake − (trend weight change × 7700/day),
 * over the most recent `windowDays` with data. Needs ≥7 weigh-ins and ≥7 intake days.
 * goalKgPerWeek: negative = cut, positive = bulk.
 */
fun computeTdee(
    weights: List<DayWeight>,
    intakes: List<DayIntake>,
    goalKgPerWeek: Double,
    windowDays: Long = 21,
): TdeeResult? {
    if (weights.size < 7 || intakes.size < 7) return null
    val trend = trendWeights(weights)
    val lastDay = trend.last().epochDay
    val windowTrend = trend.filter { it.epochDay > lastDay - windowDays }
    if (windowTrend.size < 2) return null
    val spanDays = (windowTrend.last().epochDay - windowTrend.first().epochDay).coerceAtLeast(1)
    val deltaPerDay = (windowTrend.last().kg - windowTrend.first().kg) / spanDays
    val windowIntakes = intakes.filter { it.epochDay > lastDay - windowDays }
    if (windowIntakes.size < 7) return null
    val avgIntake = windowIntakes.sumOf { it.kcal } / windowIntakes.size
    val tdee = avgIntake - deltaPerDay * KCAL_PER_KG
    return TdeeResult(
        trendWeightKg = trend.last().kg,
        weeklyDeltaKg = deltaPerDay * 7,
        tdeeKcal = tdee,
        targetKcal = tdee + goalKgPerWeek * KCAL_PER_KG / 7,
    )
}
