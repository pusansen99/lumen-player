package com.lumen.player.library.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.transition.CrossfadeDrawable
import coil3.video.VideoFrameDecoder

/**
 * App-wide Coil [ImageLoader]: bounded memory + disk caches, plus video-frame decoding so
 * `content://` (and file) video models render a poster frame as their thumbnail.
 *
 * The instance is created once and reused for the life of the process.
 */
object LumenImageLoader {

    private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): ImageLoader =
        ImageLoader.Builder(appContext)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(CrossfadeDrawable.DEFAULT_DURATION)
            .build()
}
