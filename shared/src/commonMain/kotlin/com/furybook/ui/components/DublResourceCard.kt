package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DublResourceCard(
    title: String,
    current: Int,
    maximum: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val safeMaximum = maximum.coerceAtLeast(0)
    val safeCurrent = current.coerceIn(0, safeMaximum)

    DublCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$safeCurrent / $safeMaximum",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        FuryBar(current = safeCurrent, maximum = safeMaximum, color = accent)
    }
}
