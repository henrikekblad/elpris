package se.sensnology.elpris

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import java.time.ZonedDateTime

/**
 * Supplements AppWidgetProvider's regular 30-minute interval around publication time.
 * Alarms are intentionally inexact to avoid requiring exact-alarm permission.
 */
object PriceUpdateScheduler {
    private val publicationAttempts = listOf(0, 10, 20, 35)
    private const val REQUEST_CODE = 13_035

    fun scheduleNext(context: Context, tomorrowAvailable: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidgetProvider::class.java))
            .filter { WidgetSettings.isConfigured(context, it) }
        val markets = ids.map { WidgetSettings.load(context, it).area }.distinct()
            .map(PriceMarkets::find).ifEmpty { listOf(PriceMarkets.find(PriceMarkets.defaultArea(AppLanguageSettings.region(context)))) }
        val next = markets.map { market ->
            val now = ZonedDateTime.now(market.zoneId)
            if (tomorrowAvailable) nextDayAtOne(now) else nextAttempt(now)
        }.minBy { it.toInstant() }
        val alarm = context.getSystemService(AlarmManager::class.java)
        // A five-minute window lets Android coordinate wake-ups without allowing
        // the checks to drift too far from publication time.
        alarm.setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            5 * 60 * 1000L,
            pendingIntent(context)
        )
        Log.i("SpotNavScheduler", "Next publication check ${next.toLocalDateTime()} available=$tomorrowAvailable")
    }

    fun scheduleForActiveWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidgetProvider::class.java))
            .filter { WidgetSettings.isConfigured(context, it) }
        val areas = ids.map { WidgetSettings.load(context, it).area }.distinct()
        scheduleNext(context, areas.isNotEmpty() && areas.all { PriceRepository.hasCachedTomorrow(context, it) })
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun nextAttempt(now: ZonedDateTime): ZonedDateTime {
        val todayAt13 = now.withHour(13).withMinute(0).withSecond(0).withNano(0)
        publicationAttempts.forEach { minutes ->
            val candidate = todayAt13.plusMinutes(minutes.toLong())
            if (candidate.isAfter(now)) return candidate
        }
        // After the initial frequent publication attempts, retry every 30 minutes
        // until a complete set of tomorrow's prices has been stored.
        return now.plusMinutes(30).withSecond(0).withNano(0)
    }

    private fun nextDayAtOne(now: ZonedDateTime): ZonedDateTime =
        now.plusDays(1).withHour(13).withMinute(0).withSecond(0).withNano(0)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PriceWidgetProvider::class.java).apply {
            action = PriceWidgetProvider.ACTION_PUBLICATION_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
