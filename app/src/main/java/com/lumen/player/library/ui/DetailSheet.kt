package com.lumen.player.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.Formatter
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.SourceType
import com.lumen.player.player.formatTime
import kotlinx.coroutines.launch

private val Scrim = Color(0xCC000000)
private val Panel = Color(0xFF121216)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

/** Bottom panel for a library video: poster, path, size, and Resume / Play-from-start. */
@Composable
fun DetailSheet(
    row: LibraryVideoRow,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = row.episodeLabel()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Scrim),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Panel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) { VideoPoster(model = videoPosterModel(row), modifier = Modifier.fillMaxSize()) }

            Text(label, color = TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.relativePath.isNotEmpty()) {
                Text(row.relativePath, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text(Formatter.formatShortFileSize(context, row.sizeBytes), color = TextSecondary, fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (row.positionMs > 0L && !row.finished) {
                    Button(onClick = {
                        onPlay(row.documentUri, label, SourceType.SAF_FILE, true)
                        onDismiss()
                    }) { Text("Resume from ${formatTime(row.positionMs)}") }
                }
                OutlinedButton(onClick = {
                    scope.launch { HistoryRepository.get(context).restart(row.documentUri) }
                    onPlay(row.documentUri, label, SourceType.SAF_FILE, true)
                    onDismiss()
                }) { Text("Play from start") }
            }
        }
    }
}

fun LibraryVideoRow.episodeLabel(): String {
    val s = seasonNumber
    val e = episodeNumber
    return if (showKey != null && s != null && e != null) {
        "S%02dE%02d · %s".format(s, e, displayName)
    } else {
        displayName
    }
}
