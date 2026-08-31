package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.player.PlayerPrefs

private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember { PlayerPrefs.get(context) }
    val savedKey by prefs.tmdbApiKey.collectAsState("")
    var key by remember(savedKey) { mutableStateOf(savedKey) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Settings", color = TextPrimary, fontSize = 18.sp)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("TMDB API key", color = TextPrimary, fontSize = 15.sp)
            Text(
                "Used to fetch posters and titles for library videos (added in a later update). " +
                    "Create a free key at themoviedb.org → Settings → API.",
                color = TextSecondary, fontSize = 12.sp,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { prefs.setTmdbApiKey(key) }) { Text("Save") }
        }
    }
}
