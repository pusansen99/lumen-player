package com.lumen.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState

// Cinematic-dark control palette (see the design-canvas style tile).
private val Accent = Color(0xFF4C8DFF)
private val TextPrimary = Color(0xFFF5F5F7)
private val TextSecondary = Color(0xFFA1A1AA)
private val TrackIdle = Color(0x38FFFFFF)
private val TrackBuffered = Color(0x66FFFFFF)
private val ButtonFill = Color(0x24FFFFFF)
private val ButtonStroke = Color(0x40FFFFFF)

private val TabularNums = TextStyle(fontFeatureSettings = "tnum")

/** Observes [Player.getPlaybackState] as Compose state. */
@Composable
internal fun rememberPlaybackState(player: Player): Int {
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
 * Control overlay: top-and-bottom scrim gradients, a title bar, a centred transport row
 * (−10s / play-pause / +10s) and a custom scrubber with a buffered track.
 */
@Composable
fun PlayerControls(
    player: Player,
    title: String,
    resizeLabel: String,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onBack: () -> Unit,
    onInteraction: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 500L)
    val playbackState = rememberPlaybackState(player)

    Box(modifier = modifier.fillMaxSize()) {

        // Scrims: darken only the top and bottom, leave the middle of the video clear.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color(0x00000000)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.36f)
                .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000)))),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(start = 12.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            IconButton(onClick = { onInteraction(); onOpenTracks() }) {
                Icon(Icons.Filled.Tune, contentDescription = "Audio, subtitle and video tracks", tint = TextPrimary)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onInteraction(); onCycleResize() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Filled.AspectRatio,
                    contentDescription = "Change video size",
                    tint = TextPrimary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = resizeLabel,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportIcon(
                icon = Icons.Filled.Replay10,
                description = "Seek back 10 seconds",
                enabled = seekBack.isEnabled,
                onClick = { onInteraction(); seekBack.onClick() },
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ButtonFill)
                    .border(1.dp, ButtonStroke, CircleShape)
                    .clickable(enabled = playPause.isEnabled) { onInteraction(); playPause.onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playPause.showPlay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (playPause.showPlay) "Play" else "Pause",
                    tint = TextPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            TransportIcon(
                icon = Icons.Filled.Forward10,
                description = "Seek forward 10 seconds",
                enabled = seekForward.isEnabled,
                onClick = { onInteraction(); seekForward.onClick() },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            val duration = progress.durationMs
            val hasDuration = duration > 0L
            var scrubFraction by remember { mutableStateOf<Float?>(null) }

            val liveFraction = if (hasDuration) {
                (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            val bufferedFraction = if (hasDuration) {
                (progress.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            val shownFraction = scrubFraction ?: liveFraction
            val shownPositionMs =
                if (scrubFraction != null && hasDuration) (scrubFraction!! * duration).toLong()
                else progress.currentPositionMs

            Scrubber(
                fraction = shownFraction,
                bufferedFraction = bufferedFraction,
                enabled = hasDuration,
                onScrub = {
                    onInteraction()
                    scrubFraction = it
                },
                onScrubFinished = { f ->
                    onInteraction()
                    if (hasDuration) player.seekTo((f * duration).toLong())
                    scrubFraction = null
                },
                onScrubbingChange = onScrubbingChange,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(formatTime(shownPositionMs), style = TabularNums, fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (playbackState == Player.STATE_ENDED) "Ended" else formatTime(duration),
                    style = TabularNums,
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextPrimary else TextSecondary,
            modifier = Modifier.size(34.dp),
        )
    }
}

/** Thin track with a buffered segment, an accent-coloured played segment and a draggable knob. */
@Composable
private fun Scrubber(
    fraction: Float,
    bufferedFraction: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 7.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { offset ->
                        onScrubFinished((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onScrubbingChange(true)
                            onScrub((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = {
                            onScrubbingChange(false)
                            onScrubFinished(fraction)
                        },
                        onDragCancel = {
                            onScrubbingChange(false)
                            onScrubFinished(fraction)
                        },
                    )
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(TrackIdle))
        Box(
            Modifier
                .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(TrackBuffered),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(Accent),
        )
        Box(
            modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .border(4.dp, Color(0x404C8DFF), CircleShape),
            )
        }
    }
}
