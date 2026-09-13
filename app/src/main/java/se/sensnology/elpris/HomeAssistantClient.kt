package se.sensnology.elpris

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HomeAssistantCommand(
    val action: String,
    val start: String? = null,
    val end: String? = null,
    val amps: Int? = null,
    val phases: Int? = null,
    val powerKw: Double? = null,
    val energyKwh: Double? = null,
    val priceArea: String? = null,
    val estimated: Boolean? = null,
    val periods: List<ChargingPeriod>? = null
)

data class HomeAssistantStatus(
    val chargingEnabled: Boolean,
    val scheduleActive: Boolean,
    val start: String?,
    val end: String?
)

object HomeAssistantClient {
    internal fun payload(command: HomeAssistantCommand): String = JSONObject().apply {
        put("version", 1)
        put("action", command.action)
        command.start?.let { put("start", it) }
        command.end?.let { put("end", it) }
        command.amps?.let { put("amps", it) }
        command.phases?.let { put("phases", it) }
        command.powerKw?.let { put("power_kw", it) }
        command.energyKwh?.let { put("energy_kwh", it) }
        command.priceArea?.let { put("price_area", it) }
        command.estimated?.let { put("estimated", it) }
        command.periods?.let { periods ->
            put("periods", org.json.JSONArray().apply {
                periods.forEach { period -> put(JSONObject().apply {
                    put("start", period.start.toString())
                    put("end", period.end.toString())
                }) }
            })
        }
    }.toString()

    fun send(settings: HomeAssistantSettings, command: HomeAssistantCommand) {
        request(settings, command)
    }

    fun status(settings: HomeAssistantSettings): HomeAssistantStatus {
        return parseStatus(request(settings, HomeAssistantCommand("status")))
    }

    internal fun parseStatus(response: JSONObject): HomeAssistantStatus =
        HomeAssistantStatus(
            chargingEnabled = response.optBoolean("charging_enabled"),
            scheduleActive = response.optBoolean("schedule_active"),
            start = response.optString("start").takeUnless { it.isBlank() || it == "null" },
            end = response.optString("end").takeUnless { it.isBlank() || it == "null" }
        )

    private fun request(settings: HomeAssistantSettings, command: HomeAssistantCommand): JSONObject {
        require(settings.configured) { "Home Assistant is not configured" }
        require(settings.baseUrl.trim().startsWith("https://")) { "HTTPS is required" }
        require(command.action in setOf("status", "schedule", "cancel", "start", "stop")) { "Unsupported action" }

        val body = payload(command)

        val connection = URL(settings.webhookUrl()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            return JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }
}
