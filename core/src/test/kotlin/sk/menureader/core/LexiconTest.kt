package sk.menureader.core

import sk.menureader.core.allergen.Lexicon
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.Allergen.*
import sk.menureader.core.model.DietTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LexiconTest {
    private val lex = Lexicon.default
    private fun strong(s: String) = lex.allergens(s).filter { !it.weak }.map { it.target }.toSet()
    private fun all(s: String) = lex.allergens(s).map { it.target }.toSet()

    @Test fun slovakInflection() {
        assertTrue(MILK in strong("Kuracie prsia so smotanovou omáčkou"))
        assertTrue(MILK in strong("Bryndzové halušky so slaninou"))
        assertTrue(GLUTEN in strong("Bryndzové halušky so slaninou"))
        assertTrue(TREE_NUTS in strong("Palacinky s vlašskými orechmi"))
        assertTrue(EGGS in strong("Zemiakový šalát s majonézou"))
    }

    @Test fun czech() {
        assertTrue(MILK in strong("Svíčková na smetaně, houskový knedlík"))
        assertTrue(GLUTEN in strong("Svíčková na smetaně, houskový knedlík"))
        assertTrue(setOf(GLUTEN, EGGS, MILK).all { it in strong("Smažený sýr, hranolky") })
    }

    @Test fun english() {
        assertTrue(PEANUTS in strong("Pad thai with peanuts"))
        assertTrue(MOLLUSCS in strong("Grilled calamari"))
        assertTrue(CRUSTACEANS in strong("Garlic prawns"))
    }

    @Test fun diacriticsAndCaseInsensitive() {
        assertTrue(MILK in strong("SMOTANA"))
        assertTrue(MILK in strong("smotana"))
        assertTrue(CELERY in strong("Zelerový šalát"))
    }

    @Test fun noObviousFalsePositives() {
        assertFalse(EGGS in all("Grilled eggplant"))
        assertFalse(MILK in all("Syrové maso")) // Czech "syrové" = raw
        assertTrue(MILK in strong("Brokolica so syrovou omáčkou")) // Slovak "syrová" = cheese
        assertEquals(emptySet<Allergen>(), strong("Hranolky"))
        assertFalse(TREE_NUTS in strong("Peanut sauce"))
    }

    @Test fun weakHintsAreMarked() {
        val hit = lex.allergens("Hovädzí vývar").single { it.target == CELERY }
        assertTrue(hit.weak)
        assertEquals("vývar", hit.matched)
    }

    @Test fun tags() {
        val t = lex.tags("Bravčová krkovička na grile").map { it.target }
        assertTrue(DietTag.PORK in t)
        assertTrue(DietTag.POULTRY in lex.tags("Kuracie prsia").map { it.target })
    }
}
