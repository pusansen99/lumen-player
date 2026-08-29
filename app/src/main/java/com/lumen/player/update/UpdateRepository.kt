package com.lumen.player.update

import com.lumen.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A release newer than the running build, with a downloadable APK asset. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
)

/** Queries the GitHub Releases API for [BuildConfig.UPDATE_REPO]. No auth (public repo). */
class UpdateRepository(
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val repo: String = BuildConfig.UPDATE_REPO,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Returns an [UpdateInfo] when a newer release with an .apk asset exists, else null. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null // 404 = no releases yet
            val json = JSONObject(response.body.string())

            val tag = json.optString("tag_name").ifBlank { return@withContext null }
            if (!isNewer(remote = tag, current = currentVersionName)) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return@withContext UpdateInfo(
                        versionName = tag.removePrefix("v"),
                        notes = json.optString("body").trim(),
                        apkUrl = asset.optString("browser_download_url"),
                        apkName = name,
                        apkSize = asset.optLong("size", 0L),
                    )
                }
            }
            null // release exists but ships no APK
        }
    }

    companion object {
        private fun parts(version: String): List<Int> =
            Regex("""\d+""").findAll(version.substringBefore('-'))
                .map { it.value.toIntOrNull() ?: 0 }
                .toList()
                .ifEmpty { listOf(0) }

        /** Semantic-ish comparison: "v0.3.0" is newer than "0.2.0". Pre-release suffixes ignored. */
        fun isNewer(remote: String, current: String): Boolean {
            val r = parts(remote)
            val c = parts(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
