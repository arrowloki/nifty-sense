package com.niftysense.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.niftysense.app.presentation.home.HomeScreen
import com.niftysense.app.presentation.home.HomeViewModel
import com.niftysense.app.presentation.pip.PipLiveTickerScreen
import com.niftysense.app.presentation.settings.SettingsScreen
import com.niftysense.app.presentation.settings.SettingsViewModel
import com.niftysense.app.presentation.theme.NiftySenseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NiftySenseApp
        val container = app.container

        updatePipParams()

        setContent {
            val themeMode by container.preferencesDataStore.themeModeFlow.collectAsState(initial = "SYSTEM")
            val navController = rememberNavController()

            val homeViewModel: HomeViewModel by viewModels {
                HomeViewModel.Factory(container.marketRepository)
            }
            val settingsViewModel: SettingsViewModel by viewModels {
                SettingsViewModel.Factory(container)
            }

            NiftySenseTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isInPipMode) {
                        val uiState by homeViewModel.uiState.collectAsState()
                        PipLiveTickerScreen(
                            snapshot = uiState.snapshot,
                            onRefresh = { homeViewModel.refresh() }
                        )
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onEnterPip = { enterPip() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPip()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePipParams()
        val app = application as? NiftySenseApp
        app?.container?.marketRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.refresh()
            }
        }
    }
}
