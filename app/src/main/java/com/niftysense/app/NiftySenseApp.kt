package com.niftysense.app

import android.app.Application
import com.niftysense.app.di.AppContainer

class NiftySenseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.schedulePeriodicMarketUpdates(15)
    }
}
