@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sk.menureader.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.menureader.R
import sk.menureader.core.allergen.printedMismatches
import sk.menureader.core.filter.Query
import sk.menureader.core.filter.RankedDish
import sk.menureader.core.filter.RankedSection
import sk.menureader.core.filter.Ranker
import sk.menureader.core.filter.Reason
import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Price
import sk.menureader.data.ProfileStore
import sk.menureader.data.Repository
import sk.menureader.data.StoredSnapshot
import java.io.File

data class MenuUi(
    val stored: StoredSnapshot,
    val profile: UserProfile,
    val query: Query,
    val sections: List<RankedSection>,
    val counts: Map<Allergen, Int>,
)

class MenuViewModel(private val repo: Repository, private val profiles: ProfileStore, val id: String) : ViewModel() {
    val query = MutableStateFlow(Query())

    val ui = combine(repo.observe(id), profiles.profile, query) { stored, profile, q ->
        stored?.let {
            MenuUi(it, profile, q, Ranker.rank(it.snapshot, profile, q), Ranker.allergenCounts(it.snapshot, profile.strictness))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Toggling an allergen on the menu screen edits the profile, so it stays one source of truth. */
    fun toggleAvoid(a: Allergen) = profiles.update { it.copy(avoid = if (a in it.avoid) it.avoid - a else it.avoid + a) }
    fun setRestaurant(name: String) = viewModelScope.launch { repo.setRestaurantName(id, name) }
    fun rerun() = viewModelScope.launch { repo.rerun(id) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
    fun pageFile(pageId: String): File = repo.pageFile(id, pageId)
}

@Composable
fun MenuScreen(vm: MenuViewModel, back: () -> Unit, openDebug: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sourceOf by remember { mutableStateOf<Dish?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { TextButton(onClick = back) { Text("←") } },
            title = {
                Text(
                    ui?.stored?.restaurantName ?: stringResource(R.string.unassigned),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
            actions = {
                TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.restaurant)) }, onClick = { menuOpen = false; editName = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rerun)) }, onClick = { menuOpen = false; vm.rerun() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.debug)) }, onClick = { menuOpen = false; openDebug() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; confirmDelete = true })
                }
            },
        )
    }) { pad ->
        val u = ui ?: return@Scaffold
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Disclaimer() }
            item { Filters(u, vm) }
            if (u.stored.snapshot.dishes.isEmpty()) {
                item { Text(stringResource(R.string.no_dishes), Modifier.padding(16.dp)) }
            }
            for (sec in u.sections) {
                item(key = "h-" + sec.section.id) {
                    Text(
                        sec.section.name.ifBlank { stringResource(R.string.section_default) },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(sec.dishes, key = { it.dish.id }) { rd -> DishCard(rd, u.profile) { sourceOf = rd.dish } }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }

        if (editName) {
            var text by remember { mutableStateOf(u.stored.restaurantName.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editName = false },
                title = { Text(stringResource(R.string.restaurant)) },
                text = { OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(R.string.restaurant_hint)) }, singleLine = true) },
                confirmButton = { TextButton(onClick = { vm.setRestaurant(text); editName = false }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { editName = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                text = { Text(stringResource(R.string.delete_confirm)) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(back) }) { Text(stringResource(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        sourceOf?.let { d -> SourceDialog(d, vm) { sourceOf = null } }
    }
}

@Composable
private fun Disclaimer() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(stringResource(R.string.disclaimer), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Filters(u: MenuUi, vm: MenuViewModel) {
    val q = u.query
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Avoided allergens first, then the ones that occur on this menu, then the rest.
            val order = Allergen.entries.sortedWith(compareBy({ it !in u.profile.avoid }, { -(u.counts[it] ?: 0) }, { it.code }))
            for (a in order) {
                FilterChip(
                    selected = a in u.profile.avoid,
                    onClick = { vm.toggleAvoid(a) },
                    label = { Text("${label(a)} (${u.counts[a] ?: 0})") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q.text, onValueChange = { vm.query.value = q.copy(text = it) },
                label = { Text(stringResource(R.string.search)) }, singleLine = true,
                modifier = Modifier.weight(2f).padding(end = 8.dp),
            )
            var priceText by remember { mutableStateOf(q.maxPriceMinor?.let { Price(it, null).format() } ?: "") }
            OutlinedTextField(
                value = priceText,
                onValueChange = { t ->
                    priceText = t
                    vm.query.value = q.copy(maxPriceMinor = parseMinor(t))
                },
                label = { Text(stringResource(R.string.max_price)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun parseMinor(t: String): Long? {
    val n = t.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    return n.movePointRight(2).toLong()
}

@Composable
private fun DishCard(rd: RankedDish, profile: UserProfile, showSource: () -> Unit) {
    val d = rd.dish
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    d.portion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    for (p in d.prices) {
                        Text(listOfNotNull(p.variant, p.format()).joinToString("  "), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            d.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
            if (d.allergens.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow { for (c in d.allergens) AllergenChip(c, c.allergen in profile.avoid) }
            }
            Status(rd, profile)
            for (m in d.printedMismatches().filter { it.allergen in profile.avoid }) {
                Text(
                    stringResource(R.string.mismatch, label(m.allergen), m.evidence),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            if (d.confidence < 0.6) {
                Text(stringResource(R.string.low_confidence), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row {
                if (d.sourceBox != null) TextButton(onClick = showSource) { Text(stringResource(R.string.show_source)) }
                val shareTitle = stringResource(R.string.share)
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, listOfNotNull(d.name, d.description).joinToString("\n"))
                    ctx.startActivity(Intent.createChooser(send, shareTitle))
                }) { Text(shareTitle) }
            }
        }
    }
}

/** Never says "safe": either the reasons, or what was *not detected* and how it was checked. */
@Composable
private fun Status(rd: RankedDish, profile: UserProfile) {
    Spacer(Modifier.height(4.dp))
    if (rd.flagged) {
        val parts = rd.reasons.map { r ->
            when (r) {
                is Reason.Contains -> stringResource(R.string.reason_contains, label(r.claim.allergen), label(r.claim.layer), r.claim.evidence)
                is Reason.Tag -> stringResource(R.string.reason_tag, label(r.tag))
                is Reason.MissingTag -> stringResource(R.string.reason_missing_tag, label(r.tag))
                is Reason.OverPrice -> stringResource(R.string.reason_over_price, Price(r.maxMinor, null).format())
                Reason.NoTextMatch -> stringResource(R.string.reason_no_match)
                Reason.OtherSection -> stringResource(R.string.reason_other_section)
            }
        }
        Text("⚠ " + parts.joinToString("; "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } else {
        val avoided = profile.avoid.sortedBy { it.code }.map { label(it) }
        val layers = profile.strictness.layers.map { label(it) }.joinToString(", ")
        val head = if (avoided.isEmpty()) stringResource(R.string.not_detected_none) else stringResource(R.string.not_detected, avoided.joinToString(", "))
        val tail = buildList {
            add(stringResource(R.string.checked_layers, layers))
            if (rd.noPrintedCodes) add(stringResource(R.string.no_codes_printed))
        }.joinToString("; ")
        Text("$head ($tail)", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SourceDialog(d: Dish, vm: MenuViewModel, close: () -> Unit) {
    val box = d.sourceBox ?: return
    val bmp by produceState<Bitmap?>(null, d.id) {
        value = withContext(Dispatchers.IO) { crop(vm.pageFile(box.pageId), box.bbox) }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                bmp?.let { Image(it.asImageBitmap(), contentDescription = d.name, modifier = Modifier.fillMaxWidth()) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(d.printedCodes.joinToString(", ").ifEmpty { "–" }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } },
    )
}

/** Crop the dish's bounding box (plus some context) from the page photo. */
private fun crop(file: File, b: sk.menureader.core.model.BBox): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2400) sample *= 2
    val full = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val padX = b.width / 6 + 20
    val padY = b.height + 20
    val l = ((b.left - padX) / sample).coerceIn(0, full.width - 1)
    val t = ((b.top - padY) / sample).coerceIn(0, full.height - 1)
    val r = ((b.right + padX) / sample).coerceIn(l + 1, full.width)
    val btm = ((b.bottom + padY) / sample).coerceIn(t + 1, full.height)
    return Bitmap.createBitmap(full, l, t, r - l, btm - t)
}

