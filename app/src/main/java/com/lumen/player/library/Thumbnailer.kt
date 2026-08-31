package com.lumen.player.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import com.lumen.player.library.data.normalizeMediaUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Thumbnailer"

/** Sub-directory of `filesDir` holding cached poster JPEGs. Shared with [HistoryRepository] cleanup. */
internal const val THUMB_DIR = "thumbs"
private const val TARGET_WIDTH = 320
private const val JPEG_QUALITY = 80

/** Stable, path-safe file name for the cached frame of [rawUri]. */
fun thumbFileName(rawUri: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(normalizeMediaUri(rawUri).toByteArray())
    return "thumb_" + digest.joinToString("") { "%02x".format(it) }.take(32) + ".jpg"
}

/**
 * Grabs one representative frame near [atMs] and caches it as a JPEG under `filesDir/thumbs`.
 * Returns the absolute path, or null for sources a frame cannot be pulled from (most network
 * streams, DRM, codec failures). Never throws.
 */
suspend fun captureFrame(context: Context, rawUri: String, atMs: Long): String? =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
        val out = File(dir, thumbFileName(rawUri))
        if (out.exists() && out.length() > 0L) return@withContext out.absolutePath

        val retriever = MediaMetadataRetriever()
        try {
            // Only ever called for local content; the sole caller guards out http(s) URIs.
            retriever.setDataSource(context, rawUri.toUri())
            val atUs = (atMs.coerceAtLeast(0L)) * 1000L
            val frame = retriever.getScaledFrameAtTime(
                atUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                TARGET_WIDTH,
                (TARGET_WIDTH * 9 / 16),
            ) ?: return@withContext null

            FileOutputStream(out).use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            frame.recycle()
            out.absolutePath
        } catch (t: Throwable) {
            Log.d(TAG, "no frame for $rawUri", t)
            runCatching { out.delete() }
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
