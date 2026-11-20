package com.furybook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object FuryShapes {
    val control = RoundedCornerShape(6.dp)
    val panel = RoundedCornerShape(8.dp)
    val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

object FurySpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Smallest size, in either dimension, of anything the user taps. */
val FuryMinTouch = 44.dp
