package com.lumen.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.listen

/** Observes fatal playback errors; cleared when playback starts working again. */
@Composable
fun rememberPlayerError(player: Player): PlaybackException? {
    var error by remember(player) { mutableStateOf(player.playerError) }
    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_PLAYER_ERROR)) error = playerError
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && playbackState == Player.STATE_READY) {
                error = null
            }
        }
    }
    return error
}

private fun friendlyMessage(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> "Network problem — check your connection."

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> "The video could not be found. The link may be broken or offline."

    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    -> "This stream is malformed or not a supported format."

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> "This device can't decode this video."

    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
    -> "This video is protected and can't be played here."

    else -> "Something went wrong playing this video."
}

/** Full-screen error state with retry / back. */
@Composable
fun ErrorOverlay(
    error: PlaybackException,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xF2050506)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFF5A524),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Can't play this video",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = friendlyMessage(error),
                color = Color(0xFFA1A1AA),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error.errorCodeName,
                color = Color(0xFF6B6B72),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Back")
                }
            }
        }
    }
}
