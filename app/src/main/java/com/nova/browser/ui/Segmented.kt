package com.nova.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class Segment(val label: String, val lock: Boolean = false, val onLong: (() -> Unit)? = null)

@Composable
fun GlassSegments(items: List<Segment>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Boolean = true) {
    val ios = LocalIos.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = bounds[selected]
    val lens = remember { LensPill() }
    LaunchedEffect(target) { target?.let { lens.glideTo(it.first, it.first + it.second) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rim = remember(density) { androidx.compose.ui.graphics.drawscope.Stroke(with(density) { UiLayout.GLASS_RIM.toPx() }) }
    val pill = ios.segThumb
    val scroll = rememberScrollState()
    Box(modifier.height(UiLayout.dp36).liquidGlass(raised = false).padding(UiLayout.dp2)) {
        Row(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxHeight().horizontalScroll(scroll))
                .drawBehind {
                    val l = lens.left.value
                    val w = lens.right.value - l
                    if (w > 0f) {
                        val corner = CornerRadius(size.height / 2f)
                        drawRoundRect(pill, Offset(l, 0f), Size(w, size.height), corner)
                        if (Glass.level == Glass.FULL) drawRoundRect(androidx.compose.ui.graphics.Color.White, Offset(l, 0f), Size(w, size.height), corner, style = rim, alpha = 0.3f)
                    }
                }
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val src = remember { MutableInteractionSource() }
                Row(
                    (if (fill) Modifier.weight(1f) else Modifier).fillMaxHeight()
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .tapCombinedClickable(interactionSource = src, indication = null, role = Role.Tab, onLongClick = item.onLong) { onSelect(i) }
                        .semantics { this.selected = on }
                        .padding(horizontal = if (fill) UiLayout.dp4 else UiLayout.dp14),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.lock) {
                        IosIcon(IosGlyph.Lock, ios.label, UiLayout.dp12)
                        Spacer(Modifier.width(UiLayout.dp5))
                    }
                    Text(
                        item.label, style = MaterialTheme.typography.bodySmall, color = ios.label,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
