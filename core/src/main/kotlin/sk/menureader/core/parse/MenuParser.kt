package sk.menureader.core.parse

import sk.menureader.core.model.BBox
import sk.menureader.core.model.Dish
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.OcrLine
import sk.menureader.core.model.OcrPage
import sk.menureader.core.model.Price
import sk.menureader.core.model.Section
import sk.menureader.core.model.SourceBox
import sk.menureader.core.model.Allergen

data class ParseResult(
    val sections: List<Section>,
    val legend: Map<Int, Allergen>?,
    val warnings: List<String>,
    /** Rows the parser could not place, for the debug screen. */
    val unassigned: List<String>,
)

/** A logical row: one or more OCR lines that belong together left-to-right. */
internal data class Segment(val pageId: String, val text: String, val box: BBox, val lineHeight: Int)

/**
 * Rule-based menu parser for SK/CZ/EN menus (PLAN §3.2). Produces dishes without allergen
 * claims; [sk.menureader.core.allergen.Enricher] adds those.
 */
class MenuParser {

    fun parse(doc: OcrDocument): ParseResult {
        val segments = doc.pages.flatMap { orderedSegments(it) }
        return classify(segments)
    }

    // ---- Step 1: rows and reading order -------------------------------------------------------

    internal fun orderedSegments(page: OcrPage): List<Segment> {
        val rows = mergeRows(page.lines.filter { it.text.isNotBlank() })
        val segs = rows.flatMap { splitRow(page.pageId, it) }
        return readingOrder(segs, page.width)
    }

    /** Group lines whose vertical centres line up into rows (name on the left, price on the right). */
    private fun mergeRows(lines: List<OcrLine>): List<List<OcrLine>> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (l in lines.sortedBy { it.box.centerY }) {
            val row = rows.lastOrNull { r ->
                val ref = r.first().box
                val tol = minOf(ref.height, l.box.height) / 2
                kotlin.math.abs(ref.centerY - l.box.centerY) <= tol &&
                    r.none { overlapsX(it.box, l.box) }
            }
            if (row != null) row += l else rows += mutableListOf(l)
        }
        return rows.map { r -> r.sortedBy { it.box.left } }
    }

    private fun overlapsX(a: BBox, b: BBox): Boolean {
        val ov = minOf(a.right, b.right) - maxOf(a.left, b.left)
        return ov > minOf(a.width, b.width) / 4
    }

    /** A row ends after a price, so two columns on the same baseline become two segments. */
    private fun splitRow(pageId: String, row: List<OcrLine>): List<Segment> {
        val out = mutableListOf<Segment>()
        var acc = mutableListOf<OcrLine>()
        fun flush() {
            if (acc.isEmpty()) return
            val box = acc.map { it.box }.reduce(BBox::union)
            out += Segment(pageId, acc.joinToString(" ") { it.text }, box, acc.maxOf { it.box.height })
            acc = mutableListOf()
        }
        for (l in row) {
            acc += l
            val toks = Tokenizer.tokenize(Text.clean(l.text))
            val last = toks.lastOrNull()
            if (last is Token.PriceTok && last.range.last >= Text.clean(l.text).trimEnd().length - 1) flush()
        }
        flush()
        return out
    }

    /**
     * Detect columns from vertical gaps that no narrow segment crosses. Wide segments (titles,
     * full-width rows) split the page into bands; inside a band, read column by column.
     */
    private fun readingOrder(segs: List<Segment>, pageWidth: Int): List<Segment> {
        if (segs.isEmpty() || pageWidth <= 0) return segs
        val buckets = 100
        fun b(x: Int) = (x.toLong() * buckets / pageWidth).toInt().coerceIn(0, buckets - 1)
        val narrow = segs.filter { it.box.width < pageWidth * 0.6 }
        val covered = BooleanArray(buckets)
        narrow.forEach { s -> for (i in b(s.box.left)..b(s.box.right)) covered[i] = true }
        val first = covered.indexOfFirst { it }
        val last = covered.indexOfLast { it }
        val splits = mutableListOf<Int>()
        if (first >= 0) {
            var i = first
            while (i <= last) {
                if (!covered[i]) {
                    val start = i
                    while (i <= last && !covered[i]) i++
                    if (i - start >= 2) splits += ((start + i) / 2) * pageWidth / buckets
                } else i++
            }
        }
        if (splits.isEmpty()) return segs.sortedBy { it.box.top }

        fun column(s: Segment) = splits.count { s.box.centerX > it }
        fun spans(s: Segment) = splits.any { s.box.left < it && s.box.right > it }

        val out = mutableListOf<Segment>()
        val band = mutableListOf<Segment>()
        fun flushBand() {
            out += band.sortedWith(compareBy({ column(it) }, { it.box.top }))
            band.clear()
        }
        for (s in segs.sortedBy { it.box.top }) {
            if (spans(s)) { flushBand(); out += s } else band += s
        }
        flushBand()
        return out
    }

    // ---- Steps 2-5: tokens, classification, legend ------------------------------------------

    private class Draft(
        var name: String,
        val prices: MutableList<Price>,
        val codes: MutableSet<Int>,
        var portion: String?,
        var box: BBox,
        val pageId: String,
        val description: MutableList<String> = mutableListOf(),
        var explicitCurrency: Boolean,
    )

    private class SectionDraft(val name: String, val dishes: MutableList<Draft> = mutableListOf())

    private fun classify(segs: List<Segment>): ParseResult {
        val dishHeights = segs.filter { s -> Tokenizer.tokenize(Text.clean(s.text)).any { it is Token.PriceTok } }
            .map { it.lineHeight }.sorted()
        val medianH = dishHeights.getOrNull(dishHeights.size / 2) ?: segs.map { it.lineHeight }.sorted().getOrNull(segs.size / 2) ?: 0

        val sections = mutableListOf(SectionDraft(""))
        val legend = LinkedHashMap<Int, Allergen>()
        val unassigned = mutableListOf<String>()
        val pending = mutableListOf<Segment>()
        var lastDish: Draft? = null

        fun flushPendingAsDescription() {
            if (pending.isEmpty()) return
            val d = lastDish
            if (d != null) {
                d.description += pending.map { Text.clean(it.text) }
            } else unassigned += pending.map { it.text }
            pending.clear()
        }

        for (s in segs) {
            val text = Text.clean(s.text)
            val toks = Tokenizer.tokenize(text)
            val prices = toks.filterIsInstance<Token.PriceTok>()
            val codes = toks.filterIsInstance<Token.Codes>()
            val name = Tokenizer.strip(text, toks).trimStart('•', '-', '*', '·', '–').trim()

            if (prices.isEmpty() && codes.isEmpty()) {
                Legend.parseLine(text)?.let { entries ->
                    flushPendingAsDescription()
                    legend.putAll(entries)
                    continue
                }
                if (isHeader(text, s.lineHeight, medianH)) {
                    flushPendingAsDescription()
                    sections += SectionDraft(text.trimEnd(':').trim())
                    lastDish = null
                    continue
                }
                pending += s
                continue
            }

            var dishName = name
            var box = s.box
            if (dishName.count { it.isLetter() } < 2) {
                if (pending.isNotEmpty()) {
                    dishName = pending.joinToString(" ") { Text.clean(it.text) }
                    box = pending.map { it.box }.fold(box, BBox::union)
                    pending.clear()
                } else {
                    val d = lastDish
                    if (d != null && (d.prices.isEmpty() || prices.isEmpty())) {
                        d.prices += variants(text, toks)
                        codes.forEach { d.codes += it.codes }
                        d.box = d.box.union(s.box)
                    } else unassigned += s.text
                    continue
                }
            } else {
                flushPendingAsDescription()
            }

            val portions = toks.filterIsInstance<Token.Portion>()
            val ps = variants(text, toks)
            val draft = Draft(
                name = dishName,
                prices = ps.toMutableList(),
                codes = codes.flatMap { it.codes }.toMutableSet(),
                portion = if (ps.size <= 1 && portions.isNotEmpty()) portions.joinToString(", ") { it.text } else null,
                box = box,
                pageId = s.pageId,
                explicitCurrency = prices.any { it.explicitCurrency },
            )
            sections.last().dishes += draft
            lastDish = draft
        }
        flushPendingAsDescription()

        var n = 0
        val out = sections.filter { it.dishes.isNotEmpty() }.mapIndexed { si, sec ->
            Section(
                id = "s$si",
                name = sec.name,
                dishes = sec.dishes.map { d ->
                    Dish(
                        id = "d${++n}",
                        name = d.name,
                        description = d.description.joinToString(" ").ifBlank { null },
                        portion = d.portion,
                        prices = d.prices,
                        printedCodes = d.codes.sorted(),
                        sourceBox = SourceBox(d.pageId, d.box),
                        confidence = confidence(d),
                    )
                },
            )
        }
        val legendOrNull = legend.ifEmpty { null }
        return ParseResult(out, legendOrNull, legendOrNull?.let(Legend::mismatches).orEmpty(), unassigned)
    }

    /** Prices with the portion printed before each one as its variant ("0,3 l 1,50 € / 0,5 l 2,20 €"). */
    private fun variants(text: String, toks: List<Token>): List<Price> {
        val prices = toks.filterIsInstance<Token.PriceTok>()
        if (prices.size <= 1) return prices.map { it.price }
        var lastPortion: String? = null
        return toks.mapNotNull { t ->
            when (t) {
                is Token.Portion -> { lastPortion = t.text; null }
                is Token.PriceTok -> t.price.copy(variant = lastPortion).also { lastPortion = null }
                else -> null
            }
        }
    }

    private fun confidence(d: Draft): Double {
        var c = 0.5
        if (d.prices.isNotEmpty()) c += if (d.explicitCurrency) 0.25 else 0.15
        if (d.codes.isNotEmpty()) c += 0.15
        val letters = d.name.count { it.isLetter() }
        if (letters < 3 || d.name.length > 90) c -= 0.25
        if (d.name.count { it.isDigit() } > letters) c -= 0.2
        return c.coerceIn(0.05, 0.95)
    }

    private val sectionWords = listOf(
        "polievk", "polevk", "predjedl", "predkrm", "hlavne jedl", "hlavni jidl", "dezert", "zakusk",
        "napoj", "priloh", "salat", "soup", "starter", "appetizer", "main", "dessert", "drink", "side",
        "salad", "denne menu", "denni menu", "obedove menu", "obedove", "poledni menu", "pizza",
        "cestovin", "testovin", "pasta", "burger", "specialit", "minutk", "jedla na objednavku",
        "jidla na objednavku", "teple", "studene", "pivo", "piva", "vino", "vina", "alkohol", "nealko",
        "kava", "caj", "coffee", "tea", "beer", "wine", "ryby", "fish", "steak", "detsk", "vegetarian",
        "bezmas", "omacky", "sauces", "ranajk", "snidan", "breakfast", "menu", "jedla", "jidla",
    )

    private fun isHeader(text: String, height: Int, medianH: Int): Boolean {
        val words = text.split(' ').filter { it.isNotBlank() }
        val letters = text.filter { it.isLetter() }
        if (letters.length < 3 || words.size > 6) return false
        if (medianH > 0 && height >= medianH * 1.3) return true
        if (letters.count { it.isUpperCase() } >= letters.length * 0.8 && letters.length >= 4) return true
        val f = Text.fold(text).trimEnd(':', ' ', '.')
        if (text.trimEnd().endsWith(':') && words.size <= 4) return true
        return words.size <= 4 && sectionWords.any { f.startsWith(it) }
    }
}
