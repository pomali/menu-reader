package sk.menureader.core.model

import kotlinx.serialization.Serializable

/**
 * OCR output in a device-independent form. The app converts ML Kit's Text into this; the
 * debug screen exports it as JSON so the parser can be developed and tested on the JVM.
 */
@Serializable
data class OcrDocument(
    val pages: List<OcrPage>,
    /** Where the text came from: null = OCR of photos, [SOURCE_TEXT] = text shared from another app. */
    val source: String? = null,
) {
    companion object {
        const val SOURCE_TEXT = "text"
    }
}

@Serializable
data class OcrPage(val pageId: String, val width: Int, val height: Int, val blocks: List<OcrBlock>) {
    val lines get() = blocks.flatMap { it.lines }
}

@Serializable
data class OcrBlock(val text: String, val box: BBox, val lines: List<OcrLine>)

@Serializable
data class OcrLine(val text: String, val box: BBox, val confidence: Float? = null)
