@file:OptIn(ExperimentalMaterial3Api::class)

package sk.menureader.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.menureader.R
import sk.menureader.core.filter.Strictness
import sk.menureader.core.model.Allergen
import sk.menureader.data.ProfileStore
import sk.menureader.data.SettingsStore

@Composable
fun SettingsScreen(settings: SettingsStore, profiles: ProfileStore, back: () -> Unit) {
    val large by settings.largeText.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    val debug by settings.debugTools.collectAsState()
    val p by profiles.profile.collectAsState()
    var showAvoid by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            title = { Text(stringResource(R.string.settings)) },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Group(stringResource(R.string.settings_food)) }
            item {
                val avoided = Allergen.entries.filter { it in p.avoid }.map { it.emoji } + avoidableTags.filter { it in p.avoidTags }.map { it.emoji }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.avoid_title)) },
                    supportingContent = { Text(if (avoided.isEmpty()) stringResource(R.string.avoid_none) else avoided.joinToString(" ")) },
                    modifier = Modifier.clickable { showAvoid = true },
                )
            }
            item { ListItem(headlineContent = { Text(stringResource(R.string.strictness)) }) }
            items(Strictness.entries) { s ->
                val text = stringResource(
                    when (s) {
                        Strictness.PRINTED_ONLY -> R.string.strictness_printed
                        Strictness.PLUS_LEXICAL -> R.string.strictness_lexical
                        Strictness.PLUS_INFERRED -> R.string.strictness_inferred
                    }
                )
                ListItem(
                    leadingContent = { RadioButton(selected = p.strictness == s, onClick = null) },
                    headlineContent = { Text(text) },
                    modifier = Modifier.clickable { profiles.update { it.copy(strictness = s) } },
                )
            }
            item { HorizontalDivider() }

            item { Group(stringResource(R.string.settings_display)) }
            item { SwitchItem(stringResource(R.string.large_text), stringResource(R.string.large_text_hint), large, settings::setLargeText) }
            if (Build.VERSION.SDK_INT >= 31) {
                item { SwitchItem(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_hint), dynamic, settings::setDynamicColor) }
            }
            item { HorizontalDivider() }

            item { Group(stringResource(R.string.settings_dev)) }
            item { SwitchItem(stringResource(R.string.debug_tools), stringResource(R.string.debug_tools_hint), debug, settings::setDebugTools) }
            item { HorizontalDivider() }

            item { Group(stringResource(R.string.settings_about)) }
            item {
                ListItem(
                    leadingContent = { Text("🤖") },
                    headlineContent = { Text(stringResource(R.string.app_name) + " " + appVersion()) },
                    supportingContent = { Text(stringResource(R.string.about_text)) },
                )
            }
        }
    }
    if (showAvoid) AvoidSheet(profiles) { showAvoid = false }
}

@Composable
private fun Group(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
)

@Composable
private fun SwitchItem(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(hint) },
    trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    modifier = Modifier.clickable { onChange(!checked) },
)

@Composable
private fun appVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
}
