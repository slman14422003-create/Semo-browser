package com.nova.browser

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

object MenuBlur {
    var open by mutableIntStateOf(0)
}

/**
 * تمويه الخلفية خلف القوائم. تحريك نصف القطر كل إطار كان يعيد معالجة الشاشة كلها (بما فيها الـ WebView) عند كل إطار فيتقطع فتح القائمة؛
 * الآن يتغيّر نصف القطر بثلاث درجات ثابتة فقط (4 ثم 8 ثم 12dp) فلا إعادة رسم إلا عند تغيّر الدرجة، وتبقى الحركة ناعمة بتلاشي القائمة نفسها.
 */
@Composable
fun Modifier.menuBlur(): Modifier {
    val p = animateFloatAsState(if (MenuBlur.open > 0) 1f else 0f, Motion.timed(Motion.SHORT), label = "menuBlur")
    val effects = remember { arrayOfNulls<BlurEffect>(4) }
    val density = LocalDensity.current.density
    return this
        .graphicsLayer {
            val step = if (Adaptive.heavy) (p.value * 3f).toInt().coerceIn(0, 3) else 0
            renderEffect = if (step == 0) null else effects[step]
                ?: BlurEffect(step * 4 * density, step * 4 * density, TileMode.Clamp).also { effects[step] = it }
            clip = step > 0
        }
        .drawWithContent {
            drawContent()
            if (!Adaptive.heavy && p.value > 0f) drawRect(Color.Black.copy(alpha = 0.18f * p.value))
        }
}

@Composable
fun BlurBehind(active: Boolean = true) {
    DisposableEffect(active) {
        if (active) MenuBlur.open++
        onDispose { if (active) MenuBlur.open-- }
    }
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
    BlurBehind(expanded)
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset,
        properties = properties, shape = shape, containerColor = LocalIos.current.menu.copy(alpha = 0.78f),
        tonalElevation = tonalElevation, shadowElevation = shadowElevation, content = content
    )
}
