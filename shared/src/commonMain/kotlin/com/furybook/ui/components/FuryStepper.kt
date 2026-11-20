package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing

data class FuryStepperState(val canDecrease: Boolean, val canIncrease: Boolean)

/** A value outside [min]..[max] can still be stepped back towards the range, never further out. */
fun furyStepperState(value: Int, min: Int, max: Int, enabled: Boolean = true): FuryStepperState =
    FuryStepperState(canDecrease = enabled && value > min, canIncrease = enabled && value < max)

@Composable
fun FuryStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    decreaseLabel: String = "Уменьшить",
    increaseLabel: String = "Увеличить",
) {
    val state = furyStepperState(value, min, max, enabled)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        FuryIconButton(decreaseLabel, { onChange(value - 1) }, enabled = state.canDecrease) {
            Text("−", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 36.dp),
            color = DublText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        FuryIconButton(increaseLabel, { onChange(value + 1) }, enabled = state.canIncrease) {
            Text("+", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
    }
}
