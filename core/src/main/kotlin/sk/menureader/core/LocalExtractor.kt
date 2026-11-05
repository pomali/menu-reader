package sk.menureader.core

import sk.menureader.core.allergen.Enricher
import sk.menureader.core.model.Extraction
import sk.menureader.core.model.Geo
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.Page
import sk.menureader.core.model.Quality
import sk.menureader.core.model.Snapshot
import sk.menureader.core.parse.MenuParser

/** Offline extractor: OCR -> rule parser -> allergen layers A+B. Produces a BASIC snapshot. */
class LocalExtractor(
    private val parser: MenuParser = MenuParser(),
    private val enricher: Enricher = Enricher(),
) {
    fun extract(
        id: String,
        ocr: OcrDocument,
        pages: List<Page>,
        capturedAt: Long,
        geo: Geo? = null,
        restaurantId: String? = null,
    ): Snapshot {
        val parsed = parser.parse(ocr)
        val sections = parsed.sections.map { s -> s.copy(dishes = s.dishes.map { enricher.enrich(it, parsed.legend) }) }
        val currency = sections.flatMap { it.dishes }.flatMap { it.prices }.mapNotNull { it.currency }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val withCurrency = if (currency == null) sections else sections.map { s ->
            s.copy(dishes = s.dishes.map { d -> d.copy(prices = d.prices.map { p -> if (p.currency == null) p.copy(currency = currency) else p }) })
        }
        return Snapshot(
            id = id,
            capturedAt = capturedAt,
            quality = Quality.BASIC,
            sections = withCurrency,
            pages = pages,
            restaurantId = restaurantId,
            capturedGeo = geo,
            currency = currency,
            allergenLegend = parsed.legend,
            warnings = parsed.warnings,
            extraction = Extraction(engine = "local-rules", version = VERSION),
        )
    }

    companion object {
        const val VERSION = "0.1"
    }
}
