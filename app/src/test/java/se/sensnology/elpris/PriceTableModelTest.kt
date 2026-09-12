package se.sensnology.elpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class PriceTableModelTest {
    @Test fun hourlyAggregationAveragesFourQuarterHours() {
        val start = OffsetDateTime.parse("2026-09-12T10:00:00+02:00")
        val points = listOf(1.0, 2.0, 3.0, 4.0).mapIndexed { index, value ->
            PricePoint(start.plusMinutes(index * 15L), value)
        }
        val aggregated = PriceAggregation.aggregate(points, WidgetSettings(intervalMinutes = 60))
        assertEquals(1, aggregated.size)
        assertEquals(2.5, aggregated.single().second, 0.0)
    }

    @Test fun marksCurrentRowAndCalculatesRanges() {
        val start = OffsetDateTime.parse("2026-09-12T10:00:00+02:00")
        val points = listOf(1.0, 2.0, 3.0).mapIndexed { index, value -> PricePoint(start.plusMinutes(index * 15L), value) }
        val model = PriceTableModels.create(PriceResult(points, emptyList(), 0), WidgetSettings(), start.plusMinutes(16))
        assertEquals(1, model.currentIndex)
        assertTrue(model.rows[1].current)
        assertEquals(100.0 to 300.0, model.todayRange)
    }
}
