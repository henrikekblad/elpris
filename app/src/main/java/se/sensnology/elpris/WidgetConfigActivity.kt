package se.sensnology.elpris

import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
                text = t("app_title"); textSize = 27f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        tabBar.addView(tab(t("settings")) { showSettings(editing); selectTab(0) }, weight())
        tabBar.addView(tab(t("table")) { showTable(); selectTab(1) }, weight())
        tabBar.addView(tab(t("ev")) { showCharging(); selectTab(2) }, weight())
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
        content.addView(label(t("language")))
        val language = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item,
                AppLanguageSettings.choices.map { it.label })
            setSelection(AppLanguageSettings.choices.indexOfFirst { it.code == AppLanguageSettings.selected(this@WidgetConfigActivity) }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = AppLanguageSettings.choices[position].code
                    if (selected != AppLanguageSettings.selected(this@WidgetConfigActivity)) {
                        AppLanguageSettings.save(this@WidgetConfigActivity, selected)
                        recreate()
                    }
                }
            }
        }
        content.addView(language)
        content.addView(label(t("area")))
        data class AreaChoice(val label: String, val code: String? = null) {
            override fun toString() = label
        }
        val region = AppLanguageSettings.region(this)
        val areaChoices = buildList {
            PriceMarkets.groupedWithPreferredFirst(region).forEach { (country, markets) ->
                val countryCode = when (country) { "Norge" -> "NO"; "Danmark" -> "DK"; "Finland" -> "FI"; else -> "SE" }
                add(AreaChoice(t("country_$countryCode")))
                markets.forEach { add(AreaChoice(it.selectorLabel, it.area)) }
            }
        }
        val area = Spinner(this).apply {
            adapter = object : ArrayAdapter<AreaChoice>(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item, areaChoices) {
                override fun isEnabled(position: Int) = getItem(position)?.code != null
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                        val heading = getItem(position)?.code == null
                        typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        setTextColor(if (heading) muted else dark)
                        setPadding(dp(if (heading) 12 else 26), dp(10), dp(12), dp(10))
                    }
                }
            }
            setSelection(areaChoices.indexOfFirst { it.code == old.area }.takeIf { it >= 0 }
                ?: areaChoices.indexOfFirst { it.code == "SE4" })
        }
        content.addView(area)
        content.addView(label(t("theme")))
        val themeModes = listOf(AppThemeSettings.SYSTEM, AppThemeSettings.LIGHT, AppThemeSettings.DARK)
        val theme = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(t("system"), t("light"), t("dark")))
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
        content.addView(label(t("resolution")))
        val interval = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val quarter = RadioButton(this).apply { id = 15; text = t("quarter"); isChecked = old.intervalMinutes == 15 }
        val hour = RadioButton(this).apply { id = 60; text = t("hour"); isChecked = old.intervalMinutes == 60 }
        interval.addView(quarter); interval.addView(hour); content.addView(interval)
        val priceInfo = TextView(this).apply {
            textSize = 13f; setTextColor(muted); setPadding(0, dp(14), 0, dp(8))
        }
        content.addView(priceInfo)
        val vat = checkbox(t("vat"), old.vat)
        val tax = checkbox(t("tax"), old.tax)
        val taxValue = numberField(old.taxOre, tf("tax_hint", "öre"))
        val transfer = checkbox(t("transfer"), old.transfer)
        val transferValue = numberField(old.transferOre, tf("transfer_hint", "öre"))
        content.addView(vat); content.addView(tax); content.addView(taxValue); content.addView(transfer); content.addView(transferValue)
        var previousCountry = PriceMarkets.find(old.area).country
        fun selectedArea() = (area.selectedItem as? AreaChoice)?.code ?: old.area
        fun updateMarketText() {
            val market = PriceMarkets.find(selectedArea())
            val vatText = market.vatPercent.toString().replace(".0", "").replace('.', ',')
            vat.text = if (market.vatPercent == 0.0) tf("vat_zero", market.area) else tf("vat", vatText)
            vat.isEnabled = market.vatPercent > 0.0
            quarter.isEnabled = market.sourceIntervalMinutes == 15
            if (!quarter.isEnabled) hour.isChecked = true
            taxValue.hint = tf("tax_hint", market.minorUnit)
            transferValue.hint = tf("transfer_hint", market.minorUnit)
            if (market.country != previousCountry) {
                taxValue.setText(market.suggestedTax.toString())
                transferValue.setText(market.suggestedTransfer.toString())
                previousCountry = market.country
            }
            priceInfo.text = AppLanguageSettings.marketInfo(this, market)
        }
        area.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateMarketText()
        }
        updateMarketText()
        content.addView(Button(this).apply {
            text = t("save")
            setOnClickListener {
                WidgetSettings.save(this@WidgetConfigActivity, widgetId, old.copy(
                    area = selectedArea(), vat = vat.isChecked, tax = tax.isChecked,
                    transfer = transfer.isChecked, taxOre = number(taxValue), transferOre = number(transferValue),
                    intervalMinutes = interval.checkedRadioButtonId.takeIf { it in listOf(15, 60) } ?: 15
                ))
                PriceWidgetProvider.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
                if (!editing) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                } else Toast.makeText(this@WidgetConfigActivity, td("updated"), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
    }

    private fun showCharging() {
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        var settings = WidgetSettings.load(this, widgetId)
        content.addView(TextView(this).apply {
            text = td("planner_intro")
            textSize = 15f; setTextColor(muted); setPadding(0, dp(8), 0, dp(12))
        })
        val resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(cardBackground)
        }
        val resultTitle = TextView(this).apply { textSize = 21f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD }
        resultBox.addView(resultTitle)
        val costValue = resultRow(resultBox, t("cost"))
        val energyResultValue = resultRow(resultBox, t("charging"))
        val distanceValue = resultRow(resultBox, t("range"))
        val powerValue = resultRow(resultBox, t("power"))
        val resultNote = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(7), 0, 0) }
        resultBox.addView(resultNote)
        content.addView(resultBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        content.addView(label(t("connection")).apply {
            setTextColor(dark)
            textSize = 16f
            typeface = Typeface.DEFAULT
        })
        val phases = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val onePhase = RadioButton(this).apply { id = 1; text = t("phase_one"); isChecked = settings.chargingPhases == 1 }
        val threePhase = RadioButton(this).apply { id = 3; text = t("phase_three"); isChecked = settings.chargingPhases != 1 }
        phases.addView(onePhase); phases.addView(threePhase); content.addView(phases)
        val ampsValue = valueLabel()
        val amps = slider(t("charge_current"), 6, 16, settings.chargingAmps, ampsValue) { value ->
            ampsValue.text = "$value A · %.1f kW".format(ChargingPlanner.powerKw(value, phases.checkedRadioButtonId))
        }
        val consumptionValue = valueLabel()
        val consumption = slider(t("consumption"), 10, 40, (settings.consumptionKwhPerMil * 10).toInt(), consumptionValue) { value ->
            consumptionValue.text = "%.1f kWh/10 km".format(value / 10.0)
        }
        val energyValue = valueLabel()
        val energy = slider(t("charging"), 1, 100, settings.chargingKwh, energyValue) { value ->
            energyValue.text = "$value kWh"
        }
        var departureHour = settings.departureHour
        var departureMinute = settings.departureMinute
        val useDeparture = checkbox(t("departure_check"), settings.useDepartureTime)
        content.addView(useDeparture)
        val departureButton = Button(this).apply {
            text = tf("departure", "%02d:%02d".format(departureHour, departureMinute))
            isAllCaps = false
            isEnabled = useDeparture.isChecked
        }
        content.addView(departureButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val showInWidget = checkbox(t("widget_plan"), settings.showChargingPlan)
        content.addView(showInWidget)
        val status = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(8), 0, 0) }
        content.addView(status)

        var prices: PriceResult? = null
        fun currentSettings() = settings.copy(
            chargingPhases = phases.checkedRadioButtonId.takeIf { it == 1 || it == 3 } ?: 3,
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
                val durationMinutes = ChargingPlanner.durationMinutes(settings)
                val localNow = OffsetDateTime.now()
                val marketNow = (prices?.today?.firstOrNull() ?: prices?.tomorrow?.firstOrNull())
                    ?.let { localNow.withOffsetSameInstant(it.start.offset) } ?: localNow
                val firstStart = marketNow.withSecond(0).withNano(0).let {
                    val rounded = it.withMinute((it.minute / 15) * 15)
                    if (rounded < it) rounded.plusMinutes(15) else rounded
                }
                val departure = firstStart.withHour(settings.departureHour).withMinute(settings.departureMinute)
                    .let { if (it <= firstStart) it.plusDays(1) else it }
                val missesDeparture = settings.useDepartureTime && firstStart.plusMinutes(durationMinutes) > departure
                resultTitle.text = if (missesDeparture) td("misses_departure") else td("no_plan")
                costValue.text = "–"; energyResultValue.text = "–"; distanceValue.text = "–"; powerValue.text = "–"
                costValue.setTextColor(dark)
                resultNote.text = if (missesDeparture) {
                    val hours = durationMinutes / 60
                    val minutes = durationMinutes % 60
                    val duration = if (minutes == 0L) "$hours timmar" else "$hours tim $minutes min"
                    "Laddningen kräver cirka $duration. Minska energimängden, höj strömmen eller avmarkera avresetiden för att planera över flera dygn."
                } else {
                    td("not_enough_prices")
                }
            } else {
                val locale = AppLanguageSettings.locale(this)
                resultTitle.text = "${plan.start.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))}  –  ${plan.end.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))}"
                val estimated = plan.estimatedPriceSlots > 0
                val currency = PriceMarkets.find(settings.area).currency
                costValue.text = if (estimated) "${t("estimated")} %.2f %s".format(plan.estimatedCostSek, currency) else "${t("approximately")} %.2f %s".format(plan.estimatedCostSek, currency)
                costValue.setTextColor(if (estimated) 0xFFE58A2B.toInt() else dark)
                energyResultValue.text = "%.1f kWh".format(plan.energyKwh)
                distanceValue.text = if (AppLanguageSettings.language(this) in setOf("sv", "nb")) "${t("approximately")} %.1f mil".format(plan.distanceMil)
                    else "${t("approximately")} %.0f km".format(plan.distanceMil * 10)
                powerValue.text = tf("power_phase", plan.powerKw, if (settings.chargingPhases == 1) t("phase_one") else t("phase_three"))
                resultNote.text = when {
                    estimated -> td("estimated_note").format(plan.estimatedPriceSlots)
                    else -> ""
                }
            }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ampsValue.text = "${amps.progress + 6} A · %.1f kW".format(ChargingPlanner.powerKw(amps.progress + 6, phases.checkedRadioButtonId))
                consumptionValue.text = "%.1f kWh/10 km".format((consumption.progress + 10) / 10.0)
                energyValue.text = "${energy.progress + 1} kWh"
                render()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { saveCharging(currentSettings()); status.text = td("saved") }
        }
        amps.setOnSeekBarChangeListener(listener); consumption.setOnSeekBarChangeListener(listener); energy.setOnSeekBarChangeListener(listener)
        phases.setOnCheckedChangeListener { _, _ ->
            ampsValue.text = "${amps.progress + 6} A · %.1f kW".format(ChargingPlanner.powerKw(amps.progress + 6, phases.checkedRadioButtonId))
            saveCharging(currentSettings()); render(); status.text = td("saved")
        }
        departureButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                departureHour = hour; departureMinute = minute
                departureButton.text = tf("departure", "%02d:%02d".format(hour, minute))
                saveCharging(currentSettings()); render(); status.text = td("saved")
            }, departureHour, departureMinute, true).show()
        }
        useDeparture.setOnCheckedChangeListener { _, checked ->
            departureButton.isEnabled = checked
            saveCharging(currentSettings()); render(); status.text = td("saved")
        }
        showInWidget.setOnCheckedChangeListener { _, _ -> saveCharging(currentSettings()); render(); status.text = td("saved_updated") }
        render()
        Executors.newSingleThreadExecutor().execute {
            val loaded = PriceRepository.load(this, settings.area)
            runOnUiThread { prices = loaded; render(); status.text = td("prices_for").format(settings.area) }
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
                val market = PriceMarkets.find(settings.area)
                content.addView(TextView(this).apply { text = "${settings.area} · ${settings.intervalMinutes}-minuterspriser · ${market.priceUnit}"; textSize = 19f; setTextColor(dark); setPadding(0, dp(8), 0, dp(12)) })
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
                if (count == 0) content.addView(TextView(this).apply { text = t("no_prices"); setPadding(0, dp(20), 0, 0) })
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
        val header = rowView(t("time"), t("today"), t("tomorrow"), true, null, null, false).apply {
            setBackgroundColor(appBackground)
        }
        content.addView(header)
        return header
    }
    private fun addRow(time: String, today: Double?, tomorrow: Double?, todayAverage: Double?, tomorrowAverage: Double?,
                       todayRange: Pair<Double, Double>, tomorrowRange: Pair<Double, Double>, current: Boolean) =
        content.addView(rowView(time, today?.let { "%.2f".format(it) } ?: "–", tomorrow?.let { "%.2f".format(it) } ?: "–", current,
            today?.let { cellStyle(it, todayAverage ?: it, todayRange) },
            tomorrow?.let { cellStyle(it, tomorrowAverage ?: it, tomorrowRange) }, current))

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
                        todayStyle: Pair<Boolean, Int>?, tomorrowStyle: Pair<Boolean, Int>?, framed: Boolean) = LinearLayout(this).apply {
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
        if (framed) foreground = GradientDrawable().apply {
            setColor(0x00000000)
            setStroke(dp(2), if (AppThemeSettings.isDark(this@WidgetConfigActivity)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
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
    private fun t(key: String) = AppLanguageSettings.text(this, key)
    private fun tf(key: String, vararg args: Any) = String.format(AppLanguageSettings.locale(this), t(key), *args)
    private fun td(key: String) = AppLanguageSettings.detail(this, key)

    companion object { const val EXTRA_EXISTING_WIDGET = "existing_widget" }
}
