package com.lumen.player.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Renders whatever update step [controller] is on. Nothing when there is no update. */
@Composable
fun UpdateDialog(controller: UpdateController) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { controller.onInstallPermissionResult() }

    when (val state = controller.state) {
        UpdateUiState.Hidden -> Unit

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Update available") },
            text = {
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text("Version ${state.info.versionName}")
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            text = state.info.notes,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = controller::startDownload) { Text("Download") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("Later") } },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(text = "${state.percent}%", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
        )

        is UpdateUiState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Allow installs") },
            text = { Text("Lumen needs permission to install app updates. Enable it, then return here.") },
            confirmButton = {
                TextButton(onClick = {
                    permissionLauncher.launch(ApkInstaller.installPermissionIntent(context))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("Cancel") } },
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = controller::retry) { Text("Retry") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("Dismiss") } },
        )
    }
}
