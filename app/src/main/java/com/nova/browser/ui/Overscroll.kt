package com.nova.browser

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

@OptIn(ExperimentalFoundationApi::class)
private class RubberBand(private val limit: Float) : OverscrollEffect {
    private var pull by mutableFloatStateOf(0f)

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
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
            val give = (1f - abs(pull) / limit).coerceIn(0f, 1f)
            pull = (pull + left * 0.5f * give).coerceIn(-limit, limit)
            return delta
        }
        return Offset(used.x, used.y + relaxed)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val consumed = if (pull == 0f) performFling(velocity) else Velocity.Zero
        val left = velocity.y - consumed.y
        animate(pull, 0f, left * 0.25f, spring(dampingRatio = 0.85f, stiffness = 250f)) { v, _ ->
            pull = v.coerceIn(-limit, limit)
        }
    }

    override val isInProgress: Boolean get() = pull != 0f

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) { translationY = pull }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRubberBand(): OverscrollEffect? {
    val limit = with(LocalDensity.current) { 140.dp.toPx() }
    val on = Motion.enabled
    return remember(on, limit) { if (on) RubberBand(limit) else null }
}
