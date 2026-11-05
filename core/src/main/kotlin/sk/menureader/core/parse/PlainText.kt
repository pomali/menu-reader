package sk.menureader.core.parse

import sk.menureader.core.model.BBox
import sk.menureader.core.model.OcrBlock
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.OcrLine
import sk.menureader.core.model.OcrPage

/**
 * Text shared from another app (selected text in a browser, copied from Wolt) has no layout.
 * Turn it into a one-column fake OCR page, one line per text line, so the same parser runs on it.
 */
object PlainText {
    private const val LINE_H = 30
    private val url = Regex("^\\s*https?://\\S+\\s*$")

    fun toOcr(text: String, pageId: String = "t1"): OcrDocument {
        val lines = text.replace("\r", "").replace('\t', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ocrLines = lines.mapIndexed { i, l -> OcrLine(l, BBox(0, i * LINE_H * 4 / 3, 20 + l.length * 14, i * LINE_H * 4 / 3 + LINE_H)) }
        val width = (ocrLines.maxOfOrNull { it.box.right } ?: 100).coerceAtLeast(100)
        val page = OcrPage(pageId, width, (lines.size + 1) * LINE_H * 4 / 3, ocrLines.map { OcrBlock(it.text, it.box, listOf(it)) })
        return OcrDocument(listOf(page), source = OcrDocument.SOURCE_TEXT)
    }

    /** True when the shared text is only links (e.g. Wolt's "share" button), so there's nothing to parse. */
    fun isOnlyLinks(text: String): Boolean {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val links = words.count { url.matches(it) }
        return links > 0 && words.size - links <= 8 && text.lines().none { Tokenizer.tokenize(it).any { t -> t is Token.PriceTok } }
    }
}
