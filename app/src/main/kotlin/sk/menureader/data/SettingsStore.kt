package sk.menureader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App settings kept on the device. */
class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("display", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))
    private val _largeText = flag(LARGE, false)
    private val _dynamicColor = flag(DYNAMIC, false)
    private val _debugTools = flag(DEBUG, true)
    private val _byCategory = flag(BY_CATEGORY, true)

    /** Big letters and high contrast ("grandpa mode"). */
    val largeText: StateFlow<Boolean> = _largeText
    /** Android 12+ wallpaper colours instead of the app's own palette. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor
    /** Debug / OCR export entry in each menu (Phase 0). */
    val debugTools: StateFlow<Boolean> = _debugTools
    /** Menu list grouped by the menu's own sections; off = two groups, for you / better not. */
    val byCategory: StateFlow<Boolean> = _byCategory

    fun setLargeText(on: Boolean) = set(LARGE, _largeText, on)
    fun setDynamicColor(on: Boolean) = set(DYNAMIC, _dynamicColor, on)
    fun setDebugTools(on: Boolean) = set(DEBUG, _debugTools, on)
    fun setByCategory(on: Boolean) = set(BY_CATEGORY, _byCategory, on)

    private fun set(key: String, flow: MutableStateFlow<Boolean>, on: Boolean) {
        prefs.edit().putBoolean(key, on).apply()
        flow.value = on
    }

    private companion object {
        const val LARGE = "largeText"
        const val DYNAMIC = "dynamicColor"
        const val DEBUG = "debugTools"
        const val BY_CATEGORY = "byCategory"
    }
}
