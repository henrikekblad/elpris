package se.sensnology.elpris

data class PriceMarket(
    val area: String,
    val country: String,
    val description: String,
    val baseUrl: String,
    val priceField: String,
    val currency: String,
    val minorUnit: String,
    val vatPercent: Double,
    val usesAreaSuffix: Boolean = true,
    val sourceIntervalMinutes: Int = 15,
    val suggestedTax: Double = 0.0,
    val suggestedTransfer: Double = 0.0
) {
    val selectorLabel: String get() = "$area – $description"
    val priceUnit: String get() = "$minorUnit/kWh"
}

object PriceMarkets {
    val all = listOf(
        PriceMarket("SE1", "Sverige", "Luleå", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE2", "Sverige", "Sundsvall", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE3", "Sverige", "Stockholm", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE4", "Sverige", "Malmö", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("NO1", "Norge", "Oslo", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13),
        PriceMarket("NO2", "Norge", "Kristiansand", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13),
        PriceMarket("NO3", "Norge", "Trondheim", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13),
        PriceMarket("NO4", "Norge", "Tromsø", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 0.0, sourceIntervalMinutes = 60, suggestedTax = 7.13),
        PriceMarket("NO5", "Norge", "Bergen", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13),
        PriceMarket("DK1", "Danmark", "Västdanmark", "https://www.elprisenligenu.dk", "DKK_per_kWh", "DKK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 0.8),
        PriceMarket("DK2", "Danmark", "Östdanmark", "https://www.elprisenligenu.dk", "DKK_per_kWh", "DKK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 0.8),
        PriceMarket("FI", "Finland", "Finland", "https://www.sahkonhintatanaan.fi", "EUR_per_kWh", "€", "cent", 25.5, false, 60, 2.325)
    )

    fun find(area: String): PriceMarket = all.firstOrNull { it.area == area } ?: all.first { it.area == "SE4" }

    fun preferredCountry(region: String): String = when (region.uppercase()) {
        "NO" -> "Norge"
        "DK" -> "Danmark"
        "FI" -> "Finland"
        else -> "Sverige"
    }

    fun defaultArea(region: String): String = when (region.uppercase()) {
        "NO" -> "NO1"
        "DK" -> "DK1"
        "FI" -> "FI"
        else -> "SE4"
    }

    fun groupedWithPreferredFirst(region: String): List<Pair<String, List<PriceMarket>>> {
        val preferred = preferredCountry(region)
        return all.groupBy { it.country }.entries
            .sortedBy { if (it.key == preferred) 0 else 1 }
            .map { it.key to it.value }
    }
}
