package com.niftysense.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niftysense.app.data.remote.FyersWebSocketState
import com.niftysense.app.data.remote.dto.FyersTokenSanitizer
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

    val fyersSecretKey: StateFlow<String?> = preferencesDataStore.fyersSecretKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val fyersRedirectUri: StateFlow<String> = preferencesDataStore.fyersRedirectUriFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "https://trade.fyers.in/api-login/redirect-uri/index.html"
    )

    val fyersEnabled: StateFlow<Boolean> = preferencesDataStore.fyersEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val lockScreenTickerEnabled: StateFlow<Boolean> = preferencesDataStore.lockScreenTickerEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val fyersWebSocketState: StateFlow<FyersWebSocketState> = container.fyersWebSocketClient.connectionState

    private val _fyersTestState = MutableStateFlow(FyersTestState())
    val fyersTestState: StateFlow<FyersTestState> = _fyersTestState.asStateFlow()

    fun testFyersConnection(appId: String?, token: String?) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true, message = "Testing token with Fyers servers...")
            val result = container.fyersProvider.testConnection(appId, token)
            if (result.isSuccess) {
                val connResult = result.getOrNull()
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = connResult?.message,
                    isSuccess = true
                )
                // Attempt WebSocket live stream connection immediately
                val workingAppId = connResult?.resolvedAppId ?: appId
                val workingToken = connResult?.resolvedToken ?: token
                container.fyersWebSocketClient.connect(workingAppId, workingToken)
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

    fun saveFyersCredentials(appId: String?, token: String?, secretKey: String? = null, enabled: Boolean = true) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true, message = "Verifying token with Fyers...")
            val testResult = container.fyersProvider.testConnection(appId, token)

            if (testResult.isSuccess) {
                val connResult = testResult.getOrNull()
                val workingAppId = connResult?.resolvedAppId ?: FyersTokenSanitizer.cleanAppId(appId)
                val workingToken = connResult?.resolvedToken ?: FyersTokenSanitizer.cleanToken(token)

                preferencesDataStore.saveFyersCredentials(workingAppId, workingToken, secretKey, enabled = true)
                container.fyersWebSocketClient.connect(workingAppId, workingToken)
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = "✓ Fyers Connected! Live broker data & WebSocket streaming active.\n${connResult?.message}",
                    isSuccess = true
                )
                container.marketRepository.refresh()
            } else {
                // Save sanitized credentials locally so user does not lose text, but report verification issue
                val cleanId = FyersTokenSanitizer.cleanAppId(appId)
                val cleanTok = FyersTokenSanitizer.cleanToken(token)
                preferencesDataStore.saveFyersCredentials(cleanId, cleanTok, secretKey, enabled = true)

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

    fun exchangeAndSaveToken(appId: String, secretKey: String, authCodeOrUrl: String) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true, message = "Exchanging Auth Code for Access Token with Fyers...")
            val exchangeResult = container.fyersProvider.exchangeAuthCodeForAccessToken(appId, secretKey, authCodeOrUrl)

            if (exchangeResult.isSuccess) {
                val accessToken = exchangeResult.getOrNull() ?: ""
                _fyersTestState.value = FyersTestState(isLoading = true, message = "Access token generated! Connecting to live feed...")
                saveFyersCredentials(appId, accessToken, secretKey = secretKey, enabled = true)
            } else {
                val errorMsg = exchangeResult.exceptionOrNull()?.localizedMessage ?: "Failed to exchange auth code."
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = "Failed to generate Access Token:\n$errorMsg\n\nPlease ensure App ID, Secret Key, and Auth Code are correct.",
                    isSuccess = false
                )
            }
        }
    }

    fun saveFyersSecretKey(secretKey: String?) {
        viewModelScope.launch {
            preferencesDataStore.saveFyersSecretKey(secretKey)
        }
    }

    fun saveFyersRedirectUri(uri: String) {
        viewModelScope.launch {
            preferencesDataStore.saveFyersRedirectUri(uri)
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

    fun setLockScreenTickerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setLockScreenTickerEnabled(enabled)
            val current = container.marketRepository.getCachedOrFreshSnapshot()
            com.niftysense.app.widget.lockscreen.LockScreenTickerManager.updateNotification(
                container.context,
                current,
                enabled
            )
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
