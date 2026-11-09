package com.niftysense.app.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.presentation.theme.Emerald500
import com.niftysense.app.presentation.theme.Rose500

@Composable
fun MarketStatusBadge(
    status: MarketStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, labelText) = when (status) {
        MarketStatus.OPEN -> Pair(Emerald500, "LIVE IST")
        MarketStatus.PRE_OPEN -> Pair(Color(0xFFF59E0B), "PRE-OPEN")
        MarketStatus.CLOSED -> Pair(Color(0xFF94A3B8), "CLOSED")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = labelText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
