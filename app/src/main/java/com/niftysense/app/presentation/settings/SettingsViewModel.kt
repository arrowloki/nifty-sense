package com.niftysense.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
