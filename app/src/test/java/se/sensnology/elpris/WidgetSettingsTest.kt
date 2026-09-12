package se.sensnology.elpris

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {
    @Test fun appliesTaxGridFeeThenVat() {
        val settings = WidgetSettings(area = "SE4", vat = true, tax = true, transfer = true,
            taxMinorUnit = 36.0, gridFeeMinorUnit = 30.0)
        assertEquals((100.0 + 36.0 + 30.0) * 1.25, settings.apply(1.0), 0.0001)
    }

    @Test fun usesVatForSelectedMarket() {
        val finnish = WidgetSettings(area = "FI", vat = true)
        val northernNorway = WidgetSettings(area = "NO4", vat = true)
        assertEquals(125.5, finnish.apply(1.0), 0.0001)
        assertEquals(100.0, northernNorway.apply(1.0), 0.0001)
    }
}
