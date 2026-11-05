package sk.menureader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sk.menureader.core.MenuJson
import sk.menureader.core.filter.UserProfile

/** The user's allergen profile, stored on the device only. */
class ProfileStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<UserProfile> = _profile

    private fun load(): UserProfile = prefs.getString(KEY, null)
        ?.let { runCatching { MenuJson.decodeFromString<UserProfile>(it) }.getOrNull() }
        ?: UserProfile()

    fun update(transform: (UserProfile) -> UserProfile) {
        val p = transform(_profile.value)
        prefs.edit().putString(KEY, MenuJson.encodeToString(p)).apply()
        _profile.value = p
    }

    private companion object { const val KEY = "profile.v1" }
}
