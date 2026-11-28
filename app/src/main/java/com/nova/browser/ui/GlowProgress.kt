package com.nova.browser

import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

internal class RadialGlow(color: Color, heart: Float) {
    val brush: ShaderBrush

    init {
        val rgb = color.toArgb() and 0xFFFFFF
        val colors = IntArray(FALLOFF.size) { i -> rgb or ((255f * heart * FALLOFF[i]).roundToInt() shl 24) }
        brush = ShaderBrush(RadialGradient(0f, 0f, 1f, colors, STOPS, Shader.TileMode.CLAMP))
    }

    companion object {
        val STOPS = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        val FALLOFF = floatArrayOf(1f, 0.78f, 0.45f, 0.2f, 0.06f, 0f)
        const val FLATTEN = 0.6f
    }
}

fun Modifier.glowProgress(fill: () -> Float, alpha: () -> Float, color: Color): Modifier =
    drawWithCache {
        val lineH = UiLayout.PROGRESS_LINE.toPx()
        val glow = if (GlassPolicy.glowHead(Glass.level)) RadialGlow(color, 0.7f) else null
        val minR = UiLayout.PROGRESS_GLOW_MIN.toPx()
        val maxR = UiLayout.PROGRESS_GLOW_MAX.toPx()
        val radius = (lineH * 2.7f).coerceIn(minR, maxR)
        val corner = CornerRadius(lineH / 2f, lineH / 2f)
        onDrawBehind {
            val a = alpha()
            val f = fill()
            if (a > 0f && f > 0f) {
                val rtl = layoutDirection == LayoutDirection.Rtl
                val w = size.width * f
                val left = if (rtl) size.width - w else 0f
                val top = size.height - lineH
                drawRoundRect(color, Offset(left, top), Size(w, lineH), corner, alpha = a)
                if (glow != null && f < 0.995f) {
                    val headX = if (rtl) left else left + w
                    translate(headX, top + lineH / 2f) {
                        scale(radius, radius * RadialGlow.FLATTEN, Offset.Zero) {
                            drawCircle(glow.brush, 1f, Offset.Zero, alpha = a)
                        }
                    }
                }
            }
        }
    }
