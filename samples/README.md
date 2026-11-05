# Sample menus (Phase 0 test set)

- `python3 scripts/fetch_samples.py` downloads freely licensed menu photos from Wikimedia Commons into
  `images/` (gitignored) and writes `manifest.json` (licence, author, source URL). Review the results by hand.
- Add your own photos to `images/` too. Real SK/CZ menus with printed allergen codes are what we
  need most: a printed menu, a denné menu sheet, a blackboard, a menu with an allergen legend,
  multi-column pages.
- Ground truth for scoring goes in `truth/<image-name>.json`, using the snapshot schema from PLAN.md §4.
- OCR dumps exported from the app's Debug screen go in `ocr/<image-name>.json` (`OcrDocument` from
  `core/.../model/Ocr.kt`). These are the parser's JVM test fixtures.
- `ocr-rapid/` holds stand-in OCR dumps of the photos above, made with `scripts/rapid_ocr.py` (RapidOCR +
  PaddleOCR latin PP-OCRv5 model) because ML Kit only runs on Android. Text quality differs from ML Kit,
  so use them to develop the parser, not to score it. Print what the parser makes of them with
  `./gradlew :core:test --tests '*DumpParse*' -Pdump` (or `-Pdump=menu3` for one file).
