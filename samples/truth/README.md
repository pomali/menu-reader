Ground truth for `Phase0ScoreTest`: one `<image-name>.json` per OCR dump in `../ocr/`.

```json
{ "sections": [
  { "name": "Polievky", "dishes": [
    { "name": "Kapustnica s klobásou", "price": 320, "codes": [1, 7, 9] }
  ] }
] }
```
`price` is in cents (or haléře), `codes` are the allergen numbers printed on the menu.
