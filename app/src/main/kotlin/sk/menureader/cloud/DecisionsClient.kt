package sk.menureader.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import sk.menureader.core.cloud.DecisionQuestion

/**
 * OpenRouter Decisions API (alpha, `POST /api/alpha/decisions`): typed yes/no questions with
 * probabilities. One request per dish, a few in parallel. The endpoint is alpha, so its shape may change.
 */
class DecisionsClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://openrouter.ai",
) {
    /** For each dish state, question key -> probability of "yes" (null if refused / missing). */
    suspend fun ask(dishes: List<Map<String, String>>, questions: List<DecisionQuestion>): List<Map<String, Double?>> =
        withContext(Dispatchers.IO) {
            if (questions.isEmpty()) return@withContext dishes.map { emptyMap() }
            val gate = Semaphore(4)
            coroutineScope {
                dishes.map { d -> async { gate.withPermit { askOne(d, questions) } } }.awaitAll()
            }
        }

    private fun askOne(dish: Map<String, String>, questions: List<DecisionQuestion>): Map<String, Double?> {
        val body = buildJsonObject {
            put("model", model)
            putJsonObject("state") { dish.forEach { (k, v) -> put(k, v) } }
            putJsonObject("questions") {
                for (q in questions) putJsonObject(q.key) {
                    put("type", "noul")
                    put("instructions", q.instructions)
                    putJsonObject("criteria") { put("true", q.yes); put("false", q.no) }
                }
            }
        }
        val res = post("$baseUrl/api/alpha/decisions", mapOf("Authorization" to "Bearer $apiKey"), body, "OpenRouter")
        val answers = res["answers"] as? JsonObject ?: return emptyMap()
        return questions.associate { q ->
            val a = answers[q.key] as? JsonObject
            q.key to a?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "noul" }?.get("noul")?.jsonPrimitive?.doubleOrNull
        }
    }
}
