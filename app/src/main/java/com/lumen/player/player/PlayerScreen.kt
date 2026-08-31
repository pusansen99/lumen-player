package com.lumen.player.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.captureFrame
import com.lumen.player.library.data.SourceType
import com.lumen.player.library.ui.HistoryScreen
import com.lumen.player.library.ui.LibraryScreen
import com.lumen.player.library.ui.SettingsScreen
import com.lumen.player.update.UpdateDialog
import com.lumen.player.update.rememberUpdateController
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which home destination shows while no video is playing. */
enum class HomeRoute { Library, History, Settings }

private const val AUTO_HIDE_MS = 3_000L
private const val HUD_HIDE_MS = 700L
private const val SEEK_HUD_MS = 600L
private const val FORMAT_BADGE_MS = 4_500L
private const val RESUME_MIN_MS = 3_000L
private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val SCRUB_FULL_WIDTH_MS = 120_000f

/** Only the outer 35% of each side reacts to brightness/volume drags; the centre is left for taps. */
private const val EDGE_ZONE_FRACTION = 0.35f

/** Haptic tick every time brightness/volume moves this much. */
private const val HAPTIC_STEP = 0.05f

/** Cycled by the resize button: how the video fills the screen. */
private val RESIZE_MODES: List<Pair<String, ContentScale>> = listOf(
    "Fit" to ContentScale.Fit,
    "Fill" to ContentScale.Crop,
    "Stretch" to ContentScale.FillBounds,
)

/** Subtitle text size as a fraction of the video-surface height. */
private val SUBTITLE_SCALES: List<Pair<String, Float>> = listOf(
    "Small" to 0.040f,
    "Medium" to 0.0533f,
    "Large" to 0.070f,
    "Extra large" to 0.090f,
)

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    externalUri: String? = null,
    externalSourceType: SourceType? = null,
    onExternalUriConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPrefs.get(context) }
    val lastUrl by prefs.lastUrl.collectAsState("")

    var sourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceLabel by rememberSaveable { mutableStateOf("") }
    var sourceTypeName by rememberSaveable { mutableStateOf(SourceType.URL.name) }
    var hasPersistedPermission by rememberSaveable { mutableStateOf(true) }
    var homeRoute by rememberSaveable { mutableStateOf(HomeRoute.Library) }

    val updateController = rememberUpdateController()
    LaunchedEffect(Unit) { updateController.checkOnce() }
    LaunchedEffect(Unit) { prefs.migrateLegacyResumeData() }
    UpdateDialog(updateController)

    // A video handed in from another app ("Open with" / share) plays immediately.
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            if (externalUri.startsWith("http", ignoreCase = true)) prefs.setLastUrl(externalUri)
            sourceUri = externalUri
            sourceLabel = externalUri.toUri().lastPathSegment ?: "Now playing"
            sourceTypeName = (externalSourceType ?: SourceType.EXTERNAL_VIEW).name
            hasPersistedPermission = when {
                externalUri.startsWith("http", ignoreCase = true) -> true
                else -> context.contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == externalUri && it.isReadPermission
                }
            }
            onExternalUriConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val uri = sourceUri
        if (uri == null) {
            BackHandler(enabled = homeRoute != HomeRoute.Library) { homeRoute = HomeRoute.Library }
            when (homeRoute) {
                HomeRoute.Library -> LibraryScreen(
                    lastUrl = lastUrl,
                    onPlay = { value, label, type, hasPerm ->
                        if (value.startsWith("http", ignoreCase = true)) prefs.setLastUrl(value)
                        sourceUri = value
                        sourceLabel = label
                        sourceTypeName = type.name
                        hasPersistedPermission = hasPerm
                    },
                    onOpenHistory = { homeRoute = HomeRoute.History },
                    onOpenSettings = { homeRoute = HomeRoute.Settings },
                    modifier = Modifier.safeDrawingPadding(),
                )
                HomeRoute.History -> HistoryScreen(
                    onPlay = { value, label, type, hasPerm ->
                        sourceUri = value
                        sourceLabel = label
                        sourceTypeName = type.name
                        hasPersistedPermission = hasPerm
                    },
                    onBack = { homeRoute = HomeRoute.Library },
                    modifier = Modifier.safeDrawingPadding(),
                )
                HomeRoute.Settings -> SettingsScreen(
                    onBack = { homeRoute = HomeRoute.Library },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        } else {
            PlayerContainer(
                uri = uri,
                title = sourceLabel.ifBlank { "Now playing" },
                sourceType = runCatching { SourceType.valueOf(sourceTypeName) }.getOrDefault(SourceType.URL),
                hasPersistedPermission = hasPersistedPermission,
                onBack = { sourceUri = null },
            )
        }
    }
}

@Composable
private fun PlayerContainer(
    uri: String,
    title: String,
    sourceType: SourceType,
    hasPersistedPermission: Boolean,
    onBack: () -> Unit,
) {
    val player = rememberManagedExoPlayer()
    val presentationState = rememberPresentationState(player)
    val tracks = rememberTracks(player)
    val playbackState = rememberPlaybackState(player)
    val error = rememberPlayerError(player)
    val thumbnailState = rememberThumbnailState(uri)
    val skipSegments = rememberSkipSegments(uri)
    val skipProgress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000L)
    val activeSkip = activeSkipSegment(
        skipProgress.currentPositionMs,
        skipProgress.durationMs,
        skipSegments,
    )

    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val history = remember { HistoryRepository.get(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Load, restoring the saved position for this URI from the library history.
    var resumedFromMs by remember(uri) { mutableLongStateOf(0L) }
    LaunchedEffect(uri) {
        val resume = history.startSession(
            rawUri = uri,
            sourceType = sourceType,
            titleHint = title,
            hasPersistedPermission = hasPersistedPermission,
        )
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (resume > RESUME_MIN_MS) {
            player.seekTo(resume)
            resumedFromMs = resume
        }
    }

    // Persist the position periodically while playing.
    LaunchedEffect(uri) {
        while (true) {
            delay(POSITION_SAVE_INTERVAL_MS)
            history.updatePosition(uri, player.currentPosition, player.duration)
        }
    }

    // Persist on pause/stop (covers "swipe the app away") and on leaving the screen.
    DisposableEffect(uri, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                history.updatePosition(uri, player.currentPosition, player.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            history.updatePosition(uri, player.currentPosition, player.duration)
        }
    }

    // Capture a poster frame once the media is ready (best-effort; silent for network streams).
    var thumbCaptured by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri, playbackState) {
        if (!thumbCaptured && playbackState == Player.STATE_READY && !uri.startsWith("http", true)) {
            thumbCaptured = true
            val at = player.currentPosition.coerceAtLeast(player.duration.coerceAtLeast(0L) / 5)
            val path = captureFrame(context, uri, at)
            if (path != null) history.updateThumbnail(uri, path)
        }
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
    var scrubbing by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    // Auto-hide 3s after the controls appear or the last interaction; never while scrubbing.
    LaunchedEffect(controlsVisible, scrubbing, interactionTick, uri) {
        if (controlsVisible && !scrubbing) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    var showTracks by remember { mutableStateOf(false) }

    // System back: close the track sheet if open, otherwise return to the picker.
    BackHandler(enabled = showTracks) { showTracks = false }
    BackHandler(enabled = !showTracks) { onBack() }

    var subtitleScaleIndex by rememberSaveable { mutableIntStateOf(1) }
    val (subtitleScaleLabel, subtitleScaleFraction) = SUBTITLE_SCALES[subtitleScaleIndex]

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

    // Double-tap seek feedback and horizontal-drag scrub preview.
    var seekHud by remember { mutableStateOf<SeekHud?>(null) }
    LaunchedEffect(seekHud) {
        if (seekHud != null) {
            delay(SEEK_HUD_MS)
            seekHud = null
        }
    }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }

    // Format badge: shown briefly whenever a new source starts.
    val formatSummary = rememberFormatSummary(player)
    var formatBadgeVisible by remember(uri) { mutableStateOf(true) }
    LaunchedEffect(uri) {
        formatBadgeVisible = true
        delay(FORMAT_BADGE_MS)
        formatBadgeVisible = false
    }

    val showSpinner = error == null &&
        (presentationState.coverSurface || playbackState == Player.STATE_BUFFERING) &&
        scrubTargetMs == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val forward = offset.x > size.width / 2f
                        if (forward) player.seekForward() else player.seekBack()
                        seekHud = SeekHud(forward = forward, seconds = if (forward) 10 else -10)
                    },
                )
            }
            .pointerInput(uri) {
                var startPositionMs = 0L
                var accumulatedX = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        startPositionMs = player.currentPosition
                        accumulatedX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedX += dragAmount
                        val duration = player.duration.coerceAtLeast(1L)
                        val deltaMs = (accumulatedX / size.width * SCRUB_FULL_WIDTH_MS).toLong()
                        scrubTargetMs = (startPositionMs + deltaMs).coerceIn(0L, duration)
                    },
                    onDragEnd = {
                        scrubTargetMs?.let { player.seekTo(it) }
                        scrubTargetMs = null
                    },
                    onDragCancel = { scrubTargetMs = null },
                )
            }
            .pointerInput(uri) {
                // -1 = left edge (brightness), 1 = right edge (volume), 0 = centre (ignored).
                var zone = 0
                var startValue = 0f
                var accumulated = 0f
                var lastHapticValue = 0f
                val deadZonePx = 16.dp.toPx()

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        zone = when {
                            offset.x < size.width * EDGE_ZONE_FRACTION -> -1
                            offset.x > size.width * (1f - EDGE_ZONE_FRACTION) -> 1
                            else -> 0
                        }
                        accumulated = 0f
                        startValue = if (zone == -1) brightness else volume
                        lastHapticValue = startValue
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (zone != 0) {
                            change.consume()
                            accumulated += -dragAmount // drag up => positive
                            val effective = when {
                                accumulated > deadZonePx -> accumulated - deadZonePx
                                accumulated < -deadZonePx -> accumulated + deadZonePx
                                else -> 0f
                            }
                            val target =
                                (startValue + effective / (size.height * 0.7f)).coerceIn(0f, 1f)
                            val applied = if (zone == -1) {
                                brightness = target.coerceAtLeast(0.01f)
                                activity?.window?.let { w ->
                                    w.attributes = w.attributes.apply { screenBrightness = brightness }
                                }
                                hud = GestureHud.Brightness(brightness)
                                brightness
                            } else {
                                volume = audioManager?.setMusicVolumeFraction(target) ?: target
                                hud = GestureHud.Volume(volume)
                                volume
                            }
                            if (abs(applied - lastHapticValue) >= HAPTIC_STEP) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                lastHapticValue = applied
                            }
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
            textSizeFraction = subtitleScaleFraction,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (controlsVisible) 88.dp else 20.dp),
        )

        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }

        if (controlsVisible && error == null) {
            PlayerControls(
                player = player,
                title = title,
                resizeLabel = resizeLabel,
                onCycleResize = { resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size },
                onOpenTracks = { showTracks = true },
                onBack = onBack,
                onInteraction = { interactionTick++ },
                onScrubbingChange = { scrubbing = it; interactionTick++ },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (error == null) {
            hud?.let { GestureHudOverlay(it, modifier = Modifier.fillMaxSize()) }
            seekHud?.let { SeekFeedback(it, modifier = Modifier.fillMaxSize()) }
        }
        scrubTargetMs?.takeIf { error == null }?.let { targetMs ->
            val durationMs = player.duration.coerceAtLeast(1L)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val bubbleWidthPx = with(density) { 172.dp.toPx() }
                val trackWidthPx = constraints.maxWidth.toFloat()
                val fraction = (targetMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val xPx = (fraction * trackWidthPx - bubbleWidthPx / 2f)
                    .coerceIn(8f, (trackWidthPx - bubbleWidthPx - 8f).coerceAtLeast(8f))
                ThumbnailBubble(
                    state = thumbnailState,
                    positionMs = targetMs,
                    timeLabel = formatTime(targetMs),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 96.dp)
                        .offset { IntOffset(xPx.roundToInt(), 0) },
                )
            }
        }

        if (resumedFromMs > 0L) {
            ResumeChip(
                positionMs = resumedFromMs,
                onRestart = {
                    player.seekTo(0L)
                    resumedFromMs = 0L
                },
                onDismiss = { resumedFromMs = 0L },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 12.dp),
            )
        }

        activeSkip?.takeIf { error == null }?.let { seg ->
            SkipButton(
                segment = seg,
                onSkip = {
                    player.seekTo(seg.endMs ?: player.duration.coerceAtLeast(0L))
                    interactionTick++
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 24.dp, bottom = if (controlsVisible) 104.dp else 32.dp),
            )
        }

        FormatBadge(
            summary = formatSummary,
            visible = formatBadgeVisible && !controlsVisible && error == null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp),
        )

        error?.let {
            ErrorOverlay(
                error = it,
                onRetry = { player.prepare() },
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showTracks) {
            TrackSelectionSheet(
                player = player,
                tracks = tracks,
                subtitleScaleLabel = subtitleScaleLabel,
                onCycleSubtitleScale = {
                    subtitleScaleIndex = (subtitleScaleIndex + 1) % SUBTITLE_SCALES.size
                },
                onDismiss = { showTracks = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class SeekHud(val forward: Boolean, val seconds: Int)

@Composable
private fun SeekFeedback(hud: SeekHud, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = if (hud.forward) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .background(Color(0xB3000000), CircleShape)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hud.forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${abs(hud.seconds)}s",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun ResumeChip(
    positionMs: Long,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(5_000)
        onDismiss()
    }
    Row(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(999.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Resumed from ${formatTime(positionMs)}",
            color = Color.White,
            fontSize = 12.sp,
        )
        androidx.compose.material3.TextButton(onClick = onRestart) { Text("Restart", fontSize = 12.sp) }
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
