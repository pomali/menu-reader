package sk.menureader.core.allergen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.parse.Text

/** One lexical hit: which term matched and the original text it matched. */
data class LexHit<T>(val target: T, val term: String, val matched: String, val weak: Boolean)

/**
 * Layer B: ingredient words -> allergens / diet tags, for Slovak, Czech and English.
 * Matching is diacritics-insensitive and case-insensitive; "smotan*" matches "smotanou".
 */
class Lexicon private constructor(
    private val allergenTerms: List<Term<Allergen>>,
    private val tagTerms: List<Term<DietTag>>,
) {
    private class Term<T>(val target: T, val raw: String, val weak: Boolean) {
        val regex: Regex = run {
            val prefix = raw.endsWith("*")
            val words = fold(raw.removeSuffix("*")).trim().split(Regex("\\s+")).map(Regex::escape)
            Regex("(?<![\\p{L}\\d])" + words.joinToString("\\s+") + (if (prefix) "\\p{L}*" else "") + "(?![\\p{L}\\d])")
        }
    }

    fun allergens(text: String): List<LexHit<Allergen>> = match(text, allergenTerms)
    fun tags(text: String): List<LexHit<DietTag>> = match(text, tagTerms)

    private fun <T> match(text: String, terms: List<Term<T>>): List<LexHit<T>> {
        val folded = fold(text)
        val best = LinkedHashMap<T, LexHit<T>>()
        for (t in terms) {
            val m = t.regex.find(folded) ?: continue
            val hit = LexHit(t.target, t.raw, text.substring(m.range.first, m.range.last + 1), t.weak)
            val prev = best[t.target]
            if (prev == null || (prev.weak && !hit.weak)) best[t.target] = hit
        }
        return best.values.toList()
    }

    companion object {
        fun fold(s: String): String = Text.fold(s)

        val default: Lexicon by lazy {
            val stream = Lexicon::class.java.getResourceAsStream("/sk/menureader/core/lexicon.json")
                ?: error("lexicon.json missing from resources")
            fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        fun fromJson(json: String): Lexicon {
            val root = Json.parseToJsonElement(json).jsonObject
            fun <T> read(obj: JsonObject?, key: (String) -> T): List<Term<T>> = buildList {
                for ((name, langs) in obj ?: return@buildList) {
                    val target = key(name)
                    for ((lang, arr) in langs.jsonObject) {
                        for (t in (arr as JsonArray)) add(Term(target, t.jsonPrimitive.content, weak = lang == "weak"))
                    }
                }
            }
            return Lexicon(
                read(root["allergens"]?.jsonObject) { Allergen.valueOf(it) },
                read(root["tags"]?.jsonObject) { DietTag.valueOf(it) },
            )
        }
    }
}
