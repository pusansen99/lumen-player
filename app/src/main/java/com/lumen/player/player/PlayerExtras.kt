package com.lumen.player.player

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.listen
import java.util.Locale

/** What the side-swipe HUD is currently showing. */
sealed interface GestureHud {
    val fraction: Float

    data class Brightness(override val fraction: Float) : GestureHud
    data class Volume(override val fraction: Float) : GestureHud
}

/** HUD shown briefly at the relevant edge while the user swipes: brightness left, volume right. */
@Composable
fun GestureHudOverlay(hud: GestureHud, modifier: Modifier = Modifier) {
    val alignment = when (hud) {
        is GestureHud.Brightness -> Alignment.CenterStart
        is GestureHud.Volume -> Alignment.CenterEnd
    }
    Box(modifier = modifier, contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .background(Color(0xB3000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = when (hud) {
                    is GestureHud.Brightness -> Icons.Filled.BrightnessMedium
                    is GestureHud.Volume -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(6.dp)
                    .height(120.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((120 * hud.fraction.coerceIn(0f, 1f)).dp)
                        .background(Color.White, RoundedCornerShape(3.dp)),
                )
            }
            Text(
                text = "${(hud.fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Reads the fraction of max music-stream volume, 0f..1f. */
fun AudioManager.musicVolumeFraction(): Float {
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

/** Sets the music-stream volume from a 0f..1f fraction. Returns the applied fraction. */
fun AudioManager.setMusicVolumeFraction(fraction: Float): Float {
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val clamped = fraction.coerceIn(0f, 1f)
    setStreamVolume(AudioManager.STREAM_MUSIC, (clamped * max).toInt(), 0)
    return clamped
}

/** Observes [Player.getCurrentTracks] as Compose state. */
@Composable
fun rememberTracks(player: Player): Tracks {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) tracks = currentTracks
        }
    }
    return tracks
}

private data class TrackRow(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

private fun langName(tag: String?): String? =
    tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }

private fun audioLabel(f: Format): String {
    val lang = langName(f.language) ?: "Audio"
    val ch = when (f.channelCount) {
        1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null
    }
    val kbps = if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else null
    return listOfNotNull(lang, ch, kbps).joinToString(" · ")
}

private fun textLabel(f: Format): String {
    val lang = langName(f.language) ?: "Subtitle"
    val sdh = if (f.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) " (SDH)" else ""
    val forced = if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) " (forced)" else ""
    return lang + sdh + forced
}

private fun videoLabel(f: Format): String {
    val res = if (f.height > 0) "${f.height}p" else "Video"
    val kbps = if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else null
    return listOfNotNull(res, kbps).joinToString(" · ")
}

private fun hasOverride(player: Player, trackType: Int): Boolean =
    player.trackSelectionParameters.overrides.keys.any { it.type == trackType }

/**
 * In-player panel (not a system sheet, so immersive mode is kept) for choosing the audio,
 * subtitle and video track. Tapping outside the panel dismisses it.
 */
@Composable
fun TrackSelectionSheet(
    player: Player,
    tracks: Tracks,
    subtitleScaleLabel: String,
    onCycleSubtitleScale: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = listOf(
        "Audio" to C.TRACK_TYPE_AUDIO,
        "Subtitles" to C.TRACK_TYPE_TEXT,
        "Video" to C.TRACK_TYPE_VIDEO,
    )
    var tab by remember { mutableIntStateOf(0) }
    val (tabLabel, trackType) = types[tab]

    fun mutateParams(block: androidx.media3.common.TrackSelectionParameters.Builder.() -> Unit) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply(block).build()
    }

    fun applyOverride(group: Tracks.Group, trackIndex: Int) = mutateParams {
        setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        setTrackTypeDisabled(trackType, false)
    }

    fun clearType() = mutateParams {
        clearOverridesOfType(trackType)
        setTrackTypeDisabled(trackType, false)
    }

    fun disableType() = mutateParams {
        clearOverridesOfType(trackType)
        setTrackTypeDisabled(trackType, true)
    }

    val groups = tracks.groups.filter { it.type == trackType }
    val anySelected = groups.any { it.isSelected }
    val typeDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(trackType)

    val rows = buildList {
        when (trackType) {
            C.TRACK_TYPE_TEXT ->
                add(TrackRow("Off", selected = typeDisabled || !anySelected, onSelect = ::disableType))
            C.TRACK_TYPE_VIDEO ->
                add(TrackRow("Auto", selected = !hasOverride(player, trackType), onSelect = ::clearType))
        }
        groups.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                val label = when (trackType) {
                    C.TRACK_TYPE_AUDIO -> audioLabel(f)
                    C.TRACK_TYPE_TEXT -> textLabel(f)
                    else -> videoLabel(f)
                }
                add(
                    TrackRow(
                        label = label,
                        selected = group.isTrackSelected(i) && !typeDisabled,
                        onSelect = { applyOverride(group, i) },
                    )
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.62f).dp
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                .pointerInput(Unit) { detectTapGestures { /* swallow */ } },
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                // Drag this handle down to dismiss.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dy ->
                                    dragOffsetPx = (dragOffsetPx + dy).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    if (dragOffsetPx > 160f) onDismiss() else dragOffsetPx = 0f
                                },
                                onDragCancel = { dragOffsetPx = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(5.dp)
                            .background(Color(0x33FFFFFF), RoundedCornerShape(2.5.dp)),
                    )
                }
                PrimaryTabRow(selectedTabIndex = tab) {
                    types.forEachIndexed { index, (label, _) ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    if (trackType == C.TRACK_TYPE_TEXT) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onCycleSubtitleScale)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Text size", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Text(
                                text = subtitleScaleLabel,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            text = "No $tabLabel tracks in this video.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = row.selected, onClick = row.onSelect)
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = row.label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                if (row.selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
