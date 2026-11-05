package sk.menureader.core

import sk.menureader.core.allergen.Enricher
import sk.menureader.core.filter.Ranker
import sk.menureader.core.filter.Reason
import sk.menureader.core.filter.Strictness
import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.DietTag.*
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvoidedIngredientsTest {
    private val enricher = Enricher()
    private fun claims(name: String, desc: String? = null) = enricher.enrich(Dish("d", name, desc)).tagClaims
    private fun strong(name: String, desc: String? = null) = claims(name, desc).filter { it.layer == Layer.LEXICAL }.map { it.tag }.toSet()
    private fun weak(name: String) = claims(name).filter { it.layer == Layer.INFERRED }.map { it.tag }.toSet()

    @Test fun pineapple() {
        assertTrue(PINEAPPLE in strong("9. HAWAI", "Paradajkový základ, mozzarella, šunka, ananás"))
        assertTrue(PINEAPPLE in strong("Pizza Hawai"))
        assertTrue(PINEAPPLE in strong("Kuracie prsia s ananásom"))
        assertTrue(PINEAPPLE in strong("Sweet and sour pork with pineapple"))
        assertFalse(PINEAPPLE in strong("Pizza Margherita"))
    }

    @Test fun onionGarlicChilliCoriander() {
        assertTrue(ONION in strong("Pečeň na cibuľke, ryža"))
        assertTrue(GARLIC in strong("Cesnaková polievka s opekaným chlebom"))
        assertTrue(GARLIC in strong("Česnečka se sýrem a krutony"))
        assertTrue(SPICY in strong("Pizza s feferónkami"))
        assertTrue(CORIANDER in strong("Poké bowl, wakame, koriander"))
        assertTrue(TURMERIC in strong("Ryža s kurkumou"))
        assertTrue(MUSHROOMS in strong("Bravčová panenka na hubovej omáčke"))
        assertTrue(MUSHROOMS in strong("Prosciutto e funghi", "šampiňóny"))
        assertTrue(TOMATO in strong("Špagety s paradajkovou omáčkou"))
    }

    @Test fun typicalRecipesAreOnlyMayContain() {
        assertTrue(ONION in weak("Hovädzí guláš, knedľa"))
        assertTrue(CORIANDER in weak("Kuracie curry s ryžou"))
        assertTrue(TURMERIC in weak("Kuracie curry s ryžou"))
        assertFalse(ONION in strong("Hovädzí guláš, knedľa"))
    }

    @Test fun meatIsDerived() {
        val c = claims("Kuracie prsia na grile")
        assertTrue(c.any { it.tag == POULTRY })
        assertTrue(c.any { it.tag == MEAT && it.layer == Layer.LEXICAL })
        assertTrue(MEAT in strong("Trhané bravčové mäso"))
        assertFalse(MEAT in strong("Vyprážaný karfiol, varené zemiaky"))
        assertFalse(MEAT in strong("Zemiaky s maslom")) // "maslo" is butter, not "mäso"
    }

    @Test fun rankerUsesTagsWithEvidenceAndStrictness() {
        val s = LocalExtractor().extract(
            "t", simpleDoc("Pizza Hawai 9,20 €", "Pizza Margherita 7,90 €", "Hovädzí guláš 8,50 €"), emptyList(), 0,
        )
        val avoid = UserProfile(avoidTags = setOf(PINEAPPLE, ONION))
        val r = Ranker.rank(s, avoid).single().dishes
        assertEquals("Pizza Margherita", r.first().dish.name)
        val hawai = r.first { it.dish.name == "Pizza Hawai" }.reasons.single() as Reason.Tag
        assertEquals("'Hawai'", hawai.claim.evidence)
        // With printed + lexical only, the guláš "may contain onion" guess no longer flags it.
        val lexOnly = Ranker.rank(s, avoid.copy(strictness = Strictness.PLUS_LEXICAL)).single().dishes
        assertFalse(lexOnly.first { it.dish.name == "Hovädzí guláš" }.flagged)
        assertEquals(1, Ranker.tagCounts(s, Strictness.PLUS_LEXICAL)[PINEAPPLE])
    }
}
