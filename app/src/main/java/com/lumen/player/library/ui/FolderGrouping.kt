package com.lumen.player.library.ui

import com.lumen.player.library.data.LibraryVideoRow

data class FolderContents(val movies: List<LibraryVideoRow>, val shows: List<Show>)
data class Show(
    val showKey: String,
    val displayName: String,
    val episodeCount: Int,
    val seasons: List<Season>,
)
data class Season(val number: Int, val episodes: List<LibraryVideoRow>)

/** Title-cases a normalised show key ("the show" -> "The Show"). Phase 2b replaces with the TMDB name. */
fun showDisplayName(showKey: String): String =
    showKey.split(' ').filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private val ciName = Comparator<String> { a, b -> a.compareTo(b, ignoreCase = true) }

fun groupFolder(rows: List<LibraryVideoRow>): FolderContents {
    val movies = rows.filter { it.showKey == null }
        .sortedWith(compareBy(ciName) { it.displayName })

    val shows = rows.filter { it.showKey != null }
        .groupBy { it.showKey!! }
        .map { (key, showRows) ->
            val seasons = showRows.groupBy { it.seasonNumber ?: 0 }
                .map { (num, eps) ->
                    Season(
                        number = num,
                        episodes = eps.sortedWith(
                            compareBy<LibraryVideoRow> { it.episodeNumber ?: Int.MAX_VALUE }
                                .thenComparator { a, b -> ciName.compare(a.displayName, b.displayName) },
                        ),
                    )
                }
                .sortedWith(compareBy { if (it.number == 0) Int.MAX_VALUE else it.number })
            Show(
                showKey = key,
                displayName = showDisplayName(key),
                episodeCount = showRows.size,
                seasons = seasons,
            )
        }
        .sortedWith(compareBy(ciName) { it.displayName })

    return FolderContents(movies, shows)
}
