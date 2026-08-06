# Material Quran

An offline Quran reader for Android, built with Jetpack Compose and Material 3
Expressive.

Play Store title: **Material Quran** · package `app.wird` · free, no ads, no
accounts, no analytics.

## What it does

- **Read** — Uthmani Arabic text with an English translation after each ayah,
  optional tajweed colouring, and a book mode where ayahs flow continuously like
  a printed page.
- **Navigate** — by surah, juz, hizb or page. The app reopens where you stopped.
- **Listen** — 16 reciters, per-ayah or per-range playback, per-ayah repeat, and
  follow-along highlighting. Surahs can be downloaded for offline listening.
- **Bookmark** any ayah.

The full text ships in the app. Reading needs no connection.

## Building

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintVitalRelease       # the lint pass that gates a release build
./gradlew :app:bundleRelease          # signed release .aab (needs keystore.properties)
```

Unit tests run on **JDK 21** via the foojay toolchain resolver in
`settings.gradle.kts`; the app itself targets 17.

`bundleRelease` needs a gitignored `keystore.properties` in the repo root:

```properties
storeFile=upload-keystore.p12
storePassword=…
keyAlias=upload
keyPassword=…
```

Without it the build still configures, it just produces an unsigned artifact.
The `.p12` is the **upload key**, not the app signing key, which Google holds
under Play App Signing.

## The database

`app/src/main/assets/quran.db` is prebuilt by `tools/build_db.py` from the
sources in `tools/data/`. Rebuild it with:

```bash
python3 tools/build_db.py
```

Two things that must move together:

- **`PRAGMA user_version` in `build_db.py` and `QuranDb.ASSET_VERSION`.** The app
  compares them on open and re-copies the asset when they differ. That is the
  only mechanism by which a corrected database reaches someone who already
  opened the app, so bumping one without the other silently ships nothing.
- **The tajweed span offsets.** `tajweed_span.start/end` are absolute codepoint
  indices into `tajweed_text`. Anything that changes the stored text has to
  re-base them.

## The Basmala

`data/Bismillah.kt` separates the Basmala from ayah 1 so it renders as a heading
rather than being counted as part of the first verse. Four cases it has to get
right, all verified against the shipped database rather than assumed:

1. **Al-Fatiha** — the Basmala *is* ayah 1 under this text's numbering. Never
   split surah 1.
2. **At-Tawbah** — opens with no Basmala at all.
3. **An-Naml 27:30** — contains the Basmala *inside* the verse. Only a *leading*
   occurrence on ayah 1 may be cut.
4. **At-Tin (95) and Al-Qadr (97)** — spelled `بِّسْمِ`, with a shadda on the bā'
   that no other surah carries.

Case 4 is why the match compares consonantal skeletons with diacritics removed
instead of raw strings, and why the cut offset is computed per ayah (39 for most
surahs, 40 for those two) rather than being a constant. The canonical Basmala is
read out of Al-Fatiha 1:1 in the database rather than hardcoded, because the text
uses U+0671 ALEF WASLA where a hand-typed literal uses U+0627 ALEF and would
silently never match.

`BismillahTest` pins all of it, with fixtures copied verbatim out of the
database.

## Layout

```
app/src/main/java/app/wird/
  data/          Quran database, settings, audio store, reciters, Basmala split
  audio/         MediaSessionService playback and its controller
  ui/            Compose screens: library, reader, bookmarks, settings
tools/           build_db.py and the Tanzil / QuranEnc / tajweed sources
```

## Licensing

See [ATTRIBUTION.md](ATTRIBUTION.md). Short version: the Arabic text is Tanzil
under CC BY 3.0 and needs a visible source credit and a link to tanzil.net; the
translation is Noor International via QuranEnc and must stay unmodified; the
fonts are OFL; recitation audio is not bundled.

## Privacy

See [PRIVACY.md](PRIVACY.md). No accounts, no analytics, no tracking. The only
network request is fetching recitation audio from everyayah.com, and only when
the user asks for it.
