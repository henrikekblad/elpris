package se.sensnology.elpris

import java.time.OffsetDateTime

data class PriceTableRow(
    val time: OffsetDateTime,
    val today: Double?,
    val tomorrow: Double?,
    val current: Boolean
)

data class PriceTableModel(
    val rows: List<PriceTableRow>,
    val todayAverage: Double?,
    val tomorrowAverage: Double?,
    val todayRange: Pair<Double, Double>,
    val tomorrowRange: Pair<Double, Double>
) {
    val currentIndex: Int get() = rows.indexOfFirst { it.current }
}

object PriceTableModels {
    fun create(result: PriceResult, settings: WidgetSettings, now: OffsetDateTime): PriceTableModel {
        val today = PriceAggregation.aggregate(result.today, settings).map { it.first to settings.apply(it.second) }
        val tomorrow = PriceAggregation.aggregate(result.tomorrow, settings).map { it.first to settings.apply(it.second) }
        val currentIndex = today.indices.lastOrNull { today[it].first <= now } ?: -1
        val rows = (0 until maxOf(today.size, tomorrow.size)).mapNotNull { index ->
            val time = today.getOrNull(index)?.first ?: tomorrow.getOrNull(index)?.first ?: return@mapNotNull null
            PriceTableRow(time, today.getOrNull(index)?.second, tomorrow.getOrNull(index)?.second, index == currentIndex)
        }
        fun average(values: List<Pair<OffsetDateTime, Double>>) = values.map { it.second }.average().takeUnless(Double::isNaN)
        fun range(values: List<Pair<OffsetDateTime, Double>>) =
            (values.minOfOrNull { it.second } ?: 0.0) to (values.maxOfOrNull { it.second } ?: 0.0)
        return PriceTableModel(rows, average(today), average(tomorrow), range(today), range(tomorrow))
    }
}

object PriceAggregation {
    fun aggregate(points: List<PricePoint>, settings: WidgetSettings): List<Pair<OffsetDateTime, Double>> {
        if (settings.intervalMinutes == 15) return points.map { it.start to it.spotPricePerKwh }
        return points.groupBy { it.start.toLocalDate() to it.start.hour }
            .values.map { group -> group.first().start.withMinute(0) to group.map { it.spotPricePerKwh }.average() }
            .sortedBy { it.first }
    }
}
