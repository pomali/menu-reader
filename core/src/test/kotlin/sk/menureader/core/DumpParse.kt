package sk.menureader.core

import sk.menureader.core.model.OcrDocument
import sk.menureader.core.parse.MenuParser
import java.io.File
import kotlin.test.Test

/**
 * Dev aid: prints what the parser makes of every OCR dump in samples/ocr-* folders.
 * Run: ./gradlew :core:test --tests '*DumpParse*' -Pdump  (silent unless -Pdump is passed)
 */
class DumpParse {
    @Test fun dump() {
        if (System.getProperty("dump") == null) return
        val dir = File(System.getProperty("samples.dir") ?: "samples")
        val files = dir.listFiles { f -> f.isDirectory && f.name.startsWith("ocr") }.orEmpty()
            .flatMap { d -> d.listFiles { f -> f.extension == "json" }.orEmpty().toList() }.sorted()
        for (f in files) {
            val only = System.getProperty("dump")
            if (only.isNotEmpty() && only != "true" && !f.name.contains(only)) continue
            val r = MenuParser().parse(MenuJson.decodeFromString<OcrDocument>(f.readText()))
            println("===== ${f.parentFile.name}/${f.name}  legend=${r.legend?.size ?: 0} warnings=${r.warnings}")
            for (s in r.sections) {
                println("## ${s.name}")
                for (d in s.dishes) println("  - ${d.name} | ${d.prices.joinToString { it.format() + (it.variant?.let { v -> " ($v)" } ?: "") }} | ${d.printedCodes} | ${d.portion ?: ""} | ${"%.2f".format(d.confidence)}" + (d.description?.let { "\n      ~ $it" } ?: ""))
            }
            println("  unassigned: ${r.unassigned.take(12)}")
        }
    }
}
