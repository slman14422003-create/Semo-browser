package com.nova.browser

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

@OptIn(ExperimentalFoundationApi::class)
private class RubberBand(private val fallback: Float) : OverscrollEffect {
    private var pull by mutableFloatStateOf(0f)
    private var length = 0f

    private fun limit(): Float = if (length > 0f) length * UiLayout.OVERSCROLL_MAX_SHARE else fallback

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        val max = limit()
        var relaxed = 0f
        if (pull != 0f && delta.y != 0f && sign(delta.y) != sign(pull)) {
            val next = pull + delta.y
            if (sign(next) != sign(pull)) {
                relaxed = -pull
                pull = 0f
            } else {
                relaxed = delta.y
                pull = next
            }
        }
        val rest = Offset(delta.x, delta.y - relaxed)
        val used = performScroll(rest)
        val left = rest.y - used.y
        if (source == NestedScrollSource.UserInput && abs(left) > 0.5f) {
            val give = (1f - abs(pull) / max).coerceIn(0f, 1f)
            pull = (pull + left * UiLayout.OVERSCROLL_PULL * give).coerceIn(-max, max)
            return delta
        }
        return Offset(used.x, used.y + relaxed)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val resting = pull == 0f
        val consumed = if (resting) performFling(velocity) else Velocity.Zero
        val kick = if (resting) (velocity.y - consumed.y) * UiLayout.OVERSCROLL_FLING else 0f
        if (resting && abs(kick) < 1f) return
        val max = limit()
        animate(pull, 0f, kick, Motion.bounce<Float>()) { v, _ -> pull = v.coerceIn(-max, max) }
        pull = 0f
    }

    override val isInProgress: Boolean get() = pull != 0f

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            length = placeable.height.toFloat()
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    val p = pull
                    translationY = p
                    scaleY = 1f + UiLayout.OVERSCROLL_STRETCH * (abs(p) / limit()).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, if (p > 0f) 0f else 1f)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRubberBand(): OverscrollEffect? {
    val fallback = with(LocalDensity.current) { UiLayout.OVERSCROLL_FALLBACK.toPx() }
    val on = Motion.enabled
    return remember(on, fallback) { if (on) RubberBand(fallback) else null }
}
