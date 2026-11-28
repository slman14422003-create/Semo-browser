package com.nova.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object Accent {
    const val CUSTOM = 6
    const val DEFAULT_CUSTOM = 0x00C2B8
    private const val OPAQUE = 0xFF000000.toInt()

    val names = intArrayOf(
        R.string.accent_blue, R.string.accent_indigo, R.string.accent_purple, R.string.accent_pink,
        R.string.accent_orange, R.string.accent_teal, R.string.accent_custom
    )

    fun color(index: Int, dark: Boolean): Color =
        if (index == CUSTOM) custom(dark)
        else AccentChoices.getOrElse(index) { AccentChoices[0] }.let { if (dark) it.second else it.first }

    fun custom(dark: Boolean): Color = Color(OPAQUE or AccentTone.tone(Prefs.accentColor, dark))

    fun current(dark: Boolean): Color = color(Prefs.accent, dark)

    fun end(color: Color): Color = Color(OPAQUE or AccentTone.gradientEnd(color.toArgb()))

    fun on(color: Color): Color = Color(OPAQUE or AccentTone.on(color.toArgb()))

    fun opaque(rgb: Int): Color = Color(OPAQUE or (rgb and 0xFFFFFF))
}
