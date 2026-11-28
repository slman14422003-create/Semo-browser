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
    val dp46: Dp = 46.dp
    const val SNACK_CAPACITY = 3
    const val SNACK_TTL_MS = 10000L
    const val SNACK_MS = 2600L
    const val SNACK_ACTION_MS = 6000L
    const val SHEET_SCRIM = 0.28f
    const val SHEET_SCRIM_DARK = 0.5f
    const val HIGHLIGHT_ALPHA = 0.18f
    const val JUMP_WAIT_MS = 350L
    const val JUMP_HOLD_MS = 1600L
    const val STORAGE_REMEASURE_MS = 900L
    const val START_RECENT = 5
    const val START_DOWNLOADS = 3
    val SNACK_LIFT: Dp = 76.dp
    val SNACK_MAX_W: Dp = 480.dp
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
    const val REFRESH_TIMEOUT_MS = 8000L
    const val CLICK_GAP_MS = 120L

    const val MAGNET_DETACH_DP = 72f
    const val MAGNET_ATTACH_DP = 56f
    const val MAGNET_PULL = 0.5f
    const val REORDER_DETACH_DP = 36f
    const val REORDER_ATTACH_DP = 24f
    const val REORDER_PULL = 0.4f
    const val SNAP_RADIUS_SHARE = 0.4f
    const val SNAP_STRENGTH = 0.6f
    const val SWIPE_FLING_DP_S = 700f
    const val SWIPE_FLING_MIN_DP = 24f
    const val SWIPE_EXIT_SHARE = 1.3f
    const val SWIPE_TILT_DEG = 10f
    const val SWIPE_DEPTH = 0.08f
    const val LIFT_SCALE = 1.06f
    const val SWAP_INSET_SHARE = 0.2f
    const val SWAP_GAP_MS = 140L
    const val EDGE_SCROLL_ZONE_DP = 64f
    const val EDGE_SCROLL_DP_S = 520f
    const val SWIPE_RETURN_WAIT_MS = 400L

    const val HIDE_LOCK_MS = 350L
    const val BAR_HIDE_MS = 220

    const val ZOOM_MS = 360
    const val ZOOM_FADE_MS = 140
    const val ZOOM_WAIT_FRAMES = 8
    const val FROST_MS = 320
    const val FROST_STEPS = 16
    const val FROST_EXIT_SHARE = 0.6f

    const val SHIMMER_SWEEP_MS = 1100
    const val SHIMMER_REST_MS = 350
    const val SHIMMER_BAND = 0.55f

    const val OVERSCROLL_PULL = 0.5f
    const val OVERSCROLL_MAX_SHARE = 0.2f
    const val OVERSCROLL_FLING = 0.3f
    const val OVERSCROLL_STRETCH = 0.06f

    const val LAUNCH_HOLD_CAP_MS = 1200L
    const val MENU_MAX_SHARE = 0.72f

    const val SAMPLE_POINTS = 24
    const val SAMPLE_TOP_ROW = 2
    const val SAMPLE_BOTTOM_INSET = 3
    const val SAMPLE_DELAY_MS = 1800L
    const val SAMPLE_MIN_ALPHA = 250

    const val SNAP_W = 48
    const val SNAP_BLUR_R = 2
    const val SNAP_PASSES = 2

    val GLASS_BLUR_PILL: Dp = 12.dp
    val GLASS_BLUR_BAR: Dp = 24.dp
    val GLASS_BLUR_SHEET: Dp = 28.dp
    val GLASS_RIM: Dp = 0.75.dp
    val LENS_DEPTH: Dp = 18.dp
    val LENS_AMOUNT: Dp = 14.dp
    val SHADOW_RAISED: Dp = 7.dp

    val PROGRESS_LINE: Dp = PROGRESS_DP.dp
    val PROGRESS_GLOW_MIN: Dp = 6.dp
    val PROGRESS_GLOW_MAX: Dp = 12.dp

    val BLOOM_RADIUS: Dp = 30.dp
    val BLOOM_TRAVEL: Dp = 140.dp
    val BLOOM_PILL_W: Dp = 44.dp
    val BLOOM_PILL_H: Dp = 38.dp
    val BLOOM_STRETCH: Dp = 26.dp
    val LENS_PILL_STRETCH: Dp = 18.dp

    val ACCENT_PAD: Dp = 20.dp
    val ACCENT_GAP: Dp = 10.dp
    val ACCENT_CARD_H: Dp = 72.dp
    val ACCENT_CARD_R: Dp = 18.dp
    val ACCENT_RING: Dp = 2.5.dp
    val PICKER_SQUARE_H: Dp = 180.dp
    val PICKER_HUE_H: Dp = 28.dp
    val PICKER_R: Dp = 14.dp
    val PICKER_THUMB: Dp = 11.dp
    val PICKER_THUMB_RING: Dp = 3.dp
    val PICKER_SWATCH: Dp = 32.dp
    val PICKER_PREVIEW: Dp = 56.dp
    val PICKER_BUTTON_H: Dp = 48.dp
    val PICKER_FIELD_H: Dp = 44.dp
    val PICKER_SPACE: Dp = 14.dp

    val FROST_BLUR: Dp = 14.dp
    val ZOOM_CARD_R: Dp = 16.dp
    val LIFT_SHADOW: Dp = 16.dp
    val OVERSCROLL_FALLBACK: Dp = 140.dp

    val dp0: Dp = 0.dp
    val dp0_75: Dp = 0.75.dp
    val dp1_8: Dp = 1.8.dp
    val dp2: Dp = 2.dp
    val dp2_5: Dp = 2.5.dp
    val dp3: Dp = 3.dp
    val dp4: Dp = 4.dp
    val dp5: Dp = 5.dp
    val dp6: Dp = 6.dp
    val dp7: Dp = 7.dp
    val dp8: Dp = 8.dp
    val dp10: Dp = 10.dp
    val dp11: Dp = 11.dp
    val dp12: Dp = 12.dp
    val dp13: Dp = 13.dp
    val dp14: Dp = 14.dp
    val dp15: Dp = 15.dp
    val dp16: Dp = 16.dp
    val dp17: Dp = 17.dp
    val dp18: Dp = 18.dp
    val dp19: Dp = 19.dp
    val dp20: Dp = 20.dp
    val dp22: Dp = 22.dp
    val dp24: Dp = 24.dp
    val dp25: Dp = 25.dp
    val dp26: Dp = 26.dp
    val dp28: Dp = 28.dp
    val dp29: Dp = 29.dp
    val dp30: Dp = 30.dp
    val dp32: Dp = 32.dp
    val dp34: Dp = 34.dp
    val dp36: Dp = 36.dp
    val dp38: Dp = 38.dp
    val dp40: Dp = 40.dp
    val dp44: Dp = 44.dp
    val dp48: Dp = 48.dp
    val dp52: Dp = 52.dp
    val dp56: Dp = 56.dp
    val dp60: Dp = 60.dp
    val dp64: Dp = 64.dp
    val dp72: Dp = 72.dp
    val dp88: Dp = 88.dp
    val dp140: Dp = 140.dp
    val dp150: Dp = 150.dp
    val dp240: Dp = 240.dp
    val dp260: Dp = 260.dp
    val dp280: Dp = 280.dp
    val dp360: Dp = 360.dp
    val dp380: Dp = 380.dp
    val dp400: Dp = 400.dp
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

fun menuMaxHeight(screenHeightDp: Int): Dp = (screenHeightDp * UiLayout.MENU_MAX_SHARE).dp

object BarGeometry {
    fun maxPx(density: Density, navPx: Int, finding: Boolean, visible: Boolean): Int =
        if (!visible) 0 else navPx + with(density) { (if (finding) UiLayout.FIND_BAR_DP.dp else UiLayout.PILL).roundToPx() }

    fun hiddenPx(density: Density, collapse: Float, finding: Boolean, hide: Float = 0f, maxPx: Int = 0): Int =
        if (finding) 0 else maxOf(
            with(density) { ((UiLayout.PILL - UiLayout.PILL_COMPACT).toPx() * collapse.coerceIn(0f, 1f)).roundToInt() },
            (maxPx * hide.coerceIn(0f, 1f)).roundToInt()
        )
}

