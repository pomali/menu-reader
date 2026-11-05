package sk.menureader.core.cloud

/**
 * The prompt and JSON schema shared by every cloud provider (Claude, OpenAI, Gemini), PLAN §3.3.
 * The schema avoids nullable types (providers disagree on how to express them): an empty string
 * means "not on the menu". All objects list every property as required and forbid extra ones,
 * which strict structured output on all three providers accepts.
 */
object CloudPrompt {
    const val SYSTEM = """You read restaurant menus from photos, mostly Slovak and Czech, sometimes English.
Return every dish exactly as printed: keep the original language and spelling, do not translate.
- sections: in reading order. Weekly menus: one section per day, named like "Pondelok 3.11." (add " · Polievky" style sub-sections if the day has them).
- name: the dish name without numbering ("M1:", "1."), weights, codes or prices. description: the rest of the dish text, or "".
- portion: weight/volume as printed, e.g. "150 g", "0,33 l", "200 g / 200 g", or "".
- prices: one entry per price; amount as a number in the menu's currency (8.90, 189); variant like "0,5 l" when one dish has several prices, else "".
- printedCodes: the allergen numbers printed for this dish (EU numbering 1-14, plus any extra numbers the menu defines). Never invent codes; [] if none are printed.
- mayContain: allergens (EU numbers 1-14) the dish typically contains by recipe but that are NOT in printedCodes, with a short reason in the menu's language ("guláš býva zahustený múkou"). Only clear, common cases.
- legend: the allergen legend if the menu prints one (code -> EU number), else [].
- currency: "EUR" or "CZK" or another ISO code, "" if unknown. restaurantName: as printed on the menu, or "".
Skip opening hours, phone numbers, addresses and ads. If OCR text is given, use it to read small print, but trust the photo."""

    const val SCHEMA = """{
  "type": "object",
  "additionalProperties": false,
  "required": ["restaurantName", "currency", "legend", "sections"],
  "properties": {
    "restaurantName": {"type": "string"},
    "currency": {"type": "string"},
    "legend": {
      "type": "array",
      "items": {
        "type": "object", "additionalProperties": false, "required": ["code", "allergen"],
        "properties": {"code": {"type": "integer"}, "allergen": {"type": "integer"}}
      }
    },
    "sections": {
      "type": "array",
      "items": {
        "type": "object", "additionalProperties": false, "required": ["name", "dishes"],
        "properties": {
          "name": {"type": "string"},
          "dishes": {
            "type": "array",
            "items": {
              "type": "object", "additionalProperties": false,
              "required": ["name", "description", "portion", "prices", "printedCodes", "mayContain"],
              "properties": {
                "name": {"type": "string"},
                "description": {"type": "string"},
                "portion": {"type": "string"},
                "prices": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false, "required": ["amount", "variant"],
                    "properties": {"amount": {"type": "number"}, "variant": {"type": "string"}}
                  }
                },
                "printedCodes": {"type": "array", "items": {"type": "integer"}},
                "mayContain": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false, "required": ["allergen", "reason"],
                    "properties": {"allergen": {"type": "integer"}, "reason": {"type": "string"}}
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}"""

    /** The user message text that goes with the photos. */
    fun userText(ocrText: String?): String = buildString {
        append("Read this menu and return the JSON.")
        if (!ocrText.isNullOrBlank()) {
            append("\n\nOCR text from the phone (may contain errors):\n")
            append(ocrText.take(20_000))
        }
    }
}
