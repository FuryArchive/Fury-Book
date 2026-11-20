package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.furybook.dubl.model.RollContext
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.theme.FurySpacing

/** Quick combat checks (normally [SHEET_COMBAT_CONTEXTS]) as a wrapping row of buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetCombat(
    contexts: List<RollContext>,
    onRoll: (RollContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        contexts.forEach { context ->
            FuryButton(context.title, { onRoll(context) }, style = FuryButtonStyle.Secondary)
        }
    }
}
