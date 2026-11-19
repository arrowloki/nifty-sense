package com.niftysense.app.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.niftysense.app.NiftySenseApp
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.widget.provider.NiftySenseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetRefreshReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH = "com.niftysense.app.ACTION_REFRESH_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            val app = context.applicationContext as? NiftySenseApp

            // Immediate visual feedback on widget: show "Updating…"
            NiftySenseWidgetProvider.showRefreshingState(context)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = app?.container?.marketRepository?.refresh()
                    if (result?.isSuccess == true) {
                        val snapshot = result.getOrNull()
                        val niftyVal = snapshot?.nifty50?.currentValue
                        val niftyFmt = if (niftyVal != null) " • NIFTY: ₹${Formatters.formatIndexValue(niftyVal)}" else ""
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "✓ Market updated$niftyFmt", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Market refresh: Using latest rates", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
