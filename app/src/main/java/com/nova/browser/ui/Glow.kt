package com.nova.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class GlowState {
    var active by mutableStateOf<String?>(null)
    var hostX by mutableFloatStateOf(0f)
    val centers = mutableStateMapOf<String, Float>()
}

fun Modifier.glowAnchor(state: GlowState, key: String): Modifier =
    onGloballyPositioned { state.centers[key] = it.positionInRoot().x + it.size.width / 2f }

fun Modifier.glowHost(state: GlowState, color: Color): Modifier = composed {
    if (!Adaptive.heavy) return@composed this
    var last by remember { mutableFloatStateOf(0f) }
    val target = state.active?.let { state.centers[it] }
    if (target != null) last = target - state.hostX
    LaunchedEffect(state.active) {
        if (state.active != null) {
            delay(900)
            state.active = null
        }
    }
    val x by animateFloatAsState(last, Motion.soft(), label = "glowX")
    val a by animateFloatAsState(if (state.active != null) 1f else 0f, Motion.timed(Motion.MEDIUM), label = "glowA")
    onGloballyPositioned { state.hostX = it.positionInRoot().x }.drawBehind {
        if (a > 0.01f) {
            val r = 28.dp.toPx()
            val c = Offset(x, size.height / 2f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.38f * a), Color.Transparent), c, r), r, c)
        }
    }
}
