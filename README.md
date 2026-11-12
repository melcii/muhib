# Muhib · مُحِبّ

A quiet Islamic companion for Android: prayer times, the Qibla and a Hijri calendar for where you are, and a reminder (an ayah, hadith, sunnah or a passage from a scholar) that stays covered until you tap it. Muhib now includes everything that was NIDÂ.

## The app
- **Your place at the top.** Tap its name to open a sheet: switch between where you are and your cities, add a city from the built-in list, or swipe one aside to remove it.
- **A prayer card you swipe through:**
  - **Simple:** the next time and how long until it.
  - **Compact** (the default): the six times and the time left until the next. The time you are in is highlighted.
  - **Timeline:** the whole day on one line, with the times to avoid prayer, the night rows and a line for now. Tap any part to see its times. Under it are Ishrâq, Duha and Evvabin, and the night in thirds and halves with Seher.
  - **Qibla:** a compass that turns with your phone. It also shows the distance to the Kaaba, the state of your location, and a figure-8 to calibrate the compass.
- **The reminder below it**, blurred until you tap it. It then opens up to the full passage, with "a little more" and its source.
- **Calendar:** the button at the top opens any day's times. Each day shows its Hijri date, plus Ishrâq, Duha and the night. Swipe the month sideways to move between months.

## Widgets
- **Reminder:** blurred with *"Tap this when you need it the most"*. Tap once to reveal the reminder in place, and again to open the app.
- **Prayer times:** the city, the six times with the current one highlighted, and the countdown to the next. Tap the city to update your location.
- **Prayer times and reminder:** both in one widget. Only the reminder is covered. It adapts to its height: compact at about 2 rows, the meaning at 3, and the Arabic as well from about 4.
- **Next prayer:** a small 2×1 with just the next prayer and how long until it.

Every widget can also be placed on the lock screen where Android allows it (Android 16 QPR2 on Pixel phones). Under **Settings → Widgets**, either every widget shares one style or each widget gets its own. A style is the background colour (any colour, or Auto to follow light and dark mode), the opacity, and the text colour.

## Settings
Settings has two sides, **Prayer** and **Muhib**. What both share sits underneath, so changing it on either side changes both.
- **Prayer:**
  - *Update prayer times* downloads the next two months from AlAdhan.
  - Notifications come 20 minutes before each time by default (adjustable from 5 to 60, or off) and again when the time comes.
  - Choose from six calculation methods, and Standard or Hanafi Asr.
- **Muhib:**
  - The reminder renews *with the sun* (after Fajr and after Maghrib) or *at set times*.
  - A quiet note can come every few days.
- **Both:**
  - Accent colour: green, blue, plum, amber or your own.
  - Match my widget: the app wears your widget's background and text colours (on by default).
  - Font: Inter, Classic, Airy, Typewriter or Playful, the same in the app and on every widget.
  - Widget styles.
  - Exact countdown.
  - Add a widget.
  - English or Turkish.

## Private by design
- **Online only when you ask.** Muhib connects to the internet only when you tap *Update prayer times*. It then sends each saved place's coordinates, rounded to about 1 km, with your method and Asr choice, to [AlAdhan](https://aladhan.com/prayer-times-api). Nothing else is sent.
- **Your location stays on the phone.** Location is read once when you open the app or tap the city on a widget, and never in the background. Town names come from a list built into the app.
- **No account, no analytics, no ads, and no cloud backup.** Full policy: [docs/privacy.html](docs/privacy.html).

## Light on battery
- Widgets redraw at the start of each minute only while the screen is on.
- One alarm is set at a time for prayer notes, and one for the next reminder.

## Content
All reminders live in [`app/src/main/assets/content.json`](app/src/main/assets/content.json). Each item has these fields:
- `id`
- `type`: ayah, hadith, sunnah or chapter
- `arabic`
- `text`
- `source`
- `more`: the "a little more" note
- optional `link` and `headline`

A `tr` object holds the Turkish `text`, `source`, `more` and optional `headline`.

## Sources
- Qur'an Arabic text: the [Tanzil Project](https://tanzil.net).
- Hadith references follow the standard numbering used on sunnah.com.
- The English and Turkish meanings and notes are simple renderings and should be reviewed before wider use.
- Prayer time formulas: [PrayTimes.org](http://praytimes.org/), with methods numbered as [AlAdhan](https://aladhan.com/calculation-methods) numbers them. They are checked against AlAdhan in the tests.
- Hijri dates: the Umm al-Qura calendar, from Android's built-in ICU.
- Towns and cities: [GeoNames](https://www.geonames.org/) (cities with over 5,000 people), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Font: [Inter](https://rsms.me/inter/), SIL Open Font License (see `licenses/`).
