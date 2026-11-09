package com.niftysense.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niftysense.app.data.repository.MarketRepository
import com.niftysense.app.data.repository.MarketUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val marketRepository: MarketRepository
) : ViewModel() {

    val uiState: StateFlow<MarketUiState> = marketRepository.uiState

    fun refresh() {
        viewModelScope.launch {
            marketRepository.refresh()
        }
    }

    class Factory(private val marketRepository: MarketRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(marketRepository) as T
        }
    }
}
