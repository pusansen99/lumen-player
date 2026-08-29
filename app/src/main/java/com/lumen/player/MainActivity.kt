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
import com.lumen.player.player.PlayerScreen
import com.lumen.player.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {

    /** A video handed to us via ACTION_VIEW / ACTION_SEND, if any. Observed by Compose. */
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        externalUri = extractVideoUri(intent)
        setContent {
            LumenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerScreen(
                        externalUri = externalUri?.toString(),
                        onExternalUriConsumed = { externalUri = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractVideoUri(intent)?.let { externalUri = it }
    }

    private fun extractVideoUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
