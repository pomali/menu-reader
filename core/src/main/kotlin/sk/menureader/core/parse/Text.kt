package sk.menureader.core.parse

import java.text.Normalizer

object Text {
    private val marks = Regex("\\p{M}")

    /**
     * Lowercase and strip diacritics: "Smažený SÝR" -> "smazeny syr".
     * Produces exactly one char per input char, so match ranges map back to the original.
     */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s) {
            val d = marks.replace(Normalizer.normalize(c.lowercaseChar().toString(), Normalizer.Form.NFD), "")
            append(if (d.length == 1) d[0] else c.lowercaseChar())
        }
    }

    private val leaders = Regex("[.·…_]{2,}|\\s[-–—]{2,}\\s?")
    private val spaces = Regex("\\s+")

    /** Remove dot leaders and squeeze whitespace. */
    fun clean(s: String): String = spaces.replace(leaders.replace(s, " "), " ").trim()
}
