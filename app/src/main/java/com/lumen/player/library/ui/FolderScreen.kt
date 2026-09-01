package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.LibraryRepository
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch

private val Accent = Color(0xFF4C8DFF)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@Composable
fun FolderScreen(
    treeUri: String,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { LibraryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val folder by repo.observeFolder(treeUri).collectAsState(initial = null)
    val rows by repo.folderRows(treeUri).collectAsState(initial = emptyList())
    val scanning by repo.scanning.collectAsState()
    val contents = remember(rows) { groupFolder(rows) }

    // Per-show expand/collapse. Simplified from the brief's rememberSaveable map-saver:
    // expansion resets on process death, which is acceptable for v1.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    var sheetRow by remember { mutableStateOf<LibraryVideoRow?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                folder?.displayName ?: "Folder", color = TextPrimary, fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { repo.rescan(treeUri) } }) { Text("Rescan") }
        }

        val isScanning = treeUri in scanning
        when {
            rows.isEmpty() && isScanning ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No videos in this folder.", color = TextSecondary, fontSize = 13.sp)
                        TextButton(onClick = { scope.launch { repo.rescan(treeUri) } }) { Text("Rescan") }
                    }
                }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (contents.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Movies") }
                    items(contents.movies, key = { it.documentUri }) { row ->
                        MovieTile(row, onClick = { sheetRow = row })
                    }
                }
                contents.shows.forEach { show ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "show:${show.showKey}") {
                        val open = expanded[show.showKey] == true
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { expanded[show.showKey] = !open }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null, tint = TextSecondary,
                            )
                            Text(
                                "${show.displayName}  ·  ${show.episodeCount} episodes",
                                color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    if (expanded[show.showKey] == true) {
                        show.seasons.forEach { season ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "s:${show.showKey}:${season.number}") {
                                Text(
                                    if (season.number == 0) "Specials" else "Season ${season.number}",
                                    color = TextSecondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                            }
                            items(
                                season.episodes, span = { GridItemSpan(maxLineSpan) },
                                key = { it.documentUri },
                            ) { row -> EpisodeRow(row, onClick = { sheetRow = row }) }
                        }
                    }
                }
            }
        }
    }

    sheetRow?.let { r ->
        // Host the sheet in a full-screen Box with a tap-to-dismiss scrim layer behind it.
        // (DetailSheet's own scrim Box does not intercept taps — Task 11 carry-over.)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { sheetRow = null },
            )
            Box(Modifier.align(Alignment.BottomCenter)) {
                DetailSheet(row = r, onPlay = onPlay, onDismiss = { sheetRow = null })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) =
    Text(text, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun MovieTile(row: LibraryVideoRow, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
            VideoPoster(model = videoPosterModel(row), modifier = Modifier.fillMaxSize())
            ResumeBar(row, Modifier.align(Alignment.BottomStart))
        }
        Text(
            row.displayName, color = TextPrimary, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EpisodeRow(row: LibraryVideoRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(6.dp))) {
            VideoPoster(model = videoPosterModel(row), modifier = Modifier.fillMaxSize())
            ResumeBar(row, Modifier.align(Alignment.BottomStart))
        }
        Text(
            row.episodeLabel(), color = TextPrimary, fontSize = 13.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResumeBar(row: LibraryVideoRow, modifier: Modifier) {
    if (row.positionMs > 0L && !row.finished && row.durationMs > 0L) {
        LinearProgressIndicator(
            progress = { (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f) },
            color = Accent, trackColor = Color(0x33FFFFFF),
            modifier = modifier.fillMaxWidth().height(3.dp),
        )
    }
}
