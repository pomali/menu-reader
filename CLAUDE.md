# Menu Reader: project context

Android app (Kotlin, Jetpack Compose) that scans restaurant menus, extracts dishes/prices/allergens
**offline first**, upgrades the result with a cloud AI pass when online, and lets the user filter the
saved snapshots. **`PLAN.md` is the source of truth.** Read it before doing anything.

## Status (2026-11-05)
- Gradle project builds: wrapper 9.8.2, AGP 9.5 (built-in Kotlin), Kotlin 2.4.21, compileSdk 37,
  minSdk 26. Versions are in `gradle/libs.versions.toml`.
- `:core` (pure Kotlin, JVM tests): model (`model/`), tokenizer + legend + rule parser (`parse/`),
  lexicon `resources/sk/menureader/core/lexicon.json` + enricher (`allergen/`), ranker (`filter/`),
  `LocalExtractor`. Run `./gradlew :core:test`.
- `:app`: Room (snapshot stored as JSON + raw OCR JSON), ML Kit document scanner + bundled Latin OCR,
  screens: History, Menu, Profile, Debug/OCR export. The user approved the screen list. Nobody has
  run it on a device yet.
- CI: `.github/workflows/build.yml` runs the unit tests and uploads `app-debug.apk` as an artifact.
- Wikimedia Commons gave almost no real menus (and rate-limits this sandbox), so we're waiting for
  the user's own photos instead. They offered to upload some.

## Local build notes (sandbox)
- Android SDK at `/opt/android-sdk` (`local.properties` is gitignored; recreate with `sdk.dir=/opt/android-sdk`).
  Needs `platforms;android-37.0`.
- Maven Central returns 429 here. `~/.gradle/init.d/central-mirror.init.gradle.kts` adds Google's
  mirror `https://maven-central.storage-download.googleapis.com/maven2/` (local only, not committed).

## Next steps
1. Phase 0: take the user's photos, run them through the app, export OCR JSON via Debug into
   `samples/ocr/`, hand-label `samples/truth/`, add a JVM scoring test, then tune the parser.
2. Phase 1 leftovers: GPS at capture + Restaurant records, export/import all snapshots as JSON.
3. Phase 2: cloud upgrade (WorkManager + Claude vision, user's own key), OSM suggestions.

## Open questions for the user
- Test device / Android version (planned minSdk 26)?
- Can they provide their own photos of real SK/CZ menus (best test data)?

## Conventions
- UI strings in Slovak + English. Code and comments in English.
- Never present a dish as allergen-free. Use "not detected" plus the source layer
  (printed / lexical / inferred).
