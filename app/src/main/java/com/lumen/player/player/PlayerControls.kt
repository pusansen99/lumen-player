package com.lumen.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState

private val Scrim = Color(0x99000000)

/** Observes [Player.getPlaybackState] as Compose state. */
@Composable
private fun rememberPlaybackState(player: Player): Int {
    var state by remember(player) { mutableIntStateOf(player.playbackState) }
    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                state = playbackState
            }
        }
    }
    return state
}

/**
 * Minimal control overlay: back, scrubber + timestamps, and a transport row (−10s / play-pause /
 * +10s). A buffering spinner replaces the transport row while the player is buffering.
 */
@Composable
fun PlayerControls(
    player: Player,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 500L)
    val playbackState = rememberPlaybackState(player)

    Box(modifier = modifier.fillMaxSize().background(Scrim)) {

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                color = LocalContentColor.current,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp))
        } else {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = seekBack::onClick, enabled = seekBack.isEnabled) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Seek back 10 seconds", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
                    Icon(
                        imageVector = if (playPause.showPlay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (playPause.showPlay) "Play" else "Pause",
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = seekForward::onClick, enabled = seekForward.isEnabled) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Seek forward 10 seconds", modifier = Modifier.size(36.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val duration = progress.durationMs
            val hasDuration = duration > 0L
            var scrubFraction by remember { mutableFloatStateOf(-1f) }
            val liveFraction = if (hasDuration) {
                (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            val shownFraction = if (scrubFraction >= 0f) scrubFraction else liveFraction
            val shownPositionMs = if (scrubFraction >= 0f && hasDuration) {
                (scrubFraction * duration).toLong()
            } else {
                progress.currentPositionMs
            }

            Slider(
                value = shownFraction,
                onValueChange = { scrubFraction = it },
                onValueChangeFinished = {
                    if (hasDuration && scrubFraction >= 0f) {
                        player.seekTo((scrubFraction * duration).toLong())
                    }
                    scrubFraction = -1f
                },
                enabled = hasDuration,
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(shownPositionMs), fontSize = 12.sp, color = LocalContentColor.current)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), fontSize = 12.sp, color = LocalContentColor.current)
            }

            if (playbackState == Player.STATE_ENDED) {
                Text(
                    text = "Ended",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
