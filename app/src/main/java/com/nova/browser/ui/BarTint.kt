package com.nova.browser

import androidx.compose.ui.graphics.Color

/** يحدّد لون شريط الحالة / الشريط السفلي حسب اختيار المستخدم (مثل ثيمات كروم). */
object BarTint {
    /** 0 لون الصفحة (تلقائي)، 1 لون التطبيق، 2 أسود، 3 لون مخصص */
    const val PAGE = 0
    const val APP = 1
    const val BLACK = 2
    const val CUSTOM = 3

    val customColors = listOf(
        Color(0xFF1C1C1E), Color(0xFFFFFFFF), Color(0xFF0A84FF), Color(0xFF5E5CE6),
        Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFFFF9F0A), Color(0xFF30B0C7)
    )

    fun resolve(mode: Int, custom: Int, page: Int?, blank: Boolean, appBg: Color, startBg: Color): Color = when (mode) {
        BLACK -> Color.Black
        CUSTOM -> customColors[custom.coerceIn(0, customColors.lastIndex)]
        APP -> if (blank) startBg else appBg
        else -> if (blank) startBg else page?.let { Color(it) } ?: appBg
    }
}
