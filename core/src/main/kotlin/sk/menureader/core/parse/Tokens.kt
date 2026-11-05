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
    private const val CODE_LIST = "$CODE(?:\\s*[,.;/]\\s*$CODE)*"

    private val prefixedCodes = Regex(
        "(?i)\\(?\\b(?:(?:alerg[eé]ny|alerg[eé]n|allergens?|alerg\\.?)\\s*:?|(?:al|a)\\s*[:.]|a(?=\\d))\\s*($CODE_LIST)\\b\\)?"
    )
    private val parenCodes = Regex("\\(\\s*($CODE_LIST)\\s*\\)")
    private val bareCodes = Regex("(?<![\\d,.])($CODE(?:\\s*,\\s*$CODE)+)(?![\\d]|\\s*(?:€|eur|kč|kc|czk|,-|g\\b|kg|ml|l\\b|dl|ks))", RegexOption.IGNORE_CASE)

    private const val CUR = "€|eur|euro|kč|kc|czk|,-|\\.-"
    private val priceAfter = Regex("(?i)(?<![\\d,.])(\\d{1,4}(?:[ .]\\d{3})?)(?:[.,](\\d{2}|-{1,2}))?\\s?($CUR)(?![a-z])")
    private val priceBefore = Regex("(?i)(€|eur|czk)\\s?(\\d{1,4})(?:[.,](\\d{2}))?(?![\\d,])")
    /** Bare "8,90" at the end of a row; currency printed elsewhere on the menu. */
    private val priceBareEnd = Regex("(?<![\\d,.])(\\d{1,3})[.,](\\d{2})\\s*$")

    private val portion = Regex("(?i)(?<![\\d,.])(\\d+(?:[.,]\\d+)?(?:\\s?/\\s?\\d+)?)\\s?(g|kg|ml|cl|dl|l|ks|pcs|pc)\\b\\.?")
    private val itemNumber = Regex("^\\s*(?:menu\\s*)?\\d{1,3}\\s*[.)]\\s+", RegexOption.IGNORE_CASE)

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
        for (m in parenCodes.findAll(text)) {
            if (!free(m.range)) continue
            parseCodes(m.groupValues[1])?.let { add(Token.Codes(m.range, it, prefixed = false)) }
        }
        for (m in portion.findAll(text)) {
            if (free(m.range)) add(Token.Portion(m.range, m.value.trim().trimEnd('.')))
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
            if (!free(m.range)) continue
            parseCodes(m.groupValues[1])?.let { add(Token.Codes(m.range, it, prefixed = false)) }
        }
        return out.sortedBy { it.range.first }
    }

    private fun parseCodes(s: String): List<Int>? {
        val nums = s.split(Regex("\\s*[,.;/]\\s*")).mapNotNull { it.trim().toIntOrNull() }
        if (nums.isEmpty() || nums.any { it !in 1..14 }) return null
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
        return Text.clean(sb.toString()).trim(' ', ',', ';', ':', '-', '–', '/', '|', '*').trim()
    }
}
