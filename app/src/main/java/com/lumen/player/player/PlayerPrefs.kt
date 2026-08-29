package com.lumen.player.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("player_prefs")

private const val NEAR_EDGE_MS = 5_000L

/** Lightweight persistence: last-played URL and per-video resume positions. */
class PlayerPrefs private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun positionKey(uri: String) = longPreferencesKey("pos_${uri.hashCode()}")

    /** Fire-and-forget. Clears the entry near the start/end so finished videos don't "resume". */
    fun savePosition(uri: String, positionMs: Long, durationMs: Long) {
        scope.launch {
            appContext.dataStore.edit { prefs ->
                val key = positionKey(uri)
                val nearEnd = durationMs > 0 && positionMs > durationMs - NEAR_EDGE_MS
                if (positionMs < NEAR_EDGE_MS || nearEnd) prefs.remove(key)
                else prefs[key] = positionMs
            }
        }
    }

    suspend fun getPosition(uri: String): Long =
        appContext.dataStore.data.first()[positionKey(uri)] ?: 0L

    fun setLastUrl(url: String) {
        scope.launch { appContext.dataStore.edit { it[LAST_URL] = url } }
    }

    val lastUrl: Flow<String> = appContext.dataStore.data.map { it[LAST_URL] ?: "" }

    companion object {
        private val LAST_URL = stringPreferencesKey("last_url")

        @Volatile
        private var instance: PlayerPrefs? = null

        fun get(context: Context): PlayerPrefs =
            instance ?: synchronized(this) {
                instance ?: PlayerPrefs(context.applicationContext).also { instance = it }
            }
    }
}
