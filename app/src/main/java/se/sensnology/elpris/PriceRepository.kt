package se.sensnology.elpris

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

data class PricePoint(val start: OffsetDateTime, val sekPerKwh: Double)
data class PriceResult(val today: List<PricePoint>, val tomorrow: List<PricePoint>, val fetchedAt: Long)

object PriceRepository {
    private const val TAG = "ElprisRepository"
    private const val CACHE_PREFS = "price_cache"
    private val cache = ConcurrentHashMap<String, PriceResult>()

    fun load(context: Context, area: String): PriceResult {
        val today = LocalDate.now()
        val cacheKey = "$area:$today"
        val old = cache[cacheKey]
        val todayPrices = fetch(context, today, area) ?: old?.today ?: readCache(context, today, area)
        val tomorrowPrices = fetch(context, today.plusDays(1), area) ?: old?.tomorrow ?: readCache(context, today.plusDays(1), area)
        if (todayPrices.isEmpty() && tomorrowPrices.isEmpty()) {
            Log.w(TAG, "No prices available for $area today=$today")
            return old ?: PriceResult(emptyList(), emptyList(), System.currentTimeMillis())
        }
        Log.i(TAG, "Loaded $area today=${todayPrices.size} tomorrow=${tomorrowPrices.size}")
        return PriceResult(todayPrices, tomorrowPrices, System.currentTimeMillis()).also { cache[cacheKey] = it }
    }

    private fun fetch(context: Context, date: LocalDate, area: String): List<PricePoint>? {
        val path = "%04d/%02d-%02d_%s.json".format(date.year, date.monthValue, date.dayOfMonth, area)
        val address = "https://www.elprisetjustnu.se/api/v1/prices/$path"
        val connection = URL(address)
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Sensnology-Elpris/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            Log.i(TAG, "GET $address -> HTTP $status")
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return emptyList()
            if (status !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONArray(body)
            val points = buildList {
                for (i in 0 until json.length()) {
                    val row = json.getJSONObject(i)
                    add(PricePoint(OffsetDateTime.parse(row.getString("time_start")), row.getDouble("SEK_per_kWh")))
                }
            }
            writeCache(context, date, area, body)
            Log.i(TAG, "Parsed ${points.size} prices for $area $date")
            points
        } catch (error: Exception) {
            Log.e(TAG, "Failed GET $address", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCache(context: Context, date: LocalDate, area: String, json: String) {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("$area:$date", json)
            .apply()
    }

    private fun readCache(context: Context, date: LocalDate, area: String): List<PricePoint> {
        val body = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString("$area:$date", null) ?: return emptyList()
        return try {
            val json = JSONArray(body)
            buildList {
                for (i in 0 until json.length()) {
                    val row = json.getJSONObject(i)
                    add(PricePoint(OffsetDateTime.parse(row.getString("time_start")), row.getDouble("SEK_per_kWh")))
                }
            }.also { Log.i(TAG, "Cache hit $area $date count=${it.size}") }
        } catch (error: Exception) {
            Log.e(TAG, "Invalid cache $area $date", error)
            emptyList()
        }
    }
}
