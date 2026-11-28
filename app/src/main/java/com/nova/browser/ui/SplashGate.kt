package com.nova.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object SplashGate {
    var revealed by mutableStateOf(false)
        private set

    @Volatile
    var drawn = false
        private set

    var veilDone = false
        private set

    fun reveal() { revealed = true }

    fun markDrawn() { drawn = true }

    fun finishVeil() { veilDone = true }
}

@Composable
fun LaunchVeil() {
    val color = colorResource(R.color.nova_bg)
    val fade = remember { Animatable(1f) }
    var gone by remember { mutableStateOf(SplashGate.veilDone) }
    LaunchedEffect(Unit) {
        if (gone) return@LaunchedEffect
        delay(UiLayout.LAUNCH_HOLD_CAP_MS * 2)
        SplashGate.reveal()
    }
    LaunchedEffect(SplashGate.revealed) {
        if (gone || !SplashGate.revealed) return@LaunchedEffect
        fade.animateTo(0f, Motion.timed(Motion.MEDIUM, Motion.standard, Motion.QUICK))
        SplashGate.finishVeil()
        gone = true
    }
    if (!gone) Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(color)
            .drawBehind { SplashGate.markDrawn() }
            .pointerInput(Unit) {}
    )
}

@Composable
fun SplashMark(icon: Bitmap?, name: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated = Adaptive.ms(100) > 0
    val pop by animateFloatAsState(
        if (shown) 1f else 0f,
        if (animated) Motion.springy(0.55f, 260f) else snap(),
        label = "markPop"
    )
    val iconAlpha by animateFloatAsState(
        if (shown) 1f else 0f,
        Motion.timed(Motion.MEDIUM, easing = Motion.emphasized),
        label = "markAlpha"
    )
    val labelAlpha by animateFloatAsState(
        if (shown) 1f else 0f,
        Motion.timed(Motion.LONG, delayMillis = Motion.SHORT, easing = Motion.emphasized),
        label = "markLabel"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        val s = 0.62f + 0.38f * pop
                        scaleX = s; scaleY = s
                        alpha = iconAlpha.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onBackground,
            modifier = Modifier.graphicsLayer {
                alpha = labelAlpha.coerceIn(0f, 1f)
                translationY = (1f - labelAlpha.coerceIn(0f, 1f)) * 8.dp.toPx()
            }
        )
    }
}
