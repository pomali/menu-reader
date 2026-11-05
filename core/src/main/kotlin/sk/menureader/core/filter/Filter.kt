package sk.menureader.core.filter

import kotlinx.serialization.Serializable
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.AllergenClaim
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer
import sk.menureader.core.model.Section
import sk.menureader.core.model.Snapshot
import sk.menureader.core.model.TagClaim
import sk.menureader.core.parse.Text

/** Which layers count when deciding whether to flag a dish. Default: any layer (PLAN §0). */
@Serializable
enum class Strictness(val layers: Set<Layer>) {
    PRINTED_ONLY(setOf(Layer.PRINTED)),
    PLUS_LEXICAL(setOf(Layer.PRINTED, Layer.LEXICAL)),
    PLUS_INFERRED(setOf(Layer.PRINTED, Layer.LEXICAL, Layer.INFERRED)),
}

@Serializable
data class UserProfile(
    val avoid: Set<Allergen> = emptySet(),
    val avoidTags: Set<DietTag> = emptySet(),
    val strictness: Strictness = Strictness.PLUS_INFERRED,
)

data class Query(
    val text: String = "",
    val maxPriceMinor: Long? = null,
    val sectionIds: Set<String> = emptySet(),
    val requireTags: Set<DietTag> = emptySet(),
)

sealed interface Reason {
    data class Contains(val claim: AllergenClaim) : Reason
    data class Tag(val claim: TagClaim) : Reason
    data class MissingTag(val tag: DietTag) : Reason
    data class OverPrice(val maxMinor: Long) : Reason
    data object NoTextMatch : Reason
    data object OtherSection : Reason
}

/**
 * Never "safe": a dish with no reasons is *not detected*, and the UI says so together with
 * which layers were checked and whether the menu printed codes for it.
 */
data class RankedDish(val dish: Dish, val reasons: List<Reason>) {
    val flagged get() = reasons.isNotEmpty()
    val noPrintedCodes get() = dish.printedCodes.isEmpty()
}

data class RankedSection(val section: Section, val dishes: List<RankedDish>)

object Ranker {
    fun evaluate(dish: Dish, sectionId: String, profile: UserProfile, query: Query): List<Reason> = buildList {
        dish.allergens
            .filter { it.allergen in profile.avoid && it.layer in profile.strictness.layers }
            .sortedBy { it.layer }
            .distinctBy { it.allergen }
            .forEach { add(Reason.Contains(it)) }
        dish.tagClaims
            .filter { it.tag in profile.avoidTags && it.layer in profile.strictness.layers }
            .sortedBy { it.layer }
            .distinctBy { it.tag }
            .forEach { add(Reason.Tag(it)) }
        (query.requireTags - dish.tags).forEach { add(Reason.MissingTag(it)) }
        val max = query.maxPriceMinor
        if (max != null) {
            val cheapest = dish.prices.minOfOrNull { it.amountMinor }
            if (cheapest != null && cheapest > max) add(Reason.OverPrice(max))
        }
        if (query.text.isNotBlank()) {
            val hay = Text.fold(listOfNotNull(dish.name, dish.description).joinToString(" "))
            if (!hay.contains(Text.fold(query.text.trim()))) add(Reason.NoTextMatch)
        }
        if (query.sectionIds.isNotEmpty() && sectionId !in query.sectionIds) add(Reason.OtherSection)
    }

    /** Filtering reorders instead of hiding: within each section, not-flagged dishes first, order kept. */
    fun rank(snapshot: Snapshot, profile: UserProfile, query: Query = Query()): List<RankedSection> =
        snapshot.sections.map { sec ->
            val ranked = sec.dishes.map { RankedDish(it, evaluate(it, sec.id, profile, query)) }
            RankedSection(sec, ranked.sortedBy { it.flagged })
        }

    /** Facet counts: dishes per allergen and per tag, using the layers allowed by [strictness]. */
    fun allergenCounts(snapshot: Snapshot, strictness: Strictness): Map<Allergen, Int> =
        snapshot.dishes.flatMap { d -> d.allergens.filter { it.layer in strictness.layers }.map { it.allergen }.distinct() }
            .groupingBy { it }.eachCount()

    fun tagCounts(snapshot: Snapshot, strictness: Strictness): Map<DietTag, Int> =
        snapshot.dishes.flatMap { d -> d.tagClaims.filter { it.layer in strictness.layers }.map { it.tag }.distinct() }
            .groupingBy { it }.eachCount()
}
