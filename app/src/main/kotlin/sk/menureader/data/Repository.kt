package sk.menureader.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import sk.menureader.core.LocalExtractor
import sk.menureader.core.MenuJson
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.Page
import sk.menureader.core.model.Snapshot
import sk.menureader.core.parse.PlainText
import sk.menureader.ocr.OcrEngine
import java.io.File
import java.util.UUID

data class StoredSnapshot(val snapshot: Snapshot, val restaurantName: String?, val ocrJson: String)

/** Snapshots in Room, page images in app-private files. Everything stays on the device. */
class Repository(private val ctx: Context, db: AppDatabase) {
    private val dao = db.snapshots()
    private val ocr = OcrEngine()
    private val extractor = LocalExtractor()

    val snapshots: Flow<List<SnapshotRow>> = dao.observeAll()

    fun observe(id: String): Flow<StoredSnapshot?> = dao.observe(id).map { it?.toStored() }

    private fun SnapshotEntity.toStored() =
        StoredSnapshot(MenuJson.decodeFromString(snapshotJson), restaurantName, ocrJson)

    fun pageFile(snapshotId: String, pageId: String) = File(dir(snapshotId), "$pageId.jpg")
    private fun dir(snapshotId: String) = File(ctx.filesDir, "snapshots/$snapshotId").apply { mkdirs() }

    /** Copy the scanned pages, run OCR + the local extractor, and save a BASIC snapshot. */
    suspend fun createFromImages(uris: List<Uri>, restaurantName: String? = null): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val pages = uris.mapIndexed { i, uri ->
            val pageId = "p${i + 1}"
            val file = pageFile(id, pageId)
            ctx.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, opts)
            Page(pageId, file.name, opts.outWidth, opts.outHeight)
        }
        val ocrDoc = OcrDocument(pages.map { ocr.recognize(ctx, it.id, Uri.fromFile(pageFile(id, it.id))) })
        val snapshot = extractor.extract(id, ocrDoc, pages, System.currentTimeMillis())
        save(snapshot, restaurantName, MenuJson.encodeToString(ocrDoc))
        id
    }

    /**
     * Text shared from another app (selected on a web page, copied from a delivery app). No photos;
     * the text goes through the same parser as one fake OCR page.
     */
    suspend fun createFromText(text: String, restaurantName: String? = null): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val ocrDoc = PlainText.toOcr(text)
        val snapshot = extractor.extract(id, ocrDoc, emptyList(), System.currentTimeMillis())
        save(snapshot, restaurantName?.trim()?.take(80)?.ifEmpty { null }, MenuJson.encodeToString(ocrDoc))
        id
    }

    /** Re-run the local extractor on the stored OCR, e.g. after the parser or dictionary improved. */
    suspend fun rerun(id: String) = withContext(Dispatchers.IO) {
        val e = dao.get(id) ?: return@withContext
        val old = MenuJson.decodeFromString<Snapshot>(e.snapshotJson)
        val ocrDoc = MenuJson.decodeFromString<OcrDocument>(e.ocrJson)
        val fresh = extractor.extract(id, ocrDoc, old.pages, old.capturedAt, old.capturedGeo, old.restaurantId)
        save(fresh, e.restaurantName, e.ocrJson)
    }

    private suspend fun save(s: Snapshot, restaurantName: String?, ocrJson: String) {
        dao.upsert(
            SnapshotEntity(
                id = s.id, capturedAt = s.capturedAt, restaurantName = restaurantName,
                quality = s.quality.name, dishCount = s.dishes.size,
                snapshotJson = MenuJson.encodeToString(s), ocrJson = ocrJson,
            )
        )
    }

    suspend fun setRestaurantName(id: String, name: String?) = dao.setRestaurantName(id, name?.trim()?.ifEmpty { null })

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
        File(ctx.filesDir, "snapshots/$id").deleteRecursively()
    }

    /** Write text (OCR dump or snapshot JSON) to a document the user picked. */
    suspend fun writeTo(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
    }
}
