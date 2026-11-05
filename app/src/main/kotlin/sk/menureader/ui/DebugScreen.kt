@file:OptIn(ExperimentalMaterial3Api::class)

package sk.menureader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.menureader.R
import sk.menureader.core.MenuJson
import sk.menureader.core.model.OcrDocument
import sk.menureader.data.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 0 helper: shows the raw OCR and exports it (and the parsed snapshot) as JSON, so the
 * dumps can become JVM test fixtures in samples/ocr/.
 */
@Composable
fun DebugScreen(vm: MenuViewModel, repo: Repository, back: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.saved)
    var pendingText = ""
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            repo.writeTo(uri, pendingText)
            Toast.makeText(ctx, saved, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { TextButton(onClick = back) { Text("←") } },
            title = { Text(stringResource(R.string.debug)) },
        )
    }) { pad ->
        val u = ui ?: return@Scaffold
        val s = u.stored.snapshot
        val ocr = runCatching { MenuJson.decodeFromString<OcrDocument>(u.stored.ocrJson) }.getOrNull()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(s.capturedAt))
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pendingText = u.stored.ocrJson; export.launch("ocr-$stamp.json") }) {
                    Text(stringResource(R.string.export_ocr))
                }
                Button(onClick = { pendingText = MenuJson.encodeToString(s); export.launch("snapshot-$stamp.json") }) {
                    Text(stringResource(R.string.export_snapshot))
                }
            }
            s.allergenLegend?.let { legend ->
                Section(stringResource(R.string.legend))
                Text(legend.entries.joinToString("\n") { (n, a) -> "$n → ${a.code} ${a.en}" }, fontFamily = FontFamily.Monospace)
            }
            if (s.warnings.isNotEmpty()) {
                Section(stringResource(R.string.warnings))
                s.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            val lines = ocr?.pages?.flatMap { p -> p.lines.map { "[${p.pageId} y=${it.box.top}] ${it.text}" } }.orEmpty()
            Section(stringResource(R.string.ocr_text, lines.size))
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun Section(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
