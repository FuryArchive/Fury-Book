package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublSurfaceRaised

/**
 * How a resource bar should be drawn. [segments] is 0 for one continuous bar filled to [fraction];
 * otherwise the bar is [segments] blocks with the first [filledSegments] lit.
 */
data class FuryBarModel(val fraction: Float, val segments: Int, val filledSegments: Int)

/** Small pools (stamina 3/3) read better as blocks; large ones (health 32/32) as one bar. */
fun furyBarModel(current: Int, maximum: Int, segmentLimit: Int = 10): FuryBarModel {
    val max = maximum.coerceAtLeast(0)
    val value = current.coerceIn(0, max)
    val fraction = if (max == 0) 0f else value.toFloat() / max.toFloat()
    return if (max in 1..segmentLimit) FuryBarModel(fraction, max, value) else FuryBarModel(fraction, 0, 0)
}

@Composable
fun FuryBar(
    current: Int,
    maximum: Int,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val model = furyBarModel(current, maximum)
    val shape = RoundedCornerShape(3.dp)
    if (model.segments > 0) {
        Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(model.segments) { index ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .background(if (index < model.filledSegments) color else DublSurfaceRaised),
                )
            }
        }
    } else {
        Box(modifier.fillMaxWidth().height(height).clip(shape).background(DublSurfaceRaised)) {
            Box(Modifier.fillMaxWidth(model.fraction).fillMaxHeight().clip(shape).background(color))
        }
    }
}
