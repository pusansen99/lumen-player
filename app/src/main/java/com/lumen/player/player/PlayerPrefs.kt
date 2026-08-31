package com.lumen.player.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

private const val LEGACY_POSITION_PREFIX = "pos_"

/** Names of the obsolete hash-keyed resume entries in a set of DataStore key names. */
fun legacyResumeKeyNames(allKeyNames: Set<String>): Set<String> =
    allKeyNames.filterTo(mutableSetOf()) { it.startsWith(LEGACY_POSITION_PREFIX) }

/**
 * Lightweight preferences: last-played URL, TMDB API key, and a one-time migration flag.
 *
 * Per-video resume positions moved to Room ([com.lumen.player.library.HistoryRepository]) in the
 * library feature. The old `pos_<hash>` entries here cannot be mapped back to their URIs, so they
 * are cleared once on first launch of the new build rather than migrated.
 */
class PlayerPrefs private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setLastUrl(url: String) {
        scope.launch { appContext.dataStore.edit { it[LAST_URL] = url } }
    }

    val lastUrl: Flow<String> = appContext.dataStore.data.map { it[LAST_URL] ?: "" }

    val tmdbApiKey: Flow<String> = appContext.dataStore.data.map { it[TMDB_API_KEY] ?: "" }

    fun setTmdbApiKey(key: String) {
        scope.launch { appContext.dataStore.edit { it[TMDB_API_KEY] = key.trim() } }
    }

    /** Clears the obsolete `pos_*` resume entries exactly once. Safe to call on every launch. */
    suspend fun migrateLegacyResumeData() {
        val alreadyDone = appContext.dataStore.data.first()[LEGACY_MIGRATED] ?: false
        if (alreadyDone) return
        appContext.dataStore.edit { prefs ->
            val stale = legacyResumeKeyNames(prefs.asMap().keys.mapTo(mutableSetOf()) { it.name })
            prefs.asMap().keys.filter { it.name in stale }.forEach { prefs.remove(it) }
            prefs[LEGACY_MIGRATED] = true
        }
    }

    companion object {
        private val LAST_URL = stringPreferencesKey("last_url")
        private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        private val LEGACY_MIGRATED = booleanPreferencesKey("history_migrated_v1")

        @Volatile
        private var instance: PlayerPrefs? = null

        fun get(context: Context): PlayerPrefs =
            instance ?: synchronized(this) {
                instance ?: PlayerPrefs(context.applicationContext).also { instance = it }
            }
    }
}
