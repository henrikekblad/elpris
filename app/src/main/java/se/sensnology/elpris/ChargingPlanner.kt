package se.sensnology.elpris

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.sqrt

data class ChargingPlan(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val powerKw: Double,
    val energyKwh: Double,
    val distanceMil: Double,
    val estimatedCostSek: Double,
    val complete24Hours: Boolean
)

object ChargingPlanner {
    fun powerKw(amps: Int): Double = sqrt(3.0) * 400.0 * amps / 1000.0

    fun calculate(result: PriceResult, settings: WidgetSettings, now: OffsetDateTime = OffsetDateTime.now()): ChargingPlan? {
        val slotMinutes = 15L
        val firstStart = now.withSecond(0).withNano(0).let {
            val minute = (it.minute / 15) * 15
            it.withMinute(minute).let { rounded -> if (rounded < now) rounded.plusMinutes(15) else rounded }
        }
        val fullHorizon = firstStart.plusHours(24)
        val departure = if (settings.useDepartureTime) {
            now.withHour(settings.departureHour).withMinute(settings.departureMinute).withSecond(0).withNano(0)
                .let { if (it <= firstStart) it.plusDays(1) else it }
        } else null
        val horizon = departure?.let { minOf(fullHorizon, it) } ?: fullHorizon
        val points = (result.today + result.tomorrow)
            .distinctBy { it.start.toInstant() }
            .sortedBy { it.start.toInstant() }
            .filter { it.start >= firstStart && it.start < horizon }
        val power = powerKw(settings.chargingAmps)
        val wanted = settings.chargingKwh.toDouble()
        val energyPerSlot = power * slotMinutes / 60.0
        val slotsNeeded = ceil(wanted / energyPerSlot).toInt().coerceAtLeast(1)
        val plannedEnergy = slotsNeeded * energyPerSlot
        if (points.size < slotsNeeded) return null

        var bestIndex = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (startIndex in 0..points.size - slotsNeeded) {
            val slice = points.subList(startIndex, startIndex + slotsNeeded)
            val contiguous = slice.zipWithNext().all { (a, b) ->
                Duration.between(a.start, b.start).toMinutes() == slotMinutes
            }
            if (!contiguous || slice.last().start.plusMinutes(slotMinutes) > horizon) continue
            var costOre = 0.0
            slice.forEach { point ->
                costOre += energyPerSlot * settings.apply(point.sekPerKwh)
            }
            if (costOre < bestCost) { bestCost = costOre; bestIndex = startIndex }
        }
        if (bestIndex < 0) return null
        val start = points[bestIndex].start
        val end = start.plusMinutes(slotsNeeded * slotMinutes)
        val lastAvailableEnd = points.last().start.plusMinutes(slotMinutes)
        return ChargingPlan(
            start, end, power, plannedEnergy,
            plannedEnergy / settings.consumptionKwhPerMil.coerceAtLeast(0.1),
            bestCost / 100.0,
            lastAvailableEnd >= horizon
        )
    }
}
