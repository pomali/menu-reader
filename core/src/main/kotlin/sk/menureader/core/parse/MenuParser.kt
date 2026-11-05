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
internal data class Segment(val pageId: String, val text: String, val box: BBox, val lineHeight: Int, val row: Int = 0)

/**
 * Rule-based menu parser for SK/CZ/EN menus (PLAN §3.2). Produces dishes without allergen
 * claims; [sk.menureader.core.allergen.Enricher] adds those.
 */
class MenuParser {

    fun parse(doc: OcrDocument): ParseResult {
        val segments = doc.pages.flatMap { orderedSegments(it) }
        return classify(segments, plainText = doc.source == OcrDocument.SOURCE_TEXT)
    }

    // ---- Step 1: rows and reading order -------------------------------------------------------

    internal fun orderedSegments(page: OcrPage): List<Segment> {
        val rows = mergeRows(collapseBlocks(page).filter { it.text.isNotBlank() })
        val segs = rows.flatMapIndexed { i, r -> splitRow(page.pageId, r, i) }
        return readingOrder(segs, page.width)
    }

    /**
     * A narrow block of 2-5 lines with no price (a soup name with its codes next to the dishes, a
     * "02.11.2026 / PONDELOK" label) is one piece of text. Reading it line by line would interleave
     * it with the dish rows beside it.
     */
    private fun collapseBlocks(page: OcrPage): List<OcrLine> {
        fun priced(l: OcrLine) = Tokenizer.tokenize(Text.clean(l.text)).any { it is Token.PriceTok }
        // Lines that are just a price ("7,90 €"): a block whose lines each have one beside them is a list of dishes.
        val priceOnly = page.lines.filter { priced(it) && !hasWords(it.text) }
        fun sameRow(a: OcrLine, b: OcrLine) =
            minOf(a.box.bottom, b.box.bottom) - maxOf(a.box.top, b.box.top) >= minOf(a.box.height, b.box.height) / 2
        val all = page.lines
        // The next thing to the right on the same row is a bare price (nothing with words in between).
        fun hasPriceBeside(l: OcrLine) = priceOnly.any { p ->
            p.box.left >= l.box.right && sameRow(p, l) &&
                all.none { w -> w !== l && w.box.left >= l.box.right && w.box.left < p.box.left && sameRow(w, l) && hasWords(w.text) }
        }
        return page.blocks.flatMap { b ->
            val collapse = b.lines.size in 2..5 && b.box.width < page.width * 0.25 &&
                b.lines.none(::priced) && b.lines.count(::hasPriceBeside) * 2 < b.lines.size
            if (collapse) listOf(OcrLine(b.lines.joinToString(" ") { it.text }, b.box)) else b.lines
        }
    }

    /** Group lines whose vertical centres line up into rows (name on the left, price on the right). */
    private fun mergeRows(lines: List<OcrLine>): List<List<OcrLine>> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (l in lines.sortedBy { it.box.centerY }) {
            val row = rows.lastOrNull { r ->
                val ref = r.first().box
                val overlapY = minOf(ref.bottom, l.box.bottom) - maxOf(ref.top, l.box.top)
                overlapY >= minOf(ref.height, l.box.height) * 0.5 && r.none { overlapsX(it.box, l.box) }
            }
            if (row != null) row += l else rows += mutableListOf(l)
        }
        return rows.map { r -> r.sortedBy { it.box.left } }
    }

    private fun overlapsX(a: BBox, b: BBox): Boolean {
        val ov = minOf(a.right, b.right) - maxOf(a.left, b.left)
        return ov > minOf(a.width, b.width) / 4
    }

    /**
     * Split a row into logical segments: after a price (two columns on one baseline), and at wide
     * gaps between two pieces of text (a weekday label next to the dishes). A piece without letters
     * (just a price, codes or a portion) always stays with its neighbour.
     */
    private fun splitRow(pageId: String, row: List<OcrLine>, rowIndex: Int): List<Segment> {
        val out = mutableListOf<Segment>()
        var acc = mutableListOf<OcrLine>()
        fun flush() {
            if (acc.isEmpty()) return
            val box = acc.map { it.box }.reduce(BBox::union)
            out += Segment(pageId, acc.joinToString(" ") { it.text }, box, acc.minOf { it.box.height }, rowIndex)
            acc = mutableListOf()
        }
        for (l in row) {
            val prev = acc.lastOrNull()
            if (prev != null) {
                val gap = l.box.left - prev.box.right
                if (gap > minOf(prev.box.height, l.box.height) * 0.8 && hasWords(acc.joinToString(" ") { it.text }) && hasWords(l.text)) flush()
            }
            acc += l
            val clean = Text.clean(l.text)
            val last = Tokenizer.tokenize(clean).lastOrNull()
            if (last is Token.PriceTok && last.range.last >= clean.trimEnd().length - 1) flush()
        }
        flush()
        return out
    }

    private fun hasWords(text: String): Boolean {
        val clean = Text.clean(text)
        return Tokenizer.strip(clean, Tokenizer.tokenize(clean)).count { it.isLetter() } >= 2
    }

    /**
     * Detect columns from vertical gaps that no narrow segment crosses. A gap only counts as a
     * column break if the text just left of it ends in prices: that's a menu column, not a weekday
     * label or a name/price gap. Wide segments (titles, full-width rows) split the page into bands;
     * inside a band, read column by column, row by row.
     */
    private fun readingOrder(segs: List<Segment>, pageWidth: Int): List<Segment> {
        val byRow = compareBy<Segment>({ it.row }, { it.box.left })
        if (segs.isEmpty() || pageWidth <= 0) return segs.sortedWith(byRow)
        val buckets = 100
        fun b(x: Int) = (x.toLong() * buckets / pageWidth).toInt().coerceIn(0, buckets - 1)
        val narrow = segs.filter { it.box.width < pageWidth * 0.6 }
        // A bucket is "covered" when several segments cross it, so one title over the gap doesn't hide a column break.
        val hits = IntArray(buckets)
        narrow.forEach { s -> for (i in b(s.box.left)..b(s.box.right)) hits[i]++ }
        val typical = hits.filter { it > 0 }.sorted().let { it.getOrNull(it.size / 2) ?: 0 }
        val covered = BooleanArray(buckets) { hits[it] > maxOf(1, typical / 4) }
        val first = covered.indexOfFirst { it }
        val last = covered.indexOfLast { it }
        val splits = mutableListOf<Int>()
        if (first >= 0) {
            var i = first
            while (i <= last) {
                if (!covered[i]) {
                    val start = i
                    while (i <= last && !covered[i]) i++
                    val x = ((start + i) / 2) * pageWidth / buckets
                    if (i - start >= 2 && endsInPrices(narrow, start * pageWidth / buckets, pageWidth)) splits += x
                } else i++
            }
        }
        if (splits.isEmpty()) return segs.sortedWith(byRow)

        fun column(s: Segment) = splits.count { s.box.centerX > it }
        fun spans(s: Segment) = splits.any { s.box.left < it && s.box.right > it }

        val out = mutableListOf<Segment>()
        val band = mutableListOf<Segment>()
        fun flushBand() {
            out += band.sortedWith(compareBy<Segment>({ column(it) }).then(byRow))
            band.clear()
        }
        for (s in segs.sortedWith(byRow)) {
            if (spans(s)) { flushBand(); out += s } else band += s
        }
        flushBand()
        return out
    }

    /** Do most segments whose right edge lies just left of [gapLeft] contain a price? */
    private fun endsInPrices(narrow: List<Segment>, gapLeft: Int, pageWidth: Int): Boolean {
        val near = narrow.filter { it.box.right in (gapLeft - pageWidth / 25)..(gapLeft + pageWidth / 50) }
        if (near.size < 2) return false
        val priced = near.count { s -> Tokenizer.tokenize(Text.clean(s.text)).any { it is Token.PriceTok } }
        return priced * 2 >= near.size
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
        val height: Int,
    )

    private class SectionDraft(val name: String, val dishes: MutableList<Draft> = mutableListOf())

    private fun classify(segs: List<Segment>, plainText: Boolean = false): ParseResult {
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

        var day: String? = null
        fun startSection(name: String, isDay: Boolean = false) {
            flushPendingAsDescription()
            if (isDay) day = name
            // Weekly menus: "Štvrtok 10.7.2025" then "Polievky" -> "Štvrtok 10.7.2025 · Polievky".
            val full = if (!isDay && day != null) "$day · $name" else name
            if (sections.last().dishes.isEmpty() && sections.last().name == day && !isDay) sections.removeAt(sections.lastIndex)
            sections += SectionDraft(full)
            lastDish = null
        }

        for (s in segs) {
            var text = Text.clean(s.text)

            // "Pondelok /26.10.2026 ..... Polievka: ..." -> section "Pondelok 26.10.2026" + the rest.
            Weekday.split(text)?.let { (dayLabel, rest) ->
                startSection(dayLabel, isDay = true)
                text = rest
            }
            if (text.isBlank()) continue

            val toks = Tokenizer.tokenize(text)
            val prices = toks.filterIsInstance<Token.PriceTok>()
            val codes = toks.filterIsInstance<Token.Codes>()
            val marked = toks.any { it is Token.ItemNumber } || dishMarker.containsMatchIn(Text.fold(text))
            val name = Tokenizer.strip(text, toks).trimStart('•', '-', '*', '·', '–').trim()

            if (prices.isEmpty() && codes.isEmpty()) {
                Legend.parseLine(text)?.let { entries ->
                    flushPendingAsDescription()
                    legend.putAll(entries)
                    continue
                }
                if (legendHeader.containsMatchIn(Text.fold(text))) { flushPendingAsDescription(); continue }
                // Text right below a dish is its description, even in capitals, unless it names a section.
                val belowDish = lastDish?.let { s.lineHeight < it.height * 0.9 || isUpper(it.name) } == true
                val header = if (marked) Header.NO else headerKind(text, s.lineHeight, medianH)
                if (header == Header.STRONG || (header == Header.WEAK && !belowDish)) {
                    startSection(text.trimEnd(':').trim())
                    continue
                }
                if (!marked || name.count { it.isLetter() } < 3) {
                    pending += s.copy(text = text)
                    continue
                }
                // A numbered/marked line without price: a dish whose price is elsewhere (daily menus).
            }

            val d0 = lastDish
            // Continuation of the previous dish: "(bez polievky 7,90€ / s polievkou 8,90€)",
            // or an uppercase description line that carries the codes ("... /1,3,7/").
            if (d0 != null && !marked && name.count { it.isLetter() } >= 2 &&
                ((name.startsWith("(") || name.first().isLowerCase()) ||
                    (prices.isEmpty() && d0.codes.isEmpty() &&
                        (s.lineHeight <= d0.height * 0.95 || pending.isNotEmpty() || isUpper(d0.name))))
            ) {
                d0.description += pending.map { Text.clean(it.text) } + name
                pending.clear()
                codes.forEach { d0.codes += it.codes }
                if (d0.prices.isEmpty()) d0.prices += variants(text, toks)
                d0.box = d0.box.union(s.box)
                continue
            }

            var dishName = name
            var box = s.box
            var extraDescription = emptyList<String>()
            if (dishName.count { it.isLetter() } < 2) {
                val d1 = lastDish
                if (pending.isNotEmpty() && prices.isEmpty() && d1 != null && d1.codes.isEmpty() && d1.prices.isNotEmpty()) {
                    // "NAME 3,50" / "description…" / "/1,3,7/": the codes close the previous dish's description.
                    d1.description += pending.map { Text.clean(it.text) }
                    pending.clear()
                    codes.forEach { d1.codes += it.codes }
                    continue
                }
                if (pending.isNotEmpty()) {
                    // Shared text (Wolt, a web page) is usually "name / description / price", one per line.
                    // From photos, several lines before a price are more often one name wrapped over lines.
                    val lines = pending.map { Text.clean(it.text) }
                    dishName = if (plainText) lines.first() else lines.joinToString(" ")
                    if (plainText) extraDescription = lines.drop(1)
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
                height = s.lineHeight,
            )
            draft.description += extraDescription
            sections.last().dishes += draft
            lastDish = draft
        }
        flushPendingAsDescription()

        // Very unlikely dishes (dates, opening hours with a stray number) go to the debug list instead.
        for (sec in sections) sec.dishes.removeAll { d -> (confidence(d) < 0.15).also { if (it) unassigned += d.name } }

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
        if (d.name.split(Regex("[^\\p{L}]+")).none { it.length >= 4 }) c -= 0.3
        if (d.name.count { it.isDigit() } > letters) c -= 0.2
        return c.coerceIn(0.05, 0.95)
    }

    /** Lines that start a dish even without a price: "Polievka A:", "Polévka:", "Soup:". */
    private val dishMarker = Regex("^\\s*(polievka|polevka|soup)\\b\\s*[a-z0-9]?\\s*[:.-]")
    private val legendHeader = Regex("^\\s*(zoznam |seznam )?(alergen|allergen)\\w*\\s*:?\\s*$")

    private val sectionWords = listOf(
        "polievk", "polevk", "predjedl", "predkrm", "hlavne jedl", "hlavni jidl", "dezert", "zakusk",
        "napoj", "priloh", "salat", "soup", "starter", "appetizer", "main", "dessert", "drink", "side",
        "salad", "denne menu", "denni menu", "obedove menu", "obedove", "poledni menu", "pizza",
        "cestovin", "testovin", "pasta", "burger", "specialit", "minutk", "jedla na objednavku",
        "jidla na objednavku", "teple", "studene", "pivo", "piva", "vino", "vina", "alkohol", "nealko",
        "kava", "caj", "coffee", "tea", "beer", "wine", "ryby", "fish", "steak", "detsk", "vegetarian",
        "bezmas", "omacky", "sauces", "ranajk", "snidan", "breakfast", "menu", "jedla", "jidla",
    )

    private enum class Header { NO, WEAK, STRONG }

    private fun isUpper(t: String): Boolean {
        val letters = t.filter { it.isLetter() }
        return letters.length >= 4 && letters.count { it.isUpperCase() } >= letters.length * 0.8
    }

    private val time = Regex("\\d{1,2}[:.]\\d{2}\\s*(-|–|do|hod)")

    /** STRONG: a known section word or "Something:". WEAK: only looks like one (capitals, big font). */
    private fun headerKind(text: String, height: Int, medianH: Int): Header {
        val words = text.split(' ').filter { it.isNotBlank() }
        val letters = text.filter { it.isLetter() }
        if (letters.length < 3 || words.size > 6 || time.containsMatchIn(text)) return Header.NO
        val f = Text.fold(text).trimEnd(':', ' ', '.')
        if (text.trimEnd().endsWith(':') && words.size <= 4) return Header.STRONG
        if (words.size <= 4 && sectionWords.any { w -> f.startsWith(w) || (words.size <= 3 && f.contains(" $w")) }) return Header.STRONG
        if (medianH > 0 && height >= medianH * 1.3) return Header.WEAK
        if (isUpper(text)) return Header.WEAK
        return Header.NO
    }
}
