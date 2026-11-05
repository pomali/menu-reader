package sk.menureader.core

import sk.menureader.core.model.BBox
import sk.menureader.core.model.OcrBlock
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.OcrLine
import sk.menureader.core.model.OcrPage

/** Builds fake OCR pages: each line is (text, left, top, right, height). */
class PageBuilder(private val width: Int = 1000, private val height: Int = 1400) {
    private val lines = mutableListOf<OcrLine>()
    fun line(text: String, left: Int, top: Int, right: Int = left + text.length * 14, h: Int = 30) {
        lines += OcrLine(text, BBox(left, top, right, top + h))
    }
    fun build(id: String = "p1") = OcrPage(id, width, height, lines.map { OcrBlock(it.text, it.box, listOf(it)) })
}

fun page(width: Int = 1000, block: PageBuilder.() -> Unit) = PageBuilder(width).apply(block).build()
fun doc(vararg pages: OcrPage) = OcrDocument(pages.toList())

/** One line per row, left column only, 40 px apart. */
fun simpleDoc(vararg rows: String) = doc(page { rows.forEachIndexed { i, r -> line(r, 50, 50 + i * 40) } })
