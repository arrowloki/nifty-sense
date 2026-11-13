package com.niftysense.app.presentation.pip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.presentation.theme.Emerald500
import com.niftysense.app.presentation.theme.Rose500

@Composable
fun PipLiveTickerScreen(
    snapshot: MarketSnapshot?,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Continuous live ticker loop in Picture-in-Picture mode
    LaunchedEffect(Unit) {
        while (true) {
            val status = MarketStatus.current()
            val interval = if (status == MarketStatus.OPEN) 2_000L else 30_000L
            delay(interval)
            onRefresh()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isMarketOpen = snapshot?.marketStatus == MarketStatus.OPEN

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Mini Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isMarketOpen) Emerald500 else Color(0xFF64748B))
                            .alpha(if (isMarketOpen) pulseAlpha else 1f)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isMarketOpen) "LIVE F&O / INDICES" else "MARKET CLOSED",
                        color = if (isMarketOpen) Emerald500 else Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Text(
                    text = if (snapshot?.sourceProvider?.contains("Fyers", ignoreCase = true) == true) "⚡ FYERS" else "NSE/BSE",
                    color = Color(0xFF64748B),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Two Indices Side by Side
            if (snapshot != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PipIndexColumn(
                        index = snapshot.nifty50,
                        modifier = Modifier.weight(1f)
                    )

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0xFF1E293B))
                    )

                    PipIndexColumn(
                        index = snapshot.sensex,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Syncing live prices…",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PipIndexColumn(
    index: MarketIndexData,
    modifier: Modifier = Modifier
) {
    val isPositive = index.isPositive
    val accentColor = if (isPositive) Emerald500 else Rose500

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = index.displayName,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = Formatters.formatPercentageChange(index.percentageChange),
                color = accentColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = Formatters.formatIndexValue(index.currentValue),
            color = Color.White,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )

        Text(
            text = Formatters.formatAbsoluteChange(index.absoluteChange),
            color = accentColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}