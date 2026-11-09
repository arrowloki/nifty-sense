package com.niftysense.app.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.niftysense.app.NiftySenseApp
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

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = app?.container?.marketRepository?.refresh()
                    if (result?.isSuccess == true) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "NiftySense: Market updated", Toast.LENGTH_SHORT).show()
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
