package sk.menureader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sk.menureader.MenuReaderApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MenuReaderApp
        setContent { MenuTheme { AppNav(app) } }
    }
}

@Composable
private fun AppNav(app: MenuReaderApp) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "history") {
        composable("history") {
            val vm = viewModel { HistoryViewModel(app.repository) }
            HistoryScreen(vm, openSnapshot = { nav.navigate("menu/$it") }, openProfile = { nav.navigate("profile") })
        }
        composable("menu/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm = viewModel { MenuViewModel(app.repository, app.profiles, id) }
            MenuScreen(vm, back = { nav.popBackStack() }, openDebug = { nav.navigate("debug/$id") })
        }
        composable("debug/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm = viewModel { MenuViewModel(app.repository, app.profiles, id) }
            DebugScreen(vm, app.repository, back = { nav.popBackStack() })
        }
        composable("profile") { ProfileScreen(app.profiles, back = { nav.popBackStack() }) }
    }
}
