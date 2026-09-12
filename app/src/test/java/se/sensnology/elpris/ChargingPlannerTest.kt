package se.sensnology.elpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ChargingPlannerTest {
    @Test fun powerCalculationsUseSelectedPhaseCount() {
        assertEquals(3.68, ChargingPlanner.powerKw(16, 1), 0.001)
        assertEquals(11.085, ChargingPlanner.powerKw(16, 3), 0.001)
    }

    @Test fun selectsCheapestContiguousSlotsBeforeDeparture() {
        val start = OffsetDateTime.parse("2026-09-12T18:00:00+02:00")
        val prices = listOf(4.0, 3.0, 0.5, 0.4, 2.0, 3.0).mapIndexed { index, price ->
            PricePoint(start.plusMinutes(index * 15L), price)
        }
        val settings = WidgetSettings(chargingPhases = 1, chargingAmps = 10, chargingKwh = 1,
            useDepartureTime = true, departureHour = 20, departureMinute = 0)
        val plan = ChargingPlanner.calculate(PriceResult(prices, emptyList(), 0), settings, start)
        assertNotNull(plan)
        assertEquals(start.plusMinutes(30), plan!!.start)
        assertEquals(0, plan.estimatedPriceSlots)
    }

    @Test fun estimatesMissingFutureSlotsFromLatestDailyProfile() {
        val now = OffsetDateTime.parse("2026-09-12T23:30:00+02:00")
        val dayStart = now.toLocalDate().atStartOfDay().atOffset(now.offset)
        val prices = (0 until 96).map { index -> PricePoint(dayStart.plusMinutes(index * 15L), 1.0) }
        val settings = WidgetSettings(chargingPhases = 1, chargingAmps = 6, chargingKwh = 4,
            useDepartureTime = false)
        val plan = ChargingPlanner.calculate(PriceResult(prices, emptyList(), 0), settings, now)
        assertNotNull(plan)
        assertTrue(plan!!.estimatedPriceSlots > 0)
        assertTrue(plan.estimatedCost > 0)
    }
}
