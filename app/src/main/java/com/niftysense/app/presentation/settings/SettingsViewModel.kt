package com.niftysense.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.di.AppContainer
import com.niftysense.app.service.LiveMarketService
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

    val liveStreamEnabled: StateFlow<Boolean> = preferencesDataStore.liveStreamEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val liveStreamInterval: StateFlow<Int> = preferencesDataStore.liveStreamIntervalSecondsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 15
    )

    private val _fyersTestState = MutableStateFlow(FyersTestState())
    val fyersTestState: StateFlow<FyersTestState> = _fyersTestState.asStateFlow()

    fun testFyersConnection(appId: String?, token: String?) {
        viewModelScope.launch {
            _fyersTestState.value = FyersTestState(isLoading = true)
            val result = container.fyersProvider.testConnection(appId, token)
            if (result.isSuccess) {
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = result.getOrNull(),
                    isSuccess = true
                )
                // Automatically save verified credentials
                preferencesDataStore.saveFyersCredentials(appId, token, enabled = true)
                container.marketRepository.refresh()
            } else {
                _fyersTestState.value = FyersTestState(
                    isLoading = false,
                    message = result.exceptionOrNull()?.localizedMessage ?: "Connection failed",
                    isSuccess = false
                )
            }
        }
    }

    fun saveFyersCredentials(appId: String?, token: String?, enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.saveFyersCredentials(appId, token, enabled)
            container.marketRepository.refresh()
        }
    }

    fun clearFyersCredentials() {
        viewModelScope.launch {
            preferencesDataStore.clearFyersCredentials()
            _fyersTestState.value = FyersTestState()
            container.marketRepository.refresh()
        }
    }

    fun setLiveStreamEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            preferencesDataStore.setLiveStreamEnabled(enabled)
            if (enabled) {
                LiveMarketService.start(context)
            } else {
                LiveMarketService.stop(context)
            }
        }
    }

    fun setLiveStreamInterval(seconds: Int) {
        viewModelScope.launch {
            preferencesDataStore.setLiveStreamIntervalSeconds(seconds)
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
