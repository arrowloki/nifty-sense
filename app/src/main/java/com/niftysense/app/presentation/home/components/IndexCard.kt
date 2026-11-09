package com.niftysense.app.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.presentation.theme.Emerald500
import com.niftysense.app.presentation.theme.EmeraldBgDark
import com.niftysense.app.presentation.theme.EmeraldBgLight
import com.niftysense.app.presentation.theme.Rose500
import com.niftysense.app.presentation.theme.RoseBgDark
import com.niftysense.app.presentation.theme.RoseBgLight

@Composable
fun IndexCard(
    data: MarketIndexData,
    modifier: Modifier = Modifier
) {
    val isPositive = data.isPositive
    val changeColor = if (isPositive) Emerald500 else Rose500
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val badgeBg = if (isPositive) {
        if (isDark) EmeraldBgDark else EmeraldBgLight
    } else {
        if (isDark) RoseBgDark else RoseBgLight
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Title & Exchange Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = data.exchange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Change Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Formatters.formatPercentageChange(data.percentageChange),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Price & Point Change
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = Formatters.formatIndexValue(data.currentValue),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = Formatters.formatAbsoluteChange(data.absoluteChange),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sparkline Intraday Chart
            if (data.sparklinePoints.isNotEmpty()) {
                SparklineChart(
                    points = data.sparklinePoints,
                    isPositive = isPositive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Day Range Slider
            DayRangeBar(
                low = data.dayLow,
                high = data.dayHigh,
                current = data.currentValue
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Stats Row (Prev Close & Open)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Prev Close: ${Formatters.formatIndexValue(data.previousClose)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Open: ${Formatters.formatIndexValue(data.openPrice)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
