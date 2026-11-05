package sk.menureader.core.cloud

import sk.menureader.core.model.DietTag

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
- ingredients: the dish's likely main ingredients (what it is usually made of here), short words in the menu's language, most important first, at most 12.
- ingredientFlags: which of these the dish contains: meat (any meat or meat broth), pork, beef, poultry, lamb, onion (incl. shallot, leek), garlic, chilli/spicy, coriander, turmeric, mushrooms, tomato (incl. ketchup, tomato sauce), pineapple, alcohol. For each give a short reason in the menu's language and explicit=true if the menu text itself says so, false if it's from the typical recipe. Only include flags you are fairly sure about.
- vegetarian / vegan: true only if the dish as usually made has no meat, fish or meat broth (vegan: also no eggs, milk, honey).
- legend: the allergen legend if the menu prints one (code -> EU number), else [].
- currency: "EUR" or "CZK" or another ISO code, "" if unknown. restaurantName: as printed on the menu, or "".
Skip opening hours, phone numbers, addresses and ads. If OCR text is given, use it to read small print, but trust the photo."""

    /** Non-allergen things the user can avoid, as the model names them. */
    val FLAGS: List<String> = DietTag.entries.filter { it != DietTag.VEGETARIAN && it != DietTag.VEGAN }.map { it.name }

    val SCHEMA: String = """{
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
              "required": ["name", "description", "portion", "prices", "printedCodes", "mayContain", "ingredients", "ingredientFlags", "vegetarian", "vegan"],
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
                },
                "ingredients": {"type": "array", "items": {"type": "string"}},
                "ingredientFlags": {
                  "type": "array",
                  "items": {
                    "type": "object", "additionalProperties": false, "required": ["flag", "reason", "explicit"],
                    "properties": {
                      "flag": {"type": "string", "enum": [${FLAGS.joinToString(", ") { "\"$it\"" }}]},
                      "reason": {"type": "string"},
                      "explicit": {"type": "boolean"}
                    }
                  }
                },
                "vegetarian": {"type": "boolean"},
                "vegan": {"type": "boolean"}
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
