package androidx.compose.ui.input.pointer

import androidx.compose.ui.Modifier

/** Web compatibility for the desktop hover helper; click/touch behavior stays intact. */
fun Modifier.pointerMoveFilter(
    onMove: (PointerEvent) -> Boolean = { false },
    onEnter: (PointerEvent) -> Boolean = { false },
    onExit: (PointerEvent) -> Boolean = { false },
): Modifier = this
