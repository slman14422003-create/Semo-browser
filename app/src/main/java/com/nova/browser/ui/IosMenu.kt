package com.nova.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

object MenuScrim {
    var open by mutableIntStateOf(0)
}

@Composable
fun Modifier.menuScrim(): Modifier {
    val p = animateFloatAsState(if (MenuScrim.open > 0) 1f else 0f, Motion.timed(Motion.SHORT), label = "menuScrim")
    val peak = if (LocalIos.current.dark) 0.34f else 0.2f
    return this.drawWithContent {
        drawContent()
        val v = p.value
        if (v > 0f) drawRect(Color.Black.copy(alpha = peak * v))
    }
}

@Composable
fun ScrimBehind(active: Boolean = true) {
    DisposableEffect(active) {
        if (active) MenuScrim.open++
        onDispose { if (active) MenuScrim.open-- }
    }
}

@Composable
fun menuSurface(): Color {
    val ios = LocalIos.current
    val base = ios.menu.copy(alpha = 1f)
    return if (ios.dark) lerp(base, Color.Black, 0.35f) else lerp(base, Color.Black, 0.03f)
}

@Composable
fun IosMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = IosShape.menuCompact,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    ScrimBehind(expanded)
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset,
        properties = properties, shape = shape, containerColor = menuSurface(),
        tonalElevation = tonalElevation, shadowElevation = shadowElevation, content = content
    )
}
