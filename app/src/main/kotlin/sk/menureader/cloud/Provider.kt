package sk.menureader.cloud

/** Which API key a provider uses. Nano Banana runs on the Gemini API, so it shares the Gemini key. */
enum class KeyKind { ANTHROPIC, OPENAI, GEMINI, OPENROUTER }

/**
 * Cloud readers the user can pick. Models are suggestions; the user can type any model id.
 * [structured]: the API enforces our JSON schema. Nano Banana is an image model, so it only gets
 * the schema in the prompt.
 */
enum class Provider(
    val title: String,
    val key: KeyKind,
    val defaultModel: String,
    val models: List<String>,
    val structured: Boolean = true,
    val keyUrl: String,
) {
    ANTHROPIC(
        "Claude (Anthropic)", KeyKind.ANTHROPIC, "claude-opus-5-5",
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        keyUrl = "https://platform.claude.com/settings/keys",
    ),
    OPENAI(
        "OpenAI", KeyKind.OPENAI, "gpt-6.1-sol",
        listOf("gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna"),
        keyUrl = "https://platform.openai.com/api-keys",
    ),
    GEMINI(
        "Google Gemini", KeyKind.GEMINI, "gemini-3.8-flash",
        listOf("gemini-3.8-flash", "gemini-4-argon", "gemini-3.5-flash-lite"),
        keyUrl = "https://aistudio.google.com/apikey",
    ),
    NANO_BANANA(
        "Nano Banana (Gemini)", KeyKind.GEMINI, "gemini-nano-banana-2.1",
        listOf("gemini-nano-banana-2.1", "gemini-3-pro-image"),
        structured = false,
        keyUrl = "https://aistudio.google.com/apikey",
    ),
}

/** A page photo ready to send: JPEG bytes, base64. */
class CloudImage(val base64Jpeg: String)

class CloudException(message: String, val retryable: Boolean = false) : Exception(message)

/** Sends photos (+ OCR text) to one provider and returns the raw JSON text of its answer. */
interface CloudReader {
    suspend fun read(images: List<CloudImage>, ocrText: String?): String
}
