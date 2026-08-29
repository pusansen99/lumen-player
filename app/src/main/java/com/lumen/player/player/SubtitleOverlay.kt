package com.lumen.player.player

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** White text, no background box, black outline for contrast against bright video. */
private val TransparentCaptionStyle = CaptionStyleCompat(
    /* foregroundColor = */ Color.WHITE,
    /* backgroundColor = */ Color.TRANSPARENT,
    /* windowColor = */ Color.TRANSPARENT,
    /* edgeType = */ CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    /* edgeColor = */ Color.BLACK,
    /* typeface = */ null,
)

/**
 * Draws the player's current text-track cues. [PlayerSurface] renders video only, so subtitles need
 * their own view. [textSizeFraction] is the cue height as a fraction of the view height.
 */
@Composable
fun SubtitleOverlay(
    player: Player,
    textSizeFraction: Float,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf<SubtitleView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SubtitleView(context).apply {
                // Ignore stream-embedded colours/sizes so the black caption box never comes back.
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                setStyle(TransparentCaptionStyle)
            }.also { view = it }
        },
        update = { it.setFractionalTextSize(textSizeFraction) },
    )

    DisposableEffect(player, view) {
        val subtitleView = view ?: return@DisposableEffect onDispose {}
        subtitleView.setCues(player.currentCues.cues)
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                subtitleView.setCues(cueGroup.cues)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
