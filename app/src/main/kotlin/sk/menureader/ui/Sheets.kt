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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.menureader.R
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.Layer
import sk.menureader.core.model.Price
import sk.menureader.data.ProfileStore
import kotlin.math.roundToLong

/** Pick allergens and other ingredients to avoid. Saved to the profile right away. */
@Composable
fun AvoidSheet(store: ProfileStore, onDismiss: () -> Unit) {
    val p by store.profile.collectAsState()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(R.string.avoid_title), style = MaterialTheme.typography.headlineSmall)
            SheetLabel(stringResource(R.string.avoid_allergens))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Allergen.entries) {
                    FilterChip(
                        selected = a in p.avoid,
                        onClick = { store.update { it.copy(avoid = if (a in it.avoid) it.avoid - a else it.avoid + a) } },
                        label = { Text(label(a)) },
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
                        label = { Text(label(t)) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** What a tapped chip means. */
sealed interface ChipInfo {
    data class Claim(
        val label: String,
        val layer: Layer,
        val evidence: String,
        val printedCode: Int?,
        val avoided: Boolean,
        /** The menu prints codes for this dish but not this allergen. */
        val mismatch: Boolean,
    ) : ChipInfo
    data object LowConfidence : ChipInfo
}

@Composable
fun ChipSheet(info: ChipInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            when (info) {
                is ChipInfo.Claim -> {
                    Text(info.label, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    val (title, body) = when (info.layer) {
                        Layer.PRINTED -> stringResource(R.string.claim_printed_title) to
                            stringResource(R.string.claim_printed_body, info.printedCode ?: 0)
                        Layer.LEXICAL -> stringResource(R.string.claim_lexical_title) to
                            stringResource(R.string.claim_lexical_body, info.evidence.ifBlank { "?" })
                        Layer.INFERRED -> stringResource(R.string.claim_inferred_title) to
                            stringResource(R.string.claim_inferred_body, info.evidence.ifBlank { "?" })
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                    if (info.mismatch) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.claim_mismatch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (info.avoided) {
                        Spacer(Modifier.height(8.dp))
                        Text("🙅 " + stringResource(R.string.you_avoid), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                ChipInfo.LowConfidence -> {
                    Text(stringResource(R.string.low_confidence_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.low_confidence_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.claim_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Max-price slider. [maxOnMenu] is the most expensive dish, in minor units. */
@Composable
fun PriceSheet(current: Long?, maxOnMenu: Long, currency: String?, onChange: (Long?) -> Unit, onDismiss: () -> Unit) {
    val top = ((maxOnMenu + 49) / 50 * 50).coerceAtLeast(100)
    var value by remember { mutableFloatStateOf((current ?: top).toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.price_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            val rounded = (value / 50f).roundToLong() * 50
            Text(stringResource(R.string.price_upto, Price(rounded, currency).format()), style = MaterialTheme.typography.titleLarge)
            Slider(
                value = value, onValueChange = { value = it }, valueRange = 0f..top.toFloat(),
                onValueChangeFinished = { onChange((value / 50f).roundToLong() * 50) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onChange(null); onDismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.price_clear)) }
                Button(onClick = { onChange(rounded); onDismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
