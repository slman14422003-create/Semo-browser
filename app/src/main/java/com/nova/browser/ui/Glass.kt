package com.nova.browser

import android.graphics.BitmapShader
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

object Glass {
    const val FULL = GlassPolicy.FULL
    const val LITE = GlassPolicy.LITE
    const val FLAT = GlassPolicy.FLAT

    var page by mutableStateOf<ImageBitmap?>(null)

    val pick: Int get() = if (Prefs.glass == GlassPolicy.UNSET) GlassPolicy.defaultPick(Adaptive.weak) else Prefs.glass

    val level: Int get() = GlassPolicy.effective(Prefs.glass, Adaptive.weak, Prefs.smoothAnim, Adaptive.level, Adaptive.saver)

    val lowered: Boolean get() = GlassPolicy.forcedFlat(Prefs.smoothAnim, Adaptive.level, Adaptive.saver)

    val saturation: ColorFilter by lazy { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }) }

    val noise: ShaderBrush by lazy {
        val size = 96
        val random = java.util.Random(7)
        val px = IntArray(size * size) { val v = random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val bmp = android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        ShaderBrush(BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
    }

    private const val LENS_HEAD = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float amount;
uniform float split;
float sd(float2 p, float2 h, float r) {
    float2 q = abs(p) - (h - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}
half4 main(float2 coord) {
    float2 h = size * 0.5;
    float2 p = coord - h;
    float d = sd(p, h, radius);
    if (-d >= depth) { return content.eval(coord); }
    float t = clamp(1.0 + d / depth, 0.0, 1.0);
    float k = t * t * amount;
    float2 e = float2(1.0, 0.0);
    float2 n = normalize(float2(sd(p + e.xy, h, radius) - sd(p - e.xy, h, radius), sd(p + e.yx, h, radius) - sd(p - e.yx, h, radius)) + float2(0.0001));
    float2 o = n * k;
"""

    private const val LENS_LITE = LENS_HEAD + """
    half4 r = content.eval(coord - o * (1.0 + split));
    half4 g = content.eval(coord - o);
    half4 b = content.eval(coord - o * (1.0 - split));
    return half4(r.r, g.g, b.b, g.a);
}
"""

    private const val LENS_FULL = LENS_HEAD + """
    half4 a0 = content.eval(coord - o * (1.0 + split));
    half4 a1 = content.eval(coord - o * (1.0 + split * 0.5));
    half4 a2 = content.eval(coord - o);
    half4 a3 = content.eval(coord - o * (1.0 - split * 0.5));
    half4 a4 = content.eval(coord - o * (1.0 - split));
    return half4((a0.r + a1.r) * 0.5, a2.g, (a3.b + a4.b) * 0.5, a2.a);
}
"""

    fun lensSource(level: Int): String = if (level == FULL) LENS_FULL else LENS_LITE
}

val LocalGlassPage = compositionLocalOf<ImageBitmap?> { null }

@Immutable
class GlassStyle(
    val blur: Dp,
    val tint: Float,
    val surface: (IosColors) -> Color,
    val gradientFlat: Boolean,
    val lens: Boolean,
    val grain: Boolean,
    val sheen: Boolean
)

object GlassStyles {
    val Pill = GlassStyle(
        UiLayout.GLASS_BLUR_PILL, 0.62f, { if (it.dark) Color(0xFF202022) else Color(0xFFFBFBFD) },
        gradientFlat = true, lens = true, grain = true, sheen = true
    )
    val Bar = GlassStyle(
        UiLayout.GLASS_BLUR_BAR, 0.6f, { it.cell },
        gradientFlat = false, lens = true, grain = true, sheen = true
    )
    val Sheet = GlassStyle(
        UiLayout.GLASS_BLUR_SHEET, 0.72f, { it.groupedBg },
        gradientFlat = false, lens = false, grain = true, sheen = true
    )
    val Dialog = GlassStyle(
        UiLayout.GLASS_BLUR_SHEET, 0.78f, { it.alert },
        gradientFlat = false, lens = false, grain = true, sheen = false
    )
}

class GlassSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

fun Modifier.glassSource(source: GlassSource): Modifier =
    onGloballyPositioned { val p = it.positionInWindow(); if (p != source.origin) source.origin = p }
        .drawWithContent {
            if (Glass.level == Glass.FLAT) {
                drawContent()
            } else {
                source.layer.record { this@drawWithContent.drawContent() }
                drawLayer(source.layer)
            }
        }

internal class GlassHold {
    var origin by mutableStateOf(Offset.Zero)
    var lens: RuntimeShader? = null
    var lensLevel = -1
    var gen = 0
    private var ox = Float.NaN
    private var oy = Float.NaN
    private var sx = Float.NaN
    private var sy = Float.NaN
    private var w = -1f
    private var h = -1f
    private var g = -1

    fun moveTo(p: Offset) { if (p != origin) origin = p }

    fun stale(ox: Float, oy: Float, sx: Float, sy: Float, w: Float, h: Float): Boolean {
        if (ox == this.ox && oy == this.oy && sx == this.sx && sy == this.sy && w == this.w && h == this.h && g == gen) return false
        this.ox = ox; this.oy = oy; this.sx = sx; this.sy = sy; this.w = w; this.h = h; g = gen
        return true
    }
}

private fun DrawScope.drawSnapshot(img: ImageBitmap, ox: Float, oy: Float, vw: Int, vh: Int, filter: ColorFilter) {
    if (vw <= 0 || vh <= 0) return
    val bw = img.width
    val bh = img.height
    val x = (ox / vw * bw).toInt().coerceIn(0, bw - 1)
    val y = (oy / vh * bh).toInt().coerceIn(0, bh - 1)
    val sw = (size.width / vw * bw).toInt().coerceIn(1, bw - x)
    val sh = (size.height / vh * bh).toInt().coerceIn(1, bh - y)
    drawImage(
        img, IntOffset(x, y), IntSize(sw, sh), IntOffset.Zero,
        IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Low, colorFilter = filter
    )
}

@Composable
fun Modifier.glass(
    style: GlassStyle,
    shape: Shape = IosShape.pill,
    source: GlassSource? = null,
    page: ImageBitmap? = LocalGlassPage.current
): Modifier {
    val ios = LocalIos.current
    val level = Glass.level
    val view = LocalView.current
    val layer = rememberGraphicsLayer()
    val hold = remember { GlassHold() }
    val backed = level != Glass.FLAT && (source != null || page != null)
    val draw = remember(style, shape, ios, level, source, page, backed) {
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val surface = style.surface(ios)
            val tint = if (backed) surface.copy(alpha = style.tint) else surface
            val flat: Brush? = if (!backed && style.gradientFlat) {
                Brush.verticalGradient(
                    if (ios.dark) listOf(Color(0xD6202022), Color(0xC2161618)) else listOf(Color(0xDBFBFBFD), Color(0xC7EFEFF3)),
                    startY = 0f, endY = h
                )
            } else null
            val sheen: Brush? = if (style.sheen && level != Glass.FLAT) {
                Brush.verticalGradient(
                    0f to (if (ios.dark) Color(0x24FFFFFF) else Color(0x8CFFFFFF)),
                    0.5f to Color.Transparent,
                    1f to Color.Transparent,
                    startY = 0f, endY = h
                )
            } else null
            val rim = Brush.verticalGradient(
                0f to (if (ios.dark) Color(0x70FFFFFF) else Color(0xF2FFFFFF)),
                0.5f to (if (ios.dark) Color(0x14FFFFFF) else Color(0x0A000000)),
                1f to (if (ios.dark) Color(0x33FFFFFF) else Color(0x26000000)),
                startY = 0f, endY = h
            )
            val stroke = Stroke(UiLayout.GLASS_RIM.toPx())
            val grain = style.grain && GlassPolicy.grain(level) && backed
            if (source != null && backed) {
                val blur = style.blur.toPx() * GlassPolicy.blurScale(level)
                val radius = minOf(w, h) / 2f
                val screen = view.rootView
                val lens = if (style.lens && GlassPolicy.lensAllowed(level, w.roundToInt(), h.roundToInt(), screen.width, screen.height)) {
                    if (hold.lens == null || hold.lensLevel != level) {
                        hold.lens = RuntimeShader(Glass.lensSource(level))
                        hold.lensLevel = level
                    }
                    hold.lens?.also {
                        it.setFloatUniform("size", w, h)
                        it.setFloatUniform("radius", minOf(radius, h / 2f))
                        it.setFloatUniform("depth", UiLayout.LENS_DEPTH.toPx())
                        it.setFloatUniform("amount", UiLayout.LENS_AMOUNT.toPx())
                        it.setFloatUniform("split", 0.06f)
                    }
                } else null
                val frost = android.graphics.RenderEffect.createBlurEffect(
                    blur.coerceAtLeast(0.1f), blur.coerceAtLeast(0.1f),
                    android.graphics.RenderEffect.createColorFilterEffect(
                        ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1.4f) })
                    ),
                    Shader.TileMode.CLAMP
                )
                val effect = (if (lens != null) {
                    android.graphics.RenderEffect.createChainEffect(
                        android.graphics.RenderEffect.createRuntimeShaderEffect(lens, "content"), frost
                    )
                } else frost).asComposeRenderEffect()
                hold.gen++
                onDrawBehind {
                    val o = hold.origin
                    val so = source.origin
                    if (hold.stale(o.x, o.y, so.x, so.y, w, h)) {
                        layer.renderEffect = effect
                        layer.record { translate(so.x - o.x, so.y - o.y) { drawLayer(source.layer) } }
                    }
                    clipPath(clip) {
                        drawLayer(layer)
                        drawRect(tint)
                        if (sheen != null) drawRect(sheen)
                        if (grain) drawRect(Glass.noise, alpha = 0.04f)
                    }
                    drawOutline(outline, rim, style = stroke)
                }
            } else {
                onDrawBehind {
                    val o = hold.origin
                    clipPath(clip) {
                        if (backed && page != null) {
                            val screen = view.rootView
                            drawSnapshot(page, o.x, o.y, screen.width, screen.height, Glass.saturation)
                        }
                        if (flat != null) drawRect(flat) else drawRect(tint)
                        if (sheen != null) drawRect(sheen)
                        if (grain) drawRect(Glass.noise, alpha = 0.04f)
                    }
                    drawOutline(outline, rim, style = stroke)
                }
            }
        }
    }
    return this.onGloballyPositioned { hold.moveTo(it.positionInWindow()) }.then(draw)
}
