# Menu Reader — plan

Take photos of a restaurant menu, turn them into a structured "snapshot" (dishes, prices, sections),
add allergen information to each dish, and let the user filter it ("no gluten, no nuts, under €12").
Snapshots are saved so the user can come back to them later.

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

### Recommendation: semi-local, local-first
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

---

## 3. Platform: PWA first

- A **PWA** (installable web app) gets camera access (`<input capture>` / `getUserMedia`), offline
  support (service worker), and storage (IndexedDB) on both Android and iOS from one codebase, with
  no app-store review.
- If we later need native features (better camera control, native on-device OCR such as
  Apple Vision or ML Kit, which beat WASM OCR), wrap the same app with **Capacitor**.
  This keeps the option open without paying for it now.

### Proposed stack
- **Frontend:** Vite + React + TypeScript, `vite-plugin-pwa`, Dexie (IndexedDB), Zod (shared schema).
- **Proxy:** Cloudflare Worker (or Vercel/Netlify function). It holds the API key, rate-limits, and
  validates the response against the Zod schema.
- **Extraction model:** Claude vision with structured (JSON-schema) output. Use a cheaper tier by
  default and retry with a stronger model when validation fails or confidence is low.
- **Search and facets:** a menu has about 20–200 items, so plain in-memory filtering is enough, plus
  MiniSearch for fuzzy text search (diacritics-insensitive).
- **Offline OCR (phase 2):** PaddleOCR.js (PP-OCRv5, ONNX/WASM) or Tesseract.js.

---

## 4. Data model (draft)

```ts
Snapshot   { id, restaurant: { name, address?, geo? }, capturedAt, language, currency,
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

## 5. UI flows (MVP)
1. **Capture:** take or pick 1–N photos and optionally enter the restaurant name. The device's GPS can
   suggest it later.
2. **Processing:** progress indicator. The user can review and fix the extracted dishes (an inline
   editor in case the AI made mistakes).
3. **Menu view:** sections and dishes. Each dish shows allergen chips with styling by layer
   (solid = printed, outline = lexical, dashed = inferred "may contain").
4. **Filters/facets:** exclude allergens (pre-filled from the profile), diet tags, price range,
   section, text search, and a "strictness" toggle. Counts appear next to each facet.
5. **History:** list of snapshots, searchable by restaurant and date. Export/import as JSON.

---

## 6. Phases
- **Phase 0 (spike, about 2–3 days):** collect about 20 real menu photos (different languages and
  layouts, some with printed allergen codes). Write an extraction script (proxy logic only) and measure
  how many dishes, prices, and allergen codes come out right. This shows whether the LLM-first approach
  is good enough before building the UI.
- **Phase 1 (MVP):** PWA with capture, cloud extraction, layers A+B+C, local storage, filters, history.
- **Phase 2:** offline OCR fallback and an offline queue, translation of dish names, editing.
- **Phase 3:** optional sync and sharing, a Capacitor wrapper, native OCR, a crowd-sourced restaurant
  menu cache.

---

## 7. Risks
- **Wrong "safe" signal** → never claim a dish is safe. Show the layers and evidence, and add a disclaimer.
- **Bad photos** (glare, curved pages, blackboards) → tips during capture, multi-photo support, manual edits.
- **Hallucinated dishes or prices** → strict schema, bounding boxes to check against, and a low
  temperature. Compare the result against OCR text when it is available.
- **API key abuse** → proxy with rate limiting and an optional per-device token. Alternatively, users
  bring their own key.
- **Cost** → resize images (about 1500 px on the long edge) and cache results by image hash.
