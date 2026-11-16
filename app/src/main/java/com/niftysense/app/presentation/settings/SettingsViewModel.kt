package com.niftysense.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niftysense.app.data.remote.FyersWebSocketState
import com.niftysense.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FyersTestState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val preferencesDataStore = container.preferencesDataStore

    val themeMode: StateFlow<String> = preferencesDataStore.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "SYSTEM"
    )

    val refreshInterval: StateFlow<Int> = preferencesDataStore.refreshIntervalFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 15
    )

    val fyersAppId: StateFlow<String?> = preferencesDataStore.fyersAppIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val fyersAccessToken: StateFlow<String?> = preferencesDataStore.fyersAccessTokenFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val fyersEnabled: StateFlow<Boolean> = preferencesDataStore.fyersEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val fyersWebSocketState: StateFlow<FyersWebSocketState> = container.fyersWebSocketClient.connectionState

    private val _fyersTestState = MutableStateFlow(FyersTestState())
    val fyersTestState: StateFlow<FyersTestState> = _fyersTestState.asStateFlow()

    fun testFyersConnection(appId: String?, token: String?) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true, message = null)
            val result = container.fyersProvider.testConnection(appId, token)
            if (result.isSuccess) {
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = result.getOrNull(),
                    isSuccess = true
                )
                // Attempt WebSocket live stream connection immediately
                container.fyersWebSocketClient.connect(appId, token)
                container.marketRepository.refresh()
            } else {
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = result.exceptionOrNull()?.localizedMessage ?: "Connection test failed",
                    isSuccess = false
                )
            }
        }
    }

    fun saveFyersCredentials(appId: String?, token: String?, enabled: Boolean = true) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true, message = "Verifying token with Fyers...")
            val testResult = container.fyersProvider.testConnection(appId, token)

            if (testResult.isSuccess) {
                preferencesDataStore.saveFyersCredentials(appId, token, enabled = true)
                container.fyersWebSocketClient.connect(appId, token)
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = "✓ Fyers Connected! Live broker data & WebSocket streaming active.\n${testResult.getOrNull()}",
                    isSuccess = true
                )
                container.marketRepository.refresh()
            } else {
                // Save credentials locally so user does not lose text, but report verification issue
                preferencesDataStore.saveFyersCredentials(appId, token, enabled = true)
                val errorMsg = testResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = "Saved locally, but verification failed:\n$errorMsg\n\nApp will fallback to public web feed until a valid token is provided.",
                    isSuccess = false
                )
                container.marketRepository.refresh()
            }
        }
    }

    fun clearFyersCredentials() {
        viewModelScope.launch {
            preferencesDataStore.clearFyersCredentials()
            container.fyersWebSocketClient.disconnect()
            _fyersTestState.value = FyersTestState(
                isLoading = false,
                message = "Fyers credentials cleared. Reverted to public web feed.",
                isSuccess = null
            )
            container.marketRepository.refresh()
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesDataStore.setThemeMode(mode)
        }
    }

    fun setRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            preferencesDataStore.setRefreshInterval(minutes)
            container.schedulePeriodicMarketUpdates(minutes)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            preferencesDataStore.clearCache()
            container.marketRepository.refresh()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(container) as T
        }
    }
}
