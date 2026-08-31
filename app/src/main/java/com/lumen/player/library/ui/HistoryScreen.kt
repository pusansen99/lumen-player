package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch

private val TextPrimary = Color(0xFFF4F4F5)

@Composable
fun HistoryScreen(
    onPlay: (String, String, SourceType, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val history = remember { HistoryRepository.get(context) }
    val scope = rememberCoroutineScope()
    val all by history.all.collectAsState(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    var menuUri by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = TextPrimary)
                    }
                    Text("History", color = TextPrimary, fontSize = 18.sp,
                        modifier = Modifier.weight(1f))
                    if (all.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
                    }
                }
            }
            items(all, key = { it.uri }) { entry ->
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
                            onMarkFinished = { scope.launch { history.setFinished(entry.uri, true) } },
                            onRemove = { scope.launch { history.forget(entry.uri) } },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Resume positions for every video will be forgotten.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { history.clearAll() }
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
