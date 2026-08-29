package com.lumen.player.player

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** One thumbnail entry from a WebVTT thumbnails track. [rect] is null for one-image-per-cue tracks. */
data class ThumbnailCue(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    val hasRect get() = w > 0 && h > 0
}

class ThumbnailTrack(private val cues: List<ThumbnailCue>) {
    val isEmpty get() = cues.isEmpty()

    fun cueAt(positionMs: Long): ThumbnailCue? {
        if (cues.isEmpty()) return null
        // cues are sorted by startMs
        var lo = 0
        var hi = cues.size - 1
        var result = cues.first()
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val c = cues[mid]
            when {
                positionMs < c.startMs -> hi = mid - 1
                positionMs >= c.endMs -> {
                    result = c
                    lo = mid + 1
                }
                else -> return c
            }
        }
        return result
    }
}

/** Pairs a resolved [ThumbnailTrack] with the store that can fetch its sprite images. */
class ThumbnailState(val track: ThumbnailTrack, val store: ThumbnailStore)

/** Resolves an optional WebVTT thumbnails sidecar for a media URL and caches its sprite bitmaps. */
class ThumbnailStore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val spriteCache = object : LruCache<String, ImageBitmap>(6) {}

    /** Tries a few conventional sidecar paths; returns the first that parses to >=1 cue. */
    suspend fun resolve(mediaUrl: String): ThumbnailTrack? = withContext(Dispatchers.IO) {
        for (candidate in candidateVttUrls(mediaUrl)) {
            val body = runCatching {
                client.newCall(Request.Builder().url(candidate).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body.string() else null
                }
            }.getOrNull() ?: continue
            val track = parseWebVtt(body, baseUrl = candidate)
            if (track != null && !track.isEmpty) return@withContext track
        }
        null
    }

    suspend fun sprite(url: String): ImageBitmap? {
        spriteCache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body.bytes() else null
                }
            }.getOrNull() ?: return@withContext null
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?: return@withContext null
            bitmap.asImageBitmap().also { spriteCache.put(url, it) }
        }
    }

    private fun candidateVttUrls(mediaUrl: String): List<String> {
        val noQuery = mediaUrl.substringBefore('?').substringBefore('#')
        val withoutExt = noQuery.substringBeforeLast('.', noQuery)
        return listOf(
            "$withoutExt.vtt",
            "$withoutExt-thumbnails.vtt",
            "$withoutExt.thumbnails.vtt",
            "$noQuery.vtt",
            noQuery.substringBeforeLast('/', "") + "/thumbnails.vtt",
        ).filter { it.startsWith("http") }.distinct()
    }
}

private val TIME_RE = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?""")
private val XYWH_RE = Regex("""#xywh=(\d+),(\d+),(\d+),(\d+)""", RegexOption.IGNORE_CASE)

private fun parseTime(s: String): Long? {
    val m = TIME_RE.matchEntire(s.trim()) ?: return null
    val (h, mm, ss, ms) = m.destructured
    val hours = h.toLongOrNull() ?: 0L
    return ((hours * 3600) + (mm.toLong() * 60) + ss.toLong()) * 1000 +
        (ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
}

/** Minimal WebVTT thumbnails parser: time-range blocks whose payload is an image URL (+ #xywh). */
private fun parseWebVtt(content: String, baseUrl: String): ThumbnailTrack? {
    if (!content.trimStart().startsWith("WEBVTT")) return null
    val cues = mutableListOf<ThumbnailCue>()
    val blocks = content.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
    for (block in blocks) {
        val lines = block.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val timingLine = lines.firstOrNull { it.contains("-->") } ?: continue
        val payload = lines.getOrNull(lines.indexOf(timingLine) + 1) ?: continue

        val parts = timingLine.split("-->")
        val start = parseTime(parts.getOrNull(0)?.trim().orEmpty()) ?: continue
        val end = parseTime(parts.getOrNull(1)?.trim()?.substringBefore(' ').orEmpty()) ?: continue

        val xywh = XYWH_RE.find(payload)
        val imageRef = payload.substringBefore('#').trim()
        val absolute = runCatching { URI(baseUrl).resolve(imageRef).toString() }.getOrNull() ?: imageRef

        cues += ThumbnailCue(
            startMs = start,
            endMs = end,
            imageUrl = absolute,
            x = xywh?.groupValues?.get(1)?.toInt() ?: 0,
            y = xywh?.groupValues?.get(2)?.toInt() ?: 0,
            w = xywh?.groupValues?.get(3)?.toInt() ?: 0,
            h = xywh?.groupValues?.get(4)?.toInt() ?: 0,
        )
    }
    if (cues.isEmpty()) return null
    return ThumbnailTrack(cues.sortedBy { it.startMs })
}

/** Resolves the thumbnails sidecar once per media URL. Null when there is none. */
@Composable
fun rememberThumbnailState(mediaUri: String): ThumbnailState? {
    val store = remember { ThumbnailStore() }
    var state by remember(mediaUri) { mutableStateOf<ThumbnailState?>(null) }
    LaunchedEffect(mediaUri) {
        state = if (mediaUri.startsWith("http", ignoreCase = true)) {
            runCatching { store.resolve(mediaUri) }.getOrNull()?.let { ThumbnailState(it, store) }
        } else {
            null
        }
    }
    return state
}

/** The preview frame for [positionMs]; loads its sprite lazily. Null while loading / unavailable. */
@Composable
fun rememberThumbnailFrame(state: ThumbnailState, positionMs: Long): Pair<ImageBitmap, ThumbnailCue>? {
    val cue = remember(state, positionMs / 1000) { state.track.cueAt(positionMs) } ?: return null
    var sprite by remember(cue.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(cue.imageUrl) { sprite = state.store.sprite(cue.imageUrl) }
    return sprite?.let { it to cue }
}

/** Draws [frame] cropped to [cue]'s rect (or whole image) at the target box size. */
fun DrawScope.drawThumbnail(frame: ImageBitmap, cue: ThumbnailCue) {
    if (cue.hasRect) {
        drawImage(
            image = frame,
            srcOffset = IntOffset(cue.x, cue.y),
            srcSize = IntSize(cue.w, cue.h),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    } else {
        drawImage(
            image = frame,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

/** Thumbnail + timestamp bubble used by the scrub preview. */
@Composable
fun ThumbnailBubble(
    state: ThumbnailState?,
    positionMs: Long,
    timeLabel: String,
    modifier: Modifier = Modifier,
) {
    val frame = state?.let { rememberThumbnailFrame(it, positionMs) }
    Column(
        modifier = modifier
            .background(Color(0xE6000000), RoundedCornerShape(10.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (frame != null) {
            val (bitmap, cue) = frame
            Canvas(modifier = Modifier.size(width = 160.dp, height = 90.dp)) {
                drawThumbnail(bitmap, cue)
            }
        }
        Text(
            text = timeLabel,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = if (frame != null) 4.dp else 2.dp),
        )
    }
}
