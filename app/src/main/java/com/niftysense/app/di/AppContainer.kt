package com.niftysense.app.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.data.remote.FyersMarketDataProvider
import com.niftysense.app.data.remote.FyersWebSocketClient
import com.niftysense.app.data.remote.HybridMarketDataProvider
import com.niftysense.app.data.remote.SmartMarketDataProvider
import com.niftysense.app.data.repository.MarketDataProvider
import com.niftysense.app.data.repository.MarketRepository
import com.niftysense.app.widget.worker.MarketUpdateWorker
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class AppContainer(val context: Context) {

    val preferencesDataStore: MarketPreferencesDataStore by lazy {
        MarketPreferencesDataStore(context)
    }

    val fyersWebSocketClient: FyersWebSocketClient by lazy {
        FyersWebSocketClient()
    }

    val fyersProvider: FyersMarketDataProvider by lazy {
        FyersMarketDataProvider(
            credentialsProvider = {
                runBlocking {
                    preferencesDataStore.getFyersCredentials()
                }
            }
        )
    }

    val dataProvider: MarketDataProvider by lazy {
        SmartMarketDataProvider(
            fyersProvider = fyersProvider,
            fyersWebSocketClient = fyersWebSocketClient,
            hybridProvider = HybridMarketDataProvider(),
            preferencesDataStore = preferencesDataStore
        )
    }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            context = context,
            dataProvider = dataProvider,
            preferencesDataStore = preferencesDataStore,
            fyersWebSocketClient = fyersWebSocketClient
        )
    }

    val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    fun schedulePeriodicMarketUpdates(intervalMinutes: Int = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<MarketUpdateWorker>(
            intervalMinutes.toLong().coerceAtLeast(15),
            TimeUnit.MINUTES,
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("market_update_periodic")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "market_update_periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}
