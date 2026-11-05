package sk.menureader.core.cloud

import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.AllergenClaim
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer
import sk.menureader.core.model.TagClaim
import kotlin.math.roundToInt

/** One yes/no question for a decisions model: "does this dish contain X?". */
data class DecisionQuestion(val key: String, val instructions: String, val yes: String, val no: String)

/**
 * Allergen double-check with a "decisions" model (OpenRouter `/api/alpha/decisions`): for every dish,
 * ask only about what the user avoids and get a probability per question. Answers at or above
 * [THRESHOLD] become layer C ("may contain") claims with evidence "p=0.87". Never removes a claim:
 * a low probability is not "safe".
 */
object Decisions {
    const val THRESHOLD = 0.5
    private val evidenceRe = Regex("^p=([0-9.]+)$")

    fun questions(profile: UserProfile): List<DecisionQuestion> =
        profile.avoid.sortedBy { it.code }.map { a ->
            DecisionQuestion(
                key = "a${a.code}",
                instructions = "Does this dish contain ${a.en.lowercase()} (EU allergen ${a.code}), counting typical Slovak/Czech/central European recipes for it?",
                yes = "The dish, as usually prepared, contains ${a.en.lowercase()} or ingredients made from it.",
                no = "The dish is usually prepared without ${a.en.lowercase()}.",
            )
        } + profile.avoidTags.sortedBy { it.ordinal }.map { t ->
            val what = tagWords[t] ?: t.name.lowercase()
            DecisionQuestion(
                key = "t_${t.name}",
                instructions = "Does this dish contain $what, counting typical recipes for it?",
                yes = "The dish, as usually prepared, contains $what.",
                no = "The dish is usually prepared without $what.",
            )
        }

    /** The dish as the model sees it. */
    fun state(dish: Dish, section: String): Map<String, String> = buildMap {
        put("dish", dish.name)
        dish.description?.let { put("description", it) }
        if (section.isNotBlank()) put("menu_section", section)
    }

    /** Add claims for answers >= [THRESHOLD] that no printed/lexical claim already covers. */
    fun apply(dish: Dish, answers: Map<String, Double?>): Dish {
        val haveA = dish.allergens.map { it.allergen }.toSet()
        val haveT = dish.tagClaims.map { it.tag }.toSet()
        val newA = answers.mapNotNull { (k, p) ->
            if (p == null || p < THRESHOLD || !k.startsWith("a")) return@mapNotNull null
            val a = k.removePrefix("a").toIntOrNull()?.let(Allergen::fromCode) ?: return@mapNotNull null
            if (a in haveA) null else AllergenClaim(a, Layer.INFERRED, p, evidence(p))
        }
        val newT = answers.mapNotNull { (k, p) ->
            if (p == null || p < THRESHOLD || !k.startsWith("t_")) return@mapNotNull null
            val t = runCatching { DietTag.valueOf(k.removePrefix("t_")) }.getOrNull() ?: return@mapNotNull null
            if (t in haveT) null else TagClaim(t, Layer.INFERRED, evidence(p))
        }
        if (newA.isEmpty() && newT.isEmpty()) return dish
        val claims = dish.tagClaims + newT
        return dish.copy(allergens = dish.allergens + newA, tagClaims = claims, tags = claims.map { it.tag }.toSet())
    }

    private fun evidence(p: Double) = "p=" + "%.2f".format(java.util.Locale.ROOT, p)

    /** Percent for an evidence string written by [apply], or null for other evidence. */
    fun percent(evidence: String): Int? = evidenceRe.find(evidence)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 100).roundToInt() }

    private val tagWords = mapOf(
        DietTag.MEAT to "meat of any kind (including meat broth)",
        DietTag.PORK to "pork (including bacon, ham, lard)",
        DietTag.BEEF to "beef or veal",
        DietTag.POULTRY to "chicken, turkey, duck or other poultry",
        DietTag.LAMB to "lamb or mutton",
        DietTag.ONION to "onion, shallot or leek",
        DietTag.GARLIC to "garlic",
        DietTag.SPICY to "chilli or hot spices",
        DietTag.CORIANDER to "coriander / cilantro",
        DietTag.TURMERIC to "turmeric",
        DietTag.MUSHROOMS to "mushrooms",
        DietTag.TOMATO to "tomato (including ketchup or tomato sauce)",
        DietTag.PINEAPPLE to "pineapple",
        DietTag.ALCOHOL to "alcohol",
    )
}
