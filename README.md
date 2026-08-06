# Elpris

En anpassningsbar Android-widget som visar spotpriser för idag och imorgon på samma 24-timmarsaxel. Appen hämtar öppna prisdata från [Elpriset just nu](https://www.elprisetjustnu.se/elpris-api) och stöder samtliga svenska elområden.

<p align="center">
  <img src="assets/widget.jpg" alt="Elpriswidget med dagens och morgondagens priser" width="720">
</p>

## Funktioner

- Överlagrade kvartskurvor för idag och imorgon. Morgondagen visas som ljusa grå punkter bakom dagens priser.
- Aktuell kvart markeras med en större punkt; dygnets min-, max- och aktuella pris visas överst.
- Val mellan 15-minuterspriser och heltimmesmedel.
- Separata inställningar per widget för elområde SE1–SE4.
- Valfri moms, energiskatt och överföringsavgift.
- Pristabell med idag och imorgon sida vid sida, färgkodning och automatisk scrollning till aktuell tid.
- Storleksanpassningsbar widget för olika launchers och skärmstorlekar.
- Persistent lokal cache: senast hämtade priser finns kvar vid tillfälliga nätverksfel.
- Automatiska publiceringskontroller från 13:00 och fortsatta återförsök tills morgondagens priser har hämtats.

<p align="center">
  <img src="assets/table.jpg" alt="Färgkodad pristabell för idag och imorgon" width="360">
  &nbsp;&nbsp;
  <img src="assets/settings.jpg" alt="Inställningar för elområde, upplösning, moms, skatt och avgifter" width="360">
</p>

## Användning

1. Installera APK-filen från projektets [Releases](../../releases).
2. Lägg till **Elpris** från startskärmens widgetväljare.
3. Välj elområde, upplösning och eventuella påslag.
4. Tryck på widgeten för att öppna inställningar och pristabell.

Flera widgetinstanser kan använda olika elområden och avgifter.

## Prismodell

API-värdet är spotpris utan moms, skatter och tillägg. Appen räknar om kronor till öre och tillämpar valda påslag enligt:

```text
(spotpris × 100 + energiskatt + överföringsavgift) × moms
```

Moms multipliceras sist. Fasta månadsavgifter ingår inte eftersom de inte kan uttryckas korrekt per kWh utan en förbrukningsprognos. Kontrollera beloppen mot ditt elnätsavtal och aktuella skatteregler.

## Bygga lokalt

Krav:

- JDK 17
- Android SDK 36

Bygg en debug-APK:

```bash
./gradlew assembleDebug
```

APK:n skapas i `app/build/outputs/apk/debug/`.

## Uppdateringar och cache

Android begär normalt widgetuppdatering ungefär var 30:e minut, men systemet kan senarelägga körningar för att spara batteri. Appen gör dessutom särskilda kontroller kring morgondagens publicering och fortsätter var 30:e minut tills ett komplett morgondagsdygn har sparats.

Lyckade API-svar cachas lokalt per datum och elområde. Appen samlar inte in eller skickar någon användardata.

## Felsökning

Relevanta Android-loggar kan läsas med:

```bash
adb logcat -d | grep -E 'ElprisRepository|ElprisWidget|ElprisScheduler'
```

Loggarna innehåller URL, HTTP-status, antal parsade priser, cacheträffar och nästa schemalagda kontroll – men inga lösenord eller personuppgifter.

## Datakälla och ansvar

Prisdata tillhandahålls av [Elpriset just nu](https://www.elprisetjustnu.se/elpris-api). Projektet är inte anslutet till dataleverantören, Nord Pool, något elbolag eller elnätsföretag. Uppgifterna är vägledande; kontrollera alltid ditt avtal och din faktura.

## Paketnamn

`se.sensnology.elpris`

## Licens

Projektet distribueras under [MIT-licensen](LICENSE).
