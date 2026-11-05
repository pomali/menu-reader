package sk.menureader.core.parse

import sk.menureader.core.model.Price

/** A token found inside a menu row. [range] is relative to the cleaned row text. */
sealed interface Token {
    val range: IntRange

    data class PriceTok(override val range: IntRange, val price: Price, val explicitCurrency: Boolean) : Token
    data class Codes(override val range: IntRange, val codes: List<Int>, val prefixed: Boolean) : Token
    data class Portion(override val range: IntRange, val text: String) : Token
    data class ItemNumber(override val range: IntRange) : Token
}

/**
 * Regex tokenizer for SK/CZ/EN menu rows (PLAN §3.2 step 2).
 *
 * Order matters: prefixed allergen lists ("A: 1,3,7") and parenthesised lists win over prices,
 * so "(1,10)" is read as codes and not as a price of 1.10.
 */
object Tokenizer {
    private const val CODE = "(?:1[0-4]|0?[1-9])"
    /** Inside parentheses/slashes/after "A:" we also accept 15-20: some menus add their own (e.g. 15 = honey). */
    private const val CODE_X = "(?:1[0-9]|20|0?[1-9])"
    private const val CODE_LIST = "$CODE_X(?:\\s*[,.;]\\s*$CODE_X)*"

    private val prefixedCodes = Regex(
        "(?i)\\(?\\b(?:(?:alerg[eé]ny|alerg[eé]n|allergens?|alerg\\.?)\\s*:?|(?:al|a)\\s*[:.]|a(?=\\d))\\s*($CODE_LIST)\\b\\)?"
    )
    private val parenCodes = Regex("\\(\\s*($CODE_LIST)\\s*[,.]?\\s*\\)")
    /** "/1,3,7/" or a trailing "/1,3,7" (common on SK menus). Not "1/2" or "200g/200g". */
    private val slashCodes = Regex("(?<![\\d/])/\\s*($CODE_LIST)\\s*[,.]?\\s*/?(?![\\d\\p{L}])")
    /** "1,3,7", also "1.3,7" (OCR reads a comma as a dot); at least one comma, so dates like "2.11." don't match. */
    private val bareCodes = Regex("(?<![\\d,.])($CODE(?:\\s*[,.]\\s*$CODE)+)(?![\\d]|\\s*(?:€|eur|kč|kc|czk|,-|g\\b|kg|ml|l\\b|dl|ks))", RegexOption.IGNORE_CASE)

    private const val CUR = "€|eur|euro|kč|kc|czk|,-|\\.-"
    private val priceAfter = Regex("(?i)(?<![\\d,.])(\\d{1,4}(?:[ .]\\d{3})?)(?:[.,](\\d{2}|-{1,2}))?\\s?($CUR)(?![a-z])")
    private val priceBefore = Regex("(?i)(€|eur|czk)\\s?(\\d{1,4})(?:[.,](\\d{2}))?(?![\\d,])")
    /** Bare "8,90" at the end of a row (currency printed elsewhere), or OCR misreading "€" as C/c/e. */
    private val priceBareEnd = Regex("(?<![\\d,.])(\\d{1,3})[.,](\\d{2})\\s*[Cce]?\\s*$")

    private const val UNIT = "(?:gr|g|kg|ml|cl|dl|l|ks|pcs|pc)"
    /** "150 g", "0,33l", "2ks", and meat/side pairs like "200g/200g" or "400g/50g". */
    private val portion = Regex("(?i)(?<![\\d,.])(?:\\d+(?:[.,]\\d+)?\\s?$UNIT?\\s?/\\s?)?\\d+(?:[.,]\\d+)?\\s?$UNIT\\b\\.?")
    /** "1. ", "1.Kelová", "12) ", "M1: ", "M1-120g", "Menu 2 ". */
    private val itemNumber = Regex("^\\s*(?:(?:menu\\s*|m)\\d{1,2}\\s*[.):\\-–]?|\\d{1,3}\\s*[.)])\\s*(?=\\S)", RegexOption.IGNORE_CASE)

    fun tokenize(text: String): List<Token> {
        val taken = BooleanArray(text.length)
        val out = mutableListOf<Token>()

        fun free(r: IntRange) = r.all { !taken[it] }
        fun take(r: IntRange) { for (i in r) taken[i] = true }
        fun add(t: Token) { take(t.range); out += t }

        itemNumber.find(text)?.let { add(Token.ItemNumber(it.range)) }

        for (m in prefixedCodes.findAll(text)) {
            // A bare "a" is the SK/CZ word "and", so "a"/"al" need ':' or '.' or a digit right after.
            if (!free(m.range)) continue
            parseCodes(m.groupValues[1])?.let { add(Token.Codes(m.range, it, prefixed = true)) }
        }
        for (re in listOf(parenCodes, slashCodes)) for (m in re.findAll(text)) {
            if (!free(m.range)) continue
            parseCodes(m.groupValues[1])?.let { add(Token.Codes(m.range, it, prefixed = false)) }
        }
        for (m in portion.findAll(text)) {
            if (free(m.range)) add(Token.Portion(m.range, prettyPortion(m.value)))
        }
        for (m in priceAfter.findAll(text)) {
            if (!free(m.range)) continue
            val major = m.groupValues[1].replace(" ", "").replace(".", "").toLongOrNull() ?: continue
            val minor = m.groupValues[2].takeIf { it.isNotEmpty() && it[0].isDigit() }?.toLong() ?: 0
            add(Token.PriceTok(m.range, Price(major * 100 + minor, currencyOf(m.groupValues[3])), true))
        }
        for (m in priceBefore.findAll(text)) {
            if (!free(m.range)) continue
            val major = m.groupValues[2].toLongOrNull() ?: continue
            val minor = m.groupValues[3].ifEmpty { "0" }.toLong()
            add(Token.PriceTok(m.range, Price(major * 100 + minor, currencyOf(m.groupValues[1])), true))
        }
        if (out.none { it is Token.PriceTok }) {
            priceBareEnd.find(text)?.takeIf { free(it.range) }?.let { m ->
                add(Token.PriceTok(m.range, Price(m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong(), null), false))
            }
        }
        for (m in bareCodes.findAll(text)) {
            if (!free(m.range) || ',' !in m.value) continue
            parseCodes(m.groupValues[1])?.takeIf { c -> c.all { it <= 14 } }?.let { add(Token.Codes(m.range, it, prefixed = false)) }
        }
        return out.sortedBy { it.range.first }
    }

    private fun parseCodes(s: String): List<Int>? {
        val nums = s.split(Regex("\\s*[,.;/]\\s*")).mapNotNull { it.trim().toIntOrNull() }
        if (nums.isEmpty() || nums.any { it !in 1..20 }) return null
        return nums.distinct().sorted()
    }

    private fun currencyOf(s: String): String = when (s.lowercase()) {
        "€", "eur", "euro" -> "EUR"
        else -> "CZK"
    }

    /** Text with all token spans removed, e.g. the dish name. */
    fun strip(text: String, tokens: List<Token>): String {
        val sb = StringBuilder(text)
        for (t in tokens.sortedByDescending { it.range.first }) sb.replace(t.range.first, t.range.last + 1, " ")
        val noEmptyParens = Regex("\\(\\s*[,.]?\\s*\\)").replace(sb.toString(), " ")
        return Text.clean(noEmptyParens).trim(' ', ',', ';', ':', '-', '–', '/', '|', '*').trim()
    }

    private val portionPart = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s?(gr|g|kg|ml|cl|dl|l|ks|pcs|pc)?")

    /** "200g/200g" -> "200 g / 200 g", "0,33L" -> "0,33 l", "1KS" -> "1 ks", "100gr" -> "100 g". */
    fun prettyPortion(raw: String): String {
        val parts = raw.trim().trimEnd('.').split(Regex("\\s*/\\s*"))
        val units = parts.map { portionPart.find(it)?.groupValues?.get(2)?.lowercase()?.let { u -> if (u == "gr") "g" else u }.orEmpty() }
        val last = units.lastOrNull { it.isNotEmpty() }.orEmpty()
        return parts.mapIndexed { i, p ->
            val n = portionPart.find(p)?.groupValues?.get(1) ?: p
            "$n ${units[i].ifEmpty { last }}".trim()
        }.joinToString(" / ")
    }

    /** Portions found in [text], and the text without them (descriptions often carry "450g"). */
    fun extractPortions(text: String): Pair<List<String>, String> {
        val toks = tokenize(text).filter { it is Token.Portion }
        if (toks.isEmpty()) return emptyList<String>() to text
        return toks.map { (it as Token.Portion).text } to strip(text, toks)
    }
}
