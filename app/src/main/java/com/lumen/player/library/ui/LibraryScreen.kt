package com.lumen.player.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch

private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    lastUrl: String,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolder: (treeUri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val history = remember { HistoryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val libraryRepo = remember { com.lumen.player.library.LibraryRepository.get(context) }
    val folders by libraryRepo.folders.collectAsState(emptyList())
    val scanningFolders by libraryRepo.scanning.collectAsState()
    val folderErrors by libraryRepo.foldersWithError.collectAsState()
    val addFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) scope.launch {
            when (libraryRepo.addFolder(uri)) {
                is com.lumen.player.library.LibraryRepository.AddFolderResult.PermissionDenied ->
                    android.widget.Toast.makeText(
                        context,
                        "Couldn't get lasting access to that folder.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                else -> {}
            }
        }
    }
    var folderMenuUri by remember { mutableStateOf<String?>(null) }
    var confirmRemoveUri by remember { mutableStateOf<String?>(null) }

    val recentFlow = remember { history.recent(24) }
    val continueWatching by history.continueWatching.collectAsState(emptyList())
    val recent by recentFlow.collectAsState(emptyList())
    // "Recent" = rows not already shown in Continue Watching.
    val continueUris = continueWatching.map { it.uri }.toSet()
    val recentOnly = recent.filter { it.uri !in continueUris }.take(10)

    var url by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(lastUrl) { if (url.isEmpty() && lastUrl.isNotEmpty()) url = lastUrl }

    var menuUri by remember { mutableStateOf<String?>(null) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            onPlay(picked.toString(), picked.lastPathSegment ?: "Local file", SourceType.SAF_FILE, granted)
        }
    }

    fun play(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        onPlay(trimmed, trimmed, SourceType.URL, true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lumen", color = TextPrimary, fontSize = 26.sp,
                        modifier = Modifier.weight(1f))
                    var overflow by remember { mutableStateOf(false) }
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text("History") },
                            onClick = { overflow = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Settings") },
                            onClick = { overflow = false; onOpenSettings() })
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("Video URL") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { play(url) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { play(url) }) { Text("Play") }
                        Button(onClick = { openDocument.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Text("  Open file")
                        }
                    }
                }
            }

            if (continueWatching.isNotEmpty()) {
                item {
                    Text("Continue watching", color = TextPrimary, fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { it.uri }) { entry ->
                            Box {
                                ContinueWatchingCard(
                                    entry = entry,
                                    onClick = {
                                        onPlay(
                                            entry.uri, entry.title,
                                            runCatching { SourceType.valueOf(entry.sourceType) }
                                                .getOrDefault(SourceType.URL),
                                            entry.hasPersistedPermission,
                                        )
                                    },
                                    onLongPress = { menuUri = entry.uri },
                                )
                                if (menuUri == entry.uri) {
                                    HistoryItemMenu(
                                        entry = entry,
                                        onDismiss = { menuUri = null },
                                        onRestart = { scope.launch { history.restart(entry.uri) } },
                                        onMarkFinished = {
                                            scope.launch { history.setFinished(entry.uri, true) }
                                        },
                                        onRemove = { scope.launch { history.forget(entry.uri) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (recentOnly.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent", color = TextPrimary, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        Text("See all", color = Color(0xFF4C8DFF), fontSize = 13.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickableNoRipple(onOpenHistory))
                    }
                }
                items(recentOnly, key = { it.uri }) { entry ->
                    Box {
                        HistoryRow(
                            entry = entry,
                            onClick = {
                                onPlay(
                                    entry.uri, entry.title,
                                    runCatching { SourceType.valueOf(entry.sourceType) }
                                        .getOrDefault(SourceType.URL),
                                    entry.hasPersistedPermission,
                                )
                            },
                            onLongPress = { menuUri = entry.uri },
                        )
                        if (menuUri == entry.uri) {
                            HistoryItemMenu(
                                entry = entry,
                                onDismiss = { menuUri = null },
                                onRestart = { scope.launch { history.restart(entry.uri) } },
                                onMarkFinished = {
                                    scope.launch { history.setFinished(entry.uri, true) }
                                },
                                onRemove = { scope.launch { history.forget(entry.uri) } },
                            )
                        }
                    }
                }
            }

            if (folders.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Folders", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addFolderLauncher.launch(null) }) { Text("+ Add folder") }
                    }
                }
                items(folders, key = { it.treeUri }) { folder ->
                    val subtitle = when {
                        folder.treeUri in folderErrors -> "Can't read — long-press to remove"
                        folder.treeUri in scanningFolders -> "Scanning…"
                        folder.videoCount == 0 && folder.lastScannedAt > 0L -> "No videos"
                        folder.videoCount == 0 -> "Scanning…"
                        else -> "${folder.videoCount} videos"
                    }
                    Box {
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenFolder(folder.treeUri) },
                                    onLongClick = { folderMenuUri = folder.treeUri },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = TextSecondary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    folder.displayName, color = TextPrimary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        if (folderMenuUri == folder.treeUri) {
                            DropdownMenu(expanded = true, onDismissRequest = { folderMenuUri = null }) {
                                DropdownMenuItem(text = { Text("Rescan") }, onClick = {
                                    folderMenuUri = null
                                    libraryRepo.rescanInBackground(folder.treeUri)
                                })
                                DropdownMenuItem(text = { Text("Remove folder") }, onClick = {
                                    folderMenuUri = null
                                    confirmRemoveUri = folder.treeUri
                                })
                            }
                        }
                    }
                }
            } else {
                // Folders section header with just the add button, so the entry point exists on a fresh install
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Folders", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addFolderLauncher.launch(null) }) { Text("+ Add folder") }
                    }
                }
            }

            if (continueWatching.isEmpty() && recentOnly.isEmpty() && folders.isEmpty()) {
                item {
                    Text(
                        "Videos you play show up here.",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }

        confirmRemoveUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { confirmRemoveUri = null },
                title = { Text("Remove this folder?") },
                text = { Text("Its videos leave the library. Watch history is kept.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemoveUri = null
                        scope.launch { libraryRepo.removeFolder(uri) }
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmRemoveUri = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interaction, indication = null, onClick = onClick,
        ),
    )
}
