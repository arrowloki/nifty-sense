package com.niftysense.app.widget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.niftysense.app.NiftySenseApp
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.widget.ui.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class NiftySenseWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context, snapshot: MarketSnapshot?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Update Small Widgets
            val smallComponent = ComponentName(context, SmallWidgetProvider::class.java)
            val smallIds = appWidgetManager.getAppWidgetIds(smallComponent)
            if (smallIds != null && smallIds.isNotEmpty()) {
                val views = WidgetRenderer.renderSmallWidget(context, snapshot)
                appWidgetManager.updateAppWidget(smallIds, views)
            }

            // Update Medium Widgets
            val mediumComponent = ComponentName(context, MediumWidgetProvider::class.java)
            val mediumIds = appWidgetManager.getAppWidgetIds(mediumComponent)
            if (mediumIds != null && mediumIds.isNotEmpty()) {
                val views = WidgetRenderer.renderMediumWidget(context, snapshot)
                appWidgetManager.updateAppWidget(mediumIds, views)
            }

            // Update Large Widgets
            val largeComponent = ComponentName(context, LargeWidgetProvider::class.java)
            val largeIds = appWidgetManager.getAppWidgetIds(largeComponent)
            if (largeIds != null && largeIds.isNotEmpty()) {
                val views = WidgetRenderer.renderLargeWidget(context, snapshot)
                appWidgetManager.updateAppWidget(largeIds, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? NiftySenseApp ?: return
        val repository = app.container.marketRepository

        // Synchronously bind whatever snapshot is available (or placeholder) so launcher gets instant RemoteViews
        val cached = repository.uiState.value.snapshot
        when (this) {
            is SmallWidgetProvider -> {
                appWidgetManager.updateAppWidget(appWidgetIds, WidgetRenderer.renderSmallWidget(context, cached))
            }
            is MediumWidgetProvider -> {
                appWidgetManager.updateAppWidget(appWidgetIds, WidgetRenderer.renderMediumWidget(context, cached))
            }
            is LargeWidgetProvider -> {
                appWidgetManager.updateAppWidget(appWidgetIds, WidgetRenderer.renderLargeWidget(context, cached))
            }
            else -> {
                updateAllWidgets(context, cached)
            }
        }

        // Asynchronously fetch fresh live data to update immediately
        CoroutineScope(Dispatchers.IO).launch {
            repository.refresh()
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val app = context.applicationContext as? NiftySenseApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            app.container.marketRepository.refresh()
        }
    }
}
