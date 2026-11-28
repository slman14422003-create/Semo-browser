package com.nova.browser

import android.animation.ValueAnimator
import android.view.Choreographer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

object ShimmerClock {
    private const val PERIOD = UiLayout.SHIMMER_SWEEP_MS + UiLayout.SHIMMER_REST_MS
    private const val REST = 1f
    val sweep = mutableFloatStateOf(REST)
    private var users = 0
    private var posted = false

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            posted = false
            if (users <= 0) {
                sweep.floatValue = REST
                return
            }
            val t = ((frameTimeNanos / 1_000_000L) % PERIOD).toInt()
            sweep.floatValue = if (t < UiLayout.SHIMMER_SWEEP_MS) Motion.standard.transform(t / UiLayout.SHIMMER_SWEEP_MS.toFloat()) else REST
            post()
        }
    }

    private fun post() {
        if (posted) return
        posted = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun acquire() {
        users++
        post()
    }

    fun release() {
        if (users > 0) users--
    }
}

private class BandBrush(val color: Int, val width: Float) : ShaderBrush() {
    private val shader = android.graphics.LinearGradient(
        0f, 0f, width, 0f,
        intArrayOf(color and 0x00FFFFFF, color, color and 0x00FFFFFF),
        floatArrayOf(0f, 0.5f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )

    override fun createShader(size: Size): android.graphics.Shader = shader
}

private object ShimmerBand {
    private var current: BandBrush? = null

    fun get(color: Int, width: Float): BandBrush {
        val b = current
        if (b != null && b.color == color && b.width == width) return b
        return BandBrush(color, width).also { current = it }
    }
}

@Composable
fun Modifier.shimmerHost(): Modifier {
    val live = Motion.enabled && Adaptive.heavy && ValueAnimator.areAnimatorsEnabled()
    val view = LocalView.current
    val highlight = LocalIos.current.fill.toArgb()
    val origin = remember { FloatArray(2) }
    if (live) DisposableEffect(Unit) {
        ShimmerClock.acquire()
        onDispose { ShimmerClock.release() }
    }
    return this
        .onGloballyPositioned { c ->
            val p = c.positionInWindow()
            origin[0] = p.x
            origin[1] = p.y
        }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (!live) return@drawWithContent
            val t = ShimmerClock.sweep.floatValue
            if (t >= 1f) return@drawWithContent
            val windowW = view.rootView.width.toFloat()
            if (windowW <= 0f) return@drawWithContent
            val band = windowW * UiLayout.SHIMMER_BAND
            val x = -band + (windowW + band) * t - origin[0]
            translate(left = x) {
                drawRect(ShimmerBand.get(highlight, band), size = Size(band, size.height), blendMode = BlendMode.SrcAtop)
            }
        }
}

@Composable
private fun Bone(modifier: Modifier, radius: Dp = UiLayout.dp6) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(LocalIos.current.fill3))
}

@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().shimmerHost().padding(horizontal = UiLayout.dp16, vertical = UiLayout.dp8)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().height(UiLayout.dp52), verticalAlignment = Alignment.CenterVertically) {
                Bone(Modifier.size(UiLayout.dp29), UiLayout.dp8)
                Spacer(Modifier.width(UiLayout.dp14))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiLayout.dp6)) {
                    Bone(Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.5f).height(UiLayout.dp12))
                    Bone(Modifier.fillMaxWidth(0.35f).height(UiLayout.dp10))
                }
            }
        }
    }
}

@Composable
fun SkeletonTiles(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().shimmerHost().padding(horizontal = UiLayout.dp12)) {
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(vertical = UiLayout.dp6)) {
                repeat(4) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Bone(Modifier.size(UiLayout.dp60), UiLayout.dp16)
                        Spacer(Modifier.height(UiLayout.dp6))
                        Bone(Modifier.width(UiLayout.dp44).height(UiLayout.dp10))
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonThumb(modifier: Modifier = Modifier) {
    Box(modifier.shimmerHost()) { Bone(Modifier.fillMaxSize(), UiLayout.dp0) }
}

@Composable
fun SkeletonReveal(
    ready: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val fade = remember { Animatable(1f) }
    var gone by remember { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready) {
            if (!gone) {
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                fade.animateTo(0f, Motion.timed(Motion.SHORT))
                gone = true
            }
        } else {
            gone = false
            fade.snapTo(1f)
        }
    }
    Box(modifier) {
        if (ready) content()
        if (!gone) Box(
            (if (ready) Modifier.matchParentSize() else Modifier).graphicsLayer { alpha = fade.value }
        ) { skeleton() }
    }
}
