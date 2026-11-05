package sk.menureader.core

import kotlinx.serialization.Serializable
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.parse.MenuParser
import sk.menureader.core.parse.Text
import java.io.File
import kotlin.test.Test

/** Hand-labelled ground truth, samples/truth/<name>.json. Only what we score. */
@Serializable
data class Truth(val sections: List<TruthSection>)

@Serializable
data class TruthSection(val name: String = "", val dishes: List<TruthDish>)

@Serializable
data class TruthDish(val name: String, val price: Long? = null, val codes: List<Int> = emptyList())

/**
 * Phase 0: scores the rule parser on samples/ocr/<name>.json against samples/truth/<name>.json.
 * Prints a report and never fails, so it can run in CI while the parser is being tuned.
 * Run: ./gradlew :core:test --tests '*Phase0ScoreTest*'
 */
class Phase0ScoreTest {
    private fun words(s: String) = Text.fold(s).split(Regex("[^\\p{L}\\d]+")).filter { it.length > 1 }.toSet()

    private fun similar(a: String, b: String): Double {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }

    @Test fun score() {
        val dir = File(System.getProperty("samples.dir") ?: "samples")
        val pairs = File(dir, "ocr").listFiles { f -> f.extension == "json" }.orEmpty().sorted()
            .mapNotNull { ocr -> File(dir, "truth/${ocr.name}").takeIf { it.exists() }?.let { ocr to it } }
        if (pairs.isEmpty()) { println("Phase 0: no samples/ocr + samples/truth pairs yet"); return }

        var tp = 0; var predicted = 0; var expected = 0; var priceOk = 0; var codesOk = 0
        for ((ocrFile, truthFile) in pairs) {
            val parsed = MenuParser().parse(MenuJson.decodeFromString<OcrDocument>(ocrFile.readText()))
            val got = parsed.sections.flatMap { it.dishes }
            val want = MenuJson.decodeFromString<Truth>(truthFile.readText()).sections.flatMap { it.dishes }
            val used = mutableSetOf<String>()
            var fileTp = 0
            for (w in want) {
                val m = got.filter { it.id !in used }.maxByOrNull { similar(it.name, w.name) }
                    ?.takeIf { similar(it.name, w.name) >= 0.5 } ?: continue
                used += m.id; fileTp++
                if (w.price == null || m.prices.any { it.amountMinor == w.price }) priceOk++
                if (m.printedCodes == w.codes.sorted()) codesOk++
            }
            tp += fileTp; predicted += got.size; expected += want.size
            println("%-40s dishes %3d/%3d found, %3d predicted, %d unassigned rows".format(
                ocrFile.nameWithoutExtension.take(40), fileTp, want.size, got.size, parsed.unassigned.size))
        }
        fun pct(a: Int, b: Int) = if (b == 0) "-" else "%.0f%%".format(100.0 * a / b)
        println("Phase 0 total: recall ${pct(tp, expected)}, precision ${pct(tp, predicted)}, " +
            "price ok ${pct(priceOk, tp)}, codes ok ${pct(codesOk, tp)} (of matched dishes)")
    }
}
