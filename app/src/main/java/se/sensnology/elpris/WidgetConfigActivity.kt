package se.sensnology.elpris

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.util.concurrent.Executors

class WidgetConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var content: LinearLayout
    private lateinit var scrollView: ScrollView
    private val dark = 0xFF192029.toInt()
    private val muted = 0xFF667180.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editing = intent.getBooleanExtra(EXTRA_EXISTING_WIDGET, false)
        if (!editing) setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(20)); setBackgroundColor(0xFFF6F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "Elpris"; textSize = 27f; setTextColor(dark) })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, dp(8)) }
        val settingsTab = tab("Inställningar") { showSettings(editing) }
        val tableTab = tab("Pristabell") { showTable() }
        tabs.addView(settingsTab, weight()); tabs.addView(tableTab, weight())
        root.addView(tabs)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply { addView(content) }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showSettings(editing)
    }

    private fun showSettings(editing: Boolean) {
        content.removeAllViews()
        val old = WidgetSettings.load(this, widgetId)
        content.addView(label("Elprisområde"))
        val area = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item, listOf("SE1", "SE2", "SE3", "SE4"))
            setSelection(listOf("SE1", "SE2", "SE3", "SE4").indexOf(old.area).coerceAtLeast(0))
        }
        content.addView(area)
        content.addView(label("Upplösning"))
        val interval = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val quarter = RadioButton(this).apply { id = 15; text = "15 minuter"; isChecked = old.intervalMinutes == 15 }
        val hour = RadioButton(this).apply { id = 60; text = "Heltimmesmedel"; isChecked = old.intervalMinutes == 60 }
        interval.addView(quarter); interval.addView(hour); content.addView(interval)
        content.addView(TextView(this).apply {
            text = "Priserna från API:t är spotpris utan moms, skatter och tillägg. Moms beräknas sist."
            textSize = 13f; setTextColor(muted); setPadding(0, dp(14), 0, dp(8))
        })
        val vat = checkbox("Lägg på 25 % moms", old.vat)
        val tax = checkbox("Lägg på energiskatt", old.tax)
        val taxValue = numberField(old.taxOre, "Energiskatt, öre/kWh")
        val transfer = checkbox("Lägg på överföringsavgift", old.transfer)
        val transferValue = numberField(old.transferOre, "Överföringsavgift, öre/kWh")
        content.addView(vat); content.addView(tax); content.addView(taxValue); content.addView(transfer); content.addView(transferValue)
        content.addView(Button(this).apply {
            text = "Spara och uppdatera"
            setOnClickListener {
                WidgetSettings.save(this@WidgetConfigActivity, widgetId, WidgetSettings(
                    area.selectedItem.toString(), vat.isChecked, tax.isChecked, transfer.isChecked,
                    number(taxValue), number(transferValue), interval.checkedRadioButtonId.takeIf { it in listOf(15, 60) } ?: 15
                ))
                PriceWidgetProvider.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
                if (!editing) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                } else Toast.makeText(this@WidgetConfigActivity, "Widgeten uppdateras", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
    }

    private fun showTable() {
        content.removeAllViews()
        content.addView(ProgressBar(this).apply { isIndeterminate = true })
        Executors.newSingleThreadExecutor().execute {
            val settings = WidgetSettings.load(this, widgetId)
            val result = PriceRepository.load(this, settings.area)
            runOnUiThread {
                content.removeAllViews()
                content.addView(TextView(this).apply { text = "${settings.area} · ${settings.intervalMinutes}-minuterspriser"; textSize = 19f; setTextColor(dark); setPadding(0, dp(8), 0, dp(12)) })
                addTableHeader()
                val today = ChartRenderer.aggregate(result.today, settings)
                val tomorrow = ChartRenderer.aggregate(result.tomorrow, settings)
                val todayAverage = today.map { settings.apply(it.second) }.average().takeUnless { it.isNaN() }
                val tomorrowAverage = tomorrow.map { settings.apply(it.second) }.average().takeUnless { it.isNaN() }
                val now = OffsetDateTime.now()
                val currentIndex = today.indices.lastOrNull { today[it].first <= now } ?: -1
                val count = maxOf(today.size, tomorrow.size)
                repeat(count) { index ->
                    val time = (today.getOrNull(index)?.first ?: tomorrow.getOrNull(index)?.first)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
                    addRow(
                        time,
                        today.getOrNull(index)?.second?.let(settings::apply),
                        tomorrow.getOrNull(index)?.second?.let(settings::apply),
                        todayAverage,
                        tomorrowAverage,
                        index == currentIndex
                    )
                }
                if (count == 0) content.addView(TextView(this).apply { text = "Inga priser kunde hämtas."; setPadding(0, dp(20), 0, 0) })
                if (currentIndex >= 0) {
                    content.post {
                        val row = content.getChildAt(currentIndex + 2) // titel + tabellhuvud
                        scrollView.scrollTo(0, (row.top - scrollView.height / 3).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    private fun addTableHeader() = content.addView(rowView("Tid", "Idag", "Imorgon", true, null, null))
    private fun addRow(time: String, today: Double?, tomorrow: Double?, todayAverage: Double?, tomorrowAverage: Double?, current: Boolean) =
        content.addView(rowView(time, today?.let { "%.2f".format(it) } ?: "–", tomorrow?.let { "%.2f".format(it) } ?: "–", current,
            today?.let { it <= (todayAverage ?: it) }, tomorrow?.let { it <= (tomorrowAverage ?: it) }))

    private fun rowView(a: String, b: String, c: String, bold: Boolean, todayCheap: Boolean?, tomorrowCheap: Boolean?) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        listOf(a to null, b to todayCheap, c to tomorrowCheap).forEach { (value, cheap) -> addView(TextView(this@WidgetConfigActivity).apply {
            text = value
            textSize = 14f
            setTextColor(dark)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            if (cheap != null) setBackgroundColor(if (cheap) 0xFFDDF3E6.toInt() else 0xFFF9DEDC.toInt())
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, weight()) }
    }
    private fun tab(name: String, action: () -> Unit) = Button(this).apply { text = name; setOnClickListener { action() } }
    private fun label(value: String) = TextView(this).apply { text = value; textSize = 14f; setTextColor(muted); setPadding(0, dp(16), 0, dp(4)) }
    private fun checkbox(value: String, checked: Boolean) = CheckBox(this).apply { text = value; isChecked = checked; textSize = 16f }
    private fun numberField(value: Double, hintText: String) = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = hintText; setText(if (value == 0.0) "" else value.toString()) }
    private fun number(field: EditText) = field.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_EXISTING_WIDGET = "existing_widget" }
}
