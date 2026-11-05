@file:OptIn(ExperimentalMaterial3Api::class)

package sk.menureader.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
fun HistoryScreen(vm: HistoryViewModel, openSnapshot: (String) -> Unit, openProfile: () -> Unit) {
    val rows by vm.snapshots.collectAsState()
    val state by vm.state.collectAsState()
    val activity = LocalActivity.current

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages.orEmpty()
            vm.import(pages.map { it.imageUri }, openSnapshot)
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.import(uris, openSnapshot)
    }
    val scannerUnavailable = stringResource(R.string.scanner_unavailable)

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val act = activity ?: return
        GmsDocumentScanning.getClient(options).getStartScanIntent(act)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.state.value = ImportState.Failed(scannerUnavailable) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = openProfile) { Text(stringResource(R.string.profile)) } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::startScan, enabled = state != ImportState.Busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan))
                }
                OutlinedButton(
                    onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = state != ImportState.Busy, modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.pick_photos)) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                ImportState.Busy -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.processing))
                }
                is ImportState.Failed -> Text(
                    stringResource(R.string.processing_failed, s.message),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp),
                )
                ImportState.LinkOnly -> Text(
                    stringResource(R.string.share_link_only),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp),
                )
                ImportState.Idle -> {}
            }
            if (rows.isEmpty() && state == ImportState.Idle) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.id }) { row -> HistoryRow(row) { openSnapshot(row.id) } }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: SnapshotRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(row.restaurantName ?: stringResource(R.string.unassigned), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.capturedAt))
            val quality = stringResource(if (row.quality == "FULL") R.string.quality_full else R.string.quality_basic)
            Text(
                "$date · ${pluralStringResource(R.plurals.dish_count, row.dishCount, row.dishCount)} · $quality",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
