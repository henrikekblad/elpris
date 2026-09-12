# Elpris

[Svensk dokumentation](README.sv.md)

Elpris is a configurable Android home-screen widget that displays today's and tomorrow's electricity spot prices on the same 24-hour chart. It supports Sweden, Norway, Denmark and Finland and is available in Swedish, Norwegian, Danish, Finnish and English.

<p align="center">
  <img src="assets/widget.jpg" alt="Elpris widget showing today's and tomorrow's electricity prices" width="720">
</p>

## Features

- Overlaid price charts make it easy to compare the same time today and tomorrow.
- The current interval is highlighted, with the day's minimum, maximum and current price shown above the chart.
- Swedish SE1–SE4, Norwegian NO1–NO5, Danish DK1–DK2 and Finland.
- 15-minute prices where supplied by the source, or hourly averages.
- Local currencies and country-specific VAT rates.
- Optional editable electricity tax and grid fee.
- Price table with colour-coded rows and highlighting of the current interval.
- EV charging planner for single-phase or three-phase charging, including energy demand, consumption and departure time.
- Optional charging recommendation at the bottom of the widget.
- Flexible widget sizing for different screens and launchers.
- Local cache for temporary network failures.
- Automatic publication checks for tomorrow's prices.

<p align="center">
  <img src="assets/table.jpg" alt="Colour-coded electricity price table" width="360">
  &nbsp;&nbsp;
  <img src="assets/settings.jpg" alt="Price-area, tax and display settings" width="360">
</p>

## Installation

1. Download the APK from [Releases](../../releases).
2. Install it and add **Elpris** from the Android widget picker.
3. Select a language, price area, resolution and any applicable taxes or fees.
4. Tap the widget to open its settings, price table and EV planner.

## EV charging planner

Choose single-phase or three-phase charging (6–16 A), vehicle consumption, energy to add and optionally a departure time. The app finds the cheapest continuous charging period and estimates its cost and resulting range.

If charging extends beyond the published prices, missing intervals are estimated from the latest published daily price profile and the result is clearly marked as estimated. Calculations use ideal power at 230 V single phase or 400 V three phase; actual charging losses and the vehicle's charging curve are not included.

<p align="center">
  <img src="assets/charge.jpg" alt="EV charging planner" width="360">
</p>

## Price data and calculation

Prices are retrieved from the open APIs provided by [elprisetjustnu.se](https://www.elprisetjustnu.se/), [hvakosterstrommen.no](https://www.hvakosterstrommen.no/), [elprisenligenu.dk](https://www.elprisenligenu.dk/) and [sahkonhintatanaan.fi](https://www.sahkonhintatanaan.fi/). The widget uses the local currency: SEK, NOK, DKK or EUR.

The API value is a spot price without VAT, electricity tax or grid fees. Enabled additions are calculated as:

```text
(spot price × 100 + electricity tax + grid fee) × VAT
```

VAT is applied last and follows the selected market. Suggested tax values are editable. Grid fees vary by provider, tariff and agreement, so users should verify all values against their local rules and electricity contract. Fixed monthly charges are not included.

## Build locally

Requirements:

- JDK 17
- Android SDK 36

```bash
./gradlew assembleDebug
```

The debug APK is created in `app/build/outputs/apk/debug/`.

## Privacy and troubleshooting

Successful API responses are cached locally by date and price area. The app does not collect or transmit personal data.

```bash
adb logcat -d | grep -E 'ElprisRepository|ElprisWidget|ElprisScheduler'
```

Logs contain URLs, HTTP status codes, parsed price counts, cache hits and the next scheduled check, but no passwords or personal information.

## Disclaimer

Elpris is not affiliated with the API providers, Nord Pool, electricity retailers or grid operators. Prices and charging calculations are guidance only; always verify them against your agreement and invoice.

Package name: `se.sensnology.elpris`

## License

Distributed under the [MIT License](LICENSE).
