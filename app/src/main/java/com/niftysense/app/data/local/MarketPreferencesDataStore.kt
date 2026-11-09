package com.niftysense.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.niftysense.app.data.model.MarketSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "niftysense_preferences")

class MarketPreferencesDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    companion object {
        private val KEY_CACHED_SNAPSHOT = stringPreferencesKey("cached_market_snapshot")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        private val KEY_LAST_REFRESH_TIME = longPreferencesKey("last_refresh_timestamp")
    }

    val cachedSnapshotFlow: Flow<MarketSnapshot?> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CACHED_SNAPSHOT]
        if (!raw.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<MarketSnapshot>(raw).copy(isCached = true)
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun getCachedSnapshot(): MarketSnapshot? {
        return cachedSnapshotFlow.first()
    }

    suspend fun saveSnapshot(snapshot: MarketSnapshot) {
        val raw = json.encodeToString(snapshot)
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHED_SNAPSHOT] = raw
            prefs[KEY_LAST_REFRESH_TIME] = snapshot.lastSyncTimestamp
        }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    val refreshIntervalFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_INTERVAL] ?: 15
    }

    suspend fun setRefreshInterval(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REFRESH_INTERVAL] = minutes
        }
    }

    suspend fun clearCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CACHED_SNAPSHOT)
        }
    }
}
