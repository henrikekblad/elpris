package se.sensnology.elpris

import java.time.ZoneId

enum class MarketCountry { SE, NO, DK, FI }

data class PriceMarket(
    val area: String,
    val country: MarketCountry,
    val description: String,
    val baseUrl: String,
    val priceField: String,
    val currency: String,
    val minorUnit: String,
    val vatPercent: Double,
    val usesAreaSuffix: Boolean = true,
    val sourceIntervalMinutes: Int = 15,
    val suggestedTax: Double = 0.0,
    val suggestedTransfer: Double = 0.0,
    val zoneId: ZoneId = ZoneId.of("Europe/Stockholm")
) {
    val selectorLabel: String get() = "$area – $description"
    val priceUnit: String get() = "$minorUnit/kWh"
}

object PriceMarkets {
    val all = listOf(
        PriceMarket("SE1", MarketCountry.SE, "Luleå", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE2", MarketCountry.SE, "Sundsvall", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE3", MarketCountry.SE, "Stockholm", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("SE4", MarketCountry.SE, "Malmö", "https://www.elprisetjustnu.se", "SEK_per_kWh", "kr", "öre", 25.0, suggestedTax = 36.0, suggestedTransfer = 30.0),
        PriceMarket("NO1", MarketCountry.NO, "Oslo", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13, zoneId = ZoneId.of("Europe/Oslo")),
        PriceMarket("NO2", MarketCountry.NO, "Kristiansand", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13, zoneId = ZoneId.of("Europe/Oslo")),
        PriceMarket("NO3", MarketCountry.NO, "Trondheim", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13, zoneId = ZoneId.of("Europe/Oslo")),
        PriceMarket("NO4", MarketCountry.NO, "Tromsø", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 0.0, sourceIntervalMinutes = 60, suggestedTax = 7.13, zoneId = ZoneId.of("Europe/Oslo")),
        PriceMarket("NO5", MarketCountry.NO, "Bergen", "https://www.hvakosterstrommen.no", "NOK_per_kWh", "NOK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 7.13, zoneId = ZoneId.of("Europe/Oslo")),
        PriceMarket("DK1", MarketCountry.DK, "Västdanmark", "https://www.elprisenligenu.dk", "DKK_per_kWh", "DKK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 0.8, zoneId = ZoneId.of("Europe/Copenhagen")),
        PriceMarket("DK2", MarketCountry.DK, "Östdanmark", "https://www.elprisenligenu.dk", "DKK_per_kWh", "DKK", "øre", 25.0, sourceIntervalMinutes = 60, suggestedTax = 0.8, zoneId = ZoneId.of("Europe/Copenhagen")),
        PriceMarket("FI", MarketCountry.FI, "Finland", "https://www.sahkonhintatanaan.fi", "EUR_per_kWh", "€", "cent", 25.5, false, 60, 2.325, zoneId = ZoneId.of("Europe/Helsinki"))
    )

    fun find(area: String): PriceMarket = all.firstOrNull { it.area == area } ?: all.first { it.area == "SE4" }

    fun preferredCountry(region: String): MarketCountry = when (region.uppercase()) {
        "NO" -> MarketCountry.NO
        "DK" -> MarketCountry.DK
        "FI" -> MarketCountry.FI
        else -> MarketCountry.SE
    }

    fun defaultArea(region: String): String = when (region.uppercase()) {
        "NO" -> "NO1"
        "DK" -> "DK1"
        "FI" -> "FI"
        else -> "SE4"
    }

    fun groupedWithPreferredFirst(region: String): List<Pair<MarketCountry, List<PriceMarket>>> {
        val preferred = preferredCountry(region)
        return all.groupBy { it.country }.entries
            .sortedBy { if (it.key == preferred) 0 else 1 }
            .map { it.key to it.value }
    }
}
