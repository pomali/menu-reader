package sk.menureader.core

import sk.menureader.core.cloud.CloudMapper
import sk.menureader.core.cloud.CloudPrompt
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.Layer
import sk.menureader.core.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudMapperTest {
    private val ocr = simpleDoc("Guláš 7,50 €", "Vyprážaný syr (1,3,7) 8,90 €")
    private val basic = LocalExtractor().extract("s1", ocr, emptyList(), 123)

    private val answer = """
        ```json
        {"restaurantName":"Bistro","currency":"EUR","legend":[],"sections":[{"name":"Hlavné jedlá","dishes":[
          {"name":"Hovädzí guláš","description":"","portion":"300 g","prices":[{"amount":7.5,"variant":""}],"printedCodes":[],
           "mayContain":[{"allergen":1,"reason":"zahustený múkou"},{"allergen":9,"reason":"zelenina"}]},
          {"name":"Vyprážaný syr","description":"hranolky, tatárska omáčka","portion":"","prices":[{"amount":9.9,"variant":""}],
           "printedCodes":[1,3,7],"mayContain":[]}
        ]}]}
        ```
    """.trimIndent()

    @Test fun mapsToFullSnapshotWithLayers() {
        val s = CloudMapper.toSnapshot(CloudMapper.parse(answer), basic, ocr, "anthropic", "claude-opus-5-5")
        assertEquals(Quality.FULL, s.quality)
        assertEquals(123, s.capturedAt)
        assertEquals("anthropic", s.extraction.engine)
        val gulas = s.dishes[0]
        assertEquals("300 g", gulas.portion)
        assertEquals(750L, gulas.prices.single().amountMinor)
        val gluten = gulas.allergens.single { it.allergen == Allergen.GLUTEN }
        assertEquals(Layer.INFERRED, gluten.layer)
        assertEquals("'zahustený múkou'", gluten.evidence)
        val syr = s.dishes[1]
        assertEquals(setOf(Allergen.GLUTEN, Allergen.EGGS, Allergen.MILK), syr.allergens.filter { it.layer == Layer.PRINTED }.map { it.allergen }.toSet())
    }

    @Test fun priceNotInOcrLowersConfidence() {
        val s = CloudMapper.toSnapshot(CloudMapper.parse(answer), basic, ocr, "anthropic", "m")
        assertTrue(s.dishes[0].confidence > 0.8)   // 7,50 is in the OCR
        assertTrue(s.dishes[1].confidence < 0.6)   // 9,90 is not (OCR says 8,90)
    }

    @Test fun schemaIsValidJson() {
        val schema = MenuJson.parseToJsonElement(CloudPrompt.SCHEMA)
        assertTrue(schema.toString().contains("mayContain"))
    }
}
