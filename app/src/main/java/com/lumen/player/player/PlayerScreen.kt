package com.lumen.player.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.lumen.player.update.UpdateDialog
import com.lumen.player.update.rememberUpdateController
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L

/** Cycled by the resize button: how the video fills the screen. */
private val RESIZE_MODES: List<Pair<String, ContentScale>> = listOf(
    "Fit" to ContentScale.Fit,
    "Fill" to ContentScale.Crop,
    "Stretch" to ContentScale.FillBounds,
)

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    externalUri: String? = null,
    onExternalUriConsumed: () -> Unit = {},
) {
    var sourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceLabel by rememberSaveable { mutableStateOf("") }

    val updateController = rememberUpdateController()
    LaunchedEffect(Unit) { updateController.checkOnce() }
    UpdateDialog(updateController)

    // A video handed in from another app ("Open with" / share) plays immediately.
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            sourceUri = externalUri
            sourceLabel = externalUri.toUri().lastPathSegment ?: "Now playing"
            onExternalUriConsumed()
        }
    }

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

private const val HUD_HIDE_MS = 700L
private const val FORMAT_BADGE_MS = 4_500L

@Composable
private fun PlayerContainer(
    uri: String,
    title: String,
    onBack: () -> Unit,
) {
    val player = rememberManagedExoPlayer()
    val presentationState = rememberPresentationState(player)
    val tracks = rememberTracks(player)

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    PlayerWindowEffects()

    // Video plays in landscape; restore the previous orientation on exit.
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var resizeIndex by rememberSaveable { mutableIntStateOf(0) }
    val (resizeLabel, contentScale) = RESIZE_MODES[resizeIndex]

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, uri) {
        if (controlsVisible) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    var showTracks by remember { mutableStateOf(false) }

    // Side-swipe brightness (left half) and volume (right half).
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
        )
    }
    var volume by remember { mutableFloatStateOf(audioManager?.musicVolumeFraction() ?: 0.5f) }
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(HUD_HIDE_MS)
            hud = null
        }
    }

    // Format badge: shown briefly whenever a new source starts.
    val formatSummary = rememberFormatSummary(player)
    var formatBadgeVisible by remember(uri) { mutableStateOf(true) }
    LaunchedEffect(uri) {
        formatBadgeVisible = true
        delay(FORMAT_BADGE_MS)
        formatBadgeVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
            .pointerInput(Unit) {
                var onLeft = true
                detectVerticalDragGestures(
                    onDragStart = { offset -> onLeft = offset.x < size.width / 2f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val step = -dragAmount / size.height // drag up => positive
                        if (onLeft) {
                            brightness = (brightness + step).coerceIn(0.01f, 1f)
                            activity?.window?.let { w ->
                                w.attributes = w.attributes.apply { screenBrightness = brightness }
                            }
                            hud = GestureHud.Brightness(brightness)
                        } else {
                            volume = audioManager?.setMusicVolumeFraction(volume + step) ?: volume
                            hud = GestureHud.Volume(volume)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp),
        )

        SubtitleOverlay(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (controlsVisible) 88.dp else 20.dp),
        )

        if (controlsVisible) {
            PlayerControls(
                player = player,
                title = title,
                resizeLabel = resizeLabel,
                onCycleResize = { resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size },
                onOpenTracks = { showTracks = true },
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }

        hud?.let { GestureHudOverlay(it, modifier = Modifier.fillMaxSize()) }

        FormatBadge(
            summary = formatSummary,
            visible = formatBadgeVisible && !controlsVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp),
        )

        if (showTracks) {
            TrackSelectionSheet(
                player = player,
                tracks = tracks,
                onDismiss = { showTracks = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * While the player is on screen: keep the display awake and go immersive (hide the status bar and
 * navigation bar; a swipe from the edge reveals them transiently). Both are reverted on dispose.
 */
@Composable
private fun PlayerWindowEffects() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
