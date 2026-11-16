package com.niftysense.app.data.repository

import android.content.Context
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.remote.FyersWebSocketClient
import com.niftysense.app.widget.provider.NiftySenseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MarketUiState(
    val snapshot: MarketSnapshot? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MarketRepository(
    private val context: Context,
    private val dataProvider: MarketDataProvider,
    private val preferencesDataStore: MarketPreferencesDataStore,
    private val fyersWebSocketClient: FyersWebSocketClient? = null,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _uiState = MutableStateFlow(MarketUiState(isLoading = true))
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    init {
        // Load initial cached snapshot
        repositoryScope.launch {
            val cached = preferencesDataStore.getCachedSnapshot()
            if (cached != null) {
                _uiState.value = MarketUiState(snapshot = cached, isLoading = false)
            }
            // Trigger fresh background fetch on startup
            refresh()
        }

        // Initialize and listen to real-time Fyers WebSocket ticks
        if (fyersWebSocketClient != null) {
            repositoryScope.launch {
                val (appId, token) = preferencesDataStore.getFyersCredentials()
                if (!token.isNullOrBlank()) {
                    fyersWebSocketClient.connect(appId, token)
                }
            }

            repositoryScope.launch {
                fyersWebSocketClient.liveSnapshotFlow.collect { freshSnapshot ->
                    if (freshSnapshot != null) {
                        preferencesDataStore.saveSnapshot(freshSnapshot)
                        _uiState.value = MarketUiState(
                            snapshot = freshSnapshot,
                            isLoading = false,
                            errorMessage = null
                        )
                        NiftySenseWidgetProvider.updateAllWidgets(context, freshSnapshot)
                    }
                }
            }
        }
    }

    suspend fun refresh(): Result<MarketSnapshot> {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        val result = dataProvider.fetchMarketSnapshot()

        if (result.isSuccess) {
            val freshSnapshot = result.getOrThrow()
            preferencesDataStore.saveSnapshot(freshSnapshot)
            _uiState.value = MarketUiState(
                snapshot = freshSnapshot,
                isLoading = false,
                errorMessage = null
            )
            // Trigger immediate widget update across all active home-screen widgets
            NiftySenseWidgetProvider.updateAllWidgets(context, freshSnapshot)
            return Result.success(freshSnapshot)
        } else {
            val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to update market data"
            // Keep existing cached data if present
            val currentSnapshot = _uiState.value.snapshot ?: preferencesDataStore.getCachedSnapshot()
            _uiState.value = MarketUiState(
                snapshot = currentSnapshot,
                isLoading = false,
                errorMessage = error
            )
            if (currentSnapshot != null) {
                NiftySenseWidgetProvider.updateAllWidgets(context, currentSnapshot)
            }
            return Result.failure(result.exceptionOrNull() ?: Exception(error))
        }
    }

    suspend fun getCachedOrFreshSnapshot(): MarketSnapshot? {
        val inMemory = _uiState.value.snapshot
        if (inMemory != null) return inMemory
        return preferencesDataStore.getCachedSnapshot()
    }
}
