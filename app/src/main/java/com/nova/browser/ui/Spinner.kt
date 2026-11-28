package com.nova.browser

import android.animation.ValueAnimator
import android.graphics.Canvas as GCanvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/*
 * مؤشر التحميل الموحّد للتطبيق (نمط iPhone): 12 شريطاً مستديراً يدور بخطوات منفصلة ويتلاشى ذيله.
 * المصدر الوحيد لكل مؤشرات الانتظار — لا تستخدم CircularProgressIndicator في أي مكان آخر.
 * ثلاث صيغ: مكوّن Compose (NovaSpinner)، ومؤشر السحب للتحديث (NovaPullIndicator).
 */

private const val SPOKES = 12

/** شفافية الشريط: 0 = الرأس (الأغمق) … 11 = آخر الذيل (الأفتح). المنحنى يطابق شكل المؤشر الأصلي (معظم الأشرطة داكنة وطرف الذيل فاتح). */
private fun spokeAlpha(d: Int): Float = 1f - 0.88f * (d / (SPOKES - 1f)).pow(1.5f)

/** مؤشر تحميل بنمط iPhone. اللون الافتراضي يتبع لون المحتوى الحالي فيناسب الفاتح والداكن. */
@Composable
fun NovaSpinner(modifier: Modifier = Modifier, size: Dp = UiLayout.dp24, color: Color = LocalContentColor.current) {
    val t = rememberInfiniteTransition(label = "nova-spinner")
    // قيمة صحيحة تتغيّر 12 مرة في الثانية فقط: لا إعادة رسم بلا داعٍ
    val step by t.animateValue(0, SPOKES, Int.VectorConverter, Motion.loop(Motion.SPIN), label = "step")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val sw = r * 0.22f
        val inner = r * 0.5f + sw / 2f
        val outer = r - sw / 2f
        val c = center
        val head = step % SPOKES
        for (i in 0 until SPOKES) {
            val d = ((head - i) % SPOKES + SPOKES) % SPOKES
            val ang = Math.toRadians(i * 30.0 - 90.0)
            val cx = cos(ang).toFloat(); val sy = sin(ang).toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * spokeAlpha(d)),
                start = Offset(c.x + cx * inner, c.y + sy * inner),
                end = Offset(c.x + cx * outer, c.y + sy * outer),
                strokeWidth = sw, cap = StrokeCap.Round
            )
        }
    }
}

/** صف انتظار: المؤشر ونص اختياري بجانبه (للقوائم والنوافذ). */
@Composable
fun NovaLoadingRow(text: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(UiLayout.dp16), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NovaSpinner(size = UiLayout.dp22, color = LocalIos.current.blue)
        if (text != null) {
            Spacer(Modifier.width(UiLayout.dp10))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** مؤشر السحب للتحديث في القوائم (يحل محل مؤشر Material الافتراضي). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaPullIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .graphicsLayer {
                val p = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1.2f)
                translationY = p * UiLayout.dp64.toPx() - UiLayout.dp36.toPx()
                alpha = p.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * min(p, 1f)
                scaleX = s; scaleY = s
            }
            .shadow(UiLayout.dp4, CircleShape)
            .background(cs.surfaceContainerHigh, CircleShape)
            .size(UiLayout.dp40),
        contentAlignment = Alignment.Center
    ) { NovaSpinner(size = UiLayout.dp20, color = LocalIos.current.blue) }
}
