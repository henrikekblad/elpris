package se.sensnology.elpris

import android.content.Context

data class WidgetSettings(
    val area: String = "SE4",
    val vat: Boolean = false,
    val tax: Boolean = false,
    val transfer: Boolean = false,
    val taxOre: Double = DEFAULT_TAX_ORE,
    val transferOre: Double = DEFAULT_TRANSFER_ORE,
    val intervalMinutes: Int = 15,
    val chargingPhases: Int = 3,
    val chargingAmps: Int = 10,
    val consumptionKwhPerMil: Double = 2.0,
    val chargingKwh: Int = 20,
    val showChargingPlan: Boolean = false,
    val useDepartureTime: Boolean = true,
    val departureHour: Int = 8,
    val departureMinute: Int = 0
) {
    fun apply(rawSek: Double): Double {
        var ore = rawSek * 100.0
        if (tax) ore += taxOre
        if (transfer) ore += transferOre
        if (vat) ore *= 1.0 + PriceMarkets.find(area).vatPercent / 100.0
        return ore
    }

    companion object {
        const val DEFAULT_TAX_ORE = 36.0
        const val DEFAULT_TRANSFER_ORE = 30.0
        private const val PREFS = "widget_settings"
        fun load(context: Context, id: Int): WidgetSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "$id."
            val region = AppLanguageSettings.region(context)
            val defaultArea = PriceMarkets.defaultArea(region)
            val marketDefaults = PriceMarkets.find(defaultArea)
            return WidgetSettings(
                area = p.getString(key + "area", defaultArea) ?: defaultArea,
                vat = p.getBoolean(key + "vat", false),
                tax = p.getBoolean(key + "tax", false),
                transfer = p.getBoolean(key + "transfer", false),
                taxOre = p.getString(key + "taxOre", marketDefaults.suggestedTax.toString())?.toDoubleOrNull() ?: marketDefaults.suggestedTax,
                transferOre = p.getString(key + "transferOre", marketDefaults.suggestedTransfer.toString())?.toDoubleOrNull() ?: marketDefaults.suggestedTransfer,
                intervalMinutes = p.getInt(key + "intervalMinutes", 15),
                chargingPhases = p.getInt(key + "chargingPhases", 3).takeIf { it == 1 || it == 3 } ?: 3,
                chargingAmps = p.getInt(key + "chargingAmps", 10),
                consumptionKwhPerMil = p.getString(key + "consumptionKwhPerMil", "2.0")?.toDoubleOrNull() ?: 2.0,
                chargingKwh = p.getInt(key + "chargingKwh", 20),
                showChargingPlan = p.getBoolean(key + "showChargingPlan", false),
                useDepartureTime = p.getBoolean(key + "useDepartureTime", true),
                departureHour = p.getInt(key + "departureHour", 8),
                departureMinute = p.getInt(key + "departureMinute", 0)
            )
        }

        fun save(context: Context, id: Int, value: WidgetSettings) {
            val key = "$id."
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key + "area", value.area)
                .putBoolean(key + "vat", value.vat)
                .putBoolean(key + "tax", value.tax)
                .putBoolean(key + "transfer", value.transfer)
                .putString(key + "taxOre", value.taxOre.toString())
                .putString(key + "transferOre", value.transferOre.toString())
                .putInt(key + "intervalMinutes", value.intervalMinutes)
                .putInt(key + "chargingPhases", value.chargingPhases)
                .putInt(key + "chargingAmps", value.chargingAmps)
                .putString(key + "consumptionKwhPerMil", value.consumptionKwhPerMil.toString())
                .putInt(key + "chargingKwh", value.chargingKwh)
                .putBoolean(key + "showChargingPlan", value.showChargingPlan)
                .putBoolean(key + "useDepartureTime", value.useDepartureTime)
                .putInt(key + "departureHour", value.departureHour)
                .putInt(key + "departureMinute", value.departureMinute)
                .apply()
        }

        fun delete(context: Context, id: Int) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            listOf("area", "vat", "tax", "transfer", "taxOre", "transferOre", "intervalMinutes",
                "chargingPhases", "chargingAmps", "consumptionKwhPerMil", "chargingKwh", "showChargingPlan")
                .forEach { editor.remove("$id.$it") }
            listOf("useDepartureTime", "departureHour", "departureMinute")
                .forEach { editor.remove("$id.$it") }
            editor.apply()
        }
    }
}
