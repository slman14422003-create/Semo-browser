package com.nova.browser

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

object ThemeMode {
    const val AUTO = 0
    const val LIGHT = 1
    const val DARK = 2

    fun dark(systemDark: Boolean): Boolean = when (Prefs.theme) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }

    @Composable
    fun isDark(): Boolean = dark(isSystemInDarkTheme())
}
