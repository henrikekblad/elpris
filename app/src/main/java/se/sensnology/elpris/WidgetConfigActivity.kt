package se.sensnology.elpris

import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.net.Uri
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.PasswordTransformationMethod
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
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
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var viewGeneration = 0

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
                text = t(R.string.app_title); textSize = 27f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        tabBar.addView(tab(t(R.string.settings)) { showSettings(editing); selectTab(0) }, weight())
        tabBar.addView(tab(t(R.string.table)) { showTable(); selectTab(1) }, weight())
        tabBar.addView(tab(t(R.string.ev)) { showCharging(); selectTab(2) }, weight())
        root.addView(tabBar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply { addView(content) }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showSettings(editing)
        selectTab(0)
        if (intent.hasExtra(EXTRA_HOME_ASSISTANT_PAIRING)) {
            val message = if (intent.getBooleanExtra(EXTRA_HOME_ASSISTANT_PAIRING, false))
                t(R.string.home_assistant_paired) else t(R.string.home_assistant_pairing_invalid)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettings(editing: Boolean) {
        viewGeneration++
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        val old = WidgetSettings.load(this, widgetId)
        content.addView(sectionTitle(t(R.string.section_general)))
        content.addView(label(t(R.string.language)))
        val language = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item,
                AppLanguageSettings.choices.map { t(it.label) })
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
        content.addView(label(t(R.string.theme)))
        val themeModes = listOf(AppThemeSettings.SYSTEM, AppThemeSettings.LIGHT, AppThemeSettings.DARK)
        val theme = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(t(R.string.system), t(R.string.light), t(R.string.dark)))
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
        content.addView(sectionTitle(t(R.string.section_electricity_price)))
        content.addView(label(t(R.string.area)))
        data class AreaChoice(val label: String, val code: String? = null) {
            override fun toString() = label
        }
        val region = AppLanguageSettings.region(this)
        val areaChoices = buildList {
            PriceMarkets.groupedWithPreferredFirst(region).forEach { (country, markets) ->
                val countryName = when (country) {
                    MarketCountry.NO -> t(R.string.country_no); MarketCountry.DK -> t(R.string.country_dk)
                    MarketCountry.FI -> t(R.string.country_fi); MarketCountry.SE -> t(R.string.country_se)
                }
                add(AreaChoice(countryName))
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
        content.addView(label(t(R.string.resolution)))
        val interval = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val quarterText = t(R.string.quarter)
        val hourText = t(R.string.hour)
        val quarterId = View.generateViewId()
        val hourId = View.generateViewId()
        val quarter = RadioButton(this).apply { id = quarterId; text = quarterText; isChecked = old.intervalMinutes == 15 }
        val hour = RadioButton(this).apply { id = hourId; text = hourText; isChecked = old.intervalMinutes == 60 }
        interval.addView(quarter); interval.addView(hour); content.addView(interval)
        val priceInfo = TextView(this).apply {
            textSize = 13f; setTextColor(muted); setPadding(0, dp(14), 0, dp(8))
        }
        content.addView(priceInfo)
        val vat = checkbox(t(R.string.vat, "25"), old.vat)
        val tax = checkbox(t(R.string.tax), old.tax)
        val taxValue = numberField(old.taxMinorUnit, t(R.string.tax_hint, "öre"))
        val transfer = checkbox(t(R.string.transfer), old.transfer)
        val transferValue = numberField(old.gridFeeMinorUnit, t(R.string.transfer_hint, "öre"))
        content.addView(vat); content.addView(tax); content.addView(taxValue); content.addView(transfer); content.addView(transferValue)
        var previousCountry = PriceMarkets.find(old.area).country
        fun selectedArea() = (area.selectedItem as? AreaChoice)?.code ?: old.area
        fun updateMarketText() {
            val market = PriceMarkets.find(selectedArea())
            val vatText = market.vatPercent.toString().replace(".0", "").replace('.', ',')
            vat.text = if (market.vatPercent == 0.0) t(R.string.vat_zero, market.area) else t(R.string.vat, vatText)
            vat.isEnabled = market.vatPercent > 0.0
            quarter.isEnabled = market.sourceIntervalMinutes == 15
            if (!quarter.isEnabled) hour.isChecked = true
            taxValue.hint = t(R.string.tax_hint, market.minorUnit)
            transferValue.hint = t(R.string.transfer_hint, market.minorUnit)
            if (market.country != previousCountry) {
                taxValue.setText(market.suggestedTax.toString())
                transferValue.setText(market.suggestedTransfer.toString())
                previousCountry = market.country
            }
            priceInfo.text = t(R.string.market_info, market.suggestedTax.toString(), market.minorUnit, market.suggestedTransfer.toString())
        }
        area.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateMarketText()
        }
        updateMarketText()
        content.addView(Button(this).apply {
            text = t(R.string.save)
            setOnClickListener {
                WidgetSettings.save(this@WidgetConfigActivity, widgetId, old.copy(
                    area = selectedArea(), vat = vat.isChecked, tax = tax.isChecked,
                    transfer = transfer.isChecked, taxMinorUnit = number(taxValue), gridFeeMinorUnit = number(transferValue),
                    intervalMinutes = if (interval.checkedRadioButtonId == hourId) 60 else 15
                ))
                PriceWidgetProvider.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
                if (!editing) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                } else Toast.makeText(this@WidgetConfigActivity, t(R.string.updated), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
        addHomeAssistantSettings()
    }

    private fun showCharging() {
        val generation = ++viewGeneration
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        var settings = WidgetSettings.load(this, widgetId)
        content.addView(TextView(this).apply {
            text = t(R.string.planner_intro)
            textSize = 15f; setTextColor(muted); setPadding(0, dp(8), 0, dp(12))
        })
        var currentPlan: ChargingPlan? = null
        val homeAssistantControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (HomeAssistantSettings.load(this).configured) content.addView(homeAssistantControls)
        val resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(cardBackground)
        }
        val resultTitle = TextView(this).apply { textSize = 21f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD }
        resultBox.addView(resultTitle)
        val costValue = resultRow(resultBox, t(R.string.cost))
        val energyResultValue = resultRow(resultBox, t(R.string.charging))
        val distanceValue = resultRow(resultBox, t(R.string.range))
        val powerValue = resultRow(resultBox, t(R.string.power))
        val resultNote = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(7), 0, 0) }
        resultBox.addView(resultNote)
        content.addView(resultBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        content.addView(label(t(R.string.connection)).apply {
            setTextColor(dark)
            textSize = 16f
            typeface = Typeface.DEFAULT
        })
        val phases = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val onePhaseText = t(R.string.phase_one)
        val threePhaseText = t(R.string.phase_three)
        val onePhaseId = View.generateViewId()
        val threePhaseId = View.generateViewId()
        val onePhase = RadioButton(this).apply { id = onePhaseId; text = onePhaseText; isChecked = settings.chargingPhases == 1 }
        val threePhase = RadioButton(this).apply { id = threePhaseId; text = threePhaseText; isChecked = settings.chargingPhases != 1 }
        phases.addView(onePhase); phases.addView(threePhase); content.addView(phases)
        fun selectedPhases() = if (phases.checkedRadioButtonId == onePhaseId) 1 else 3
        val ampsValue = valueLabel()
        val amps = slider(t(R.string.charge_current), 6, 16, settings.chargingAmps, ampsValue) { value ->
            ampsValue.text = t(R.string.amps_power, value, ChargingPlanner.powerKw(value, selectedPhases()))
        }
        val consumptionValue = valueLabel()
        val consumption = slider(t(R.string.consumption), 10, 40, (settings.consumptionKwhPerMil * 10).toInt(), consumptionValue) { value ->
            consumptionValue.text = t(R.string.consumption_value, value / 10.0)
        }
        val energyValue = valueLabel()
        val energy = slider(t(R.string.charging), 1, 100, settings.chargingKwh, energyValue) { value ->
            energyValue.text = t(R.string.energy_value_integer, value)
        }
        val periodsValue = valueLabel()
        val periods = slider(t(R.string.max_charging_periods), 1, 8, settings.maxChargingPeriods, periodsValue) { value ->
            periodsValue.text = value.toString()
        }
        var departureHour = settings.departureHour
        var departureMinute = settings.departureMinute
        val useDeparture = checkbox(t(R.string.departure_check), settings.useDepartureTime)
        content.addView(useDeparture)
        val departureButton = Button(this).apply {
            text = t(R.string.departure, "%02d:%02d".format(departureHour, departureMinute))
            isAllCaps = false
            isEnabled = useDeparture.isChecked
        }
        content.addView(departureButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val showInWidget = checkbox(t(R.string.widget_plan), settings.showChargingPlan)
        content.addView(showInWidget)
        val status = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, dp(8), 0, 0) }
        content.addView(status)

        fun currentSettings() = settings.copy(
            chargingPhases = selectedPhases(),
            chargingAmps = amps.progress + 6,
            consumptionKwhPerMil = (consumption.progress + 10) / 10.0,
            chargingKwh = energy.progress + 1,
            maxChargingPeriods = periods.progress + 1,
            showChargingPlan = showInWidget.isChecked,
            useDepartureTime = useDeparture.isChecked,
            departureHour = departureHour,
            departureMinute = departureMinute
        )

        val updateHomeAssistantSync = addHomeAssistantControls(homeAssistantControls, generation, ::currentSettings) { currentPlan }

        var prices: PriceResult? = null
        fun render() {
            settings = currentSettings()
            val plan = prices?.let { ChargingPlanner.calculate(it, settings) }
            currentPlan = plan
            updateHomeAssistantSync(plan)
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
                resultTitle.text = if (missesDeparture) t(R.string.misses_departure) else t(R.string.no_plan)
                costValue.text = "–"; energyResultValue.text = "–"; distanceValue.text = "–"; powerValue.text = "–"
                costValue.setTextColor(dark)
                resultNote.text = if (missesDeparture) {
                    val hours = durationMinutes / 60
                    val minutes = durationMinutes % 60
                    val duration = if (minutes == 0L) tq(R.plurals.duration_hours, hours.toInt(), hours) else t(R.string.duration_hours_minutes, hours, minutes)
                    t(R.string.charging_too_long, duration)
                } else {
                    t(R.string.not_enough_prices)
                }
            } else {
                val locale = AppLanguageSettings.locale(this)
                resultTitle.text = plan.periods.joinToString("\n") { period ->
                    t(R.string.charging_window,
                        period.start.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale)),
                        period.end.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale)))
                }
                val estimated = plan.estimatedPriceSlots > 0
                val currency = PriceMarkets.find(settings.area).currency
                costValue.text = if (estimated) "${t(R.string.estimated)} %.2f %s".format(plan.estimatedCost, currency) else "${t(R.string.approximately)} %.2f %s".format(plan.estimatedCost, currency)
                costValue.setTextColor(if (estimated) 0xFFE58A2B.toInt() else dark)
                energyResultValue.text = t(R.string.energy_value, plan.energyKwh)
                distanceValue.text = if (AppLanguageSettings.language(this) in setOf("sv", "nb")) "${t(R.string.approximately)} %.1f mil".format(plan.distanceMil)
                    else "${t(R.string.approximately)} %.0f km".format(plan.distanceMil * 10)
                powerValue.text = t(R.string.power_phase, plan.powerKw, if (settings.chargingPhases == 1) t(R.string.phase_one) else t(R.string.phase_three))
                resultNote.text = when {
                    estimated -> tq(R.plurals.estimated_note, plan.estimatedPriceSlots, plan.estimatedPriceSlots)
                    else -> ""
                }
            }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ampsValue.text = t(R.string.amps_power, amps.progress + 6, ChargingPlanner.powerKw(amps.progress + 6, selectedPhases()))
                consumptionValue.text = t(R.string.consumption_value, (consumption.progress + 10) / 10.0)
                energyValue.text = t(R.string.energy_value_integer, energy.progress + 1)
                periodsValue.text = (periods.progress + 1).toString()
                render()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { saveCharging(currentSettings()); status.text = t(R.string.saved) }
        }
        amps.setOnSeekBarChangeListener(listener); consumption.setOnSeekBarChangeListener(listener)
        energy.setOnSeekBarChangeListener(listener); periods.setOnSeekBarChangeListener(listener)
        phases.setOnCheckedChangeListener { _, _ ->
            ampsValue.text = t(R.string.amps_power, amps.progress + 6, ChargingPlanner.powerKw(amps.progress + 6, selectedPhases()))
            saveCharging(currentSettings()); render(); status.text = t(R.string.saved)
        }
        departureButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                departureHour = hour; departureMinute = minute
                departureButton.text = t(R.string.departure, "%02d:%02d".format(hour, minute))
                saveCharging(currentSettings()); render(); status.text = t(R.string.saved)
            }, departureHour, departureMinute, true).show()
        }
        useDeparture.setOnCheckedChangeListener { _, checked ->
            departureButton.isEnabled = checked
            saveCharging(currentSettings()); render(); status.text = t(R.string.saved)
        }
        showInWidget.setOnCheckedChangeListener { _, _ -> saveCharging(currentSettings()); render(); status.text = t(R.string.saved_updated) }
        render()
        ioExecutor.execute {
            val loaded = PriceRepository.load(applicationContext, settings.area)
            runOnUiThread {
                if (isDestroyed || generation != viewGeneration) return@runOnUiThread
                prices = loaded; render(); status.text = t(R.string.prices_for, settings.area)
            }
        }
    }

    private fun saveCharging(value: WidgetSettings) {
        WidgetSettings.save(this, widgetId, value)
        PriceWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
    }

    private fun addHomeAssistantSettings() {
        content.addView(sectionTitle(t(R.string.home_assistant)))
        val integrationLink = t(R.string.home_assistant_integration_link)
        val introduction = SpannableString(t(R.string.home_assistant_intro, integrationLink)).apply {
            val start = toString().indexOf(integrationLink)
            if (start >= 0) setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HOME_ASSISTANT_REPOSITORY)))
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = accent
                    ds.isUnderlineText = true
                }
            }, start, start + integrationLink.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        content.addView(TextView(this).apply {
            text = introduction
            textSize = 13f; setTextColor(muted); setPadding(0, 0, 0, dp(6))
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0x00000000
        })
        val saved = HomeAssistantSettings.load(this)
        val url = EditText(this).apply {
            hint = t(R.string.home_assistant_url)
            setText(saved.baseUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val webhookId = EditText(this).apply {
            hint = t(R.string.home_assistant_webhook)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            isSingleLine = true
        }
        fun enteredSettings() = HomeAssistantSettings(
            url.text.toString(),
            webhookId.text.toString().ifBlank { saved.webhookId }
        )
        val status = TextView(this).apply {
            textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, 0)
        }
        content.addView(url)
        content.addView(webhookId)
        content.addView(Button(this).apply {
            text = t(R.string.home_assistant_save); isAllCaps = false
            setOnClickListener {
                val entered = enteredSettings()
                if (entered.configured && !entered.baseUrl.startsWith("https://")) {
                    status.text = t(R.string.home_assistant_https_required)
                    status.setTextColor(0xFFD65C5C.toInt())
                } else {
                    HomeAssistantSettings.save(this@WidgetConfigActivity, entered)
                    status.text = t(R.string.home_assistant_saved)
                    status.setTextColor(accent)
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(Button(this).apply {
            text = t(R.string.home_assistant_check); isAllCaps = false
            setOnClickListener {
                val entered = enteredSettings()
                if (!entered.configured || !entered.baseUrl.startsWith("https://")) {
                    status.text = t(R.string.home_assistant_https_required)
                    status.setTextColor(0xFFD65C5C.toInt())
                    return@setOnClickListener
                }
                status.text = t(R.string.home_assistant_checking)
                status.setTextColor(muted)
                ioExecutor.execute {
                    val result = runCatching { HomeAssistantClient.status(entered) }
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        status.text = if (result.isSuccess) t(R.string.home_assistant_connection_ok)
                            else t(R.string.home_assistant_error, result.exceptionOrNull()?.message ?: "")
                        status.setTextColor(if (result.isSuccess) accent else 0xFFD65C5C.toInt())
                    }
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(status)
    }

    private fun addHomeAssistantControls(
        parent: LinearLayout,
        generation: Int,
        currentSettings: () -> WidgetSettings,
        currentPlan: () -> ChargingPlan?
    ): (ChargingPlan?) -> Unit {
        val connection = HomeAssistantSettings.load(this)
        if (!connection.configured) return {}
        parent.addView(sectionTitle(t(R.string.home_assistant)))
        val status = TextView(this).apply {
            text = t(R.string.home_assistant_checking)
            textSize = 13f; setTextColor(muted); setPadding(0, 0, 0, dp(4))
        }
        parent.addView(status)
        var remoteStatus: HomeAssistantStatus? = null
        var scheduleButton: Button? = null
        fun periodsMatch(local: ChargingPlan, remote: HomeAssistantStatus): Boolean {
            if (!remote.scheduleActive || remote.amps != currentSettings().chargingAmps) return false
            return local.periods.size == remote.periods.size && local.periods.zip(remote.periods).all { (a, b) ->
                a.start.toInstant() == b.start.toInstant() && a.end.toInstant() == b.end.toInstant()
            }
        }
        fun updateSync(plan: ChargingPlan?) {
            val button = scheduleButton ?: return
            val remote = remoteStatus
            when {
                plan != null && remote != null && periodsMatch(plan, remote) -> {
                    button.text = t(R.string.home_assistant_schedule_synced)
                    button.backgroundTintList = ColorStateList.valueOf(0xFF2E8B57.toInt())
                }
                remote?.scheduleActive == true -> {
                    button.text = t(R.string.home_assistant_schedule_update)
                    button.backgroundTintList = ColorStateList.valueOf(0xFFD47A19.toInt())
                }
                else -> {
                    button.text = t(R.string.home_assistant_schedule)
                    button.backgroundTintList = ColorStateList.valueOf(accent)
                }
            }
            button.isEnabled = plan != null
        }
        ioExecutor.execute {
            val result = runCatching { HomeAssistantClient.status(connection) }
            runOnUiThread {
                if (isDestroyed || generation != viewGeneration) return@runOnUiThread
                remoteStatus = result.getOrNull()
                status.text = result.fold(
                    onSuccess = {
                        when {
                            it.scheduleActive -> t(R.string.home_assistant_schedule_active)
                            it.chargingEnabled -> t(R.string.home_assistant_charging_enabled)
                            else -> t(R.string.home_assistant_connection_ok)
                        }
                    },
                    onFailure = { t(R.string.home_assistant_error, it.message ?: "") }
                )
                status.setTextColor(if (result.isSuccess) muted else 0xFFD65C5C.toInt())
                updateSync(currentPlan())
            }
        }
        fun send(command: HomeAssistantCommand) {
            status.text = t(R.string.home_assistant_sending)
            status.setTextColor(muted)
            ioExecutor.execute {
                val result = runCatching {
                    HomeAssistantClient.send(connection, command)
                    HomeAssistantClient.status(connection)
                }
                runOnUiThread {
                    if (isDestroyed || generation != viewGeneration) return@runOnUiThread
                    remoteStatus = result.getOrNull()
                    status.text = if (result.isSuccess) t(R.string.home_assistant_sent)
                        else t(R.string.home_assistant_error, result.exceptionOrNull()?.message ?: "")
                    status.setTextColor(if (result.isSuccess) accent else 0xFFD65C5C.toInt())
                    updateSync(currentPlan())
                }
            }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = t(R.string.home_assistant_start); isAllCaps = false
            setOnClickListener {
                val value = currentSettings()
                send(HomeAssistantCommand("start", amps = value.chargingAmps, phases = value.chargingPhases))
            }
        }, weight())
        actions.addView(Button(this).apply {
            text = t(R.string.home_assistant_stop); isAllCaps = false
            setOnClickListener { send(HomeAssistantCommand("stop")) }
        }, weight())
        parent.addView(actions)
        val createdScheduleButton = Button(this).apply {
            text = t(R.string.home_assistant_schedule); isAllCaps = false
            setOnClickListener {
                val plan = currentPlan()
                if (plan == null) {
                    status.text = t(R.string.home_assistant_no_plan)
                    status.setTextColor(0xFFD65C5C.toInt())
                } else {
                    val value = currentSettings()
                    send(HomeAssistantCommand(
                        action = "schedule", start = plan.start.toString(), end = plan.end.toString(),
                        amps = value.chargingAmps, phases = value.chargingPhases,
                        powerKw = plan.powerKw, energyKwh = plan.energyKwh,
                        priceArea = value.area, estimated = plan.estimatedPriceSlots > 0,
                        periods = plan.periods
                    ))
                }
            }
        }
        scheduleButton = createdScheduleButton
        parent.addView(createdScheduleButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        parent.addView(Button(this).apply {
            text = t(R.string.home_assistant_cancel); isAllCaps = false
            setOnClickListener { send(HomeAssistantCommand("cancel")) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        updateSync(currentPlan())
        return ::updateSync
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(dark)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(18), 0, dp(4))
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
        val generation = ++viewGeneration
        scrollView.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        content.removeAllViews()
        content.addView(ProgressBar(this).apply { isIndeterminate = true })
        ioExecutor.execute {
            val settings = WidgetSettings.load(this, widgetId)
            val result = PriceRepository.load(applicationContext, settings.area)
            runOnUiThread {
                if (isDestroyed || generation != viewGeneration) return@runOnUiThread
                content.removeAllViews()
                val market = PriceMarkets.find(settings.area)
                content.addView(TextView(this).apply { text = t(R.string.table_title, settings.area, settings.intervalMinutes, market.priceUnit); textSize = 19f; setTextColor(dark); setPadding(0, dp(8), 0, dp(12)) })
                val tableHeader = addTableHeader()
                scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    val offset = (scrollY - tableHeader.top).coerceAtLeast(0)
                    tableHeader.translationY = offset.toFloat()
                    tableHeader.elevation = if (offset > 0) dp(3).toFloat() else 0f
                }
                val model = PriceTableModels.create(result, settings, OffsetDateTime.now(PriceMarkets.find(settings.area).zoneId))
                model.rows.forEach { row ->
                    addRow(
                        row.time.format(DateTimeFormatter.ofPattern("HH:mm")), row.today, row.tomorrow,
                        model.todayAverage, model.tomorrowAverage, model.todayRange, model.tomorrowRange, row.current
                    )
                }
                if (model.rows.isEmpty()) content.addView(TextView(this).apply { text = t(R.string.no_prices); setPadding(0, dp(20), 0, 0) })
                if (model.currentIndex >= 0) {
                    content.post {
                        val row = content.getChildAt(model.currentIndex + 2) // title + table header
                        scrollView.scrollTo(0, (row.top - scrollView.height / 3).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    private fun addTableHeader(): View {
        val header = rowView(t(R.string.time), t(R.string.today), t(R.string.tomorrow), true, null, null, false).apply {
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
    private fun t(id: Int, vararg args: Any) = AppLanguageSettings.text(this, id, *args)
    private fun tq(id: Int, quantity: Int, vararg args: Any) = AppLanguageSettings.quantityText(this, id, quantity, *args)

    override fun onDestroy() {
        viewGeneration++
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val HOME_ASSISTANT_REPOSITORY = "https://github.com/henrikekblad/elpris-home-assistant"
        const val EXTRA_EXISTING_WIDGET = "existing_widget"
        const val EXTRA_HOME_ASSISTANT_PAIRING = "home_assistant_pairing"
    }
}
