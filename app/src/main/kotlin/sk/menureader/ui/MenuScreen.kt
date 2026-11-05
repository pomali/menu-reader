@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package sk.menureader.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import sk.menureader.MenuReaderApp
import sk.menureader.R
import sk.menureader.cloud.CloudUpgradeWorker
import sk.menureader.core.filter.Query
import sk.menureader.core.filter.RankedDish
import sk.menureader.core.filter.RankedSection
import sk.menureader.core.filter.Ranker
import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Dish
import sk.menureader.core.model.Layer
import sk.menureader.core.model.Price
import sk.menureader.core.model.Quality
import sk.menureader.data.ProfileStore
import sk.menureader.data.Repository
import sk.menureader.data.StoredSnapshot
import java.io.File
import java.text.DateFormat
import java.util.Date

data class MenuUi(
    val stored: StoredSnapshot,
    val profile: UserProfile,
    val query: Query,
    val sections: List<RankedSection>,
    val counts: Map<Allergen, Int>,
    val tagCounts: Map<DietTag, Int>,
)

/** State of the AI upgrade for this menu. */
sealed interface AiState {
    data object Idle : AiState
    data object Waiting : AiState
    data object Running : AiState
    data class Failed(val message: String) : AiState
}

class MenuViewModel(
    private val repo: Repository,
    private val profiles: ProfileStore,
    val id: String,
    private val app: MenuReaderApp? = null,
) : ViewModel() {
    val query = MutableStateFlow(Query())

    val ai = (app?.let { WorkManager.getInstance(it).getWorkInfosForUniqueWorkFlow(CloudUpgradeWorker.workName(id)) } ?: flowOf(emptyList()))
        .map { infos ->
            val w = infos.lastOrNull() ?: return@map AiState.Idle
            when (w.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> AiState.Waiting
                WorkInfo.State.RUNNING -> AiState.Running
                WorkInfo.State.FAILED -> AiState.Failed(w.outputData.getString(CloudUpgradeWorker.KEY_ERROR) ?: "?")
                else -> AiState.Idle
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiState.Idle)

    /** False when AI reading isn't set up (no provider or no key): the UI points to Settings. */
    fun readWithAi(): Boolean {
        val a = app ?: return false
        if (a.cloud.reader() == null) return false
        CloudUpgradeWorker.enqueue(a, id)
        return true
    }

    val ui = combine(repo.observe(id), profiles.profile, query) { stored, profile, q ->
        stored?.let {
            MenuUi(
                it, profile, q, Ranker.rank(it.snapshot, profile, q),
                Ranker.allergenCounts(it.snapshot, profile.strictness), Ranker.tagCounts(it.snapshot, profile.strictness),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Chips on the menu screen edit the profile, so it stays one source of truth. */
    fun toggleAvoid(a: Allergen) = profiles.update { it.copy(avoid = if (a in it.avoid) it.avoid - a else it.avoid + a) }
    fun toggleAvoid(t: DietTag) = profiles.update { it.copy(avoidTags = if (t in it.avoidTags) it.avoidTags - t else it.avoidTags + t) }
    fun setRestaurant(name: String) = viewModelScope.launch { repo.setRestaurantName(id, name) }
    fun rerun() = viewModelScope.launch { repo.rerun(id) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
    fun pageFile(pageId: String): File = repo.pageFile(id, pageId)
}

@Composable
fun MenuScreen(
    vm: MenuViewModel,
    profiles: ProfileStore,
    debugTools: Boolean,
    back: () -> Unit,
    openDebug: () -> Unit,
    openSettings: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val ai by vm.ai.collectAsState()
    var askSetup by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var showAvoid by remember { mutableStateOf(false) }
    var showPrice by remember { mutableStateOf(false) }
    var chipInfo by remember { mutableStateOf<ChipInfo?>(null) }
    var sourceOf by remember { mutableStateOf<Dish?>(null) }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scroll,
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    if (searching) {
                        val q = ui?.query ?: Query()
                        TextField(
                            value = q.text, onValueChange = { vm.query.value = q.copy(text = it) },
                            placeholder = { Text(stringResource(R.string.search)) }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; vm.query.value = vm.query.value.copy(text = "") }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.close))
                        }
                    } else {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("✨ " + stringResource(R.string.read_with_ai)) },
                                onClick = { menuOpen = false; if (!vm.readWithAi()) askSetup = true },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; editName = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.rerun)) }, onClick = { menuOpen = false; vm.rerun() })
                            if (debugTools) DropdownMenuItem(text = { Text(stringResource(R.string.debug)) }, onClick = { menuOpen = false; openDebug() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        val u = ui ?: return@Scaffold
        val hasPhotos = u.stored.snapshot.pages.isNotEmpty()
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item { Header(u) { editName = true } }
            if (ai != AiState.Idle) item { AiStatus(ai) { if (!vm.readWithAi()) askSetup = true } }
            item { FilterRow(u, vm, onEditAvoid = { showAvoid = true }, onPrice = { showPrice = true }) }
            if (u.stored.snapshot.dishes.isEmpty()) {
                item { Text(stringResource(R.string.no_dishes), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            }
            for (sec in u.sections) {
                stickyHeader(key = "h-" + sec.section.id) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            sec.section.name.ifBlank { stringResource(R.string.section_default) },
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                }
                items(sec.dishes, key = { it.dish.id }) { rd ->
                    DishCard(rd, u.profile, hasPhotos, onChip = { chipInfo = it }, onPhoto = { sourceOf = rd.dish })
                }
            }
        }

        if (editName) {
            var text by remember { mutableStateOf(u.stored.restaurantName.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editName = false },
                title = { Text(stringResource(R.string.rename)) },
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
        if (askSetup) {
            AlertDialog(
                onDismissRequest = { askSetup = false },
                title = { Text("✨ " + stringResource(R.string.ai_setup_title)) },
                text = { Text(stringResource(R.string.ai_setup_body)) },
                confirmButton = { TextButton(onClick = { askSetup = false; openSettings() }) { Text(stringResource(R.string.settings)) } },
                dismissButton = { TextButton(onClick = { askSetup = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (showAvoid) AvoidSheet(profiles) { showAvoid = false }
        if (showPrice) {
            val max = u.stored.snapshot.dishes.flatMap { it.prices }.maxOfOrNull { it.amountMinor } ?: 2000
            PriceSheet(u.query.maxPriceMinor, max, u.stored.snapshot.currency, onChange = { vm.query.value = vm.query.value.copy(maxPriceMinor = it) }) { showPrice = false }
        }
        chipInfo?.let { ChipSheet(it) { chipInfo = null } }
        sourceOf?.let { d -> SourceDialog(d, vm) { sourceOf = null } }
    }
}

@Composable
private fun AiStatus(ai: AiState, retry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when (ai) {
                AiState.Waiting -> Text("📡 " + stringResource(R.string.ai_waiting), Modifier.weight(1f))
                AiState.Running -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ai_running), Modifier.weight(1f))
                }
                is AiState.Failed -> {
                    Text("😵 " + stringResource(R.string.ai_failed, ai.message), Modifier.weight(1f))
                    TextButton(onClick = retry) { Text(stringResource(R.string.ai_retry)) }
                }
                AiState.Idle -> {}
            }
        }
    }
}

/** Restaurant name (scrolls away; tap to rename), date, and a cheerful summary. */
@Composable
private fun Header(u: MenuUi, onRename: () -> Unit) {
    val s = u.stored.snapshot
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.clickable(onClick = onRename), verticalAlignment = Alignment.CenterVertically) {
            Text(
                u.stored.restaurantName ?: stringResource(R.string.unnamed_restaurant),
                style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Edit, stringResource(R.string.rename), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.capturedAt))
        val quality = if (s.quality == Quality.FULL) "✨ " + stringResource(R.string.quality_full) + (s.extraction.model?.let { " ($it)" } ?: "")
        else stringResource(R.string.quality_basic)
        Text("$date · $quality", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val all = u.sections.flatMap { it.dishes }
        if (all.isNotEmpty() && (u.profile.avoid.isNotEmpty() || u.profile.avoidTags.isNotEmpty())) {
            val ok = all.count { !it.flagged }
            val flagged = all.size - ok
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    ok == 0 -> stringResource(R.string.summary_none)
                    flagged == 0 -> stringResource(R.string.summary_all)
                    else -> pluralStringResource(R.plurals.summary_ok, ok, ok) + "  ·  " + pluralStringResource(R.plurals.summary_flagged, flagged, flagged)
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * First chip opens the avoid sheet, second the price sheet; then a quick toggle for every allergen
 * and ingredient that actually occurs on this menu, in EU number order (ingredients after).
 */
@Composable
private fun FilterRow(u: MenuUi, vm: MenuViewModel, onEditAvoid: () -> Unit, onPrice: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val n = u.profile.avoid.size + u.profile.avoidTags.size
            AssistChip(
                onClick = onEditAvoid,
                label = { Text(if (n == 0) stringResource(R.string.avoid_none) else "${stringResource(R.string.avoid_chip)} · $n") },
                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) },
            )
        }
        item {
            val max = u.query.maxPriceMinor
            FilterChip(
                selected = max != null, onClick = onPrice,
                label = { Text("💸 " + if (max == null) stringResource(R.string.price_any) else stringResource(R.string.price_upto, Price(max, u.stored.snapshot.currency).format())) },
            )
        }
        items(Allergen.entries.filter { (u.counts[it] ?: 0) > 0 }, key = { "a" + it.name }) { a ->
            FilterChip(selected = a in u.profile.avoid, onClick = { vm.toggleAvoid(a) }, label = { Text("${label(a)}  ${u.counts[a]}") })
        }
        items(avoidableTags.filter { (u.tagCounts[it] ?: 0) > 0 }, key = { "t" + it.name }) { t ->
            FilterChip(selected = t in u.profile.avoidTags, onClick = { vm.toggleAvoid(t) }, label = { Text("${label(t)}  ${u.tagCounts[t]}") })
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DishCard(rd: RankedDish, profile: UserProfile, hasPhotos: Boolean, onChip: (ChipInfo) -> Unit, onPhoto: () -> Unit) {
    val d = rd.dish
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    // Flagged dishes get a red outline; the red chips inside say why.
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (rd.flagged) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    d.portion?.let { Text("⚖️ $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    d.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    for (p in d.prices) {
                        Text(p.format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        p.variant?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (hasPhotos && d.sourceBox != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.show_photo)) }, onClick = { menu = false; onPhoto() })
                        }
                        val shareTitle = stringResource(R.string.share)
                        DropdownMenuItem(text = { Text(shareTitle) }, onClick = {
                            menu = false
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, listOfNotNull(d.name, d.description).joinToString("\n"))
                            ctx.startActivity(Intent.createChooser(send, shareTitle))
                        })
                    }
                }
            }
            val allergenChips = d.allergens.sortedBy { it.allergen.code }
            val tagChips = d.tagClaims.filter { it.tag in profile.avoidTags }.distinctBy { it.tag }
            if (allergenChips.isNotEmpty() || tagChips.isNotEmpty() || d.confidence < 0.6) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.padding(end = 12.dp)) {
                    for (c in allergenChips) {
                        val text = label(c.allergen)
                        val avoided = c.allergen in profile.avoid
                        LayerChip(text, c.layer, avoided) {
                            onChip(ChipInfo.Claim(text, c.layer, c.evidence, c.allergen.code.takeIf { c.layer == Layer.PRINTED }, avoided,
                                mismatch = d.printedCodes.isNotEmpty() && c.layer != Layer.PRINTED))
                        }
                    }
                    for (c in tagChips) {
                        val text = label(c.tag)
                        LayerChip(text, c.layer, avoided = true) { onChip(ChipInfo.Claim(text, c.layer, c.evidence, null, true, false)) }
                    }
                    if (d.confidence < 0.6) NoteChip("🤔 " + stringResource(R.string.low_confidence_chip)) { onChip(ChipInfo.LowConfidence) }
                }
            }
        }
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
        text = { bmp?.let { Image(it.asImageBitmap(), contentDescription = d.name, modifier = Modifier.fillMaxWidth()) } ?: Text("📷…", fontSize = 32.sp) },
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
