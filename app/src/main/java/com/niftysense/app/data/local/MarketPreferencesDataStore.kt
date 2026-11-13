package com.niftysense.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

        // Fyers Broker Integration Keys
        private val KEY_FYERS_APP_ID = stringPreferencesKey("fyers_app_id")
        private val KEY_FYERS_ACCESS_TOKEN = stringPreferencesKey("fyers_access_token")
        private val KEY_FYERS_ENABLED = booleanPreferencesKey("fyers_enabled")

        // Live Foreground Streaming Service Keys
        private val KEY_LIVE_STREAM_ENABLED = booleanPreferencesKey("live_stream_enabled")
        private val KEY_LIVE_STREAM_INTERVAL_SECONDS = intPreferencesKey("live_stream_interval_seconds")
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

    // --- Fyers Credentials ---
    val fyersAppIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FYERS_APP_ID]?.takeIf { it.isNotBlank() }
    }

    val fyersAccessTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FYERS_ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
    }

    val fyersEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FYERS_ENABLED] ?: false
    }

    suspend fun getFyersCredentials(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        val appId = prefs[KEY_FYERS_APP_ID]?.takeIf { it.isNotBlank() }
        val token = prefs[KEY_FYERS_ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
        val enabled = prefs[KEY_FYERS_ENABLED] ?: false
        return if (enabled && !token.isNullOrEmpty()) {
            Pair(appId, token)
        } else {
            Pair(null, null)
        }
    }

    suspend fun saveFyersCredentials(appId: String?, token: String?, enabled: Boolean = true) {
        context.dataStore.edit { prefs ->
            if (appId != null) prefs[KEY_FYERS_APP_ID] = appId.trim() else prefs.remove(KEY_FYERS_APP_ID)
            if (token != null) prefs[KEY_FYERS_ACCESS_TOKEN] = token.trim() else prefs.remove(KEY_FYERS_ACCESS_TOKEN)
            prefs[KEY_FYERS_ENABLED] = enabled && !token.isNullOrBlank()
        }
    }

    suspend fun clearFyersCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_FYERS_APP_ID)
            prefs.remove(KEY_FYERS_ACCESS_TOKEN)
            prefs[KEY_FYERS_ENABLED] = false
        }
    }

    // --- Live Streaming Service ---
    val liveStreamEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_STREAM_ENABLED] ?: false
    }

    val liveStreamIntervalSecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_STREAM_INTERVAL_SECONDS] ?: 15
    }

    suspend fun setLiveStreamEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_STREAM_ENABLED] = enabled
        }
    }

    suspend fun setLiveStreamIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_STREAM_INTERVAL_SECONDS] = seconds.coerceIn(10, 60)
        }
    }

    suspend fun clearCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CACHED_SNAPSHOT)
        }
    }
}
