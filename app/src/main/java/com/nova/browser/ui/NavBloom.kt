package com.nova.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class NavBloomState {
    var active by mutableStateOf<String?>(null)
    var selected by mutableStateOf<String?>(null)
    var hostX by mutableFloatStateOf(0f)
    val centers = mutableStateMapOf<String, Float>()
}

fun Modifier.bloomAnchor(state: NavBloomState, key: String): Modifier =
    onGloballyPositioned { state.centers[key] = it.positionInRoot().x + it.size.width / 2f }

@Composable
fun Modifier.bloomHost(state: NavBloomState, color: Color): Modifier {
    if (!Motion.enabled) return this
    val level = Glass.level
    val density = LocalDensity.current
    var last by remember { mutableFloatStateOf(0f) }
    val key = state.active ?: state.selected
    val target = key?.let { state.centers[it] }
    if (target != null) last = target - state.hostX
    LaunchedEffect(state.active) {
        if (state.active != null) {
            delay(Motion.millis(Motion.BLOOM_HOLD))
            state.active = null
        }
    }
    val x by animateFloatAsState(last, Motion.bloom(), label = "bloomX")
    val a by animateFloatAsState(if (key != null) 1f else 0f, Motion.timed(Motion.MEDIUM), label = "bloomA")
    val glow = remember(color) { RadialGlow(color, 0.55f) }
    val rim = remember(density) { Stroke(with(density) { UiLayout.GLASS_RIM.toPx() }) }
    return this.onGloballyPositioned { state.hostX = it.positionInRoot().x }.drawBehind {
        if (a > 0.01f) {
            val travel = (abs(last - x) / UiLayout.BLOOM_TRAVEL.toPx()).coerceIn(0f, 1f)
            val cy = size.height / 2f
            val pw = UiLayout.BLOOM_PILL_W.toPx() + UiLayout.BLOOM_STRETCH.toPx() * travel
            val ph = UiLayout.BLOOM_PILL_H.toPx() * (1f + 0.12f * travel)
            val corner = CornerRadius(ph / 2f, ph / 2f)
            val topLeft = Offset(x - pw / 2f, cy - ph / 2f)
            if (level == Glass.FLAT) {
                drawRoundRect(color, topLeft, Size(pw, ph), corner, alpha = 0.16f * a)
            } else {
                val r = UiLayout.BLOOM_RADIUS.toPx() * (1f - 0.35f * travel)
                translate(x, cy) {
                    scale(r, r * 0.8f, Offset.Zero) { drawCircle(glow.brush, 1f, Offset.Zero, alpha = a) }
                }
                drawRoundRect(Color.White, topLeft, Size(pw, ph), corner, alpha = 0.10f * a)
                if (level == Glass.FULL) drawRoundRect(Color.White, topLeft, Size(pw, ph), corner, style = rim, alpha = 0.22f * a)
            }
        }
    }
}

@Composable
fun Modifier.tabBloom(on: Boolean, color: Color): Modifier {
    if (!on || Glass.level == Glass.FLAT) return this
    val glow = remember(color) { RadialGlow(color, 0.5f) }
    return this.drawBehind {
        translate(size.width / 2f, size.height / 2f) {
            scale(size.width * 0.8f, size.height * 0.7f, Offset.Zero) { drawCircle(glow.brush, 1f, Offset.Zero, alpha = 0.6f) }
        }
    }
}

class LensPill {
    val left = Animatable(0f)
    val right = Animatable(0f)
    private var placed = false

    suspend fun glideTo(l: Float, r: Float) {
        if (!placed) {
            placed = true
            left.snapTo(l)
            right.snapTo(r)
            return
        }
        val rightward = l >= left.value
        kotlinx.coroutines.coroutineScope {
            launch { left.animateTo(l, if (rightward) Motion.glideTrail() else Motion.glideLead()) }
            launch { right.animateTo(r, if (rightward) Motion.glideLead() else Motion.glideTrail()) }
        }
    }

    fun spread(): Float = abs((right.value - left.value))
}
