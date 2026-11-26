package com.nova.browser

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * ملف تنظيم الواجهة — المصدر الوحيد لقياسات وسلوك تخطيط الصفحة.
 *
 * القاعدة: أي رقم يخص ارتفاع شريط أو مساحة الصفحة أو توقيت الكيبورد يُعرَّف هنا فقط، ولا يُكتب رقماً مباشراً
 * في MainActivity أو SiteBar (فحص tools/project_check.py يتحقق من ذلك). هكذا لا يختلف ارتفاع الشريط عن
 * الحشوة المحجوزة له، ولا تتضارب قيم الكيبورد بين ملفين.
 *
 * هيكل الشاشة (من الأعلى إلى الأسفل):
 *   [شريط الحالة]            statusBarsPadding
 *   [SiteBar  BAR_DP]         وضع التطبيق فقط (يوتيوب/ذكاء اصطناعي) — حشوة علوية في الصفحة بنفس الارتفاع
 *   [الصفحة WebView]          pageInsets: تنتهي فوق BottomPill في الوضع العادي، وفوق الكيبورد أثناء الكتابة
 *   [BottomPill + شريط التنقل] تختفي أثناء الكتابة داخل الصفحة
 */
object UiLayout {
    /** ارتفاع الشريط العلوي لوضع التطبيق كاملاً (الصف + خط التقدّم). */
    const val BAR_DP = 48

    /** ارتفاع خط التقدّم أسفل الشريط. الصف = BAR_DP − PROGRESS_DP. */
    const val PROGRESS_DP = 3
    const val BAR_ROW_DP = BAR_DP - PROGRESS_DP

    /** المساحة المحجوزة للكبسولة السفلية فوق شريط التنقل. */
    const val BAR_H = 60
    val PILL: Dp = BAR_H.dp

    const val BAR_COMPACT_H = 36
    val PILL_COMPACT: Dp = BAR_COMPACT_H.dp
    const val COMPACT_PILL_DP = 28
    const val COLLAPSE_ANIM_MS = 240
    const val COLLAPSE_SETTLE_MS = 80L
    const val SCROLL_COLLAPSE_PX = 56
    const val SCROLL_EXPAND_PX = 24
    const val SCROLL_TOP_PX = 8
    const val TOGGLE_LOCK_MS = 450L
    const val SWIPE_OUT_MS = 130
    const val SWIPE_IN_MS = 260

    /**
     * مهلة استقرار الكيبورد: لا نغيّر ارتفاع الـ WebView إلا بعد أن يثبت ارتفاع الكيبورد هذه المدة.
     * تغيير ارتفاع الـ WebView في كل إطار من حركة الكيبورد (≈ 20 مرة) يعيد تخطيط صفحة يوتيوب كاملة
     * في كل مرة، وهو سبب التقطيع (lag) السابق. الآن يُعاد التخطيط مرة واحدة.
     */
    const val KB_SETTLE_MS = 60L

    /** مدة تلاشي ستار الانتقال عند تبديل التبويب أو فتح صفحة من الرئيسية (ms قبل Adaptive). */
    const val VEIL_MS = 180
}

/**
 * ارتفاع الكيبورد «المستقر»: يتحدّث مرة واحدة بعد انتهاء حركة الظهور/الاختفاء، وليس مع كل إطار.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberSettledIme(ime: WindowInsets): State<Int> {
    val density = LocalDensity.current
    val settled = remember { mutableIntStateOf(0) }
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) }
            .debounce(UiLayout.KB_SETTLE_MS)
            .collect { settled.intValue = it }
    }
    return settled
}

/**
 * ارتفاع منطقة الصفحة (WebView) في طبقة التخطيط:
 * - كتابة داخل الصفحة: تنتهي الصفحة عند أعلى الكيبورد بعد استقراره (تغيير واحد). وقبل الاستقرار تبقى
 *   الصفحة بحجمها السابق بدل أن تقفز إلى الشاشة الكاملة ثم تنكمش (كانت تُعاد مرتين).
 * - الوضع العادي: تترك مكان شريط العنوان السفلي وشريط التنقل.
 * - النافذة المنبثقة: ملء الشاشة.
 * القراءة داخل layout تعني أن تغيّر [imeSettled] يعيد القياس فقط دون إعادة تركيب.
 */
fun Modifier.pageInsets(
    imeSettled: () -> Int,
    nav: WindowInsets,
    typing: Boolean,
    inPip: Boolean,
    pill: () -> Dp = { UiLayout.PILL }
): Modifier = this.layout { m, c ->
    val resting = nav.getBottom(this) + pill().roundToPx()
    val ime = imeSettled()
    val pad = when {
        inPip -> 0
        typing && ime > 0 -> ime
        else -> resting
    }
    val h = (c.maxHeight - pad).coerceAtLeast(0)
    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
    layout(c.maxWidth, c.maxHeight) { p.placeRelative(0, 0) }
}

@OptIn(FlowPreview::class)
@Composable
fun rememberSettledCollapse(target: () -> Boolean): State<Boolean> {
    val settled = remember { mutableStateOf(target()) }
    val latest by rememberUpdatedState(target)
    LaunchedEffect(Unit) {
        snapshotFlow { latest() }
            .debounce { Adaptive.ms(UiLayout.COLLAPSE_ANIM_MS) + UiLayout.COLLAPSE_SETTLE_MS }
            .collect { settled.value = it }
    }
    return settled
}
