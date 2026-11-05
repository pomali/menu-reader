package sk.menureader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sk.menureader.MenuReaderApp
import sk.menureader.cloud.CloudUpgradeWorker

class MainActivity : ComponentActivity() {
    /** Content shared into the app that hasn't been imported yet. */
    private var pendingShare by mutableStateOf<SharedContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingShare = SharedContent.from(intent)
        val app = application as MenuReaderApp
        setContent {
            val large by app.settings.largeText.collectAsState()
            val dynamic by app.settings.dynamicColor.collectAsState()
            MenuTheme(large = large, dynamic = dynamic) { AppNav(app, pendingShare) { pendingShare = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SharedContent.from(intent)?.let { pendingShare = it }
    }
}

@Composable
private fun AppNav(app: MenuReaderApp, share: SharedContent?, shareHandled: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "history") {
        composable("history") {
            val vm = viewModel {
                HistoryViewModel(app.repository) { id ->
                    // Offline result first; the AI upgrade follows when online (if the user set it up).
                    val c = app.cloud.config.value
                    if (c.auto && app.cloud.reader() != null) CloudUpgradeWorker.enqueue(app, id)
                }
            }
            LaunchedEffect(share) {
                if (share != null) {
                    shareHandled()
                    vm.importShare(share) { nav.navigate("menu/$it") }
                }
            }
            HistoryScreen(vm, app.profiles, openSnapshot = { nav.navigate("menu/$it") }, openSettings = { nav.navigate("settings") })
        }
        composable("menu/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm = viewModel { MenuViewModel(app.repository, app.profiles, id, app) }
            val debugTools by app.settings.debugTools.collectAsState()
            val byCategory by app.settings.byCategory.collectAsState()
            MenuScreen(
                vm, app.profiles, debugTools, back = { nav.popBackStack() }, openDebug = { nav.navigate("debug/$id") },
                openSettings = { nav.navigate("settings") },
                byCategory = byCategory, setByCategory = app.settings::setByCategory,
            )
        }
        composable("debug/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm = viewModel { MenuViewModel(app.repository, app.profiles, id, app) }
            DebugScreen(vm, app.repository, back = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(app.settings, app.profiles, app.cloud, back = { nav.popBackStack() }) }
    }
    // A share that arrives while another screen is open: go back to History, which imports it.
    LaunchedEffect(share) {
        if (share != null && nav.currentDestination?.route != "history") nav.popBackStack("history", inclusive = false)
    }
}
