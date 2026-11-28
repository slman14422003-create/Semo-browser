package com.nova.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlin.math.roundToInt

object FrostCache {
    private val steps = arrayOfNulls<RenderEffect>(UiLayout.FROST_STEPS + 1)
    private var radius = -1f

    fun at(step: Int, maxRadius: Float): RenderEffect? {
        if (step <= 0) return null
        if (maxRadius != radius) {
            steps.fill(null)
            radius = maxRadius
        }
        val i = step.coerceAtMost(UiLayout.FROST_STEPS)
        steps[i]?.let { return it }
        val r = maxRadius * i / UiLayout.FROST_STEPS
        return BlurEffect(r, r, TileMode.Clamp).also { steps[i] = it }
    }
}

fun frostExitMs(): Int = (UiLayout.FROST_MS * UiLayout.FROST_EXIT_SHARE).roundToInt()

fun Modifier.frost(clarity: () -> Float): Modifier = graphicsLayer {
    val c = clarity().coerceIn(0f, 1f)
    alpha = c
    renderEffect = if (c >= 1f || !Adaptive.blurOk) null
    else FrostCache.at(((1f - c) * UiLayout.FROST_STEPS).roundToInt(), UiLayout.FROST_BLUR.toPx())
}

@Composable
fun FrostPresence(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = updateTransition(visible, label = "frost")
    val clarity = transition.animateFloat(
        transitionSpec = {
            if (targetState) Motion.timed<Float>(UiLayout.FROST_MS, Motion.emphasizedDecelerate)
            else Motion.timed<Float>(frostExitMs(), Motion.emphasizedAccelerate)
        },
        label = "frostClarity"
    ) { if (it) 1f else 0f }
    if (transition.currentState || transition.targetState) {
        Box(modifier.frost { clarity.value }) { content() }
    }
}

@Stable
class FrostGhostState {
    var thumb by mutableStateOf<Bitmap?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set

    fun show(bitmap: Bitmap?) {
        if (bitmap == null || !Motion.enabled) return
        thumb = bitmap
        serial++
    }

    fun clear(forSerial: Int) {
        if (forSerial == serial) thumb = null
    }
}

@Composable
fun rememberFrostGhost(): FrostGhostState = remember { FrostGhostState() }

@Composable
fun FrostGhost(state: FrostGhostState, modifier: Modifier = Modifier) {
    val bitmap = state.thumb ?: return
    val serial = state.serial
    val clarity = remember(serial) { Animatable(1f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    LaunchedEffect(serial) {
        clarity.animateTo(0f, Motion.timed(frostExitMs(), Motion.emphasizedAccelerate))
        state.clear(serial)
    }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize().frost { clarity.value }
    )
}
