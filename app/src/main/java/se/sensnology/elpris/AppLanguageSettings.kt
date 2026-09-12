package se.sensnology.elpris

import android.content.Context
import java.util.Locale

object AppLanguageSettings {
    const val SYSTEM = "system"
    private const val PREFS = "app_language"
    private const val KEY = "language"

    data class Choice(val code: String, val label: String)
    val choices = listOf(
        Choice(SYSTEM, "Systemstandard"), Choice("sv", "Svenska"), Choice("nb", "Norsk"),
        Choice("da", "Dansk"), Choice("fi", "Suomi"), Choice("en", "English")
    )

    fun selected(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, SYSTEM) ?: SYSTEM

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    fun language(context: Context): String {
        val selected = selected(context)
        if (selected != SYSTEM) return selected
        return when (context.resources.configuration.locales[0].language.lowercase()) {
            "sv" -> "sv"; "no", "nb", "nn" -> "nb"; "da" -> "da"; "fi" -> "fi"; else -> "en"
        }
    }

    fun region(context: Context): String = when (selected(context)) {
        "sv" -> "SE"; "nb" -> "NO"; "da" -> "DK"; "fi" -> "FI"
        else -> context.resources.configuration.locales[0].country
    }

    fun locale(context: Context): Locale = Locale.forLanguageTag(language(context))

    fun marketInfo(context: Context, market: PriceMarket): String = when (language(context)) {
        "sv" -> "API-priset är spotpris utan moms, skatt och tillägg. Förslag för 2026: ${format(market.suggestedTax)} ${market.minorUnit}/kWh i energiskatt och ${format(market.suggestedTransfer)} ${market.minorUnit}/kWh i överföringsavgift, exkl. moms. Beloppen varierar – kontrollera lokala regler och ditt nätavtal. Momsen läggs sist på hela summan."
        "nb" -> "API-prisen er spotpris uten mva., avgifter og tillegg. Forslag for 2026: ${format(market.suggestedTax)} ${market.minorUnit}/kWh i elavgift og ${format(market.suggestedTransfer)} ${market.minorUnit}/kWh i nettleie, ekskl. mva. Beløpene varierer – kontroller lokale regler og nettavtalen din. Mva. legges til sist på hele summen."
        "da" -> "API-prisen er spotpris uden moms, afgifter og tillæg. Forslag for 2026: ${format(market.suggestedTax)} ${market.minorUnit}/kWh i elafgift og ${format(market.suggestedTransfer)} ${market.minorUnit}/kWh i nettarif, ekskl. moms. Beløbene varierer – kontrollér lokale regler og din netaftale. Moms tilføjes sidst til hele beløbet."
        "fi" -> "API-hinta on spot-hinta ilman arvonlisäveroa, veroja ja lisämaksuja. Vuoden 2026 ehdotus: sähkövero ${format(market.suggestedTax)} ${market.minorUnit}/kWh ja siirtomaksu ${format(market.suggestedTransfer)} ${market.minorUnit}/kWh ilman ALV:tä. Maksut vaihtelevat – tarkista paikalliset säännöt ja verkkosopimus. ALV lisätään lopuksi koko summaan."
        else -> "The API price is the spot price without VAT, taxes or fees. Suggested 2026 values: ${format(market.suggestedTax)} ${market.minorUnit}/kWh electricity tax and ${format(market.suggestedTransfer)} ${market.minorUnit}/kWh grid fee, excluding VAT. Amounts vary – check local rules and your grid agreement. VAT is applied last to the entire amount."
    }

    private fun format(value: Double) = if (value % 1.0 == 0.0) "%.1f".format(Locale.ROOT, value) else value.toString()

    fun text(context: Context, key: String): String {
        val language = language(context)
        return translations[language]?.get(key) ?: translations.getValue("en")[key]
            ?: translations.getValue("sv").getValue(key)
    }

    fun detail(context: Context, key: String): String {
        val values = details[key] ?: return key
        val index = listOf("sv", "nb", "da", "fi", "en").indexOf(language(context)).takeIf { it >= 0 } ?: 4
        return values[index]
    }

    private val details = mapOf(
        "updated" to listOf("Widgeten uppdateras", "Widgeten oppdateres", "Widgetten opdateres", "Widget päivitetään", "Widget is updating"),
        "saved" to listOf("Sparat", "Lagret", "Gemt", "Tallennettu", "Saved"),
        "saved_updated" to listOf("Sparat och widgeten uppdateras", "Lagret, og widgeten oppdateres", "Gemt, og widgetten opdateres", "Tallennettu, ja widget päivitetään", "Saved; widget is updating"),
        "planner_intro" to listOf("Hitta den billigaste sammanhängande laddtiden under det närmaste dygnet.", "Finn den billigste sammenhengende ladetiden det neste døgnet.", "Find den billigste sammenhængende ladetid i det næste døgn.", "Etsi seuraavan vuorokauden halvin yhtäjaksoinen latausaika.", "Find the cheapest continuous charging period during the next 24 hours."),
        "misses_departure" to listOf("Hinner inte före avresan", "Rekker ikke før avreise", "Kan ikke nås før afgang", "Ei valmistu ennen lähtöä", "Cannot finish before departure"),
        "no_plan" to listOf("Ingen laddtid kan beräknas", "Kan ikke beregne ladetid", "Ladetiden kan ikke beregnes", "Latausaikaa ei voida laskea", "No charging period can be calculated"),
        "not_enough_prices" to listOf("Det saknas tillräckligt med prisdata för att beräkna en laddtid.", "Det finnes ikke nok prisdata til å beregne ladetid.", "Der er ikke tilstrækkelige prisdata til at beregne en ladetid.", "Latausajan laskemiseen ei ole riittävästi hintatietoja.", "There is not enough price data to calculate a charging period."),
        "estimated_note" to listOf("Kostnaden innehåller %d uppskattade kvartsvärden, baserade på det senast publicerade dygnets prisprofil.", "Kostnaden inneholder %d estimerte kvartersverdier basert på den sist publiserte døgnprofilen.", "Prisen indeholder %d estimerede kvartersværdier baseret på det senest offentliggjorte døgns prisprofil.", "Hinta sisältää %d arvioitua varttiarvoa, jotka perustuvat viimeksi julkaistun vuorokauden hintaprofiiliin.", "The cost includes %d estimated quarter-hour values based on the latest published daily price profile."),
        "prices_for" to listOf("Priser för %s", "Priser for %s", "Priser for %s", "Alueen %s hinnat", "Prices for %s")
    )

    private val translations = mapOf(
        "sv" to mapOf(
            "app_title" to "Elpris", "settings" to "Inställningar", "table" to "Pristabell", "ev" to "Elbil", "language" to "Språk",
            "area" to "Elprisområde", "theme" to "Färgtema", "system" to "Systemstandard", "light" to "Ljust", "dark" to "Mörkt",
            "resolution" to "Upplösning", "quarter" to "15 minuter", "hour" to "Heltimmesmedel", "vat" to "Lägg på %s %% moms",
            "vat_zero" to "Moms (0 %% i %s)", "tax" to "Lägg på energiskatt", "transfer" to "Lägg på överföringsavgift",
            "tax_hint" to "Energiskatt, %s/kWh", "transfer_hint" to "Överföringsavgift, %s/kWh", "save" to "Spara och uppdatera",
            "country_SE" to "Sverige", "country_NO" to "Norge", "country_DK" to "Danmark", "country_FI" to "Finland",
            "today" to "Idag", "tomorrow" to "Imorgon", "time" to "Tid", "no_prices" to "Inga priser kunde hämtas.",
            "connection" to "Anslutning", "charge_current" to "Laddström", "consumption" to "Förbrukning", "charging" to "Laddning",
            "phase_one" to "1-fas", "phase_three" to "3-fas", "power_phase" to "%.1f kW (%s)",
            "cost" to "Kostnad", "range" to "Räckvidd", "power" to "Effekt", "departure_check" to "Laddningen ska vara klar före avresa",
            "departure" to "Avresa %s", "widget_plan" to "Visa rekommenderad laddtid underst i widgeten", "estimated" to "uppskattad",
            "approximately" to "cirka", "charge" to "Ladda", "could_not_fetch" to "Kunde inte hämta priser", "choose_widget" to "Välj vilken widget du vill öppna"
        ),
        "nb" to mapOf(
            "app_title" to "Strømpris", "settings" to "Innstillinger", "table" to "Pristabell", "ev" to "Elbil", "language" to "Språk", "area" to "Prisområde",
            "theme" to "Fargetema", "system" to "Systemstandard", "light" to "Lyst", "dark" to "Mørkt", "resolution" to "Oppløsning",
            "quarter" to "15 minutter", "hour" to "Timesgjennomsnitt", "vat" to "Legg til %s %% mva.", "vat_zero" to "Mva. (0 %% i %s)",
            "tax" to "Legg til elavgift", "transfer" to "Legg til nettleie", "tax_hint" to "Elavgift, %s/kWh", "transfer_hint" to "Nettleie, %s/kWh",
            "save" to "Lagre og oppdater", "country_SE" to "Sverige", "country_NO" to "Norge", "country_DK" to "Danmark", "country_FI" to "Finland",
            "today" to "I dag", "tomorrow" to "I morgen", "time" to "Tid", "no_prices" to "Kunne ikke hente priser.", "connection" to "Tilkobling",
            "charge_current" to "Ladestrøm", "consumption" to "Forbruk", "charging" to "Lading", "cost" to "Kostnad", "range" to "Rekkevidde",
            "phase_one" to "1-fase", "phase_three" to "3-fase", "power_phase" to "%.1f kW (%s)",
            "power" to "Effekt", "departure_check" to "Ladingen skal være ferdig før avreise", "departure" to "Avreise %s",
            "widget_plan" to "Vis anbefalt ladetid nederst i widgeten", "estimated" to "estimert", "approximately" to "cirka", "charge" to "Lad",
            "could_not_fetch" to "Kunne ikke hente priser", "choose_widget" to "Velg hvilken widget du vil åpne"
        ),
        "da" to mapOf(
            "app_title" to "Elpris", "settings" to "Indstillinger", "table" to "Pristabel", "ev" to "Elbil", "language" to "Sprog", "area" to "Prisområde",
            "theme" to "Farvetema", "system" to "Systemstandard", "light" to "Lyst", "dark" to "Mørkt", "resolution" to "Opløsning",
            "quarter" to "15 minutter", "hour" to "Timegennemsnit", "vat" to "Tilføj %s %% moms", "vat_zero" to "Moms (0 %% i %s)",
            "tax" to "Tilføj elafgift", "transfer" to "Tilføj nettarif", "tax_hint" to "Elafgift, %s/kWh", "transfer_hint" to "Nettarif, %s/kWh",
            "save" to "Gem og opdater", "country_SE" to "Sverige", "country_NO" to "Norge", "country_DK" to "Danmark", "country_FI" to "Finland",
            "today" to "I dag", "tomorrow" to "I morgen", "time" to "Tid", "no_prices" to "Priserne kunne ikke hentes.", "connection" to "Tilslutning",
            "charge_current" to "Ladestrøm", "consumption" to "Forbrug", "charging" to "Opladning", "cost" to "Pris", "range" to "Rækkevidde",
            "phase_one" to "1-faset", "phase_three" to "3-faset", "power_phase" to "%.1f kW (%s)",
            "power" to "Effekt", "departure_check" to "Opladningen skal være færdig før afgang", "departure" to "Afgang %s",
            "widget_plan" to "Vis anbefalet ladetid nederst i widgeten", "estimated" to "estimeret", "approximately" to "cirka", "charge" to "Oplad",
            "could_not_fetch" to "Priserne kunne ikke hentes", "choose_widget" to "Vælg den widget, du vil åbne"
        ),
        "fi" to mapOf(
            "app_title" to "Sähkön hinta", "settings" to "Asetukset", "table" to "Hintataulukko", "ev" to "Sähköauto", "language" to "Kieli", "area" to "Hinta-alue",
            "theme" to "Väriteema", "system" to "Järjestelmän oletus", "light" to "Vaalea", "dark" to "Tumma", "resolution" to "Tarkkuus",
            "quarter" to "15 minuuttia", "hour" to "Tuntikeskiarvo", "vat" to "Lisää ALV %s %%", "vat_zero" to "ALV (0 %% alueella %s)",
            "tax" to "Lisää sähkövero", "transfer" to "Lisää siirtomaksu", "tax_hint" to "Sähkövero, %s/kWh", "transfer_hint" to "Siirtomaksu, %s/kWh",
            "save" to "Tallenna ja päivitä", "country_SE" to "Ruotsi", "country_NO" to "Norja", "country_DK" to "Tanska", "country_FI" to "Suomi",
            "today" to "Tänään", "tomorrow" to "Huomenna", "time" to "Aika", "no_prices" to "Hintoja ei voitu hakea.", "connection" to "Liitäntä",
            "charge_current" to "Latausvirta", "consumption" to "Kulutus", "charging" to "Lataus", "cost" to "Hinta", "range" to "Toimintamatka",
            "phase_one" to "1-vaihe", "phase_three" to "3-vaihe", "power_phase" to "%.1f kW (%s)",
            "power" to "Teho", "departure_check" to "Latauksen on valmistuttava ennen lähtöä", "departure" to "Lähtö %s",
            "widget_plan" to "Näytä suositeltu latausaika widgetissä", "estimated" to "arvioitu", "approximately" to "noin", "charge" to "Lataa",
            "could_not_fetch" to "Hintoja ei voitu hakea", "choose_widget" to "Valitse avattava widget"
        ),
        "en" to mapOf(
            "app_title" to "Electricity price", "settings" to "Settings", "table" to "Price table", "ev" to "EV", "language" to "Language", "area" to "Price area",
            "theme" to "Colour theme", "system" to "System default", "light" to "Light", "dark" to "Dark", "resolution" to "Resolution",
            "quarter" to "15 minutes", "hour" to "Hourly average", "vat" to "Add %s %% VAT", "vat_zero" to "VAT (0 %% in %s)",
            "tax" to "Add electricity tax", "transfer" to "Add grid fee", "tax_hint" to "Electricity tax, %s/kWh", "transfer_hint" to "Grid fee, %s/kWh",
            "save" to "Save and update", "country_SE" to "Sweden", "country_NO" to "Norway", "country_DK" to "Denmark", "country_FI" to "Finland",
            "today" to "Today", "tomorrow" to "Tomorrow", "time" to "Time", "no_prices" to "Prices could not be loaded.", "connection" to "Connection",
            "charge_current" to "Charging current", "consumption" to "Consumption", "charging" to "Charging", "cost" to "Cost", "range" to "Range",
            "phase_one" to "Single phase", "phase_three" to "Three phase", "power_phase" to "%.1f kW (%s)",
            "power" to "Power", "departure_check" to "Charging must finish before departure", "departure" to "Departure %s",
            "widget_plan" to "Show recommended charging time in the widget", "estimated" to "estimated", "approximately" to "about", "charge" to "Charge",
            "could_not_fetch" to "Could not load prices", "choose_widget" to "Choose which widget to open"
        )
    )
}
