# Sample menus (Phase 0 test set)

- `python3 scripts/fetch_samples.py` downloads freely licensed menu photos from Wikimedia Commons into
  `images/` (gitignored) and writes `manifest.json` (licence, author, source URL). Review the results by hand.
- Add your own photos to `images/` too. Real SK/CZ menus with printed allergen codes are what we
  need most: a printed menu, a denné menu sheet, a blackboard, a menu with an allergen legend,
  multi-column pages.
- Ground truth for scoring goes in `truth/<image-name>.json`, using the snapshot schema from PLAN.md §4.
- OCR dumps exported from the app's Debug screen go in `ocr/<image-name>.json` (`OcrDocument` from
  `core/.../model/Ocr.kt`). These are the parser's JVM test fixtures.
