package com.lumen.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SkipType(val label: String) {
    INTRO("Skip intro"),
    RECAP("Skip recap"),
    CREDITS("Skip credits"),
}

/** A skippable stretch of the timeline. [endMs] null = runs to the end of the video. */
data class SkipSegment(val type: SkipType, val startMs: Long, val endMs: Long?)

private const val SHOW_LEAD_MS = 0L

/**
 * Resolves an optional "skip segments" sidecar for a media URL:
 * `{ "segments": [ { "type": "intro", "start": 8000, "end": 98000 }, ... ] }`
 * (`start`/`startMs` and `end`/`endMs` both accepted; type intro|recap|credits|outro).
 */
class SkipSegmentsStore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(mediaUrl: String): List<SkipSegment>? = withContext(Dispatchers.IO) {
        for (candidate in candidateUrls(mediaUrl)) {
            val body = runCatching {
                client.newCall(Request.Builder().url(candidate).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body.string() else null
                }
            }.getOrNull() ?: continue
            val parsed = runCatching { parse(body) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return@withContext parsed
        }
        null
    }

    private fun candidateUrls(mediaUrl: String): List<String> {
        val noQuery = mediaUrl.substringBefore('?').substringBefore('#')
        val base = noQuery.substringBeforeLast('.', noQuery)
        val dir = noQuery.substringBeforeLast('/', "")
        return listOf(
            "$base.skip.json",
            "$base-skip.json",
            "$base.segments.json",
            "$dir/skip.json",
        ).filter { it.startsWith("http") }.distinct()
    }

    internal fun parse(json: String): List<SkipSegment> {
        val arr = JSONObject(json).optJSONArray("segments") ?: return emptyList()
        val out = ArrayList<SkipSegment>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = when (o.optString("type").lowercase()) {
                "intro", "opening" -> SkipType.INTRO
                "recap", "previously" -> SkipType.RECAP
                "credits", "outro", "ending" -> SkipType.CREDITS
                else -> continue
            }
            val start = o.optLong("startMs", o.optLong("start", -1L))
            if (start < 0L) continue
            val endRaw = o.optLong("endMs", o.optLong("end", -1L))
            out += SkipSegment(type, start, endRaw.takeIf { it > start })
        }
        return out.sortedBy { it.startMs }
    }
}

@Composable
fun rememberSkipSegments(mediaUri: String): List<SkipSegment> {
    val store = remember { SkipSegmentsStore() }
    var segments by remember(mediaUri) { mutableStateOf(emptyList<SkipSegment>()) }
    LaunchedEffect(mediaUri) {
        segments = if (mediaUri.startsWith("http", ignoreCase = true)) {
            runCatching { store.resolve(mediaUri) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
    }
    return segments
}

/** The segment covering [positionMs], if any. */
fun activeSkipSegment(positionMs: Long, durationMs: Long, segments: List<SkipSegment>): SkipSegment? =
    segments.firstOrNull { seg ->
        val end = seg.endMs ?: durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        positionMs >= seg.startMs - SHOW_LEAD_MS && positionMs < end
    }

/** Bottom-right pill shown while inside a skip segment. */
@Composable
fun SkipButton(segment: SkipSegment, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xE6151518), RoundedCornerShape(999.dp))
            .clickable(onClick = onSkip)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = segment.type.label, color = Color(0xFFF5F5F7), fontSize = 14.sp)
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = null,
            tint = Color(0xFFF5F5F7),
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}
