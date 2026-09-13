package se.sensnology.elpris

import android.content.Context

data class HomeAssistantSettings(
    val baseUrl: String = "",
    val webhookId: String = ""
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && webhookId.isNotBlank()

    fun webhookUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        return "$base/api/webhook/${webhookId.trim()}"
    }

    companion object {
        private const val PREFS = "home_assistant"
        private const val BASE_URL = "base_url"
        private const val WEBHOOK_ID = "webhook_id"

        fun load(context: Context): HomeAssistantSettings {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return HomeAssistantSettings(
                baseUrl = preferences.getString(BASE_URL, "") ?: "",
                webhookId = preferences.getString(WEBHOOK_ID, "") ?: ""
            )
        }

        fun save(context: Context, settings: HomeAssistantSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(BASE_URL, settings.baseUrl.trim().trimEnd('/'))
                .putString(WEBHOOK_ID, settings.webhookId.trim())
                .apply()
        }
    }
}
