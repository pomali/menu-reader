package sk.menureader.core.parse

/** Weekly menus: "Pondelok /26.10.2026 ..... Polievka: …", "02.11.2026 PONDELOK", "Štvrtok 10.7.2025". */
object Weekday {
    private const val DATE = "\\d{1,2}\\.\\s?\\d{1,2}\\.(?:\\s?\\d{2,4})?"
    private const val DAYS = "pondelok|utorok|streda|stvrtok|piatok|sobota|nedela|pondeli|utery|ctvrtek|patek|nedele|" +
        "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private val re = Regex("^\\s*((?:$DATE\\s*)?(?:$DAYS)\\b(?:[\\s/.,:-]*$DATE)?)[\\s/.,:-]*")

    /** Returns (day label, rest of the line) when the line starts with a weekday, else null. */
    fun split(text: String): Pair<String, String>? {
        val m = re.find(Text.fold(text)) ?: return null
        val label = text.substring(m.groups[1]!!.range).replace(Regex("\\s*/\\s*"), " ").trim()
        return label to Text.clean(text.substring(m.range.last + 1))
    }
}
