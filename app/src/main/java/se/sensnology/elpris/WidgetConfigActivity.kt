package se.sensnology.elpris

import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.Executors

class WidgetConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var content: LinearLayout
    private lateinit var scrollView: ScrollView
    private var dark = 0xFF192029.toInt()
    private var muted = 0xFF667180.toInt()
    private var accent = 0xFF1769AA.toInt()
    private var appBackground = 0xFFF6F7FB.toInt()
    private var cardBackground = 0xFFE7EEF6.toInt()
    private val tabs = mutableListOf<Pair<TextView, View>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeSettings.apply(this)
        super.onCreate(savedInstanceState)
        if (AppThemeSettings.isDark(this)) {
            dark = 0xFFF5F7FA.toInt(); muted = 0xFFA8B1BC.toInt(); accent = 0xFF65B9F0.toInt()
            appBackground = 0xFF10151B.toInt(); cardBackground = 0xFF222C37.toInt()
        }
        val editing = intent.getBooleanExtra(EXTRA_EXISTING_WIDGET, false)
        if (!editing) setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        if (widgetId > 0) LauncherActivity.rememberWidget(this, widgetId)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(20)); setBackgroundColor(appBackground) }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(10))
            translationY = dp(12).toFloat()
            addView(ImageView(this@WidgetConfigActivity).apply {
                setImageResource(R.drawable.app_icon); contentDescription = "Elpris"
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "Elpris"; textSize = 27f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        tabBar.addView(tab("Inställningar") { showSettings(editing); selectTab(0) }, weight())
        tabBar.addView(tab("Pristabell") { showTable(); selectTab(1) }, weight())
        tabBar.addView(tab("Elbil") { showCharging(); selectTab(2) }, weight())
        root.addView(tabBar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply { addView(content) }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showSettings(editing)
        selectTab(0)
    }

    private fun showSettings(editing: Boolean) {
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        val old = WidgetSettings.load(this, widgetId)
        content.addView(label("Färgtema"))
        val themeModes = listOf(AppThemeSettings.SYSTEM, AppThemeSettings.LIGHT, AppThemeSettings.DARK)
        val theme = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Systemstandard", "Ljust", "Mörkt"))
            setSelection(themeModes.indexOf(AppThemeSettings.mode(this@WidgetConfigActivity)).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = themeModes[position]
                    if (selected != AppThemeSettings.mode(this@WidgetConfigActivity)) {
                        AppThemeSettings.save(this@WidgetConfigActivity, selected)
                        recreate()
                    }
                }
            }
        }
        content.addView(theme)
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
            text = "Priserna från API:t är spotpris utan moms, skatter och tillägg. Förslag för 2026: energiskatt 36,0 och överföringsavgift 30,0 öre/kWh exkl. moms. Överföringsavgiften varierar – kontrollera ditt nätavtal. Om moms är aktiverad läggs 25 % på hela summan."
            textSize = 13f; setTextColor(muted); setPadding(0, dp(14), 0, dp(8))
        })
        val vat = checkbox("Lägg på 25 % moms", old.vat)
        val tax = checkbox("Lägg på energiskatt", old.tax)
        val taxValue = numberField(old.taxOre.takeUnless { it == 0.0 } ?: WidgetSettings.DEFAULT_TAX_ORE, "Energiskatt, öre/kWh")
        val transfer = checkbox("Lägg på överföringsavgift", old.transfer)
        val transferValue = numberField(old.transferOre.takeUnless { it == 0.0 } ?: WidgetSettings.DEFAULT_TRANSFER_ORE, "Överföringsavgift, öre/kWh")
        content.addView(vat); content.addView(tax); content.addView(taxValue); content.addView(transfer); content.addView(transferValue)
        content.addView(Button(this).apply {
            text = "Spara och uppdatera"
            setOnClickListener {
                WidgetSettings.save(this@WidgetConfigActivity, widgetId, old.copy(
                    area = area.selectedItem.toString(), vat = vat.isChecked, tax = tax.isChecked,
                    transfer = transfer.isChecked, taxOre = number(taxValue), transferOre = number(transferValue),
                    intervalMinutes = interval.checkedRadioButtonId.takeIf { it in listOf(15, 60) } ?: 15
                ))
                PriceWidgetProvider.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
                if (!editing) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                } else Toast.makeText(this@WidgetConfigActivity, "Widgeten uppdateras", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
    }

    private fun showCharging() {
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        var settings = WidgetSettings.load(this, widgetId)
        content.addView(TextView(this).apply {
            text = "Hitta den billigaste sammanhängande laddtiden under det närmaste dygnet."
            textSize = 15f; setTextColor(muted); setPadding(0, dp(8), 0, dp(12))
        })
        val resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(cardBackground)
        }
        val resultTitle = TextView(this).apply { textSize = 21f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD }
        resultBox.addView(resultTitle)
        val costValue = resultRow(resultBox, "Kostnad")
        val energyResultValue = resultRow(resultBox, "Laddning")
        val distanceValue = resultRow(resultBox, "Räckvidd")
        val powerValue = resultRow(resultBox, "Effekt")
        val resultNote = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(7), 0, 0) }
        resultBox.addView(resultNote)
        content.addView(resultBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val ampsValue = valueLabel()
        val amps = slider("Laddström", 6, 16, settings.chargingAmps, ampsValue) { value ->
            ampsValue.text = "$value A · %.1f kW".format(ChargingPlanner.powerKw(value))
        }
        val consumptionValue = valueLabel()
        val consumption = slider("Förbrukning", 10, 40, (settings.consumptionKwhPerMil * 10).toInt(), consumptionValue) { value ->
            consumptionValue.text = "%.1f kWh/mil".format(value / 10.0)
        }
        val energyValue = valueLabel()
        val energy = slider("Laddning", 1, 100, settings.chargingKwh, energyValue) { value ->
            energyValue.text = "$value kWh"
        }
        var departureHour = settings.departureHour
        var departureMinute = settings.departureMinute
        val useDeparture = checkbox("Laddningen ska vara klar före avresa", settings.useDepartureTime)
        content.addView(useDeparture)
        val departureButton = Button(this).apply {
            text = "Avresa %02d:%02d".format(departureHour, departureMinute)
            isAllCaps = false
            isEnabled = useDeparture.isChecked
        }
        content.addView(departureButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val showInWidget = checkbox("Visa rekommenderad laddtid underst i widgeten", settings.showChargingPlan)
        content.addView(showInWidget)
        val status = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(8), 0, 0) }
        content.addView(status)

        var prices: PriceResult? = null
        fun currentSettings() = settings.copy(
            chargingAmps = amps.progress + 6,
            consumptionKwhPerMil = (consumption.progress + 10) / 10.0,
            chargingKwh = energy.progress + 1,
            showChargingPlan = showInWidget.isChecked,
            useDepartureTime = useDeparture.isChecked,
            departureHour = departureHour,
            departureMinute = departureMinute
        )
        fun render() {
            settings = currentSettings()
            val plan = prices?.let { ChargingPlanner.calculate(it, settings) }
            if (plan == null) {
                resultTitle.text = "Ingen laddtid kan beräknas"
                costValue.text = "–"; energyResultValue.text = "–"; distanceValue.text = "–"; powerValue.text = "–"
                resultNote.text = "Det saknas tillräckligt många publicerade, sammanhängande priser."
            } else {
                val swedish = Locale.forLanguageTag("sv-SE")
                resultTitle.text = "${plan.start.format(DateTimeFormatter.ofPattern("EEE HH:mm", swedish))}  –  ${plan.end.format(DateTimeFormatter.ofPattern("EEE HH:mm", swedish))}"
                costValue.text = "cirka %.2f kr".format(plan.estimatedCostSek)
                energyResultValue.text = "%.1f kWh".format(plan.energyKwh)
                distanceValue.text = "cirka %.1f mil".format(plan.distanceMil)
                powerValue.text = "%.1f kW".format(plan.powerKw)
                resultNote.text = if (plan.complete24Hours) "" else "Baserat på hittills publicerade priser."
            }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ampsValue.text = "${amps.progress + 6} A · %.1f kW".format(ChargingPlanner.powerKw(amps.progress + 6))
                consumptionValue.text = "%.1f kWh/mil".format((consumption.progress + 10) / 10.0)
                energyValue.text = "${energy.progress + 1} kWh"
                render()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { saveCharging(currentSettings()); status.text = "Sparat" }
        }
        amps.setOnSeekBarChangeListener(listener); consumption.setOnSeekBarChangeListener(listener); energy.setOnSeekBarChangeListener(listener)
        departureButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                departureHour = hour; departureMinute = minute
                departureButton.text = "Avresa %02d:%02d".format(hour, minute)
                saveCharging(currentSettings()); render(); status.text = "Sparat"
            }, departureHour, departureMinute, true).show()
        }
        useDeparture.setOnCheckedChangeListener { _, checked ->
            departureButton.isEnabled = checked
            saveCharging(currentSettings()); render(); status.text = "Sparat"
        }
        showInWidget.setOnCheckedChangeListener { _, _ -> saveCharging(currentSettings()); render(); status.text = "Sparat och widgeten uppdateras" }
        render()
        Executors.newSingleThreadExecutor().execute {
            val loaded = PriceRepository.load(this, settings.area)
            runOnUiThread { prices = loaded; render(); status.text = "Priser för ${settings.area}" }
        }
    }

    private fun saveCharging(value: WidgetSettings) {
        WidgetSettings.save(this, widgetId, value)
        PriceWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
    }

    private fun slider(title: String, min: Int, max: Int, value: Int, valueView: TextView, updateValue: (Int) -> Unit): SeekBar {
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(dark) }, weight())
        heading.addView(valueView)
        content.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        val seek = SeekBar(this).apply { this.max = max - min; progress = (value - min).coerceIn(0, max - min) }
        updateValue(seek.progress + min)
        content.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateValue(progress + min)
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        return seek
    }

    private fun valueLabel() = TextView(this).apply { textSize = 16f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD }

    private fun resultRow(parent: LinearLayout, name: String): TextView {
        val value = TextView(this).apply {
            textSize = 15f; setTextColor(dark); gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD
        }
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, 0)
            addView(TextView(this@WidgetConfigActivity).apply { text = name; textSize = 15f; setTextColor(muted) }, weight())
            addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        return value
    }

    private fun showTable() {
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        content.addView(ProgressBar(this).apply { isIndeterminate = true })
        Executors.newSingleThreadExecutor().execute {
            val settings = WidgetSettings.load(this, widgetId)
            val result = PriceRepository.load(this, settings.area)
            runOnUiThread {
                content.removeAllViews()
                content.addView(TextView(this).apply { text = "${settings.area} · ${settings.intervalMinutes}-minuterspriser"; textSize = 19f; setTextColor(dark); setPadding(0, dp(8), 0, dp(12)) })
                val tableHeader = addTableHeader()
                scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    val offset = (scrollY - tableHeader.top).coerceAtLeast(0)
                    tableHeader.translationY = offset.toFloat()
                    tableHeader.elevation = if (offset > 0) dp(3).toFloat() else 0f
                }
                val today = ChartRenderer.aggregate(result.today, settings)
                val tomorrow = ChartRenderer.aggregate(result.tomorrow, settings)
                val todayAverage = today.map { settings.apply(it.second) }.average().takeUnless { it.isNaN() }
                val tomorrowAverage = tomorrow.map { settings.apply(it.second) }.average().takeUnless { it.isNaN() }
                val todayPrices = today.map { settings.apply(it.second) }
                val tomorrowPrices = tomorrow.map { settings.apply(it.second) }
                val todayRange = (todayPrices.minOrNull() ?: 0.0) to (todayPrices.maxOrNull() ?: 0.0)
                val tomorrowRange = (tomorrowPrices.minOrNull() ?: 0.0) to (tomorrowPrices.maxOrNull() ?: 0.0)
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
                        todayRange,
                        tomorrowRange,
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

    private fun addTableHeader(): View {
        val header = rowView("Tid", "Idag", "Imorgon", true, null, null).apply {
            setBackgroundColor(appBackground)
        }
        content.addView(header)
        return header
    }
    private fun addRow(time: String, today: Double?, tomorrow: Double?, todayAverage: Double?, tomorrowAverage: Double?,
                       todayRange: Pair<Double, Double>, tomorrowRange: Pair<Double, Double>, current: Boolean) =
        content.addView(rowView(time, today?.let { "%.2f".format(it) } ?: "–", tomorrow?.let { "%.2f".format(it) } ?: "–", current,
            today?.let { cellStyle(it, todayAverage ?: it, todayRange) },
            tomorrow?.let { cellStyle(it, tomorrowAverage ?: it, tomorrowRange) }))

    private fun cellStyle(value: Double, average: Double, range: Pair<Double, Double>): Pair<Boolean, Int> {
        val span = range.second - range.first
        val fraction = if (span <= 0.0) 1.0 else (value - range.first) / span
        return (value <= average) to (1200 + fraction.coerceIn(0.0, 1.0) * 8800).toInt()
    }

    private fun priceBackground(cheap: Boolean, level: Int) = LayerDrawable(arrayOf(
        ColorDrawable(if (AppThemeSettings.isDark(this)) {
            if (cheap) 0xFF17372C.toInt() else 0xFF482524.toInt()
        } else if (cheap) 0xFFDDF3E6.toInt() else 0xFFF9DEDC.toInt()),
        ClipDrawable(ColorDrawable(if (AppThemeSettings.isDark(this)) {
            if (cheap) 0xFF246348.toInt() else 0xFF7B3835.toInt()
        } else if (cheap) 0xFF9DDEB9.toInt() else 0xFFF2A29E.toInt()), Gravity.START, ClipDrawable.HORIZONTAL).apply {
            this.level = level
        }
    ))

    private fun rowView(a: String, b: String, c: String, bold: Boolean,
                        todayStyle: Pair<Boolean, Int>?, tomorrowStyle: Pair<Boolean, Int>?) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        listOf(a to null, b to todayStyle, c to tomorrowStyle).forEachIndexed { index, (value, style) -> addView(TextView(this@WidgetConfigActivity).apply {
            text = value
            textSize = 14f
            setTextColor(dark)
            gravity = if (index == 0) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(7), dp(5), dp(7), dp(5))
            if (style != null) background = priceBackground(style.first, style.second)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, weight()) }
    }
    private fun tab(name: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val label = TextView(this@WidgetConfigActivity).apply {
            text = name; textSize = 15f; gravity = Gravity.CENTER; setPadding(dp(4), dp(10), dp(4), dp(9)); setOnClickListener { action() }
        }
        val indicator = View(this@WidgetConfigActivity).apply { setBackgroundColor(accent); setOnClickListener { action() } }
        addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(indicator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        tabs.add(label to indicator)
    }
    private fun selectTab(selected: Int) = tabs.forEachIndexed { index, (label, indicator) ->
        label.setTextColor(if (index == selected) accent else muted)
        label.typeface = if (index == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        indicator.visibility = if (index == selected) View.VISIBLE else View.INVISIBLE
    }
    private fun label(value: String) = TextView(this).apply { text = value; textSize = 14f; setTextColor(muted); setPadding(0, dp(16), 0, dp(4)) }
    private fun checkbox(value: String, checked: Boolean) = CheckBox(this).apply { text = value; isChecked = checked; textSize = 16f }
    private fun numberField(value: Double, hintText: String) = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = hintText; setText(if (value == 0.0) "" else value.toString()) }
    private fun number(field: EditText) = field.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_EXISTING_WIDGET = "existing_widget" }
}
