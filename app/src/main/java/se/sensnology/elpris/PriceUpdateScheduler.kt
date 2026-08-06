package se.sensnology.elpris

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZonedDateTime

/**
 * Kompletterar AppWidgetProviders vanliga 30-minutersintervall kring publiceringen.
 * Alarmen är avsiktligt inexakta för att inte kräva behörigheten för exakta alarm.
 */
object PriceUpdateScheduler {
    private val publicationAttempts = listOf(0, 10, 20, 35)
    private const val REQUEST_CODE = 13_035

    fun scheduleNext(context: Context, tomorrowAvailable: Boolean) {
        val now = ZonedDateTime.now()
        val next = if (tomorrowAvailable) {
            nextDayAtOne(now)
        } else {
            nextAttempt(now)
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        // Fem minuters fönster ger Android möjlighet att samordna väckningar utan
        // att kontrollerna driver långt bort från publiceringstiden.
        alarm.setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            5 * 60 * 1000L,
            pendingIntent(context)
        )
        Log.i("ElprisScheduler", "Next publication check ${next.toLocalDateTime()} available=$tomorrowAvailable")
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
        // Efter de täta publiceringsförsöken fortsätter vi var 30:e minut
        // tills ett faktiskt morgondagsdygn har sparats.
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
