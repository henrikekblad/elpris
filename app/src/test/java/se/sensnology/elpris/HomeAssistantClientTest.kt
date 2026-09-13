package se.sensnology.elpris

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantClientTest {
    @Test fun buildsWebhookUrlWithoutDuplicateSlash() {
        val settings = HomeAssistantSettings("https://ha.example/", "secret-id")
        assertEquals("https://ha.example/api/webhook/secret-id", settings.webhookUrl())
    }

    @Test fun schedulePayloadContainsPlanAndOmitsNulls() {
        val json = JSONObject(HomeAssistantClient.payload(HomeAssistantCommand(
            action = "schedule",
            start = "2026-09-14T01:00:00+02:00",
            end = "2026-09-14T03:00:00+02:00",
            amps = 16,
            phases = 3,
            powerKw = 11.1,
            energyKwh = 22.2,
            priceArea = "SE4",
            estimated = false
        )))

        assertEquals(1, json.getInt("version"))
        assertEquals("schedule", json.getString("action"))
        assertEquals(16, json.getInt("amps"))
        assertEquals("SE4", json.getString("price_area"))
        assertFalse(json.has("unused"))
    }

    @Test fun parsesLimitedStatusResponse() {
        val status = HomeAssistantClient.parseStatus(JSONObject(
            """{"ok":true,"charging_enabled":true,"schedule_active":false,"start":null,"end":null}"""
        ))

        assertTrue(status.chargingEnabled)
        assertFalse(status.scheduleActive)
        assertNull(status.start)
        assertNull(status.end)
    }

    @Test fun schedulePayloadContainsAllChargingPeriods() {
        val periods = listOf(
            ChargingPeriod(
                java.time.OffsetDateTime.parse("2026-09-14T01:00:00+02:00"),
                java.time.OffsetDateTime.parse("2026-09-14T01:30:00+02:00")
            ),
            ChargingPeriod(
                java.time.OffsetDateTime.parse("2026-09-14T03:00:00+02:00"),
                java.time.OffsetDateTime.parse("2026-09-14T03:30:00+02:00")
            )
        )
        val json = JSONObject(HomeAssistantClient.payload(HomeAssistantCommand("schedule", periods = periods)))

        assertEquals(2, json.getJSONArray("periods").length())
        assertEquals("2026-09-14T03:00+02:00", json.getJSONArray("periods").getJSONObject(1).getString("start"))
    }
}
