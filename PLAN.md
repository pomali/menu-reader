# Menu Reader — plan

Take photos of a restaurant menu, turn them into a structured "snapshot" (dishes, prices, sections),
add allergen information to each dish, and let the user filter it ("no gluten, no nuts, under €12").
Snapshots are saved so the user can come back to them later.

## 0. Decisions so far (2026-11-05)
- **Native Android app, offline-first** (changed from "PWA first"). The phone produces a
  "basic" snapshot with no network (document scanner → ML Kit OCR → rule parser → allergen layers
  A+B). When online, a cloud AI pass replaces it with a "full" snapshot (better structure + layer C).
  See §2.1 and §3.
- **Languages:** Slovak, Czech, English menus, usually with printed EU allergen codes (1–14).
  Layer A (printed codes) is therefore the main source, and the lexical dictionary needs sk/cs/en.
- **No translation inside the app.** Dish text can be sent to a translate app through the system
  share dialog (Android `ACTION_SEND` intent).
- **API keys:** the user enters their own key in settings. It is stored on the device, and the app
  calls the AI API directly from the app. **No proxy server in the MVP.**
- **Single user:** no accounts and no sync. Export/import as JSON is the backup.
- **Allergen conflicts:** by default a dish is flagged if *any* layer suggests an excluded allergen,
  even when the printed codes don't list it. A mismatch between the printed codes and the
  ingredients is shown explicitly ("menu doesn't list 7, but 'smotana' suggests milk").
- **Filtering reorders instead of hiding.** Within each section, OK dishes come first and flagged
  dishes after them, with the reason shown. Nothing is hidden or dimmed.
- **Snapshot = this menu, on this date, in this restaurant (at this location).** A snapshot is a
  one-off. Restaurants are their own records, so a restaurant's snapshots can be listed, but there
  is no merging or tracking of dishes across days.
- **Location:** the app reads GPS at capture time and suggests nearby restaurants. The restaurant
  can be left empty and assigned later.
- **No editing in the MVP.** The extracted result is read-only. The user can re-run extraction or
  delete the snapshot.

---

## 1. Processing pipeline

```
photo(s) ──► text + layout ──► structured dishes ──► allergen enrichment ──► snapshot (stored) ──► facets/filter UI
             (OCR or vision      (sections, name,      (3 layers, each with
              LLM)                description, price)   source + confidence)
```

Allergen enrichment has three layers. Each claim keeps track of which layer produced it, so the UI
can show how far to trust it:

| Layer | Source | Confidence | Example |
|---|---|---|---|
| **A. Printed** | Allergen codes/legend on the menu (EU menus often print `A: 1,3,7`) | High: the restaurant stated it | `Vyprážaný syr (1,3,7)` → gluten, eggs, milk |
| **B. Lexical** | Ingredient words matched against a multilingual dictionary (Open Food Facts allergen taxonomy synonyms + our own list) | Medium | "smotana" / "cream" → milk |
| **C. Inferred** | LLM knowledge of typical recipes ("carbonara usually contains egg, pork, gluten, milk") | Low: shown as *"may contain"* | "Pad thai" → peanuts?, fish sauce? |

Rules:
- Layer A always wins. If the menu prints codes for a dish, B and C are shown only as additional hints.
- **No allergen is ever shown as safe.** The UI says "not detected", never "free of", and always tells
  the user to confirm with staff. This is a safety-critical domain.
- Allergen IDs follow the EU 14 (Reg. 1169/2011 Annex II): gluten, crustaceans, eggs, fish, peanuts,
  soy, milk, tree nuts, celery, mustard, sesame, sulphites, lupin, molluscs. Dietary tags are kept
  separately (vegetarian, vegan, pork, spicy, alcohol…).

---

## 2. Local vs semi-local vs online

| | **Fully local** (on-device OCR + rules, maybe on-device LLM) | **Semi-local** (local-first data, cloud only for extraction) | **Fully online** (server stores and processes everything) |
|---|---|---|---|
| Extraction quality | Weak to OK. Tesseract.js / PaddleOCR (WASM) give raw text; working out the menu layout (columns, prices, multi-line descriptions) needs heuristics. Browser on-device LLMs (Chrome Prompt API / Gemini Nano) are desktop-only and limited by language | **Best.** A vision LLM reads layout, sections, and price columns directly and returns JSON | Best (same models) |
| Allergen inference (layer C) | Only layers A+B, unless the device runs a local LLM | Full A+B+C | Full A+B+C |
| Works offline (basements, abroad without data) | ✅ | Partly: viewing/filtering saved snapshots works offline, new photos are queued until online, and an offline OCR fallback can be added | ❌ |
| Privacy | ✅ nothing leaves the device | Only menu photos leave the device (public information). History and allergy profile stay local | Server holds the history and allergy profile, which is health data under GDPR |
| Running cost | €0 | About €0.005–0.03 per page with a vision LLM, depending on model tier | Same as semi-local plus hosting and a database |
| Build complexity | High: OCR tuning and layout heuristics per menu style | **Medium.** A thin proxy (it holds the API key) plus a client-side database | Medium-high: auth, database, sync, GDPR |
| Sharing / multiple devices | ❌ | Can be added later (export a file or link, optional sync) | ✅ built in |
| App size | Large (OCR models 10–40 MB, an LLM even more) | Small | Small |

### Recommendation (original, before the Android decision): semi-local, local-first
- **Data is local-first.** Snapshots, photos, and the user's allergy profile live in IndexedDB/OPFS on
  the device. Filtering is instant and works offline. There is no account in the MVP.
- **Extraction runs in the cloud.** A stateless proxy (Cloudflare Worker or similar) sends the
  photos to a vision LLM with a strict JSON schema and stores nothing.
- **Allergen layers A+B run locally and deterministically** on the result, so they can be
  re-run whenever the dictionary improves. The LLM also returns layer C in the same call.
- **Offline fallback (phase 2):** on-device OCR plus layers A+B. Photos taken offline are queued and
  upgraded to full extraction once the device is back online.
- **Sync and sharing (phase 3, optional):** opt-in. Snapshots are content-addressable, so they can
  be shared ("menu of Bistro X, 2026-11-05") without accounts at first.

### 2.1 Offline modes in detail

The real question is **what happens at each step when there's no network**. There are three steps:
*capture* (take photos), *process* (photos → dishes + allergens), *view* (browse and filter).

| Mode | Capture offline | Process offline | View offline | Quality offline | Quality online |
|---|---|---|---|---|---|
| **Online** (server stores and processes) | ❌ | ❌ | ❌ (or cache only) | – | ★★★ |
| **Semi-offline** (local storage, processing in the cloud) | ✅ queued | ❌ waits for network | ✅ | Photos only, until online | ★★★ |
| **Offline-first** (local pass right away, cloud upgrades it later) | ✅ | ✅ basic | ✅ | ★★ (A+B, simpler structure) | ★★★ |
| **Offline-only** (no cloud at all) | ✅ | ✅ | ✅ | ★★ | ★★ (same) |

**Online.** With one user and their own API key, a server adds hosting, auth and GDPR work for no
benefit. ❌ Not for us.

**Semi-offline** (the current MVP plan)
- The simplest design that's still useful. Without network, photos wait in a queue
  ("3 photos waiting"). Processing starts automatically once online (service worker / `online` event).
- Weak spot: in a basement restaurant with no signal you get nothing until you're outside. In SK/CZ
  mobile data or restaurant Wi-Fi is usually available, and EU roaming means no extra cost abroad
  in the EU.
- Small fallback: show the photo zoomable while it waits, so the user can still read the codes themselves.

**Offline-first**
- A local extractor runs right away: OCR → a rule-based parser → layers A+B. The result is a working
  snapshot marked "basic". Once online, the cloud AI re-extracts it, adds layer C and fixes the
  structure, and its result *replaces* the basic one. Replacing avoids having to match dishes
  between the two versions.
- Our menus make rules work better than usual. SK/CZ menus have regular line patterns:
  `[weight 150 g | 0,33 l] Name ........ (1,3,7) ........ 8,90 €`. Allergen codes and prices are easy
  to find with regular expressions, and the dish name is the text between them. Descriptions,
  multiple columns, and blackboards are where rules break down.
- OCR engine:
  - Web/WASM: Tesseract.js (about 2–4 MB per language, slow and weak on photos) or PaddleOCR.js
    (PP-OCRv5, about 10–20 MB, better on photos, needs a lot of setup).
  - Native: ML Kit / Apple Vision (free, fast, returns line bounding boxes). **Offline-first only
    really works well with the native build.**
- Extra benefit even when online: the local OCR text can be used to check the AI's output. A price
  that doesn't appear anywhere in the OCR text is a likely hallucination.
- Cost: a second extractor to build and maintain (parser + tests), plus app size and battery use.

**Offline-only**
- No AI cost and maximum privacy, but no layer C, and structure quality stays at the rule-based level
  forever. It doesn't fit with "we can have API keys" unless privacy is the top priority.

**Decision: offline-first on native Android** (see §3). The original recommendation was to build **semi-offline** now, behind an `Extractor` interface
(`extract(pages) → Snapshot`, with a `quality: 'basic'|'full'` flag). Move to **offline-first** in the
native phase by adding a `LocalExtractor` (ML Kit OCR + SK/CZ rule parser) next to `CloudExtractor`.
Nothing else needs to change: storage, UI, and allergen layers A+B already run locally and work the
same on either extractor's output.

---

## 3. Platform: native Android (Kotlin)

Since the target is Android only and offline-first depends on on-device ML, we use plain native
Kotlin instead of a cross-platform layer. Flutter / React Native would only make sense if iOS
came back into scope.

### 3.1 On-device building blocks (all free, all offline after first install)
| Need | Component | Notes |
|---|---|---|
| Capture | **ML Kit Document Scanner** (`play-services-mlkit-document-scanner`) | System UI: edge detection, perspective and curve correction, multi-page, gallery import. No camera permission needed. Requires Google Play services |
| Fallback capture | CameraX | Only if the scanner's UI turns out to be bad for blackboards or very large menus |
| OCR | **ML Kit Text Recognition v2, Latin** (bundled model, about 4 MB) | Handles sk/cs diacritics. Returns blocks → lines → elements with bounding boxes, which the parser and the "show source" crop use |
| Parsing | Our own Kotlin rule parser (§3.2) | Pure Kotlin, so unit tests run on the JVM without a device |
| Allergen layers A+B | Our own Kotlin + bundled dictionary JSON (sk/cs/en) | Same code runs on both basic and full snapshots |
| Optional on-device layer C | ML Kit GenAI **Prompt API** (Gemini Nano via AICore) | Only on recent flagships (Pixel 10, Galaxy S26, …). Detect at runtime and use if present; never required |
| Storage | Room (SQLite) + photos in app-private files | FTS4/FTS5 table for diacritics-insensitive search |
| Location | Fused Location Provider + OSM Overpass (online) | Restaurant suggestions only when online. Otherwise the GPS point is stored and matched later |
| Background upgrade | WorkManager with a `CONNECTED` network constraint | Runs the cloud extraction automatically when back online |
| UI | Jetpack Compose + Material 3 | |
| Translation | `Intent.ACTION_SEND` text → user picks Google Translate / DeepL | |

### 3.2 Rule parser for SK/CZ/EN menus (basic quality)
Input: OCR lines with bounding boxes. Steps:
1. **Merge into rows.** Group lines by vertical overlap so a name on the left and its price on the
   right become one row, even across columns.
2. **Find tokens with regular expressions:**
   - price `\d{1,3}([.,]\d{2})?\s?(€|EUR|Kč|CZK|,-)`
   - allergen codes `\(?\b(1[0-4]|[1-9])(\s?[,.]\s?(1[0-4]|[1-9]))*\b\)?`, also with prefixes such as `A:` / `Alergény:` / `Alergeny:`
   - portion `\d+\s?(g|kg|ml|l|dl|ks)`
   - soup/menu markers (`Polievka`, `Polévka`, `Menu 1`, `1.`)
3. **Classify rows:** a dish row has a price and/or allergen codes; a section header has larger
   font height, no price, and often uppercase; a description is an indented or smaller line below
   a dish with no price.
4. **Allergen legend:** if the page has a legend ("1 – obilniny obsahujúce lepok …"), parse it and
   check that the numbers match EU numbering.
5. Each dish gets `quality: basic` and a confidence score. Dishes with low confidence are shown
   with their photo crop, so the user can read the original.

Phase 0 measures how good this is on real photos. The cloud pass covers the cases it can't handle.

### 3.3 Cloud upgrade (full quality)
- WorkManager job sends page images + OCR text to Claude vision with a JSON schema (and the user's
  own API key, stored with the Android Keystore).
- Including the OCR text helps the model with small print and lets us check the result: prices or
  codes that don't appear anywhere in the OCR text are flagged as likely hallucinations.
- The full result **replaces** the basic one (no merging of dish IDs). The basic one is kept for
  debugging and comparison.

## 4. Data model (draft)

```ts
Restaurant { id, name, address?, geo?: { lat, lon }, externalRef?: { source: 'osm', id } }
Snapshot   { id, restaurantId?, capturedAt, capturedGeo?: { lat, lon, accuracy },
             language, currency,
             pages: Page[], sections: Section[], allergenLegend?: Record<string, AllergenId>,
             extraction: { engine, model?, version } }
Page       { id, imageBlobRef, width, height }
Section    { id, name, dishes: Dish[] }
Dish       { id, name, nameTranslated?, description?, price?: { amount, currency, variant? }[],
             allergens: AllergenClaim[], tags: DietTag[], sourceBox?: { pageId, bbox } }
AllergenClaim { allergen: AllergenId, layer: 'printed'|'lexical'|'inferred',
                confidence: number, evidence: string }   // e.g. evidence: "code 7", "'smotana'"
UserProfile { avoid: AllergenId[], diet: DietTag[], strictness: 'printed-only'|'+lexical'|'+inferred' }
```

`sourceBox` lets the user tap a dish and see the cropped area of the original photo. That is the
main way to check what the AI extracted.

---

## 4.1 Choosing the restaurant from GPS
- At capture, read `navigator.geolocation` (or the native plugin later) and store it on the snapshot.
- Suggestions: (1) restaurants already saved within about 100 m, (2) nearby places from
  OpenStreetMap via the Overpass API (`amenity=restaurant|cafe|pub|fast_food`, radius about 75 m).
  OSM is free, needs no key, and covers SK/CZ well.
- If the AI read a restaurant name from the menu header, use it to rank the suggestions.
- If nothing matches, the snapshot has no restaurant ("unassigned") and the user can pick or create
  one later from history.

## 5. UI flows (MVP)
1. **Capture:** take or pick 1–N photos and optionally enter the restaurant name. The device's GPS can
   suggest it later.
2. **Processing:** progress indicator. The result is read-only in the MVP, with a "re-run" button.
3. **Menu view:** sections and dishes. Each dish shows allergen chips with styling by layer
   (solid = printed, outline = lexical, dashed = inferred "may contain").
4. **Filters/facets:** exclude allergens (pre-filled from the profile), diet tags, price range,
   section, text search. Filters **reorder** each section (OK dishes first, flagged ones after,
   with the reason). Counts appear next to each facet.
5. **History:** list of snapshots, searchable by restaurant and date. Export/import as JSON.

---

## 6. Phases
- **Phase 0 (parser + extraction test):** about 20 real SK/CZ menu photos. Run ML Kit OCR on them once
  (a tiny debug screen in the app dumps OCR JSON). Then develop the rule parser and the cloud prompt
  on the JVM against those dumps and score them against hand-made ground truth.
- **Phase 1 (MVP app):** document scanner → OCR → rule parser → layers A+B → Room. Menu screen with
  reordering, allergen profile, facets, history, GPS capture. Works fully offline.
- **Phase 2:** cloud upgrade via WorkManager (layer C, better structure), OSM restaurant
  suggestions, share-to-translate.
- **Phase 3:** optional Gemini Nano layer C on supported devices, editing, export/import.

---

## 7. Risks
- **Wrong "safe" signal** → never claim a dish is safe. Show the layers and evidence, and add a disclaimer.
- **Bad photos** (glare, curved pages, blackboards) → tips during capture, multi-photo support, manual edits.
- **Hallucinated dishes or prices** → strict schema, bounding boxes to check against, and a low
  temperature. Compare the result against OCR text when it is available.
- **API key abuse** → proxy with rate limiting and an optional per-device token. Alternatively, users
  bring their own key.
- **Cost** → resize images (about 1500 px on the long edge) and cache results by image hash.
