package com.lumen.player.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Downloads an update APK via the system [DownloadManager] and hands it to the package installer. */
object ApkInstaller {

    private const val FILE_NAME = "lumen-update.apk"

    /** True once the user has granted this app "install unknown apps". */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants the install-unknown-apps permission. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    /**
     * Downloads [url] into the app's external Download dir, reporting progress as 0..100.
     * Returns the downloaded file. Cancelling the coroutine removes the pending download.
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(DownloadManager::class.java)
            ?: throw IOException("DownloadManager unavailable")

        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(url.toUri())
            .setTitle("Lumen update")
            .setDescription("Downloading the new version")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val id = dm.enqueue(request)
        try {
            while (true) {
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) return@use
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            onProgress(100)
                            return@withContext dest
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            throw IOException("Download failed (reason $reason)")
                        }
                        else -> {
                            val done = c.getLong(
                                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                            val total = c.getLong(
                                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
                delay(400)
            }
            @Suppress("UNREACHABLE_CODE")
            dest
        } catch (e: CancellationException) {
            dm.remove(id)
            throw e
        }
    }

    /** Deletes a previously downloaded update APK (call on app start, after an install). */
    fun deleteDownloaded(context: Context) {
        runCatching {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
                .takeIf { it.exists() }
                ?.delete()
        }
    }

    /** Launches the system package installer for a downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
