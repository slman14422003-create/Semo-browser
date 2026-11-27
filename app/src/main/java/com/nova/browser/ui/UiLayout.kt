package com.nova.browser

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

object UiLayout {
    const val BAR_DP = 48

    const val PROGRESS_DP = 3
    const val BAR_ROW_DP = BAR_DP - PROGRESS_DP

    const val BAR_H = 60
    val PILL: Dp = BAR_H.dp
    const val FIND_BAR_DP = 52

    const val BAR_COMPACT_H = 36
    val PILL_COMPACT: Dp = BAR_COMPACT_H.dp
    const val COMPACT_PILL_DP = 28
    const val COLLAPSE_ANIM_MS = 240
    const val SCROLL_COLLAPSE_PX = 56
    const val SCROLL_EXPAND_PX = 24
    const val SCROLL_TOP_PX = 8
    const val TOGGLE_LOCK_MS = 450L
    const val SWIPE_OUT_MS = 130
    const val SWIPE_IN_MS = 260

    const val KB_SETTLE_MS = 60L

    const val VEIL_MS = 180

    const val ARC_TRIGGER_DP = 120
    const val ARC_SLINGSHOT_DP = 140
    const val ARC_DIAMETER_DP = 26
    const val ARC_STROKE_DP = 2.5f
    const val ARC_REST_DP = 52
}

/**
 * ارتفاع الكيبورد «المستقر»: يتحدّث مرة واحدة بعد انتهاء حركة الظهور/الاختفاء، وليس مع كل إطار.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberSettledIme(ime: WindowInsets): State<Int> {
    val density = LocalDensity.current
    val settled = remember { mutableIntStateOf(0) }
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) }
            .debounce(UiLayout.KB_SETTLE_MS)
            .collect { settled.intValue = it }
    }
    return settled
}

fun Modifier.pageBounds(
    imeSettled: () -> Int,
    typing: Boolean,
    inPip: Boolean,
    barPx: Int
): Modifier = this.layout { m, c ->
    val ime = imeSettled()
    val pad = if (!inPip && typing && ime > 0) ime else 0
    val h = (c.maxHeight - maxOf(pad, barPx)).coerceAtLeast(0)
    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
    layout(c.maxWidth, c.maxHeight) { p.placeRelative(0, 0) }
}

object BarGeometry {
    fun maxPx(density: Density, navPx: Int, finding: Boolean, visible: Boolean): Int =
        if (!visible) 0 else navPx + with(density) { (if (finding) UiLayout.FIND_BAR_DP.dp else UiLayout.PILL).roundToPx() }

    fun hiddenPx(density: Density, collapse: Float, finding: Boolean): Int =
        if (finding) 0 else with(density) { ((UiLayout.PILL - UiLayout.PILL_COMPACT).toPx() * collapse.coerceIn(0f, 1f)).roundToInt() }
}

