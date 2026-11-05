@file:OptIn(ExperimentalMaterial3Api::class)

package sk.menureader.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.menureader.R
import sk.menureader.core.parse.PlainText
import sk.menureader.data.ProfileStore
import sk.menureader.data.Repository
import sk.menureader.data.SnapshotRow
import java.text.DateFormat
import java.util.Date

sealed interface ImportState {
    data object Idle : ImportState
    data object Busy : ImportState
    data class Failed(val message: String) : ImportState
    /** The share contained only a link (e.g. Wolt's share button): nothing to read. */
    data object LinkOnly : ImportState
    data object NoScanner : ImportState
}

class HistoryViewModel(private val repo: Repository) : ViewModel() {
    val snapshots = repo.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val state = MutableStateFlow<ImportState>(ImportState.Idle)

    fun import(uris: List<Uri>, onDone: (String) -> Unit) {
        if (uris.isEmpty()) return
        run(onDone) { repo.createFromImages(uris) }
    }

    fun importShare(share: SharedContent, onDone: (String) -> Unit) = when (share) {
        is SharedContent.Images -> import(share.uris, onDone)
        is SharedContent.Text ->
            if (PlainText.isOnlyLinks(share.text)) state.value = ImportState.LinkOnly
            else run(onDone) { repo.createFromText(share.text, share.subject) }
    }

    private fun run(onDone: (String) -> Unit, block: suspend () -> String) {
        state.value = ImportState.Busy
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { state.value = ImportState.Idle; onDone(it) }
                .onFailure { state.value = ImportState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }
}

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    profiles: ProfileStore,
    openSnapshot: (String) -> Unit,
    openSettings: () -> Unit,
) {
    val rows by vm.snapshots.collectAsState()
    val state by vm.state.collectAsState()
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var showAvoid by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.import(uris, openSnapshot)
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages.orEmpty()
            vm.import(pages.map { it.imageUri }, openSnapshot)
        }
    }

    /** The ML Kit scanner (camera + gallery import). Without Play services, fall back to the photo picker. */
    fun startScan() {
        val act = activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(act)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                vm.state.value = ImportState.NoScanner
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
    }

    LaunchedEffect(state) {
        val msg = when (val s = state) {
            is ImportState.Failed -> ctx.getString(R.string.processing_failed, s.message)
            ImportState.LinkOnly -> ctx.getString(R.string.share_link_only)
            ImportState.NoScanner -> ctx.getString(R.string.scanner_unavailable)
            else -> null
        }
        if (msg != null) {
            vm.state.value = ImportState.Idle
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🍽️ " + stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showAvoid = true }) { Icon(Icons.Filled.Person, stringResource(R.string.avoid_title)) }
                    IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            if (state != ImportState.Busy) {
                ExtendedFloatingActionButton(
                    onClick = ::startScan,
                    icon = { Icon(painterResource(R.drawable.ic_camera), null) },
                    text = { Text(stringResource(R.string.scan)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state == ImportState.Busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            if (rows.isEmpty() && state != ImportState.Busy) EmptyState()
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.id }) { row -> HistoryRow(row) { openSnapshot(row.id) } }
            }
        }
    }
    if (showAvoid) AvoidSheet(profiles) { showAvoid = false }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🥟🍝🥗", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.share_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HistoryRow(row: SnapshotRow, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.capturedAt))
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Text(if (row.dishCount == 0) "🤷" else "📋", fontSize = 28.sp) },
            headlineContent = { Text(row.restaurantName ?: stringResource(R.string.unnamed_restaurant)) },
            supportingContent = { Text("$date · ${pluralStringResource(R.plurals.dish_count, row.dishCount, row.dishCount)}") },
        )
    }
}
