package se.sensnology.elpris

import android.content.Context
import java.net.URI

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

        fun isAllowedBaseUrl(value: String): Boolean {
            val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
            val host = uri.host?.lowercase()?.trim('[', ']') ?: return false
            if (uri.scheme.equals("https", ignoreCase = true)) return true
            if (!uri.scheme.equals("http", ignoreCase = true)) return false
            if (host == "localhost" || host == "::1" || host.endsWith(".local") || host.endsWith(".lan") || '.' !in host) return true
            val ipv4 = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { part -> part in 0..255 } }
            if (ipv4.size == 4) return ipv4[0] == 10 || ipv4[0] == 127 ||
                (ipv4[0] == 172 && ipv4[1] in 16..31) ||
                (ipv4[0] == 192 && ipv4[1] == 168) ||
                (ipv4[0] == 169 && ipv4[1] == 254) ||
                (ipv4[0] == 100 && ipv4[1] in 64..127)
            return host.startsWith("fc") || host.startsWith("fd") || host.matches(Regex("fe[89ab].*"))
        }

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
