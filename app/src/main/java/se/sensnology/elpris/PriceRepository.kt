package se.sensnology.elpris

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

data class PricePoint(val start: OffsetDateTime, val spotPricePerKwh: Double)
data class PriceResult(val today: List<PricePoint>, val tomorrow: List<PricePoint>, val fetchedAt: Long)

object PriceRepository {
    private const val TAG = "ElprisRepository"
    private const val CACHE_PREFS = "price_cache"
    private val cache = ConcurrentHashMap<String, PriceResult>()
    private const val MEMORY_CACHE_MS = 10 * 60 * 1000L
    private const val FORCE_REFRESH_DEDUP_MS = 30 * 1000L

    @Synchronized
    fun load(context: Context, area: String, forceRefresh: Boolean = false): PriceResult {
        val market = PriceMarkets.find(area)
        val today = LocalDate.now(market.zoneId)
        val cacheKey = "$area:$today"
        val old = cache[cacheKey]
        val age = old?.let { System.currentTimeMillis() - it.fetchedAt } ?: Long.MAX_VALUE
        if (old != null && ((!forceRefresh && age < MEMORY_CACHE_MS) || (forceRefresh && age < FORCE_REFRESH_DEDUP_MS))) {
            Log.i(TAG, "Memory cache hit $area today=$today")
            return old
        }
        val todayPrices = fetch(context, today, area) ?: old?.today ?: readCache(context, today, area)
        val tomorrowPrices = fetch(context, today.plusDays(1), area) ?: old?.tomorrow ?: readCache(context, today.plusDays(1), area)
        if (todayPrices.isEmpty() && tomorrowPrices.isEmpty()) {
            Log.w(TAG, "No prices available for $area today=$today")
            return old ?: PriceResult(emptyList(), emptyList(), System.currentTimeMillis())
        }
        Log.i(TAG, "Loaded $area today=${todayPrices.size} tomorrow=${tomorrowPrices.size}")
        return PriceResult(todayPrices, tomorrowPrices, System.currentTimeMillis()).also { cache[cacheKey] = it }
    }

    fun hasCachedTomorrow(context: Context, area: String): Boolean {
        val market = PriceMarkets.find(area)
        val tomorrow = LocalDate.now(market.zoneId).plusDays(1)
        return readCache(context, tomorrow, area).isNotEmpty()
    }

    private fun fetch(context: Context, date: LocalDate, area: String): List<PricePoint>? {
        val market = PriceMarkets.find(area)
        val suffix = if (market.usesAreaSuffix) "_$area" else ""
        val path = "%04d/%02d-%02d%s.json".format(date.year, date.monthValue, date.dayOfMonth, suffix)
        val address = "${market.baseUrl}/api/v1/prices/$path"
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
            val points = parse(body, market)
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
            parse(body, PriceMarkets.find(area))
                .also { Log.i(TAG, "Cache hit $area $date count=${it.size}") }
        } catch (error: Exception) {
            Log.e(TAG, "Invalid cache $area $date", error)
            emptyList()
        }
    }

    /** Also normalizes hourly API data into quarter-hour points for charts and charging plans. */
    internal fun parse(body: String, market: PriceMarket): List<PricePoint> = parse(JSONArray(body), market)

    private fun parse(json: JSONArray, market: PriceMarket): List<PricePoint> = buildList {
        for (i in 0 until json.length()) {
            val row = json.getJSONObject(i)
            val start = OffsetDateTime.parse(row.getString("time_start"))
            val end = OffsetDateTime.parse(row.getString("time_end"))
            val price = row.getDouble(market.priceField)
            var slot = start.toInstant()
            val endInstant = end.toInstant()
            while (slot < endInstant) {
                add(PricePoint(slot.atZone(market.zoneId).toOffsetDateTime(), price))
                slot = slot.plus(15, ChronoUnit.MINUTES)
            }
        }
    }
}
