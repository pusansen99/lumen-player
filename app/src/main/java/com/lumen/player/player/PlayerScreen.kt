package com.lumen.player.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L

@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    var sourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceLabel by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val uri = sourceUri
        if (uri == null) {
            SourcePicker(
                onPlay = { value, label ->
                    sourceUri = value
                    sourceLabel = label
                },
                modifier = Modifier.safeDrawingPadding().padding(24.dp),
            )
        } else {
            PlayerContainer(
                uri = uri,
                title = sourceLabel.ifBlank { "Now playing" },
                onBack = { sourceUri = null },
            )
        }
    }
}

@Composable
private fun SourcePicker(
    onPlay: (uri: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            onPlay(picked.toString(), picked.lastPathSegment ?: "Local file")
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Lumen", color = Color.White, fontSize = 28.sp)
        Text(
            "Paste a stream URL (HLS / DASH / SmoothStreaming / MP4) or open a video file.",
            color = Color(0xFFA1A1AA),
            fontSize = 13.sp,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            label = { Text("Video URL") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onPlay(url.trim(), url.trim()) }),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (url.isNotBlank()) onPlay(url.trim(), url.trim()) },
                enabled = url.isNotBlank(),
            ) { Text("Play URL") }

            OutlinedButton(
                onClick = { openDocument.launch(arrayOf("video/*")) },
            ) { Text("Open file") }
        }
    }
}

@Composable
private fun PlayerContainer(
    uri: String,
    title: String,
    onBack: () -> Unit,
) {
    val player = rememberManagedExoPlayer()

    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    KeepScreenOn()

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, uri) {
        if (controlsVisible) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = !controlsVisible },
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            PlayerControls(
                player = player,
                title = title,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
