package se.sensnology.elpris

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.util.Log
import android.widget.RemoteViews
import java.util.concurrent.Executors

class PriceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        ids.forEachIndexed { index, id -> update(context, manager, id, if (index == ids.lastIndex) result else null) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PriceUpdateScheduler.scheduleNext(context, tomorrowAvailable = false)
        } else if (intent.action == ACTION_PUBLICATION_CHECK) {
            // Planera efterföljande försök direkt. Nätverksresultatet nedan flyttar
            // alarmet till nästa dag om priserna redan har publicerats.
            PriceUpdateScheduler.scheduleNext(context, tomorrowAvailable = false)
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, PriceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            val result = goAsync()
            ids.forEachIndexed { index, id -> update(context, manager, id, if (index == ids.lastIndex) result else null) }
            if (ids.isEmpty()) result.finish()
        } else if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) update(context, AppWidgetManager.getInstance(context), id, goAsync())
        }
    }

    override fun onEnabled(context: Context) {
        PriceUpdateScheduler.scheduleNext(context, tomorrowAvailable = false)
    }

    override fun onDisabled(context: Context) {
        PriceUpdateScheduler.cancel(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { WidgetSettings.delete(context, it) }

    companion object {
        const val ACTION_REFRESH = "se.sensnology.elpris.REFRESH"
        const val ACTION_PUBLICATION_CHECK = "se.sensnology.elpris.PUBLICATION_CHECK"
        private val executor = Executors.newSingleThreadExecutor()

        fun update(context: Context, manager: AppWidgetManager, id: Int, pending: PendingResult? = null) {
            executor.execute {
                try {
                    val settings = WidgetSettings.load(context, id)
                    Log.i("ElprisWidget", "Update start widget=$id area=${settings.area}")
                    val data = PriceRepository.load(context, settings.area)
                    PriceUpdateScheduler.scheduleNext(context, data.tomorrow.isNotEmpty())
                    val options = manager.getAppWidgetOptions(id)
                    val density = context.resources.displayMetrics.density
                    val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320) * density).toInt()
                    val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180) * density).toInt()
                    val views = RemoteViews(context.packageName, R.layout.widget_price)
                    val oneSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, context.resources.displayMetrics)
                    views.setImageViewBitmap(R.id.chart, ChartRenderer.render(width, height, oneSp, settings, data))
                    val openApp = Intent(context, WidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(WidgetConfigActivity.EXTRA_EXISTING_WIDGET, true)
                    }
                    val openIntent = PendingIntent.getActivity(context, id, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, openIntent)
                    manager.updateAppWidget(id, views)
                    Log.i("ElprisWidget", "Update complete widget=$id today=${data.today.size} tomorrow=${data.tomorrow.size}")
                } finally { pending?.finish() }
            }
        }
    }
}
