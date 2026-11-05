package sk.menureader.core.cloud

import kotlinx.serialization.Serializable
import sk.menureader.core.MenuJson
import sk.menureader.core.allergen.Enricher
import sk.menureader.core.model.Allergen
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

@Serializable
data class CloudDish(
    val name: String,
    val description: String = "",
    val portion: String = "",
    val prices: List<CloudPrice> = emptyList(),
    val printedCodes: List<Int> = emptyList(),
    val mayContain: List<CloudMayContain> = emptyList(),
)

/**
 * Turns a cloud answer into a FULL snapshot that replaces the basic one (PLAN §3.3).
 * Allergen layers A+B are recomputed locally with the same [Enricher] as offline; the model's
 * "may contain" hints become layer C. Prices that don't appear anywhere in the phone's OCR text
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
                        confidence = if (unseen) 0.5 else 0.9,
                    )
                    val enriched = enricher.enrich(dish, legend)
                    val have = enriched.allergens.map { it.allergen }.toSet()
                    val inferred = d.mayContain.mapNotNull { m ->
                        val a = Allergen.fromCode(m.allergen) ?: return@mapNotNull null
                        if (a in have) null else AllergenClaim(a, Layer.INFERRED, 0.4, "'${m.reason.trim().ifBlank { "AI" }}'")
                    }.distinctBy { it.allergen }
                    enriched.copy(allergens = enriched.allergens + inferred)
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

    /** All price-like amounts in the OCR text, in minor units (8,90 -> 890; 189,- -> 18900). */
    fun pricesIn(ocr: OcrDocument): Set<Long> {
        val text = ocr.pages.flatMap { it.lines }.joinToString("\n") { Text.clean(it.text) }
        val re = Regex("(?<![\\d])(\\d{1,4})(?:[.,](\\d{2}))?(?![\\d])")
        return re.findAll(text).map { m -> m.groupValues[1].toLong() * 100 + (m.groupValues[2].ifEmpty { "0" }.toLong()) }.toSet()
    }

    private fun stripFences(s: String): String =
        s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
