package se.sensnology.elpris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

object ChartRenderer {
    private const val BG = 0xF21A2029.toInt()
    private const val TEXT = 0xFFF5F7FA.toInt()
    private const val MUTED = 0xFF9FA8B5.toInt()
    private const val CHEAP = 0xFF43C887.toInt()
    private const val EXPENSIVE = 0xFFFF625F.toInt()
    private const val TOMORROW = 0xFFC5CBD3.toInt()

    fun render(context: Context, width: Int, height: Int, scaledDensity: Float, settings: WidgetSettings, result: PriceResult): Bitmap {
        val w = width.coerceIn(180, 1400)
        val h = height.coerceIn(100, 900)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = BG
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 22f, 22f, paint)
        val scale = min(w / 420f, h / 220f).coerceIn(.72f, 1.45f)
        val pad = 14f * scale
        // The canvas can be considerably larger than the widget's logical dp size.
        // Sizes based on image height remain readable after launcher scaling.
        val today = PriceAggregation.aggregate(result.today, settings).map { it.first to settings.apply(it.second) }
        val tomorrow = PriceAggregation.aggregate(result.tomorrow, settings).map { it.first to settings.apply(it.second) }
        val allValues = (today + tomorrow).map { it.second }
        if (allValues.isEmpty()) {
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 12f * scale
            paint.color = MUTED
            canvas.drawText(AppLanguageSettings.text(context, R.string.could_not_fetch), pad, h / 2f, paint)
            return bitmap
        }

        val now = OffsetDateTime.now(PriceMarkets.find(settings.area).zoneId)
        val chargingPlan = if (settings.showChargingPlan) ChargingPlanner.calculate(result, settings, now) else null
        val current = today.lastOrNull { it.first <= now }?.second
        val dayMax = today.maxOfOrNull { it.second }
        val dayMin = today.minOfOrNull { it.second }

        val desiredHeader = 19f * scaledDensity
        val maxText = dayMax?.let { "↑%.1f".format(it) } ?: "↑–"
        val minText = dayMin?.let { "↓%.1f".format(it) } ?: "↓–"
        val priceUnit = PriceMarkets.find(settings.area).priceUnit
        val currentText = current?.let { "%.1f %s".format(it, priceUnit) } ?: "– $priceUnit"
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = desiredHeader
        val requiredWidth = paint.measureText(maxText) + paint.measureText(minText) + paint.measureText(currentText) + pad * 5
        // Keep the same size across widget dimensions; shrink only as a last resort to avoid overlap.
        val headerSize = if (requiredWidth > w) max(14f * scaledDensity, desiredHeader * w / requiredWidth) else desiredHeader
        paint.textSize = headerSize
        paint.color = EXPENSIVE
        canvas.drawText(maxText, pad, pad + headerSize, paint)
        paint.color = CHEAP
        val minX = pad + paint.measureText(maxText) + pad * 1.4f
        canvas.drawText(minText, minX, pad + headerSize, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = headerSize
        paint.color = TEXT
        canvas.drawText(currentText, w - pad, pad + headerSize, paint)
        paint.textAlign = Paint.Align.LEFT

        val axisText = 14f * scaledDensity
        val top = pad + headerSize * 1.65f
        val footerHeight = if (chargingPlan != null) axisText * 2.0f else 0f
        val bottom = h - pad - axisText * 1.35f - footerHeight
        val left = pad + max(axisText * 2.5f, w * .070f)
        val right = w - pad
        val minValue = min(0.0, allValues.minOrNull() ?: 0.0)
        val maxValue = max(1.0, allValues.maxOrNull() ?: 1.0)
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        fun y(value: Double) = bottom - ((value - minValue) / range * (bottom - top)).toFloat()
        fun x(time: OffsetDateTime): Float {
            val minute = time.hour * 60 + time.minute
            return left + minute / 1440f * (right - left)
        }

        chargingPlan?.let { plan ->
            paint.color = 0xFF4B9FEA.toInt()
            paint.alpha = 38
            plan.periods.forEach { period ->
                var segmentStart = period.start
                while (segmentStart.toLocalDate() < period.end.toLocalDate()) {
                    canvas.drawRect(x(segmentStart), top, right, bottom, paint)
                    segmentStart = segmentStart.toLocalDate().plusDays(1).atStartOfDay().atOffset(segmentStart.offset)
                }
                if (segmentStart < period.end) canvas.drawRect(
                    if (segmentStart.hour == 0 && segmentStart.minute == 0) left else x(segmentStart),
                    top, x(period.end), bottom, paint
                )
            }
            paint.alpha = 255
        }

        paint.strokeWidth = 1f
        for (i in 0..2) {
            val gy = top + i * (bottom - top) / 2f
            paint.color = 0x243E4956
            canvas.drawLine(left, gy, right, gy, paint)
            paint.color = MUTED
            paint.textSize = axisText
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("%.0f".format(maxValue - i * range / 2), left - axisText * .45f, gy + axisText * .35f, paint)
        }
        paint.textAlign = Paint.Align.LEFT

        // Draw tomorrow first as a neutral comparison behind today's prices.
        drawTomorrow(canvas, paint, tomorrow, ::x, ::y, scale)
        drawToday(canvas, paint, today, ::x, ::y, scale, now)

        paint.textSize = axisText
        paint.color = MUTED
        listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18", 24 to "24").forEach { (hour, label) ->
            val px = left + hour / 24f * (right - left)
            paint.textAlign = when (hour) { 0 -> Paint.Align.LEFT; 24 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(label, px, bottom + axisText * 1.15f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        chargingPlan?.let { plan ->
            paint.color = TEXT
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = max(axisText, 13f * scaledDensity)
            paint.textAlign = Paint.Align.CENTER
            val locale = AppLanguageSettings.locale(context)
            val day = plan.start.format(DateTimeFormatter.ofPattern("EEE", locale))
            val start = plan.start.format(DateTimeFormatter.ofPattern("HH:mm"))
            val end = plan.end.format(DateTimeFormatter.ofPattern(
                if (plan.start.toLocalDate() == plan.end.toLocalDate()) "HH:mm" else "EEE HH:mm",
                locale
            ))
            val estimate = if (plan.estimatedPriceSlots > 0) "${AppLanguageSettings.text(context, R.string.estimated)} · " else ""
            val timeText = if (plan.periods.size == 1) "$day $start–$end"
                else AppLanguageSettings.text(context, R.string.charging_period_count, plan.periods.size)
            val chargingText = "$estimate${AppLanguageSettings.text(context, R.string.charge)} $timeText · %.1f kWh".format(plan.energyKwh)
            val rangeText = if (AppLanguageSettings.language(context) in setOf("sv", "nb")) "%.1f mil".format(plan.distanceMil)
                else "%.0f km".format(plan.distanceMil * 10)
            val chargingTextWithRange = "$chargingText · $rangeText"
            val footerText = if (paint.measureText(chargingTextWithRange) <= right - left) chargingTextWithRange else chargingText
            canvas.drawText(footerText, (left + right) / 2f, h - pad * .7f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        return bitmap
    }

    private fun drawTomorrow(canvas: Canvas, paint: Paint, values: List<Pair<OffsetDateTime, Double>>,
                             x: (OffsetDateTime) -> Float, y: (Double) -> Float, scale: Float) {
        paint.style = Paint.Style.FILL; paint.color = TOMORROW; paint.alpha = 190
        val radius = max(3.2f * scale, canvas.height * .0052f)
        values.forEach { canvas.drawCircle(x(it.first), y(it.second), radius, paint) }
        paint.alpha = 255
    }

    private fun drawToday(canvas: Canvas, paint: Paint, values: List<Pair<OffsetDateTime, Double>>,
                          x: (OffsetDateTime) -> Float, y: (Double) -> Float, scale: Float, now: OffsetDateTime) {
        if (values.isEmpty()) return
        paint.style = Paint.Style.FILL
        val average = values.map { it.second }.average()
        val currentIndex = values.indices.lastOrNull { values[it].first <= now } ?: -1
        val radius = max(4f * scale, canvas.height * .0062f)
        values.forEachIndexed { index, item ->
            paint.color = if (item.second <= average) CHEAP else EXPENSIVE
            paint.alpha = if (index <= currentIndex) 245 else 205
            canvas.drawCircle(x(item.first), y(item.second), if (index == currentIndex) radius * 2.35f else radius, paint)
            if (index == currentIndex) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = max(2f, radius * .35f); paint.color = Color.WHITE; paint.alpha = 230
                canvas.drawCircle(x(item.first), y(item.second), radius * 2.35f, paint); paint.style = Paint.Style.FILL
            }
        }
        paint.alpha = 255
    }

}
