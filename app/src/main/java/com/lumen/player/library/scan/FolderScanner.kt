package com.lumen.player.library.scan

data class EpisodeHint(
    val showKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

private val SXXEXX = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})""")
private val NXM = Regex("""\b(\d{1,2})x(\d{1,3})\b""")
private val SEASON_DIR = Regex("""(?i)^season[ ._-]?(\d{1,3})$""")
private val LEADING_EP = Regex("""(?i)(?:^|[ ._-])(?:e|ep|episode)[ ._-]?(\d{1,3})\b""")

/** Lowercase, collapse any run of non-alphanumerics to one space, trim. */
private fun normaliseShowKey(raw: String): String =
    raw.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()

/**
 * Best-effort show / season / episode from a video file name and the folder names above it.
 * [pathSegments] is outermost-first and excludes the file. `showKey == null` => treat as a movie.
 */
fun episodeHints(fileName: String, pathSegments: List<String>): EpisodeHint {
    val base = fileName.substringBeforeLast('.')

    SXXEXX.find(base)?.let { m ->
        return EpisodeHint(showKeyFrom(base, pathSegments, m.range.first), m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
    NXM.find(base)?.let { m ->
        return EpisodeHint(showKeyFrom(base, pathSegments, m.range.first), m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
    // Season folder fallback
    val seasonDirIdx = pathSegments.indexOfLast { SEASON_DIR.matches(it.trim()) }
    if (seasonDirIdx >= 0) {
        val season = SEASON_DIR.find(pathSegments[seasonDirIdx].trim())!!.groupValues[1].toInt()
        val ep = LEADING_EP.find(base)?.groupValues?.get(1)?.toInt()
        val showSeg = pathSegments.getOrNull(seasonDirIdx - 1) ?: pathSegments.getOrNull(seasonDirIdx)
        return EpisodeHint(showSeg?.let(::normaliseShowKey), season, ep)
    }
    return EpisodeHint(null, null, null)
}

private fun showKeyFrom(base: String, pathSegments: List<String>, patternStart: Int): String {
    val parent = pathSegments.lastOrNull()
    if (parent != null && !SEASON_DIR.matches(parent.trim())) return normaliseShowKey(parent)
    val grandparent = pathSegments.getOrNull(pathSegments.lastIndex - 1)
    if (grandparent != null) return normaliseShowKey(grandparent)
    // no usable folder: use the file name up to the pattern
    return normaliseShowKey(base.substring(0, patternStart))
}
