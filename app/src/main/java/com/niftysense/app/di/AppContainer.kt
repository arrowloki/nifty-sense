package com.niftysense.app.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.data.remote.HybridMarketDataProvider
import com.niftysense.app.data.repository.MarketDataProvider
import com.niftysense.app.data.repository.MarketRepository
import com.niftysense.app.widget.worker.MarketUpdateWorker
import java.util.concurrent.TimeUnit

class AppContainer(val context: Context) {

    val preferencesDataStore: MarketPreferencesDataStore by lazy {
        MarketPreferencesDataStore(context)
    }

    val dataProvider: MarketDataProvider by lazy {
        HybridMarketDataProvider()
    }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            context = context,
            dataProvider = dataProvider,
            preferencesDataStore = preferencesDataStore
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
            intervalMinutes.toLong().coerceAtLeast(15L),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            MarketUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}
