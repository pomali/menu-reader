package sk.menureader.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import sk.menureader.core.model.BBox
import sk.menureader.core.model.OcrBlock
import sk.menureader.core.model.OcrLine
import sk.menureader.core.model.OcrPage

/** ML Kit Text Recognition v2, Latin, bundled model: works offline. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(ctx: Context, pageId: String, uri: Uri): OcrPage {
        val image = InputImage.fromFilePath(ctx, uri)
        val text = recognizer.process(image).await()
        val rotated = image.rotationDegrees % 180 != 0
        return text.toOcrPage(pageId, if (rotated) image.height else image.width, if (rotated) image.width else image.height)
    }

    private fun Text.toOcrPage(pageId: String, w: Int, h: Int) = OcrPage(
        pageId = pageId, width = w, height = h,
        blocks = textBlocks.mapNotNull { b ->
            val box = b.boundingBox ?: return@mapNotNull null
            OcrBlock(
                text = b.text,
                box = box.toBBox(),
                lines = b.lines.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.toBBox(), l.confidence) } },
            )
        },
    )

    private fun Rect.toBBox() = BBox(left, top, right, bottom)
}
