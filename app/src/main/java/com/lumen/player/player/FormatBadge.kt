package com.lumen.player.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.exoplayer.ExoPlayer

/** Human-readable summary of the video/audio the player is currently decoding. */
data class MediaFormatSummary(
    val dynamicRange: String,
    val videoCodec: String,
    val resolution: String,
    val audio: String,
) {
    val videoLine: String
        get() = listOf(dynamicRange, videoCodec, resolution).filter { it.isNotBlank() }.joinToString("  ·  ")
}

private fun dynamicRange(f: Format?): String {
    f ?: return ""
    if (f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || f.codecs?.startsWith("dv") == true) {
        return "Dolby Vision"
    }
    return when (f.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> "SDR"
    }
}

private fun videoCodec(mime: String?): String = when (mime) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_VP8 -> "VP8"
    MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    else -> mime?.substringAfter('/')?.uppercase() ?: "Video"
}

private fun resolution(f: Format?): String = when {
    f == null || f.height <= 0 -> ""
    f.height >= 4300 -> "8K"
    f.height >= 2000 -> "2160p"
    else -> "${f.height}p"
}

private fun audio(f: Format?): String {
    f ?: return ""
    val base = when (f.sampleMimeType) {
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital+"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_X -> "DTS:X"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_RAW -> "PCM"
        else -> f.sampleMimeType?.substringAfter('/')?.uppercase() ?: "Audio"
    }
    val channels = when (f.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        in 3..Int.MAX_VALUE -> "${f.channelCount}ch"
        else -> null
    }
    return listOfNotNull(base, channels).joinToString(" · ")
}

private fun summarize(video: Format?, audioFormat: Format?): MediaFormatSummary? {
    if (video == null && audioFormat == null) return null
    return MediaFormatSummary(
        dynamicRange = dynamicRange(video),
        videoCodec = if (video != null) videoCodec(video.sampleMimeType) else "",
        resolution = resolution(video),
        audio = audio(audioFormat),
    )
}

/** Tracks the currently decoding video/audio [Format]s as a [MediaFormatSummary]. */
@Composable
fun rememberFormatSummary(player: ExoPlayer): MediaFormatSummary? {
    var summary by remember(player) { mutableStateOf(summarize(player.videoFormat, player.audioFormat)) }
    LaunchedEffect(player) {
        player.listen { events ->
            if (
                events.containsAny(
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_VIDEO_SIZE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                summary = summarize(player.videoFormat, player.audioFormat)
            }
        }
    }
    return summary
}

/** Translucent corner chip shown briefly at playback start: dynamic range, codec, resolution, audio. */
@Composable
fun FormatBadge(
    summary: MediaFormatSummary?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && summary != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val s = summary ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .background(Color(0x99000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (s.videoLine.isNotBlank()) {
                Text(
                    text = s.videoLine,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (s.audio.isNotBlank()) {
                Text(
                    text = s.audio,
                    color = Color(0xFFCFCFCF),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
