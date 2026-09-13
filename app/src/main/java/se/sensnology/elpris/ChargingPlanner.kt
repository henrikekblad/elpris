package se.sensnology.elpris

import java.time.OffsetDateTime
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.sqrt
import java.util.BitSet

data class ChargingPeriod(val start: OffsetDateTime, val end: OffsetDateTime)

data class ChargingPlan(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val powerKw: Double,
    val energyKwh: Double,
    val distanceMil: Double,
    val estimatedCost: Double,
    val estimatedPriceSlots: Int,
    val periods: List<ChargingPeriod> = listOf(ChargingPeriod(start, end))
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
                    add(PlanningPoint(PricePoint(time, fallback.spotPricePerKwh), true))
                }
                time = time.plusMinutes(slotMinutes)
            }
        }
        val plannedEnergy = slotsNeeded * energyPerSlot
        if (points.size < slotsNeeded) return null

        data class State(val selected: Int, val runs: Int, val active: Boolean)
        data class Choice(val cost: Double, val slots: BitSet)
        var states = mapOf(State(0, 0, false) to Choice(0.0, BitSet()))
        points.forEachIndexed { index, planningPoint ->
            val next = mutableMapOf<State, Choice>()
            fun keep(state: State, choice: Choice) {
                if (choice.cost < (next[state]?.cost ?: Double.POSITIVE_INFINITY)) next[state] = choice
            }
            states.forEach { (state, choice) ->
                keep(state.copy(active = false), choice)
                val newRuns = state.runs + if (state.active) 0 else 1
                val beforeDeparture = departure == null || planningPoint.point.start.plusMinutes(slotMinutes) <= departure
                if (beforeDeparture && state.selected < slotsNeeded && newRuns <= settings.maxChargingPeriods.coerceIn(1, 8)) {
                    val selectedSlots = choice.slots.clone() as BitSet
                    selectedSlots.set(index)
                    keep(
                        State(state.selected + 1, newRuns, true),
                        Choice(choice.cost + energyPerSlot * settings.apply(planningPoint.point.spotPricePerKwh), selectedSlots)
                    )
                }
            }
            states = next
        }
        val best = states.filterKeys { it.selected == slotsNeeded }.minByOrNull { it.value.cost }?.value ?: return null
        val selected = points.filterIndexed { index, _ -> best.slots[index] }
        val periods = buildList<ChargingPeriod> {
            selected.forEach { item ->
                val previous = lastOrNull()
                if (previous != null && previous.end.toInstant() == item.point.start.toInstant()) {
                    this[lastIndex] = previous.copy(end = item.point.start.plusMinutes(slotMinutes))
                } else add(ChargingPeriod(item.point.start, item.point.start.plusMinutes(slotMinutes)))
            }
        }
        val start = periods.first().start
        val end = periods.last().end
        return ChargingPlan(
            start, end, power, plannedEnergy,
            plannedEnergy / settings.consumptionKwhPerMil.coerceAtLeast(0.1),
            best.cost / 100.0,
            selected.count { it.estimated },
            periods
        )
    }
}
