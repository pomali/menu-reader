@file:OptIn(ExperimentalMaterial3Api::class)

package sk.menureader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.menureader.R
import sk.menureader.core.filter.Strictness
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.data.ProfileStore

private val avoidableTags = listOf(DietTag.PORK, DietTag.BEEF, DietTag.POULTRY, DietTag.ALCOHOL, DietTag.SPICY)

@Composable
fun ProfileScreen(store: ProfileStore, back: () -> Unit) {
    val p by store.profile.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { TextButton(onClick = back) { Text("←") } },
            title = { Text(stringResource(R.string.profile)) },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Header(stringResource(R.string.avoid_allergens)) }
            items(Allergen.entries) { a ->
                CheckRow(label(a), a in p.avoid) { on -> store.update { it.copy(avoid = if (on) it.avoid + a else it.avoid - a) } }
            }
            item { Header(stringResource(R.string.avoid_tags)) }
            items(avoidableTags) { t ->
                CheckRow(label(t), t in p.avoidTags) { on -> store.update { it.copy(avoidTags = if (on) it.avoidTags + t else it.avoidTags - t) } }
            }
            item { Header(stringResource(R.string.strictness)) }
            items(Strictness.entries) { s ->
                val text = stringResource(
                    when (s) {
                        Strictness.PRINTED_ONLY -> R.string.strictness_printed
                        Strictness.PLUS_LEXICAL -> R.string.strictness_lexical
                        Strictness.PLUS_INFERRED -> R.string.strictness_inferred
                    }
                )
                Row(
                    Modifier.fillMaxWidth().clickable { store.update { it.copy(strictness = s) } }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = p.strictness == s, onClick = { store.update { it.copy(strictness = s) } })
                    Text(text)
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text)
    }
}
