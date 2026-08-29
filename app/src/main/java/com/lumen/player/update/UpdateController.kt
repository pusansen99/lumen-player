package com.lumen.player.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data class NeedsInstallPermission(val apk: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * Drives the in-app update flow: check GitHub for a newer release, download the APK with progress,
 * then launch the package installer (asking for the install-unknown-apps permission if needed).
 */
class UpdateController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repository: UpdateRepository = UpdateRepository(),
) {
    var state by mutableStateOf<UpdateUiState>(UpdateUiState.Hidden)
        private set

    private var checkStarted = false
    private var currentInfo: UpdateInfo? = null
    private var downloadedApk: File? = null

    /** Runs the release check at most once per controller instance. Silent on failure / no update. */
    fun checkOnce() {
        if (checkStarted) return
        checkStarted = true
        scope.launch {
            val info = runCatching { repository.fetchLatest() }.getOrNull() ?: return@launch
            currentInfo = info
            if (state is UpdateUiState.Hidden) state = UpdateUiState.Available(info)
        }
    }

    fun dismiss() {
        state = UpdateUiState.Hidden
    }

    fun startDownload() {
        val info = currentInfo ?: return
        state = UpdateUiState.Downloading(0)
        scope.launch {
            runCatching {
                ApkInstaller.download(appContext, info.apkUrl) { pct ->
                    state = UpdateUiState.Downloading(pct)
                }
            }.onSuccess { apk ->
                downloadedApk = apk
                launchInstall(apk)
            }.onFailure { e ->
                state = UpdateUiState.Failed(e.message ?: "Download failed")
            }
        }
    }

    /** Call after returning from the install-permission settings screen. */
    fun onInstallPermissionResult() {
        val apk = downloadedApk ?: return
        launchInstall(apk)
    }

    fun retry() {
        currentInfo?.let { state = UpdateUiState.Available(it) }
    }

    private fun launchInstall(apk: File) {
        if (ApkInstaller.canInstall(appContext)) {
            ApkInstaller.install(appContext, apk)
            state = UpdateUiState.Hidden
        } else {
            state = UpdateUiState.NeedsInstallPermission(apk)
        }
    }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateController(context, scope) }
}
