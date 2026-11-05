package sk.menureader.core.allergen

import sk.menureader.core.model.Allergen
import sk.menureader.core.model.AllergenClaim
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer

/**
 * Adds allergen claims to dishes: layer A from printed codes (mapped through the menu's own
 * legend when it has one) and layer B from the lexicon. Weak lexicon terms ("soup" -> celery)
 * are typical-recipe guesses and are recorded as layer C, "may contain".
 *
 * Layer A wins: a lexical/inferred claim is only kept for allergens the printed codes don't list,
 * which is exactly the mismatch the UI shows ("menu doesn't list 7, but 'smotana' suggests milk").
 * Deterministic, so it can be re-run on stored snapshots when the dictionary improves.
 */
class Enricher(private val lexicon: Lexicon = Lexicon.default) {

    fun enrich(dish: Dish, legend: Map<Int, Allergen>? = null): Dish {
        val printed = dish.printedCodes.mapNotNull { code ->
            val a = legend?.get(code) ?: Allergen.fromCode(code) ?: return@mapNotNull null
            AllergenClaim(a, Layer.PRINTED, 0.95, "code $code")
        }.distinctBy { it.allergen }
        val printedSet = printed.map { it.allergen }.toSet()

        val text = listOfNotNull(dish.name, dish.description).joinToString(" ")
        val lexical = lexicon.allergens(text)
            .filter { it.target !in printedSet }
            .map {
                if (it.weak) AllergenClaim(it.target, Layer.INFERRED, 0.3, "'${it.matched}'")
                else AllergenClaim(it.target, Layer.LEXICAL, 0.6, "'${it.matched}'")
            }

        val tags = lexicon.tags(text).map { it.target }.toMutableSet()
        if (DietTag.VEGAN in tags) tags += DietTag.VEGETARIAN

        // Claims are computed from scratch; the Phase 2 cloud pass adds its own layer C afterwards.
        return dish.copy(allergens = printed + lexical, tags = tags)
    }
}

/** Claims not backed by the printed codes, on a dish that does have printed codes. */
fun Dish.printedMismatches(): List<AllergenClaim> =
    if (printedCodes.isEmpty()) emptyList() else allergens.filter { it.layer != Layer.PRINTED }
