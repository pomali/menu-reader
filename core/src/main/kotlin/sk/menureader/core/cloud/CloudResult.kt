package sk.menureader.core.cloud

import kotlinx.serialization.Serializable
import sk.menureader.core.MenuJson
import sk.menureader.core.allergen.Enricher
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.TagClaim
import sk.menureader.core.model.AllergenClaim
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Extraction
import sk.menureader.core.model.Layer
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.Price
import sk.menureader.core.model.Quality
import sk.menureader.core.model.Section
import sk.menureader.core.model.Snapshot
import sk.menureader.core.parse.Text
import kotlin.math.roundToLong

/** What the model returns, matching [CloudPrompt.SCHEMA]. */
@Serializable
data class CloudMenu(
    val restaurantName: String = "",
    val currency: String = "",
    val legend: List<CloudLegend> = emptyList(),
    val sections: List<CloudSection> = emptyList(),
)

@Serializable data class CloudLegend(val code: Int, val allergen: Int)
@Serializable data class CloudSection(val name: String = "", val dishes: List<CloudDish> = emptyList())
@Serializable data class CloudPrice(val amount: Double, val variant: String = "")
@Serializable data class CloudMayContain(val allergen: Int, val reason: String = "")
@Serializable data class CloudFlag(val flag: String, val reason: String = "", val explicit: Boolean = false)

@Serializable
data class CloudDish(
    val name: String,
    val description: String = "",
    val portion: String = "",
    val prices: List<CloudPrice> = emptyList(),
    val printedCodes: List<Int> = emptyList(),
    val mayContain: List<CloudMayContain> = emptyList(),
    val ingredients: List<String> = emptyList(),
    val ingredientFlags: List<CloudFlag> = emptyList(),
    val vegetarian: Boolean = false,
    val vegan: Boolean = false,
)

/**
 * Turns a cloud answer into a FULL snapshot that replaces the basic one (PLAN §3.3).
 * Allergen layers A+B are recomputed locally with the same [Enricher] as offline (over the name,
 * description and the model's ingredient list); the model's "may contain" hints become layer C.
 * Ingredient flags (onion, pineapple…) become tag claims: LEXICAL when the menu says so, else
 * INFERRED. Vegetarian/vegan are only kept as a "may be" hint and never next to a meat claim. Prices that don't appear anywhere in the phone's OCR text
 * lower the dish confidence (likely misread or hallucinated).
 */
object CloudMapper {
    fun parse(json: String): CloudMenu = MenuJson.decodeFromString(CloudMenu.serializer(), stripFences(json))

    fun toSnapshot(
        menu: CloudMenu,
        basic: Snapshot,
        ocr: OcrDocument?,
        engine: String,
        model: String,
        enricher: Enricher = Enricher(),
    ): Snapshot {
        val currency = menu.currency.ifBlank { null } ?: basic.currency
        val legend = menu.legend.mapNotNull { l -> Allergen.fromCode(l.allergen)?.let { l.code to it } }.toMap().ifEmpty { null }
        val ocrPrices = ocr?.let { pricesIn(it) }
        var n = 0
        val sections = menu.sections.filter { it.dishes.isNotEmpty() }.mapIndexed { si, s ->
            Section(
                id = "s$si",
                name = s.name.trim(),
                dishes = s.dishes.filter { it.name.isNotBlank() }.map { d ->
                    val prices = d.prices.map { p -> Price((p.amount * 100).roundToLong(), currency, p.variant.ifBlank { null }) }
                    val unseen = ocrPrices != null && prices.any { it.amountMinor !in ocrPrices }
                    val dish = Dish(
                        id = "d${++n}",
                        name = d.name.trim(),
                        description = d.description.trim().ifBlank { null },
                        portion = d.portion.trim().ifBlank { null },
                        prices = prices,
                        printedCodes = d.printedCodes.filter { it in 1..20 }.distinct().sorted(),
                        ingredients = d.ingredients.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12),
                        confidence = if (unseen) 0.5 else 0.9,
                    )
                    val enriched = enricher.enrich(dish, legend)
                    // The AI's own reason beats a bare word match in its ingredient list (both are guesses).
                    val solid = enriched.allergens.filter { it.layer != Layer.INFERRED }.map { it.allergen }.toSet()
                    val inferred = d.mayContain.mapNotNull { m ->
                        val a = Allergen.fromCode(m.allergen) ?: return@mapNotNull null
                        if (a in solid) null else AllergenClaim(a, Layer.INFERRED, 0.4, quote(m.reason))
                    }.distinctBy { it.allergen }
                    val kept = enriched.allergens.filter { c -> inferred.none { it.allergen == c.allergen } }
                    enriched.copy(allergens = (kept + inferred).sortedBy { it.allergen.code }).withFlags(d)
                },
            )
        }
        return basic.copy(
            quality = Quality.FULL,
            sections = sections,
            currency = currency,
            allergenLegend = legend ?: basic.allergenLegend,
            warnings = emptyList(),
            extraction = Extraction(engine = engine, version = "1", model = model),
        )
    }

    private fun quote(reason: String) = "'${reason.trim().ifBlank { "AI" }}'"

    private val meats = setOf(DietTag.MEAT, DietTag.PORK, DietTag.BEEF, DietTag.POULTRY, DietTag.LAMB)

    private fun Dish.withFlags(d: CloudDish): Dish {
        val have = tagClaims.associateBy { it.tag }
        val fromAi = d.ingredientFlags.mapNotNull { f ->
            val tag = runCatching { DietTag.valueOf(f.flag.trim().uppercase()) }.getOrNull() ?: return@mapNotNull null
            val layer = if (f.explicit) Layer.LEXICAL else Layer.INFERRED
            val old = have[tag]
            if (old != null && old.layer <= layer) null else TagClaim(tag, layer, quote(f.reason))
        }.distinctBy { it.tag }
        var claims = tagClaims.filter { c -> fromAi.none { it.tag == c.tag } } + fromAi
        // Any specific meat also counts as "meat".
        if (claims.none { it.tag == DietTag.MEAT }) claims.filter { it.tag in meats }.minByOrNull { it.layer }?.let { claims = claims + it.copy(tag = DietTag.MEAT) }
        val hasMeat = claims.any { it.tag in meats }
        if (!hasMeat && d.vegan && claims.none { it.tag == DietTag.VEGAN }) claims = claims + TagClaim(DietTag.VEGAN, Layer.INFERRED, "'AI'")
        if (!hasMeat && (d.vegetarian || d.vegan) && claims.none { it.tag == DietTag.VEGETARIAN }) claims = claims + TagClaim(DietTag.VEGETARIAN, Layer.INFERRED, "'AI'")
        if (hasMeat) claims = claims.filter { it.tag != DietTag.VEGETARIAN && it.tag != DietTag.VEGAN }
        return copy(tagClaims = claims, tags = claims.map { it.tag }.toSet())
    }

    /** All price-like amounts in the OCR text, in minor units (8,90 -> 890; 189,- -> 18900). */
    fun pricesIn(ocr: OcrDocument): Set<Long> {
        val text = ocr.pages.flatMap { it.lines }.joinToString("\n") { Text.clean(it.text) }
        val re = Regex("(?<![\\d])(\\d{1,4})(?:[.,](\\d{2}))?(?![\\d])")
        return re.findAll(text).map { m -> m.groupValues[1].toLong() * 100 + (m.groupValues[2].ifEmpty { "0" }.toLong()) }.toSet()
    }

    private fun stripFences(s: String): String =
        s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
