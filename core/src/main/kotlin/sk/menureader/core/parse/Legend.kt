package sk.menureader.core.parse

import sk.menureader.core.model.Allergen

/**
 * Allergen legend printed on the menu ("1 – obilniny obsahujúce lepok, 3 – vajcia …"), PLAN §3.2 step 4.
 * Allergen names are recognised by sk/cs/en keywords on folded text.
 */
object Legend {
    /** Checked in this order, so "peanut" wins over "nut". */
    private val keywords: List<Pair<Allergen, List<String>>> = listOf(
        Allergen.GLUTEN to listOf("obilnin", "lepok", "lepek", "gluten", "psenic", "cereal"),
        Allergen.CRUSTACEANS to listOf("korovc", "koryse", "crustac"),
        Allergen.EGGS to listOf("vajc", "vejc", "vajic", "egg"),
        Allergen.FISH to listOf("ryb", "fish"),
        Allergen.PEANUTS to listOf("arasid", "podzemnic", "peanut", "burak"),
        Allergen.SOY to listOf("soj", "soy"),
        Allergen.MILK to listOf("mliek", "mlek", "milk", "laktoz", "lactos"),
        Allergen.TREE_NUTS to listOf("orech", "orisk", "skrupin", "skorapk", "nut"),
        Allergen.CELERY to listOf("zeler", "celer"),
        Allergen.MUSTARD to listOf("horcic", "mustard"),
        Allergen.SESAME to listOf("sezam", "sesam"),
        Allergen.SULPHITES to listOf("siric", "sulph", "sulf", "oxid"),
        Allergen.LUPIN to listOf("vlci", "lupin"),
        Allergen.MOLLUSCS to listOf("makkys", "mekkys", "mollus"),
    )

    private val entry = Regex("(?<![\\d,.])(1[0-9]|20|0?[1-9])\\s*([-–—=:.)]?)\\s*(\\p{L}[^\\d]*)")

    fun recognize(name: String): Allergen? {
        val f = Text.fold(name)
        return keywords.firstOrNull { (_, kws) -> kws.any { it in f } }?.first
    }

    /**
     * Parses one line. Returns the recognised entries if it looks like a legend line: at least one
     * entry names a known allergen and at most half don't (OCR noise, "10 mg/kg", custom codes like
     * "15. Med"). Returns null otherwise.
     */
    fun parseLine(text: String): Map<Int, Allergen>? {
        val body = text.replace(Regex("(?i)^\\s*(zoznam\\s+|seznam\\s+)?(alerg[eé]n\\w*|alergeny|allergens?)\\s*[:-]?"), " ")
        val found = entry.findAll(body).toList()
        if (found.isEmpty()) return null
        val out = LinkedHashMap<Int, Allergen>()
        var unknown = 0
        for (m in found) {
            val n = m.groupValues[1].toInt()
            val a = if (n <= 14) recognize(m.groupValues[3].take(40)) else null
            when {
                a != null -> out.putIfAbsent(n, a)
                m.groupValues[2].isNotEmpty() -> unknown++ // "15. Med": a real entry we don't know
                // "10 mg/kg": a number inside the text, not an entry
            }
        }
        return out.takeIf { it.isNotEmpty() && unknown <= out.size }
    }

    /** Warnings for legend numbers that differ from the EU 1169/2011 numbering. */
    fun mismatches(legend: Map<Int, Allergen>): List<String> = legend.mapNotNull { (n, a) ->
        if (a.code == n) null
        else "Legend says $n = ${a.en}, EU numbering says $n = ${Allergen.fromCode(n)?.en ?: "?"}"
    }
}
