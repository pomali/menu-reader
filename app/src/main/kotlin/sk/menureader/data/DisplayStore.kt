package sk.menureader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Display preferences. [largeText] = "grandpa mode": big letters and high contrast. */
class DisplayStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("display", Context.MODE_PRIVATE)
    private val _largeText = MutableStateFlow(prefs.getBoolean(KEY_LARGE, false))
    val largeText: StateFlow<Boolean> = _largeText

    fun setLargeText(on: Boolean) {
        prefs.edit().putBoolean(KEY_LARGE, on).apply()
        _largeText.value = on
    }

    private companion object { const val KEY_LARGE = "largeText" }
}
