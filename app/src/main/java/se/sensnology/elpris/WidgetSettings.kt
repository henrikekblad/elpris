package se.sensnology.elpris

import android.content.Context

data class WidgetSettings(
    val area: String = "SE4",
    val vat: Boolean = false,
    val tax: Boolean = false,
    val transfer: Boolean = false,
    val taxOre: Double = 0.0,
    val transferOre: Double = 0.0,
    val intervalMinutes: Int = 15
) {
    fun apply(rawSek: Double): Double {
        var ore = rawSek * 100.0
        if (tax) ore += taxOre
        if (transfer) ore += transferOre
        if (vat) ore *= 1.25
        return ore
    }

    companion object {
        private const val PREFS = "widget_settings"
        fun load(context: Context, id: Int): WidgetSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "$id."
            return WidgetSettings(
                area = p.getString(key + "area", "SE4") ?: "SE4",
                vat = p.getBoolean(key + "vat", false),
                tax = p.getBoolean(key + "tax", false),
                transfer = p.getBoolean(key + "transfer", false),
                taxOre = p.getString(key + "taxOre", "0")?.toDoubleOrNull() ?: 0.0,
                transferOre = p.getString(key + "transferOre", "0")?.toDoubleOrNull() ?: 0.0,
                intervalMinutes = p.getInt(key + "intervalMinutes", 15)
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
                .apply()
        }

        fun delete(context: Context, id: Int) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            listOf("area", "vat", "tax", "transfer", "taxOre", "transferOre", "intervalMinutes")
                .forEach { editor.remove("$id.$it") }
            editor.apply()
        }
    }
}
