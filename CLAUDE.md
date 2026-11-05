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
  screens: History, Menu, Settings, Debug/OCR export (+ bottom sheets, see UI below). Runs on the
  user's phone; app version 0.3.0 (versionCode 3).
- Share into the app (`ui/Share.kt`, manifest intent filters): screenshots (`image/*`, one or many) go
  through OCR like scans; text (`text/plain`, e.g. selected on a web page) becomes a fake one-column OCR
  page via `core/.../parse/PlainText.kt` (`OcrDocument.source = "text"`: name / description / price lines).
  A share that is only a link (Wolt's share button) shows a hint. Later options discussed with the user:
  accessibility service to read another app's screen (sideload only, privacy toggle), Wolt's web data (ToS).
- Avoided non-allergen ingredients (`DietTag`): meat (derived from pork/beef/poultry/lamb), lamb, onion,
  garlic, chilli/spicy, coriander, turmeric, mushrooms, tomato, pineapple, alcohol. `Dish.tagClaims` carry
  layer + evidence like allergens (weak lexicon terms = INFERRED "may contain", e.g. guláš -> onion).
  The user hates pineapple: "Hawai/Havaj" pizza counts as a direct pineapple match.
- UI (user feedback 2026-11-05): Android conventions, upbeat/funny tone with emoji. History: top bar with
  avoid-sheet (person icon) + Settings, ExtendedFAB "Scan" (scanner has gallery import; photo picker only
  as fallback). Menu: top bar hides on scroll (search, ⋮ rename/re-run/debug/delete), restaurant name is
  a list item (tap to rename), summary line, filter row = filter icon + only the active filters (avoided
  items with counts, max price) wrapping; the icon opens one sheet (`AvoidSheet` with counts + price slider).
  Sticky section headers. Dish card shows every chip, display only (filled = printed, outlined = word on
  the menu, dotted = guess; no "?" text); tapping the card opens `DishSheet` (each chip with its reason,
  likely ingredients, diet hint, low-confidence note, photo, translate/share); card ⋮ has photo + share.
  Only avoided things are red; flagged cards get a red outline. Settings in `ui/SettingsScreen.kt`
  + `data/SettingsStore` (large text, wallpaper colours, debug tools).
- AI reading (Phase 2 cloud upgrade): Settings → "✨ AI reading" picks Off / Claude / OpenAI / Gemini / Nano Banana
  (Gemini image model, shares the Gemini key, schema only in the prompt), a model (suggestions + free text) and the
  user's own API key (`cloud/KeyVault`, AES-GCM with an Android Keystore key). One prompt + JSON schema for all
  (`core/.../cloud/CloudPrompt.kt`; no nullables: "" = missing). `CloudMapper` maps the answer to a FULL snapshot,
  re-runs layers A+B locally, the model's `mayContain` = layer C, prices not in the OCR text lower confidence.
  Claude via the official Anthropic Java SDK (`AnthropicReader`, default `claude-opus-5-5`, effort medium,
  structured output, server-side fallbacks "default"; SDK 3.x sends the key as `Authorization: Bearer`).
  OpenAI Responses API and Gemini Interactions API (`Api-Revision: 2026-05-20`) over OkHttp (`HttpReaders.kt`).
  The AI also reasons per dish (user decision 2026-11-05, option A; profile stays on the phone): `ingredients`
  (likely, shown as "🧾 Probably: …"), `ingredientFlags` for the avoidable non-allergens (explicit -> LEXICAL,
  typical recipe -> INFERRED, with reason), `vegetarian`/`vegan` (a "?" hint, dropped if any meat claim). Word-list
  hits inside the AI's ingredient list count as INFERRED, never LEXICAL. OpenRouter Decisions was tried and dropped.
  `CloudUpgradeWorker` (WorkManager, CONNECTED) runs after each new menu if "auto" is on, or from menu ⋮ "Read with AI";
  the basic snapshot is kept in `basicSnapshotJson` (Room v2 migration). Request shapes are tested against
  MockWebServer (`CloudReadersTest`); no live API call has been made yet (no keys in the sandbox).
- Screenshot tests: `app/src/test/.../ScreenshotTest.kt` (Robolectric native graphics + Roborazzi), PNGs in
  `app/build/screenshots/`; copies in `docs/screenshots/`. Locally needs `robolectricRepoUrl` (mirror) in
  `~/.gradle/gradle.properties`. No emulator here (no KVM).
- Large text + high contrast ("grandpa mode"): `data/SettingsStore`, `MenuTheme(large)` scales fonts 1.4x
  on top of the system setting, bolder weights, fixed black/white (or white/black) schemes instead of
  dynamic colour. Toggle in Settings → Display. `LocalLargeText` for stacked layouts.
- CI: `.github/workflows/build.yml` runs the unit tests and uploads `app-debug.apk` as an artifact.
- The user uploaded 8 real SK menus (`samples/menu*.jpg`; `menu6.jpg` = Garni, `menu6.webp` = Fontána).
  Stand-in OCR dumps are in `samples/ocr-rapid/` (see samples/README.md). The parser has been tuned on them:
  weekly menus (weekday sections), M1-M5 markers, `/1,3,7/` and `(A1,3,9)` codes, two-column pizza lists,
  uppercase dish + description layouts. Known weak spots: dish header + variants (menu7: KUNG PAO / S TOFU…),
  soup columns beside dish lists (Fontána) depend on OCR blocks.
- First real ML Kit dumps from the phone: `samples/ocr/panorama.json` + `living.json` (photos of a screen,
  `samples/panorama.jpg`, `living.jpg`). Panorama (weekly menu, 4 dishes/day, weights in a left column,
  superscript codes) parses badly: 8 of 25 dishes, merged lines, garbled prices. Pulled from the debug
  build's Room DB via `adb exec-out run-as sk.menureader cat databases/menu-reader.db`.
- The user will make the ground-truth test set (`samples/truth/`) later.

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
- Can they provide their own photos of real SK/CZ menus (best test data)?

## Conventions
- UI strings in Slovak + English. Code and comments in English.
- Never present a dish as allergen-free. Use "not detected" plus the source layer
  (printed / lexical / inferred).
