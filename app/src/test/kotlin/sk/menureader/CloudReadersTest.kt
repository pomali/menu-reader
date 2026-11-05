package sk.menureader

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import sk.menureader.cloud.AnthropicReader
import sk.menureader.cloud.CloudException
import sk.menureader.cloud.CloudImage
import sk.menureader.cloud.GeminiReader
import sk.menureader.cloud.OpenAiReader
import sk.menureader.core.cloud.CloudMapper

/** Request/response shapes of the three cloud readers, against a local mock server (no real API calls). */
class CloudReadersTest {
    private lateinit var server: MockWebServer
    private val img = CloudImage("aGVsbG8=")
    private val menuJson = """{"restaurantName":"","currency":"EUR","legend":[],"sections":[{"name":"Menu","dishes":[{"name":"Guláš","description":"","portion":"","prices":[{"amount":7.5,"variant":""}],"printedCodes":[1],"mayContain":[]}]}]}"""
    private fun quoted(s: String) = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(s))

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun body(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun anthropic() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("content-type", "application/json").setBody(
                """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"end_turn","stop_sequence":null,
                   "content":[{"type":"text","text":${quoted(menuJson)}}],"usage":{"input_tokens":10,"output_tokens":20}}"""
            )
        )
        val out = AnthropicReader("sk-test", "claude-opus-5-5", server.url("/").toString().trimEnd('/')).read(listOf(img), "Guláš 7,50 €")
        assertEquals("Guláš", CloudMapper.parse(out).sections.single().dishes.single().name)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        // SDK 3.x sends the API key as a bearer token (API version 2026-10-21).
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        assertTrue(req.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("claude-opus-5-5", b["model"]!!.jsonPrimitive.content)
        assertEquals("default", b["fallbacks"]!!.jsonPrimitive.content)
        val oc = b["output_config"]!!.jsonObject
        assertEquals("medium", oc["effort"]!!.jsonPrimitive.content)
        assertEquals("json_schema", oc["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(oc["format"]!!.jsonObject["schema"]!!.jsonObject["properties"]!!.jsonObject.containsKey("sections"))
        val content = b["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("image", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", content[0].jsonObject["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        assertTrue(content[1].jsonObject["text"]!!.jsonPrimitive.content.contains("Guláš 7,50 €"))
    }

    @Test fun anthropicRefusal() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("content-type", "application/json").setBody(
                """{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"refusal","stop_sequence":null,
                   "content":[],"usage":{"input_tokens":10,"output_tokens":0}}"""
            )
        )
        try {
            AnthropicReader("k", "claude-opus-5-5", server.url("/").toString().trimEnd('/')).read(listOf(img), null)
            fail("expected refusal")
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("declined"))
        }
    }

    @Test fun openAi() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"completed","output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":${quoted(menuJson)}}]}]}"""
            )
        )
        val out = OpenAiReader("sk-o", "gpt-6.1-sol", server.url("").toString().trimEnd('/')).read(listOf(img), "ocr")
        assertEquals(1, CloudMapper.parse(out).sections.size)
        val req = server.takeRequest()
        assertEquals("/v1/responses", req.path)
        assertEquals("Bearer sk-o", req.getHeader("Authorization"))
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        val fmt = b["text"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", fmt["type"]!!.jsonPrimitive.content)
        assertEquals("true", fmt["strict"]!!.jsonPrimitive.content)
        val user = b["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertTrue(user[0].jsonObject["image_url"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
    }

    @Test fun openAiBadKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        try {
            OpenAiReader("bad", "m", server.url("").toString().trimEnd('/')).read(listOf(img), null); fail()
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("rejected"))
        }
    }

    @Test fun gemini() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"i1","status":"completed","steps":[{"type":"thought"},{"type":"model_output","content":[{"type":"text","text":${quoted(menuJson)}}]}]}"""
            )
        )
        val out = GeminiReader("g", "gemini-3.8-flash", true, server.url("").toString().trimEnd('/')).read(listOf(img), null)
        assertEquals(1, CloudMapper.parse(out).sections.size)
        val req = server.takeRequest()
        assertEquals("/v1beta/interactions", req.path)
        assertEquals("g", req.getHeader("x-goog-api-key"))
        assertEquals("2026-05-20", req.getHeader("Api-Revision"))
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("application/json", b["response_format"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        assertEquals("image", b["input"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun nanoBananaGetsSchemaInPromptOnly() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":${quoted("```json\n$menuJson\n```")}}]}]}"""))
        val out = GeminiReader("g", "gemini-nano-banana-2.1", false, server.url("").toString().trimEnd('/')).read(listOf(img), null)
        assertEquals("Guláš", CloudMapper.parse(out).sections.single().dishes.single().name)
        val b = body()
        assertFalse(b.containsKey("response_format"))
        assertTrue(b["system_instruction"]!!.jsonPrimitive.content.contains("JSON Schema"))
    }
}
