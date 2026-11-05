package sk.menureader.core

import kotlinx.serialization.json.Json

/** Shared JSON settings for snapshots, OCR dumps and export files. */
val MenuJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = false
}
