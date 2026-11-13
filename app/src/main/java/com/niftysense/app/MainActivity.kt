package com.niftysense.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.niftysense.app.presentation.home.HomeScreen
import com.niftysense.app.presentation.home.HomeViewModel
import com.niftysense.app.presentation.settings.SettingsScreen
import com.niftysense.app.presentation.settings.SettingsViewModel
import com.niftysense.app.presentation.theme.NiftySenseTheme
import com.niftysense.app.service.LiveMarketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NiftySenseApp
        val container = app.container

        requestNotificationPermissionIfNeeded()

        // If user previously enabled live streaming, ensure the service is running
        CoroutineScope(Dispatchers.IO).launch {
            val isLiveStreamEnabled = container.preferencesDataStore.liveStreamEnabledFlow.first()
            if (isLiveStreamEnabled && !LiveMarketService.isServiceRunning) {
                LiveMarketService.start(this@MainActivity)
            }
        }

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
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger live rates update whenever user brings the app to foreground
        val app = application as? NiftySenseApp
        app?.container?.marketRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.refresh()
            }
        }
    }
}
