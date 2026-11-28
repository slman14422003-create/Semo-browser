package com.nova.browser

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

object Motion {
    const val QUICK = 110
    const val SHORT = 180
    const val MEDIUM = 240
    const val LONG = 320
    const val XLONG = 400
    const val STAGGER = 40
    const val SPIN = 1000
    const val SHIMMER = 1200
    const val GLIDE = 360
    const val BLOOM_HOLD = 900L
    const val PROGRESS_FILL = SHORT
    const val PROGRESS_FADE = MEDIUM
    const val SNAPSHOT_FADE = MEDIUM

    private val emphasizedCurve by lazy {
        PathInterpolator(Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        })
    }
    val emphasized = Easing { emphasizedCurve.getInterpolation(it) }
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val enabled: Boolean get() = Adaptive.ms(100) > 0

    fun ms(base: Int): Int = Adaptive.ms(base)

    fun millis(base: Long): Long = Adaptive.ms(base.toInt()).toLong()

    fun <T> loop(base: Int): InfiniteRepeatableSpec<T> =
        infiniteRepeatable(tween(Adaptive.ms(base).takeIf { it > 0 } ?: base, easing = LinearEasing))

    fun <T> timed(base: Int, easing: Easing = standard, delayMillis: Int = 0): TweenSpec<T> =
        tween(Adaptive.ms(base), Adaptive.ms(delayMillis), easing)

    fun <T> springy(damping: Float, stiffness: Float): FiniteAnimationSpec<T> =
        if (enabled) spring(damping, stiffness) else snap()

    fun <T> snappy(): FiniteAnimationSpec<T> = springy(0.8f, 700f)
    fun <T> glideLead(): FiniteAnimationSpec<T> = springy(0.7f, 900f)
    fun <T> glideTrail(): FiniteAnimationSpec<T> = springy(0.78f, 420f)
    fun <T> bloom(): FiniteAnimationSpec<T> = springy(0.85f, 320f)
    fun <T> rubber(): FiniteAnimationSpec<T> = springy(0.85f, 250f)
    fun <T> soft(): FiniteAnimationSpec<T> = springy(0.8f, 400f)
    fun <T> bounce(): FiniteAnimationSpec<T> = springy(0.55f, 380f)
    fun <T> magnet(): FiniteAnimationSpec<T> = springy(0.95f, 800f)
    fun <T> drop(): FiniteAnimationSpec<T> = springy(0.72f, 520f)
    fun <T> bouncy(): FiniteAnimationSpec<T> = if (Adaptive.heavy) springy(0.68f, 400f) else timed(SHORT)

    val fadeThroughEnter: EnterTransition
        get() = fadeIn(timed(MEDIUM, emphasizedDecelerate, QUICK)) +
            scaleIn(timed(MEDIUM, emphasizedDecelerate, QUICK), initialScale = 0.96f)
    val fadeThroughExit: ExitTransition
        get() = fadeOut(timed(QUICK, emphasizedAccelerate)) +
            scaleOut(timed(QUICK, emphasizedAccelerate), targetScale = 0.98f)

    fun axisEnter(forward: Boolean): EnterTransition {
        val s = (if (forward) 1 else -1) * (if (I18n.isEnglish()) 1 else -1)
        return slideInHorizontally(timed(XLONG, emphasized)) { s * it / 3 } +
            fadeIn(timed(MEDIUM, emphasizedDecelerate, STAGGER))
    }

    fun axisExit(forward: Boolean): ExitTransition {
        val s = (if (forward) -1 else 1) * (if (I18n.isEnglish()) 1 else -1)
        return slideOutHorizontally(timed(XLONG, emphasized)) { s * it / 4 } + fadeOut(timed(SHORT))
    }

    val barEnter: EnterTransition
        get() = slideInVertically(timed(LONG, emphasizedDecelerate)) { -it } + fadeIn(timed(SHORT))
    val barExit: ExitTransition
        get() = slideOutVertically(timed(MEDIUM, emphasizedAccelerate)) { -it } + fadeOut(timed(QUICK))
}
