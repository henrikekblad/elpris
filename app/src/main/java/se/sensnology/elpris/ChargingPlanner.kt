package se.sensnology.elpris

import java.time.Duration
import java.time.OffsetDateTime
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.sqrt

data class ChargingPlan(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val powerKw: Double,
    val energyKwh: Double,
    val distanceMil: Double,
    val estimatedCostSek: Double,
    val estimatedPriceSlots: Int
)

object ChargingPlanner {
    fun powerKw(amps: Int, phases: Int = 3): Double = if (phases == 1) {
        230.0 * amps / 1000.0
    } else {
        sqrt(3.0) * 400.0 * amps / 1000.0
    }

    fun durationMinutes(settings: WidgetSettings): Long {
        val energyPerSlot = powerKw(settings.chargingAmps, settings.chargingPhases) * 0.25
        return ceil(settings.chargingKwh / energyPerSlot).toLong().coerceAtLeast(1) * 15
    }

    fun calculate(result: PriceResult, settings: WidgetSettings, now: OffsetDateTime = OffsetDateTime.now()): ChargingPlan? {
        val slotMinutes = 15L
        val published = (result.today + result.tomorrow)
            .distinctBy { it.start.toInstant() }
            .sortedBy { it.start.toInstant() }
        if (published.isEmpty()) return null
        val marketNow = now.withOffsetSameInstant(published.first().start.offset)
        val firstStart = marketNow.withSecond(0).withNano(0).let {
            val minute = (it.minute / 15) * 15
            it.withMinute(minute).let { rounded -> if (rounded < marketNow) rounded.plusMinutes(15) else rounded }
        }
        val candidateStartHorizon = firstStart.plusHours(24)
        val departure = if (settings.useDepartureTime) {
            marketNow.withHour(settings.departureHour).withMinute(settings.departureMinute).withSecond(0).withNano(0)
                .let { if (it <= firstStart) it.plusDays(1) else it }
        } else null
        val power = powerKw(settings.chargingAmps, settings.chargingPhases)
        val wanted = settings.chargingKwh.toDouble()
        val energyPerSlot = power * slotMinutes / 60.0
        val slotsNeeded = ceil(wanted / energyPerSlot).toInt().coerceAtLeast(1)
        val durationMinutes = slotsNeeded * slotMinutes
        val latestStartExclusive = departure?.minusMinutes(durationMinutes)
            ?.plusMinutes(slotMinutes)
            ?.let { minOf(candidateStartHorizon, it) }
            ?: candidateStartHorizon
        if (latestStartExclusive <= firstStart) return null

        val publishedByInstant = published.associateBy { it.start.toInstant() }
        val latestPublishedDate = published.maxOf { it.start.toLocalDate() }
        val fallbackByTime = published
            .filter { it.start.toLocalDate() == latestPublishedDate }
            .associateBy { LocalTime.of(it.start.hour, it.start.minute) }
        if (fallbackByTime.isEmpty()) return null

        data class PlanningPoint(val point: PricePoint, val estimated: Boolean)
        val requiredEnd = latestStartExclusive.plusMinutes(durationMinutes)
        val points = buildList {
            var time = firstStart
            while (time < requiredEnd) {
                val actual = publishedByInstant[time.toInstant()]
                if (actual != null) {
                    add(PlanningPoint(actual, false))
                } else {
                    val fallback = fallbackByTime[LocalTime.of(time.hour, time.minute)] ?: return@buildList
                    add(PlanningPoint(PricePoint(time, fallback.sekPerKwh), true))
                }
                time = time.plusMinutes(slotMinutes)
            }
        }
        val plannedEnergy = slotsNeeded * energyPerSlot
        if (points.size < slotsNeeded) return null

        var bestIndex = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (startIndex in 0..points.size - slotsNeeded) {
            val slice = points.subList(startIndex, startIndex + slotsNeeded)
            val contiguous = slice.zipWithNext().all { (a, b) ->
                Duration.between(a.point.start, b.point.start).toMinutes() == slotMinutes
            }
            if (!contiguous || slice.first().point.start >= latestStartExclusive) continue
            if (departure != null && slice.last().point.start.plusMinutes(slotMinutes) > departure) continue
            var costOre = 0.0
            slice.forEach { point ->
                costOre += energyPerSlot * settings.apply(point.point.sekPerKwh)
            }
            if (costOre < bestCost) { bestCost = costOre; bestIndex = startIndex }
        }
        if (bestIndex < 0) return null
        val selected = points.subList(bestIndex, bestIndex + slotsNeeded)
        val start = selected.first().point.start
        val end = start.plusMinutes(slotsNeeded * slotMinutes)
        return ChargingPlan(
            start, end, power, plannedEnergy,
            plannedEnergy / settings.consumptionKwhPerMil.coerceAtLeast(0.1),
            bestCost / 100.0,
            selected.count { it.estimated }
        )
    }
}
