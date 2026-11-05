package sk.menureader.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CloudConfig(
    /** null = AI reading off (everything stays on the phone). */
    val provider: Provider? = null,
    val models: Map<Provider, String> = emptyMap(),
    /** Upgrade every new menu automatically as soon as the phone is online. */
    val auto: Boolean = true,
) {
    fun model(p: Provider) = models[p]?.ifBlank { null } ?: p.defaultModel
}

/** Which cloud reader to use. API keys are in [KeyVault], not here. */
class CloudSettings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    val vault = KeyVault(ctx)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<CloudConfig> = _config
    /** Bumped when a key changes, so the UI can re-read [KeyVault.has]. */
    val keysVersion = MutableStateFlow(0)

    private fun load() = CloudConfig(
        provider = prefs.getString("provider", null)?.let { runCatching { Provider.valueOf(it) }.getOrNull() },
        models = Provider.entries.mapNotNull { p -> prefs.getString("model.${p.name}", null)?.let { p to it } }.toMap(),
        auto = prefs.getBoolean("auto", true),
    )

    fun update(f: (CloudConfig) -> CloudConfig) {
        val c = f(_config.value)
        prefs.edit().apply {
            if (c.provider == null) remove("provider") else putString("provider", c.provider.name)
            c.models.forEach { (p, m) -> putString("model.${p.name}", m) }
            putBoolean("auto", c.auto)
        }.apply()
        _config.value = c
    }

    fun setKey(kind: KeyKind, key: String?) { vault.set(kind, key); keysVersion.value++ }

    /** The reader for the chosen provider, or null if AI reading is off or the key is missing. */
    fun reader(): Pair<Provider, CloudReader>? {
        val c = _config.value
        val p = c.provider ?: return null
        val key = vault.get(p.key) ?: return null
        val model = c.model(p)
        val reader = when (p) {
            Provider.ANTHROPIC -> AnthropicReader(key, model)
            Provider.OPENAI -> OpenAiReader(key, model)
            Provider.GEMINI, Provider.NANO_BANANA -> GeminiReader(key, model, structured = p.structured)
        }
        return p to reader
    }
}
