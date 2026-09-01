package com.lumen.player.library.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.data.PlaybackHistoryEntry

private val Accent = Color(0xFF4C8DFF)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

/** "45% watched" when the duration is known, otherwise "13:30 in". */
fun remainingLabel(positionMs: Long, durationMs: Long): String {
    if (durationMs > 0L) {
        val pct = ((positionMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 99)
        return "$pct% watched"
    }
    val totalSec = positionMs / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d in".format(m, s)
}

@Composable
private fun Poster(path: String?, modifier: Modifier = Modifier) {
    VideoPoster(model = path, modifier = modifier)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    entry: PlaybackHistoryEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(bottom = 4.dp),
    ) {
        Box {
            Poster(
                entry.thumbnailPath,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            val fraction = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                color = Accent,
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
        Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(remainingLabel(entry.positionMs, entry.durationMs), color = TextSecondary, fontSize = 11.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    entry: PlaybackHistoryEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Poster(entry.thumbnailPath, Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(6.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.finished) "Finished" else remainingLabel(entry.positionMs, entry.durationMs),
                color = TextSecondary, fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun HistoryItemMenu(
    entry: PlaybackHistoryEntry,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onMarkFinished: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Restart from beginning") },
            onClick = { onRestart(); onDismiss() })
        if (!entry.finished) {
            DropdownMenuItem(text = { Text("Mark finished") },
                onClick = { onMarkFinished(); onDismiss() })
        }
        DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(); onDismiss() })
    }
}
