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

class Haptics(private val view: View) {
    private fun play(kind: Int) { if (Prefs.haptics) view.performHapticFeedback(kind) }
    fun tap() = play(HapticFeedbackConstants.CONTEXT_CLICK)
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = play(HapticFeedbackConstants.CONFIRM)
    fun heavy() = play(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
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
