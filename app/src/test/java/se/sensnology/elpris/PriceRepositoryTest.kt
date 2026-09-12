package se.sensnology.elpris

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceRepositoryTest {
    @Test fun expandsHourlyPricesIntoQuarterHours() {
        val json = """[{"NOK_per_kWh":1.25,"time_start":"2026-09-12T10:00:00+02:00","time_end":"2026-09-12T11:00:00+02:00"}]"""
        val points = PriceRepository.parse(json, PriceMarkets.find("NO1"))
        assertEquals(4, points.size)
        assertEquals(listOf(0, 15, 30, 45), points.map { it.start.minute })
        assertEquals(1.25, points.first().spotPricePerKwh, 0.0)
    }

    @Test fun expansionHandlesAutumnDstTransitionByInstant() {
        val json = """[{"NOK_per_kWh":1.0,"time_start":"2026-10-25T02:00:00+02:00","time_end":"2026-10-25T03:00:00+01:00"}]"""
        val points = PriceRepository.parse(json, PriceMarkets.find("NO1"))
        assertEquals(8, points.size)
        assertEquals(2, points.first().start.offset.totalSeconds / 3600)
        assertEquals(1, points.last().start.offset.totalSeconds / 3600)
    }
}
