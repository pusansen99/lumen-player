package com.lumen.player.library.data

/** How a video reached the player. Stored as the enum name on [PlaybackHistoryEntry.sourceType]. */
enum class SourceType {
    /** Typed or pasted into the play bar. */
    URL,

    /** Picked in-app via the system document picker (persistable permission taken). */
    SAF_FILE,

    /** Handed in by another app through ACTION_VIEW ("Open with"). */
    EXTERNAL_VIEW,

    /** Handed in by another app through the share sheet (ACTION_SEND). */
    EXTERNAL_SEND,
}

/**
 * Canonical form of a media URI, used as the primary key for resume state.
 *
 * Must be stable across app sessions and identical whether the same video arrives
 * through the play bar or an external intent. For `http`/`https` the scheme and host
 * are lowercased and the fragment is dropped; path and query are kept as-is.
 * `content://`, `file://` and everything else are returned trimmed but otherwise verbatim.
 */
fun normalizeMediaUri(raw: String): String {
    val trimmed = raw.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return trimmed
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return trimmed

    val rest = trimmed.substring(schemeEnd + 3)
    val withoutFragment = rest.substringBefore('#')
    val slash = withoutFragment.indexOf('/')
    return if (slash < 0) {
        "$scheme://${withoutFragment.lowercase()}"
    } else {
        val host = withoutFragment.substring(0, slash).lowercase()
        val pathAndQuery = withoutFragment.substring(slash)
        "$scheme://$host$pathAndQuery"
    }
}
