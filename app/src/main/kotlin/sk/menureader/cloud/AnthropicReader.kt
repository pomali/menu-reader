package sk.menureader.cloud

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import sk.menureader.core.cloud.CloudPrompt

/** Claude vision via the official Anthropic Java SDK, with structured output (our JSON schema). */
class AnthropicReader(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String? = null,
) : CloudReader {

    override suspend fun read(images: List<CloudImage>, ocrText: String?): String = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).apply { baseUrl?.let { baseUrl(it) } }.build()
        try {
            val schema = JsonOutputFormat.Schema.builder().apply {
                Json.parseToJsonElement(CloudPrompt.SCHEMA).jsonObject.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v.toPlain())) }
            }.build()
            val output = OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build())
            // Current Claude 5.x models: set effort explicitly (Opus 5.5 defaults to medium).
            if (model.startsWith("claude-") && Regex("-5(-|$)").containsMatchIn(model)) output.effort(OutputConfig.Effort.MEDIUM)

            val content = images.map { img ->
                ContentBlockParam.ofImage(
                    Base64ImageSource.builder().mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).data(img.base64Jpeg).build()
                )
            } + ContentBlockParam.ofText(CloudPrompt.userText(ocrText))

            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(CloudPrompt.SYSTEM)
                .outputConfig(output.build())
                .addUserMessageOfContentBlockParams(content)
                .apply {
                    // If the safety classifier declines, let the API retry on a suitable model.
                    if (supportsServerFallback(model)) {
                        putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    }
                }
                .build()

            val response = client.messages().create(params)
            when (response.stopReason().orElse(null)) {
                StopReason.REFUSAL -> throw CloudException("Claude declined to read this menu")
                StopReason.MAX_TOKENS -> throw CloudException("The menu is too long for one answer")
                else -> {}
            }
            response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
                .ifBlank { throw CloudException("Empty answer") }
        } catch (e: UnauthorizedException) {
            throw CloudException("The Claude API key was rejected")
        } catch (e: RateLimitException) {
            throw CloudException("Claude is busy (rate limit), try again later", retryable = true)
        } catch (e: AnthropicServiceException) {
            throw CloudException("Claude error ${e.statusCode()}", retryable = e.statusCode() >= 500)
        } finally {
            client.close()
        }
    }

    private fun supportsServerFallback(model: String) =
        model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5-1") || model == "claude-sonnet-5-5"
}

/** kotlinx JSON -> plain Kotlin maps/lists/values, which the SDK's [JsonValue.from] accepts. */
private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
}
