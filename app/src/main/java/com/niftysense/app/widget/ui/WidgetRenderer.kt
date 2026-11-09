package com.niftysense.app.widget.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.niftysense.app.MainActivity
import com.niftysense.app.R
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.widget.receiver.WidgetRefreshReceiver

object WidgetRenderer {

    fun renderSmallWidget(context: Context, snapshot: MarketSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_small)
        setupClickIntents(context, views, R.id.widget_small_root, R.id.btn_small_refresh)

        if (snapshot == null) {
            views.setTextViewText(R.id.tv_small_nifty_price, "--")
            views.setTextViewText(R.id.tv_small_sensex_price, "--")
            views.setTextViewText(R.id.tv_small_timestamp, "Tap to load")
            return views
        }

        val nifty = snapshot.nifty50
        val sensex = snapshot.sensex

        // NIFTY
        views.setTextViewText(R.id.tv_small_nifty_price, Formatters.formatIndexValue(nifty.currentValue))
        views.setTextViewText(R.id.tv_small_nifty_change_pill, Formatters.formatPercentageChange(nifty.percentageChange))
        bindBadgeStyle(context, views, R.id.tv_small_nifty_change_pill, nifty.isPositive)

        // SENSEX
        views.setTextViewText(R.id.tv_small_sensex_price, Formatters.formatIndexValue(sensex.currentValue))
        views.setTextViewText(R.id.tv_small_sensex_change_pill, Formatters.formatPercentageChange(sensex.percentageChange))
        bindBadgeStyle(context, views, R.id.tv_small_sensex_change_pill, sensex.isPositive)

        // Status & Timestamp
        val statusText = if (snapshot.marketStatus == MarketStatus.OPEN) "LIVE" else "CLOSED"
        views.setTextViewText(R.id.tv_small_market_status, "NiftySense • $statusText")
        views.setTextViewText(R.id.tv_small_timestamp, Formatters.formatWidgetTimestamp(snapshot.lastSyncTimestamp))

        return views
    }

    fun renderMediumWidget(context: Context, snapshot: MarketSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium)
        setupClickIntents(context, views, R.id.widget_medium_root, R.id.btn_medium_refresh)

        if (snapshot == null) {
            views.setTextViewText(R.id.tv_medium_nifty_price, "--")
            views.setTextViewText(R.id.tv_medium_sensex_price, "--")
            views.setTextViewText(R.id.tv_medium_timestamp, "• Tap to update")
            return views
        }

        val nifty = snapshot.nifty50
        val sensex = snapshot.sensex

        // NIFTY
        views.setTextViewText(R.id.tv_medium_nifty_price, Formatters.formatIndexValue(nifty.currentValue))
        views.setTextViewText(
            R.id.tv_medium_nifty_change,
            Formatters.formatCombinedChange(nifty.absoluteChange, nifty.percentageChange)
        )
        val niftyColor = ContextCompat.getColor(
            context,
            if (nifty.isPositive) R.color.positive_green else R.color.negative_red
        )
        views.setTextColor(R.id.tv_medium_nifty_change, niftyColor)
        views.setImageViewResource(
            R.id.iv_medium_nifty_trend,
            if (nifty.isPositive) R.drawable.ic_trending_up else R.drawable.ic_trending_down
        )

        // SENSEX
        views.setTextViewText(R.id.tv_medium_sensex_price, Formatters.formatIndexValue(sensex.currentValue))
        views.setTextViewText(
            R.id.tv_medium_sensex_change,
            Formatters.formatCombinedChange(sensex.absoluteChange, sensex.percentageChange)
        )
        val sensexColor = ContextCompat.getColor(
            context,
            if (sensex.isPositive) R.color.positive_green else R.color.negative_red
        )
        views.setTextColor(R.id.tv_medium_sensex_change, sensexColor)
        views.setImageViewResource(
            R.id.iv_medium_sensex_trend,
            if (sensex.isPositive) R.drawable.ic_trending_up else R.drawable.ic_trending_down
        )

        // Market Status
        val isOpen = snapshot.marketStatus == MarketStatus.OPEN
        val statusLabel = when (snapshot.marketStatus) {
            MarketStatus.OPEN -> "LIVE IST"
            MarketStatus.PRE_OPEN -> "PRE-OPEN"
            MarketStatus.CLOSED -> "CLOSED"
        }
        views.setTextViewText(R.id.tv_medium_market_status, statusLabel)
        views.setInt(
            R.id.view_medium_status_dot,
            "setBackgroundColor",
            ContextCompat.getColor(context, if (isOpen) R.color.market_open_dot else R.color.market_closed_dot)
        )

        views.setTextViewText(
            R.id.tv_medium_timestamp,
            "• " + Formatters.formatWidgetTimestamp(snapshot.lastSyncTimestamp)
        )

        return views
    }

    fun renderLargeWidget(context: Context, snapshot: MarketSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large)
        setupClickIntents(context, views, R.id.widget_large_root, R.id.btn_large_refresh)

        if (snapshot == null) {
            views.setTextViewText(R.id.tv_large_nifty_price, "--")
            views.setTextViewText(R.id.tv_large_sensex_price, "--")
            views.setTextViewText(R.id.tv_large_timestamp, "• Tap to update")
            return views
        }

        val nifty = snapshot.nifty50
        val sensex = snapshot.sensex

        // NIFTY Section
        views.setTextViewText(R.id.tv_large_nifty_price, Formatters.formatIndexValue(nifty.currentValue))
        views.setTextViewText(
            R.id.tv_large_nifty_change,
            Formatters.formatCombinedChange(nifty.absoluteChange, nifty.percentageChange)
        )
        val niftyColor = ContextCompat.getColor(
            context,
            if (nifty.isPositive) R.color.positive_green else R.color.negative_red
        )
        views.setTextColor(R.id.tv_large_nifty_change, niftyColor)
        views.setTextViewText(R.id.tv_large_nifty_high, "H: " + Formatters.formatIndexValue(nifty.dayHigh))
        views.setTextViewText(R.id.tv_large_nifty_low, "L: " + Formatters.formatIndexValue(nifty.dayLow))

        // Render NIFTY Sparkline
        val niftyBitmap = SparklineBitmapGenerator.generateSparklineBitmap(
            points = nifty.sparklinePoints,
            isPositive = nifty.isPositive,
            width = 200,
            height = 60
        )
        views.setImageViewBitmap(R.id.iv_large_nifty_sparkline, niftyBitmap)

        // SENSEX Section
        views.setTextViewText(R.id.tv_large_sensex_price, Formatters.formatIndexValue(sensex.currentValue))
        views.setTextViewText(
            R.id.tv_large_sensex_change,
            Formatters.formatCombinedChange(sensex.absoluteChange, sensex.percentageChange)
        )
        val sensexColor = ContextCompat.getColor(
            context,
            if (sensex.isPositive) R.color.positive_green else R.color.negative_red
        )
        views.setTextColor(R.id.tv_large_sensex_change, sensexColor)
        views.setTextViewText(R.id.tv_large_sensex_high, "H: " + Formatters.formatIndexValue(sensex.dayHigh))
        views.setTextViewText(R.id.tv_large_sensex_low, "L: " + Formatters.formatIndexValue(sensex.dayLow))

        // Render SENSEX Sparkline
        val sensexBitmap = SparklineBitmapGenerator.generateSparklineBitmap(
            points = sensex.sparklinePoints,
            isPositive = sensex.isPositive,
            width = 200,
            height = 60
        )
        views.setImageViewBitmap(R.id.iv_large_sensex_sparkline, sensexBitmap)

        // Status bar
        val isOpen = snapshot.marketStatus == MarketStatus.OPEN
        val statusLabel = when (snapshot.marketStatus) {
            MarketStatus.OPEN -> "MARKET OPEN (IST)"
            MarketStatus.PRE_OPEN -> "PRE-OPEN SESSION"
            MarketStatus.CLOSED -> "MARKET CLOSED"
        }
        views.setTextViewText(R.id.tv_large_market_status, statusLabel)
        views.setInt(
            R.id.view_large_status_dot,
            "setBackgroundColor",
            ContextCompat.getColor(context, if (isOpen) R.color.market_open_dot else R.color.market_closed_dot)
        )

        views.setTextViewText(
            R.id.tv_large_timestamp,
            "• " + Formatters.formatWidgetTimestamp(snapshot.lastSyncTimestamp)
        )

        return views
    }

    private fun setupClickIntents(
        context: Context,
        views: RemoteViews,
        rootViewId: Int,
        refreshBtnId: Int
    ) {
        // 1. Root tap opens MainActivity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(rootViewId, appPendingIntent)

        // 2. Refresh button sends broadcast to WidgetRefreshReceiver
        val refreshIntent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(refreshBtnId, refreshPendingIntent)
    }

    private fun bindBadgeStyle(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        isPositive: Boolean
    ) {
        val textColor = ContextCompat.getColor(
            context,
            if (isPositive) R.color.positive_green else R.color.negative_red
        )
        val bgDrawable = if (isPositive) R.drawable.badge_positive_bg else R.drawable.badge_negative_bg
        views.setTextColor(viewId, textColor)
        views.setInt(viewId, "setBackgroundResource", bgDrawable)
    }
}
