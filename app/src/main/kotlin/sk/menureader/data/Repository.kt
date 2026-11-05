package sk.menureader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import sk.menureader.core.LocalExtractor
import sk.menureader.core.MenuJson
import sk.menureader.core.model.OcrDocument
import sk.menureader.core.model.Page
import sk.menureader.core.model.Quality
import sk.menureader.core.model.Snapshot
import sk.menureader.core.cloud.CloudMapper
import sk.menureader.cloud.CloudException
import sk.menureader.cloud.CloudImage
import sk.menureader.cloud.CloudSettings
import sk.menureader.cloud.DecisionsClient
import sk.menureader.core.cloud.Decisions
import sk.menureader.core.filter.UserProfile
import java.io.ByteArrayOutputStream
import sk.menureader.core.parse.PlainText
import sk.menureader.ocr.OcrEngine
import java.io.File
import java.util.UUID

data class StoredSnapshot(val snapshot: Snapshot, val restaurantName: String?, val ocrJson: String)

/** Snapshots in Room, page images in app-private files. Everything stays on the device. */
class Repository(private val ctx: Context, db: AppDatabase) {
    private val dao = db.snapshots()
    private val ocr by lazy { OcrEngine() }
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

    /**
     * Read the menu again with the chosen cloud model (photos + the phone's OCR text) and replace
     * the snapshot with the FULL result. Text-only snapshots (shared text) send just the text.
     */
    suspend fun upgradeWithAi(id: String, cloud: CloudSettings) = withContext(Dispatchers.IO) {
        val (provider, reader) = cloud.reader() ?: throw CloudException("AI reading is off or the API key is missing")
        val e = dao.get(id) ?: return@withContext
        val current = MenuJson.decodeFromString<Snapshot>(e.snapshotJson)
        val basicJson = e.basicSnapshotJson ?: e.snapshotJson.takeIf { current.quality == Quality.BASIC }
        val basic = basicJson?.let { MenuJson.decodeFromString<Snapshot>(it) } ?: current
        val ocrDoc = runCatching { MenuJson.decodeFromString<OcrDocument>(e.ocrJson) }.getOrNull()
        val ocrText = ocrDoc?.pages?.flatMap { it.lines }?.joinToString("\n") { it.text }
        val images = basic.pages.mapNotNull { p -> pageFile(id, p.id).takeIf { it.exists() }?.let(::prepareImage) }
        if (images.isEmpty() && ocrText.isNullOrBlank()) throw CloudException("Nothing to read")

        val answer = reader.read(images, ocrText)
        val menu = runCatching { CloudMapper.parse(answer) }.getOrElse { throw CloudException("The AI answer wasn't valid JSON") }
        val model = cloud.config.value.model(provider)
        val full = CloudMapper.toSnapshot(menu, basic, ocrDoc, provider.name.lowercase(), model)
        val name = e.restaurantName ?: menu.restaurantName.trim().ifBlank { null }
        dao.upsert(
            e.copy(
                restaurantName = name, quality = full.quality.name, dishCount = full.dishes.size,
                snapshotJson = MenuJson.encodeToString(full), basicSnapshotJson = basicJson,
            )
        )
    }

    /**
     * Allergen double-check: ask the decisions model about every dish, only for what the profile
     * avoids, and add "may contain" claims for likely answers. Returns false if nothing to ask.
     */
    suspend fun doubleCheck(id: String, client: DecisionsClient, profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        val questions = Decisions.questions(profile)
        if (questions.isEmpty()) return@withContext false
        val e = dao.get(id) ?: return@withContext false
        val s = MenuJson.decodeFromString<Snapshot>(e.snapshotJson)
        val flat = s.sections.flatMap { sec -> sec.dishes.map { sec.name to it } }
        if (flat.isEmpty()) return@withContext false
        val answers = client.ask(flat.map { (sec, d) -> Decisions.state(d, sec) }, questions)
        val byId = flat.map { it.second.id }.zip(answers).toMap()
        val checked = s.copy(sections = s.sections.map { sec ->
            sec.copy(dishes = sec.dishes.map { d -> byId[d.id]?.let { Decisions.apply(d, it) } ?: d })
        })
        dao.upsert(e.copy(snapshotJson = MenuJson.encodeToString(checked)))
        true
    }

    /** Scale to at most 1568 px on the long edge (enough for menu text, keeps requests small), JPEG 85. */
    private fun prepareImage(file: File): CloudImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1568) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = 1568f / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        sized.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return CloudImage(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    /** Re-run the local extractor on the stored OCR, e.g. after the parser or dictionary improved. */
    suspend fun rerun(id: String) = withContext(Dispatchers.IO) {
        val e = dao.get(id) ?: return@withContext
        val old = MenuJson.decodeFromString<Snapshot>(e.snapshotJson)
        val ocrDoc = MenuJson.decodeFromString<OcrDocument>(e.ocrJson)
        val fresh = extractor.extract(id, ocrDoc, old.pages, old.capturedAt, old.capturedGeo, old.restaurantId)
        save(fresh, e.restaurantName, e.ocrJson, basicJson = null)
    }

    private suspend fun save(s: Snapshot, restaurantName: String?, ocrJson: String, basicJson: String? = null) {
        dao.upsert(
            SnapshotEntity(
                id = s.id, capturedAt = s.capturedAt, restaurantName = restaurantName,
                quality = s.quality.name, dishCount = s.dishes.size,
                snapshotJson = MenuJson.encodeToString(s), ocrJson = ocrJson, basicSnapshotJson = basicJson,
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
