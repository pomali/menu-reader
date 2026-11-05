# Menu Reader: project context

Android app (Kotlin, Jetpack Compose) that scans restaurant menus, extracts dishes/prices/allergens
**offline first**, upgrades the result with a cloud AI pass when online, and lets the user filter the
saved snapshots. **`PLAN.md` is the source of truth.** Read it before doing anything.

## Status (2026-11-05)
- Planning done, no app code yet. Decisions are in `PLAN.md` §0, architecture in §3, data model in §4.
- `scripts/fetch_samples.py` downloads freely licensed menu photos from Wikimedia Commons into
  `samples/images/` (gitignored). It hasn't been run yet because the network was blocked.

## Environment the next session needs
Allowed network domains (on top of the package managers):
- `dl.google.com`: Android SDK (cmdline-tools, platforms, build-tools). Needed to build locally.
- `maven.google.com`, `services.gradle.org`, `plugins.gradle.org`, `repo.maven.apache.org`: Gradle/AGP/ML Kit.
- `commons.wikimedia.org`, `upload.wikimedia.org`: sample menu photos.
- `overpass-api.de`: OSM restaurant lookup (Phase 2, optional for now).
- `api.anthropic.com`: cloud extraction tests (Phase 2).
Also set up GitHub Actions to build a debug APK and run the unit tests on every push. The user
installs the APK from the workflow artifacts.

## Next steps (agreed direction, not yet confirmed to start)
1. Gradle project skeleton: `minSdk 26`, Compose, Room, ML Kit document scanner + text recognition
   (Latin, bundled), WorkManager. Plus a GitHub Actions workflow (assembleDebug + unit tests).
2. Pure-Kotlin `:core` module (JVM unit-testable) with the snapshot model, EU-14 allergen IDs,
   the sk/cs/en allergen dictionary (layer B), the printed-code + legend parser (layer A), and the
   rule-based menu parser (PLAN §3.2).
3. App flow: scan → OCR → parse → enrich → save to Room → menu screen (reorder OK-first per section),
   allergen profile, facets, history. Debug screen that exports OCR JSON for Phase 0 test fixtures.
4. Phase 0: run fetch_samples.py, hand-label ground truth in `samples/truth/`, score the parser.

## Open questions for the user
- Test device / Android version (planned minSdk 26)?
- Can they provide their own photos of real SK/CZ menus (best test data)?

## Conventions
- UI strings in Slovak + English. Code and comments in English.
- Never present a dish as allergen-free. Use "not detected" plus the source layer
  (printed / lexical / inferred).
