@file:OptIn(ExperimentalLayoutApi::class)

package sk.menureader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sk.menureader.R
import sk.menureader.cloud.CloudConfig
import sk.menureader.cloud.CloudSettings
import sk.menureader.cloud.KeyKind
import sk.menureader.cloud.Provider

/**
 * "✨ AI reading" settings: menu reader (provider, model, the user's own API key), the OpenRouter
 * allergen double-check, and auto-upgrade.
 */
@Composable
fun AiSettings(cloud: CloudSettings) {
    val c by cloud.config.collectAsState()
    Column {
        Text(
            stringResource(R.string.ai_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        val options: List<Provider?> = listOf(null) + Provider.entries
        for (p in options) {
            ListItem(
                leadingContent = { RadioButton(selected = c.provider == p, onClick = null) },
                headlineContent = { Text(p?.title ?: stringResource(R.string.ai_off)) },
                supportingContent = when (p) {
                    null -> ({ Text(stringResource(R.string.ai_off_hint)) })
                    Provider.NANO_BANANA -> ({ Text(stringResource(R.string.ai_nano_hint)) })
                    else -> null
                },
                modifier = Modifier.clickable { cloud.update { it.copy(provider = p) } },
            )
        }
        c.provider?.let { ProviderDetails(cloud, c, it) }
        DecisionsSettings(cloud, c)
        ListItem(
            headlineContent = { Text(stringResource(R.string.ai_auto)) },
            supportingContent = { Text(stringResource(R.string.ai_auto_hint)) },
            trailingContent = { Switch(checked = c.auto, onCheckedChange = { on -> cloud.update { it.copy(auto = on) } }) },
            modifier = Modifier.clickable { cloud.update { it.copy(auto = !it.auto) } },
        )
    }
}

@Composable
private fun ProviderDetails(cloud: CloudSettings, c: CloudConfig, p: Provider) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        var model by remember(p) { mutableStateOf(c.model(p)) }
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; cloud.update { cfg -> cfg.copy(models = cfg.models + (p to it.trim())) } },
            label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in p.models) {
                FilterChip(
                    selected = model == m,
                    onClick = { model = m; cloud.update { cfg -> cfg.copy(models = cfg.models + (p to m)) } },
                    label = { Text(m) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyField(cloud, p.key, p.keyUrl, p.title)
    }
}

/** OpenRouter Decisions API: a yes/no-with-probability double-check for what the user avoids. */
@Composable
private fun DecisionsSettings(cloud: CloudSettings, c: CloudConfig) {
    ListItem(
        headlineContent = { Text("🧑‍⚖️ " + stringResource(R.string.decisions_title)) },
        supportingContent = { Text(stringResource(R.string.decisions_hint)) },
        trailingContent = { Switch(checked = c.decisions, onCheckedChange = { on -> cloud.update { it.copy(decisions = on) } }) },
        modifier = Modifier.clickable { cloud.update { it.copy(decisions = !it.decisions) } },
    )
    if (!c.decisions) return
    Column(Modifier.padding(horizontal = 16.dp)) {
        var model by remember { mutableStateOf(c.decisionsModel) }
        OutlinedTextField(
            value = model, onValueChange = { model = it; cloud.update { cfg -> cfg.copy(decisionsModel = it.trim()) } },
            label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KeyField(cloud, KeyKind.OPENROUTER, "https://openrouter.ai/settings/keys", "OpenRouter")
    }
}

@Composable
private fun KeyField(cloud: CloudSettings, kind: KeyKind, keyUrl: String, who: String) {
    val ctx = LocalContext.current
    val keysVersion by cloud.keysVersion.collectAsState()
    val hasKey = remember(kind, keysVersion) { cloud.vault.has(kind) }
    var key by remember(kind) { mutableStateOf("") }
    OutlinedTextField(
        value = key, onValueChange = { key = it },
        label = { Text(stringResource(R.string.ai_key)) },
        placeholder = { Text(if (hasKey) stringResource(R.string.ai_key_saved) else stringResource(R.string.ai_key_hint)) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { cloud.setKey(kind, key); key = "" }, enabled = key.isNotBlank()) { Text(stringResource(R.string.save)) }
        Spacer(Modifier.width(8.dp))
        if (hasKey) TextButton(onClick = { cloud.setKey(kind, null) }) { Text(stringResource(R.string.ai_key_remove)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl))) }) { Text(stringResource(R.string.ai_get_key)) }
    }
    Text(
        (if (hasKey) "🔐 " else "🔑 ") + stringResource(R.string.ai_privacy, who),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
