package sk.menureader.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** What another app shared into Menu Reader. */
sealed interface SharedContent {
    data class Images(val uris: List<Uri>) : SharedContent
    data class Text(val text: String, val subject: String?) : SharedContent

    companion object {
        fun from(intent: Intent?): SharedContent? {
            intent ?: return null
            val type = intent.type.orEmpty()
            return when (intent.action) {
                Intent.ACTION_SEND -> when {
                    type.startsWith("image/") ->
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { Images(listOf(it)) }
                    type.startsWith("text/") ->
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                            ?.let { Text(it, intent.getStringExtra(Intent.EXTRA_SUBJECT)) }
                    else -> null
                }
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.takeIf { it.isNotEmpty() }?.let { Images(it.take(10)) }
                else -> null
            }
        }
    }
}
