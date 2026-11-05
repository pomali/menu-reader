package sk.menureader.core

import sk.menureader.core.cloud.Decisions
import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecisionsTest {
    private val profile = UserProfile(avoid = setOf(Allergen.MILK, Allergen.GLUTEN), avoidTags = setOf(DietTag.ONION))

    @Test fun onlyAsksAboutAvoidedThings() {
        assertEquals(listOf("a1", "a7", "t_ONION"), Decisions.questions(profile).map { it.key })
        assertTrue(Decisions.questions(UserProfile()).isEmpty())
    }

    @Test fun highProbabilitiesBecomeMayContain() {
        val d = Decisions.apply(Dish("d1", "Segedínsky guláš"), mapOf("a1" to 0.82, "a7" to 0.91, "t_ONION" to 0.97))
        val milk = d.allergens.single { it.allergen == Allergen.MILK }
        assertEquals(Layer.INFERRED, milk.layer)
        assertEquals(91, Decisions.percent(milk.evidence))
        assertEquals(DietTag.ONION, d.tagClaims.single().tag)
        assertTrue(DietTag.ONION in d.tags)
    }

    @Test fun lowProbabilityAddsNothingAndRemovesNothing() {
        val dish = Enrich.withCode7("Vyprážaný syr")
        val d = Decisions.apply(dish, mapOf("a7" to 0.02, "a1" to 0.1, "t_ONION" to null))
        assertEquals(dish, d)
    }

    @Test fun otherEvidenceHasNoPercent() = assertNull(Decisions.percent("'smotanou'"))
}

private object Enrich {
    fun withCode7(name: String) = sk.menureader.core.allergen.Enricher().enrich(Dish("d", name, printedCodes = listOf(7)))
}
