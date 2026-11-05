package sk.menureader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2
    val centerX get() = (left + right) / 2

    fun union(o: BBox) = BBox(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

@Serializable
data class SourceBox(val pageId: String, val bbox: BBox)

@Serializable
data class Geo(val lat: Double, val lon: Double, val accuracy: Float? = null)

/** A price in minor units (cents / haléře) to avoid floating point. */
@Serializable
data class Price(val amountMinor: Long, val currency: String?, val variant: String? = null) {
    fun format(): String {
        val major = amountMinor / 100
        val minor = amountMinor % 100
        val num = if (minor == 0L && currency == "CZK") "$major" else "$major,${minor.toString().padStart(2, '0')}"
        return when (currency) {
            null -> num
            "EUR" -> "$num €"
            "CZK" -> "$num Kč"
            else -> "$num $currency"
        }
    }
}

@Serializable
data class AllergenClaim(
    val allergen: Allergen,
    val layer: Layer,
    val confidence: Double,
    /** Human-readable evidence, e.g. "code 7" or "'smotana'". */
    val evidence: String,
)

/** A dietary tag / avoided ingredient found in the dish text, with the word that matched. */
@Serializable
data class TagClaim(val tag: DietTag, val layer: Layer, val evidence: String)

@Serializable
data class Dish(
    val id: String,
    val name: String,
    val description: String? = null,
    val portion: String? = null,
    val prices: List<Price> = emptyList(),
    /** Raw allergen numbers as printed on the menu, before mapping. */
    val printedCodes: List<Int> = emptyList(),
    val allergens: List<AllergenClaim> = emptyList(),
    /** All tags from [tagClaims], any layer. */
    val tags: Set<DietTag> = emptySet(),
    val tagClaims: List<TagClaim> = emptyList(),
    val sourceBox: SourceBox? = null,
    /** Parser confidence that this really is a dish, 0..1. */
    val confidence: Double = 1.0,
)

@Serializable
data class Section(val id: String, val name: String, val dishes: List<Dish>)

@Serializable
data class Page(val id: String, val imageRef: String, val width: Int, val height: Int)

@Serializable
enum class Quality { BASIC, FULL }

@Serializable
data class Extraction(val engine: String, val version: String, val model: String? = null)

@Serializable
data class Snapshot(
    val id: String,
    val capturedAt: Long,
    val quality: Quality,
    val sections: List<Section>,
    val pages: List<Page> = emptyList(),
    val restaurantId: String? = null,
    val capturedGeo: Geo? = null,
    val language: String? = null,
    val currency: String? = null,
    /** Legend printed on the menu, code -> allergen. Null if the menu has no legend. */
    val allergenLegend: Map<Int, Allergen>? = null,
    /** Parser notes, e.g. legend numbering that differs from the EU standard. */
    val warnings: List<String> = emptyList(),
    val extraction: Extraction,
) {
    val dishes get() = sections.flatMap { it.dishes }
}
