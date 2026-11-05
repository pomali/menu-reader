@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sk.menureader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.menureader.R
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Price
import sk.menureader.data.ProfileStore
import kotlin.math.roundToLong

/** Price section of the filter sheet. [maxOnMenu] is the most expensive dish, in minor units. */
class PriceFilter(val current: Long?, val maxOnMenu: Long, val currency: String?, val onChange: (Long?) -> Unit)

/**
 * What to avoid (saved to the profile right away). From a menu it is the filter sheet: counts of
 * matching dishes on this menu next to each item, plus the max-price slider.
 */
@Composable
fun AvoidSheet(
    store: ProfileStore,
    counts: Map<Allergen, Int>? = null,
    tagCounts: Map<DietTag, Int>? = null,
    price: PriceFilter? = null,
    onDismiss: () -> Unit,
) {
    val p by store.profile.collectAsState()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun n(c: Int?) = if (c != null && c > 0) "  $c" else ""
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(if (price != null) R.string.filter_title else R.string.avoid_title), style = MaterialTheme.typography.headlineSmall)
            if (price != null) PriceSection(price)
            SheetLabel(stringResource(R.string.avoid_allergens))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Allergen.entries) {
                    FilterChip(
                        selected = a in p.avoid,
                        onClick = { store.update { it.copy(avoid = if (a in it.avoid) it.avoid - a else it.avoid + a) } },
                        label = { Text(label(a) + n(counts?.get(a))) },
                    )
                }
            }
            SheetLabel(stringResource(R.string.avoid_other))
            Text(stringResource(R.string.avoid_other_hint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in avoidableTags) {
                    FilterChip(
                        selected = t in p.avoidTags,
                        onClick = { store.update { it.copy(avoidTags = if (t in it.avoidTags) it.avoidTags - t else it.avoidTags + t) } },
                        label = { Text(label(t) + n(tagCounts?.get(t))) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PriceSection(price: PriceFilter) {
    val top = ((price.maxOnMenu + 49) / 50 * 50).coerceAtLeast(100)
    var value by remember { mutableFloatStateOf((price.current ?: top).toFloat()) }
    val rounded = (value / 50f).roundToLong() * 50
    SheetLabel("💸 " + stringResource(R.string.price_title))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (price.current == null) stringResource(R.string.price_any) else stringResource(R.string.price_upto, Price(rounded, price.currency).format()),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
        )
        if (price.current != null) TextButton(onClick = { value = top.toFloat(); price.onChange(null) }) { Text(stringResource(R.string.price_clear)) }
    }
    Slider(
        value = value, onValueChange = { value = it }, valueRange = 0f..top.toFloat(),
        onValueChangeFinished = { price.onChange(((value / 50f).roundToLong() * 50).takeIf { it < top }) },
    )
}

@Composable
private fun SheetLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
