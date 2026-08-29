package com.lumen.player.player

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
import androidx.media3.ui.SubtitleView

/**
 * Draws the player's current text-track cues. [PlayerSurface] renders video only, so subtitles need
 * their own view. Cues are pushed from a [Player.Listener]; styling follows the device caption
 * settings.
 */
@Composable
fun SubtitleOverlay(player: Player, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<SubtitleView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SubtitleView(context).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }.also { view = it }
        },
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
