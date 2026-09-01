package com.lumen.player.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.lumen.player.library.data.LibraryVideoRow

private val PosterSurface = Color(0xFF16161A)
private val PlaceholderTint = Color(0xFFA1A1AA)

/** thumbnailPath (a played video's cached frame) if present, else the document uri (Coil grabs a frame). */
fun videoPosterModel(row: LibraryVideoRow): Any? = row.thumbnailPath ?: row.documentUri

@Composable
fun VideoPoster(model: Any?, modifier: Modifier = Modifier, framePercent: Double = 0.15) {
    val context = LocalContext.current
    Box(modifier = modifier.background(PosterSurface), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Movie, contentDescription = null,
            tint = PlaceholderTint, modifier = Modifier.size(28.dp),
        )
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .videoFramePercent(framePercent)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
