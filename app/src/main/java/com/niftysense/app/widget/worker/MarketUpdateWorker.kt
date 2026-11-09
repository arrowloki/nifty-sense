package com.niftysense.app.widget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.niftysense.app.NiftySenseApp
import com.niftysense.app.data.model.MarketStatus
import java.util.Calendar
import java.util.TimeZone

class MarketUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "niftysense_periodic_market_worker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? NiftySenseApp ?: return Result.failure()
        val repository = app.container.marketRepository

        val currentStatus = MarketStatus.current()

        // Battery optimization: Avoid polling aggressively on weekends or middle of the night
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        if (currentStatus == MarketStatus.CLOSED && (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY || hour < 8 || hour >= 20)) {
            // Market is closed outside trading hours, skip unnecessary background poll
            return Result.success()
        }

        return try {
            val refreshResult = repository.refresh()
            if (refreshResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
