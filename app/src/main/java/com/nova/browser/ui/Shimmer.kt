package com.nova.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(): Modifier = composed {
    val ios = LocalIos.current
    val base = ios.fill3
    if (!Motion.enabled || !Adaptive.heavy) return@composed background(base)
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(Motion.SHIMMER, easing = LinearEasing)), label = "shimmerX"
    )
    val shine = ios.fill
    drawBehind {
        drawRect(base)
        val w = size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, shine, Color.Transparent), Offset(w * (x - 0.5f), 0f), Offset(w * (x + 0.5f), 0f)))
    }
}

@Composable
private fun Bone(modifier: Modifier, radius: Int = 6) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).shimmer())
}

@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Bone(Modifier.size(29.dp), 8)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bone(Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.5f).height(12.dp))
                    Bone(Modifier.fillMaxWidth(0.35f).height(10.dp))
                }
            }
        }
    }
}

@Composable
fun SkeletonTiles(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                repeat(4) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Bone(Modifier.size(60.dp), 16)
                        Spacer(Modifier.height(6.dp))
                        Bone(Modifier.width(44.dp).height(10.dp))
                    }
                }
            }
        }
    }
}
