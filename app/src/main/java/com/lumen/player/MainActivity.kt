package com.lumen.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.lumen.player.library.data.SourceType
import com.lumen.player.player.PlayerScreen
import com.lumen.player.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {

    private data class IncomingVideo(
        val uri: Uri,
        val sourceType: SourceType,
        /** Whether a persistable read grant was actually taken (always true for non-content URIs). */
        val hasPersistedPermission: Boolean,
    )

    private var incoming by mutableStateOf<IncomingVideo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        incoming = extractVideo(intent)
        setContent {
            LumenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerScreen(
                        externalUri = incoming?.uri?.toString(),
                        externalSourceType = incoming?.sourceType,
                        externalHasPersistedPermission = incoming?.hasPersistedPermission ?: true,
                        onExternalUriConsumed = { incoming = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractVideo(intent)?.let { incoming = it }
    }

    private fun extractVideo(intent: Intent?): IncomingVideo? {
        val (uri, type) = when (intent?.action) {
            Intent.ACTION_VIEW ->
                intent.data to SourceType.EXTERNAL_VIEW
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) to
                    SourceType.EXTERNAL_SEND
            else -> null to SourceType.EXTERNAL_VIEW
        }
        if (uri == null) return null
        val hasPersistedPermission = if (uri.scheme == "content") {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
        } else {
            true
        }
        return IncomingVideo(uri, type, hasPersistedPermission)
    }
}
