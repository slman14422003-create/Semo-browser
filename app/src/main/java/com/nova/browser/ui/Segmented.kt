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
    val x by animateFloatAsState(target?.first ?: 0f, Motion.soft(), label = "pillX")
    val w by animateFloatAsState(target?.second ?: 0f, Motion.soft(), label = "pillW")
    val pill = ios.segThumb
    val scroll = rememberScrollState()
    Box(modifier.height(36.dp).liquidGlass(raised = false).padding(2.dp)) {
        Row(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxHeight().horizontalScroll(scroll))
                .drawBehind { if (w > 0f) drawRoundRect(pill, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2f)) }
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val src = remember { MutableInteractionSource() }
                Row(
                    (if (fill) Modifier.weight(1f) else Modifier).fillMaxHeight()
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .tapCombinedClickable(interactionSource = src, indication = null, role = Role.Tab, onLongClick = item.onLong) { onSelect(i) }
                        .semantics { this.selected = on }
                        .padding(horizontal = if (fill) 4.dp else 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.lock) {
                        IosIcon(IosGlyph.Lock, ios.label, 12.dp)
                        Spacer(Modifier.width(5.dp))
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
