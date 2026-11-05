package sk.menureader.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sk.menureader.core.cloud.CloudPrompt
import java.io.IOException
import java.util.concurrent.TimeUnit

private val http by lazy {
    OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build()
}
private val jsonType = "application/json".toMediaType()
private val schema: JsonObject by lazy { Json.parseToJsonElement(CloudPrompt.SCHEMA).jsonObject }

internal fun post(url: String, headers: Map<String, String>, body: JsonObject, who: String): JsonObject {
    val req = Request.Builder().url(url).post(body.toString().toRequestBody(jsonType)).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.build()
    try {
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            when {
                res.code == 401 || res.code == 403 -> throw CloudException("The $who API key was rejected")
                res.code == 402 -> throw CloudException("No credits left on your $who account")
                res.code == 429 -> throw CloudException("$who is busy (rate limit), try again later", retryable = true)
                !res.isSuccessful -> throw CloudException("$who error ${res.code}", retryable = res.code >= 500)
            }
            return Json.parseToJsonElement(text).jsonObject
        }
    } catch (e: IOException) {
        throw CloudException("No connection to $who", retryable = true)
    }
}

/** OpenAI Responses API: image input + strict JSON schema output. */
class OpenAiReader(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.openai.com",
) : CloudReader {
    override suspend fun read(images: List<CloudImage>, ocrText: String?): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("input") {
                addJsonObject { put("role", "system"); put("content", CloudPrompt.SYSTEM) }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        for (img in images) addJsonObject {
                            put("type", "input_image")
                            put("image_url", "data:image/jpeg;base64,${img.base64Jpeg}")
                            put("detail", "high")
                        }
                        addJsonObject { put("type", "input_text"); put("text", CloudPrompt.userText(ocrText)) }
                    }
                }
            }
            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_schema"); put("name", "menu"); put("strict", true); put("schema", schema)
                }
            }
        }
        val res = post("$baseUrl/v1/responses", mapOf("Authorization" to "Bearer $apiKey"), body, "OpenAI")
        if (res["status"]?.jsonPrimitive?.contentOrNull == "incomplete") throw CloudException("The menu is too long for one answer")
        val parts = (res["output"] as? JsonArray).orEmpty().map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
            .flatMap { (it["content"] as? JsonArray).orEmpty().map { c -> c.jsonObject } }
        parts.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "refusal" }?.let {
            throw CloudException("OpenAI declined to read this menu")
        }
        parts.filter { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .ifBlank { throw CloudException("Empty answer") }
    }
}

/** Gemini Interactions API (also used for Nano Banana, without the enforced schema). */
class GeminiReader(
    private val apiKey: String,
    private val model: String,
    private val structured: Boolean,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : CloudReader {
    override suspend fun read(images: List<CloudImage>, ocrText: String?): String = withContext(Dispatchers.IO) {
        val system = if (structured) CloudPrompt.SYSTEM
        else CloudPrompt.SYSTEM + "\n\nAnswer with JSON only (no prose, no image), matching this JSON Schema:\n" + CloudPrompt.SCHEMA
        val body = buildJsonObject {
            put("model", model)
            put("system_instruction", system)
            putJsonArray("input") {
                for (img in images) addJsonObject {
                    put("type", "image"); put("data", img.base64Jpeg); put("mime_type", "image/jpeg")
                }
                addJsonObject { put("type", "text"); put("text", CloudPrompt.userText(ocrText)) }
            }
            if (structured) putJsonObject("response_format") {
                put("type", "text"); put("mime_type", "application/json"); put("schema", schema)
            }
        }
        val res = post(
            "$baseUrl/v1beta/interactions",
            mapOf("x-goog-api-key" to apiKey, "Api-Revision" to "2026-05-20"), body, "Gemini",
        )
        when (res["status"]?.jsonPrimitive?.contentOrNull) {
            "completed", null -> {}
            "incomplete" -> throw CloudException("The menu is too long for one answer")
            else -> throw CloudException("Gemini could not read this menu (${res["status"]?.jsonPrimitive?.contentOrNull})")
        }
        (res["steps"] as? JsonArray).orEmpty().map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "model_output" }
            .flatMap { (it["content"] as? JsonArray).orEmpty().map { c -> c.jsonObject } }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .ifBlank { throw CloudException("Empty answer") }
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.toList() ?: emptyList()
