package sk.menureader

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.ui.HistoryScreen
import sk.menureader.ui.HistoryViewModel
import sk.menureader.ui.MenuScreen
import sk.menureader.ui.MenuTheme
import sk.menureader.ui.MenuViewModel
import sk.menureader.ui.SettingsScreen

/**
 * Renders the main screens on the JVM (no emulator needed) with sample data.
 * Output: PNGs in app/build/screenshots. Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<MenuReaderApp>()

    private val sample = """
        DENNÉ MENU
        Polievky
        Kapustnica s klobásou 0,33l (1,7,9) 2,50 €
        Cesnaková polievka so syrom (1,7) 2,20 €
        Hlavné jedlá
        M1: 200g/200g Bravčový stroganov, ½ ryža, ½ hranolky (1,7) 7,50 €
        M2: Vyprážaný syr, hranolky, tatárska omáčka (1,3,7) 7,90 €
        M3: Pizza Hawai
        paradajkový základ, šunka, ananás, syr
        8,90 €
        M4: Zeleninové rizoto so smotanou 6,90 €
        M5: Grilovaný losos, citrónové maslo 11,90 €
    """.trimIndent()

    private fun shot(name: String, large: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MenuTheme(large = large) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun menuScreen(large: Boolean, name: String) {
        val id = runBlocking { app.repository.createFromText(sample, "Bistro Pod Lipou") }
        app.profiles.update { it.copy(avoid = setOf(Allergen.EGGS, Allergen.FISH), avoidTags = setOf(DietTag.PINEAPPLE, DietTag.GARLIC)) }
        val vm = MenuViewModel(app.repository, app.profiles, id)
        compose.setContent { MenuTheme(large = large) { MenuScreen(vm, app.profiles, debugTools = true, back = {}, openDebug = {}) } }
        // Room delivers on its own executor and resumes on the main looper, which Robolectric keeps paused.
        compose.waitUntil(10_000) { ShadowLooper.idleMainLooper(); Thread.sleep(20); vm.ui.value != null }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun menu() = menuScreen(large = false, name = "menu")
    @Test fun menuLarge() = menuScreen(large = true, name = "menu-large")

    @Test fun historyEmpty() = shot("history-empty") {
        HistoryScreen(HistoryViewModel(app.repository), app.profiles, openSnapshot = {}, openSettings = {})
    }

    @Test fun settings() = shot("settings") { SettingsScreen(app.settings, app.profiles, back = {}) }
}
