package com.nova.browser

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

object Haptics {
    private fun play(view: View, kind: Int) { if (Prefs.haptics) view.performHapticFeedback(kind) }
    fun tap(view: View) = play(view, HapticFeedbackConstants.CONTEXT_CLICK)
    fun tick(view: View) = play(view, HapticFeedbackConstants.CLOCK_TICK)
    fun confirm(view: View) = play(view, HapticFeedbackConstants.CONFIRM)
    fun heavy(view: View) = play(view, HapticFeedbackConstants.LONG_PRESS)
    fun glide(view: View) = play(view, HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
    fun reject(view: View) = play(view, HapticFeedbackConstants.REJECT)
    fun threshold(view: View, engaged: Boolean) =
        play(view, if (engaged) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
    fun toggle(view: View, on: Boolean) =
        play(view, if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)

    class On internal constructor(private val view: View) {
        fun tap() = Haptics.tap(view)
        fun tick() = Haptics.tick(view)
        fun confirm() = Haptics.confirm(view)
        fun heavy() = Haptics.heavy(view)
        fun glide() = Haptics.glide(view)
        fun reject() = Haptics.reject(view)
        fun threshold(engaged: Boolean) = Haptics.threshold(view, engaged)
        fun toggle(on: Boolean) = Haptics.toggle(view, on)
    }
}

@Composable
fun rememberHaptics(): Haptics.On {
    val view = LocalView.current
    return remember(view) { Haptics.On(view) }
}

@Composable
fun tapped(action: () -> Unit): () -> Unit {
    val haptic = rememberHaptics()
    return { haptic.tap(); action() }
}

fun Modifier.tapClickable(
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHaptics()
    clickable(enabled, onClickLabel, role) { haptic.tap(); onClick() }
}

fun Modifier.tapClickable(
    interactionSource: MutableInteractionSource, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, toggle: Boolean = false, onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHaptics()
    clickable(interactionSource = interactionSource, indication = indication, enabled = enabled, role = role) {
        if (toggle) haptic.confirm() else haptic.tap()
        onClick()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tapCombinedClickable(
    enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHaptics()
    combinedClickable(
        enabled = enabled, hapticFeedbackEnabled = false,
        onLongClick = onLongClick?.let { f -> { haptic.heavy(); f() } },
        onClick = { haptic.tap(); onClick() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tapCombinedClickable(
    interactionSource: MutableInteractionSource, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHaptics()
    combinedClickable(
        interactionSource = interactionSource, indication = indication, enabled = enabled, role = role,
        hapticFeedbackEnabled = false,
        onLongClick = onLongClick?.let { f -> { haptic.heavy(); f() } },
        onClick = { haptic.tap(); onClick() }
    )
}
