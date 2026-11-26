package com.nova.browser

import android.content.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.SystemBarStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

@Immutable
data class IosColors(
    val dark: Boolean,
    val blue: Color, val green: Color, val orange: Color, val red: Color, val yellow: Color,
    val teal: Color, val indigo: Color, val purple: Color, val pink: Color,
    val gray: Color, val gray2: Color, val gray3: Color, val gray4: Color, val gray5: Color, val gray6: Color,
    val label: Color, val label2: Color, val label3: Color, val label4: Color,
    val fill: Color, val fill2: Color, val fill3: Color, val fill4: Color,
    val separator: Color, val opaqueSeparator: Color,
    val bg: Color, val bg2: Color, val groupedBg: Color, val cell: Color,
    val chrome: Color, val menu: Color, val tabsBg: Color, val alert: Color,
    val clearGlyph: Color, val segThumb: Color
)

val IosLight = IosColors(
    dark = false,
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), orange = Color(0xFFFF9500), red = Color(0xFFFF3B30), yellow = Color(0xFFFFCC00),
    teal = Color(0xFF30B0C7), indigo = Color(0xFF5856D6), purple = Color(0xFFAF52DE), pink = Color(0xFFFF2D55),
    gray = Color(0xFF8E8E93), gray2 = Color(0xFFAEAEB2), gray3 = Color(0xFFC7C7CC), gray4 = Color(0xFFD1D1D6), gray5 = Color(0xFFE5E5EA), gray6 = Color(0xFFF2F2F7),
    label = Color(0xFF000000), label2 = Color(0x993C3C43), label3 = Color(0x4D3C3C43), label4 = Color(0x2E3C3C43),
    fill = Color(0x33787880), fill2 = Color(0x28787880), fill3 = Color(0x1F767680), fill4 = Color(0x14747480),
    separator = Color(0x4A3C3C43), opaqueSeparator = Color(0xFFC6C6C8),
    bg = Color(0xFFFFFFFF), bg2 = Color(0xFFF2F2F7), groupedBg = Color(0xFFF2F2F7), cell = Color(0xFFFFFFFF),
    chrome = Color(0xF0F9F9F9), menu = Color(0xF2F7F7F9), tabsBg = Color(0xFFE8E8ED), alert = Color(0xFFF2F2F2),
    clearGlyph = Color.White, segThumb = Color.White
)

val IosDark = IosColors(
    dark = true,
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), orange = Color(0xFFFF9F0A), red = Color(0xFFFF453A), yellow = Color(0xFFFFD60A),
    teal = Color(0xFF40C8E0), indigo = Color(0xFF5E5CE6), purple = Color(0xFFBF5AF2), pink = Color(0xFFFF375F),
    gray = Color(0xFF8E8E93), gray2 = Color(0xFF636366), gray3 = Color(0xFF48484A), gray4 = Color(0xFF3A3A3C), gray5 = Color(0xFF2C2C2E), gray6 = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF), label2 = Color(0x99EBEBF5), label3 = Color(0x4DEBEBF5), label4 = Color(0x29EBEBF5),
    fill = Color(0x5C787880), fill2 = Color(0x52787880), fill3 = Color(0x3D767680), fill4 = Color(0x2E767680),
    separator = Color(0xA6545458), opaqueSeparator = Color(0xFF38383A),
    bg = Color(0xFF000000), bg2 = Color(0xFF1C1C1E), groupedBg = Color(0xFF000000), cell = Color(0xFF1C1C1E),
    chrome = Color(0xF01C1C1E), menu = Color(0xF22C2C2E), tabsBg = Color(0xFF2C2C2E), alert = Color(0xFF2C2C2E),
    clearGlyph = Color(0xFF3A3A3C), segThumb = Color(0xFF636366)
)

val LocalIos = staticCompositionLocalOf { IosLight }

val AccentChoices = listOf(
    IosLight.blue to IosDark.blue, IosLight.indigo to IosDark.indigo, IosLight.purple to IosDark.purple,
    IosLight.pink to IosDark.pink, IosLight.orange to IosDark.orange, IosLight.teal to IosDark.teal
)

fun IosColors.accented(): IosColors {
    val pick = AccentChoices.getOrElse(Prefs.accent) { AccentChoices[0] }
    return copy(blue = if (dark) pick.second else pick.first)
}

private val LightColors = lightColorScheme(
    primary = IosLight.blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF004CB3),
    secondary = Color(0xFF8E8E93), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF000000),
    tertiary = IosLight.blue, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA), onTertiaryContainer = Color(0xFF000000),
    error = IosLight.red, onError = Color.White,
    errorContainer = Color(0xFFFFE3E0), onErrorContainer = Color(0xFFC4251A),
    background = IosLight.groupedBg, onBackground = IosLight.label,
    surface = IosLight.cell, onSurface = IosLight.label,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C70),
    outline = IosLight.opaqueSeparator, outlineVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7F9), surfaceContainerHigh = Color(0xFFEEEEF0), surfaceContainerHighest = Color(0xFFE5E5EA)
)

private val DarkColors = darkColorScheme(
    primary = IosDark.blue, onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3A73), onPrimaryContainer = Color(0xFFBFDCFF),
    secondary = Color(0xFF8E8E93), onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = IosDark.blue, onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color(0xFFFFFFFF),
    error = IosDark.red, onError = Color.White,
    errorContainer = Color(0xFF4A1612), onErrorContainer = Color(0xFFFFB4AB),
    background = IosDark.groupedBg, onBackground = IosDark.label,
    surface = IosDark.cell, onSurface = IosDark.label,
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    outline = IosDark.opaqueSeparator, outlineVariant = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF2C2C2E), surfaceContainerHigh = Color(0xFF323235), surfaceContainerHighest = Color(0xFF3A3A3C)
)

private val NovaTypography = Typography().let { t ->
    val sf = androidx.compose.ui.text.font.FontFamily.SansSerif
    fun s(b: androidx.compose.ui.text.TextStyle, size: Int, line: Int, w: FontWeight) =
        b.copy(fontFamily = sf, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = 0.sp)
    t.copy(
        displayLarge = s(t.displayLarge, 34, 41, FontWeight.Bold),
        displayMedium = s(t.displayMedium, 28, 34, FontWeight.Bold),
        displaySmall = s(t.displaySmall, 22, 28, FontWeight.Bold),
        headlineLarge = s(t.headlineLarge, 28, 34, FontWeight.Bold),
        headlineMedium = s(t.headlineMedium, 28, 34, FontWeight.Bold),
        headlineSmall = s(t.headlineSmall, 22, 28, FontWeight.Bold),
        titleLarge = s(t.titleLarge, 20, 25, FontWeight.SemiBold),
        titleMedium = s(t.titleMedium, 17, 22, FontWeight.SemiBold),
        titleSmall = s(t.titleSmall, 15, 20, FontWeight.SemiBold),
        bodyLarge = s(t.bodyLarge, 17, 22, FontWeight.Normal),
        bodyMedium = s(t.bodyMedium, 15, 20, FontWeight.Normal),
        bodySmall = s(t.bodySmall, 13, 18, FontWeight.Normal),
        labelLarge = s(t.labelLarge, 15, 20, FontWeight.Medium),
        labelMedium = s(t.labelMedium, 12, 16, FontWeight.Medium),
        labelSmall = s(t.labelSmall, 11, 13, FontWeight.Medium)
    )
}

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(22.dp)
)

object IosShape {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(26.dp)
    val field = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val alert = RoundedCornerShape(30.dp)
    val menu = RoundedCornerShape(22.dp)
    val menuCompact = RoundedCornerShape(14.dp)
    val control = RoundedCornerShape(10.dp)
}

object IosText {
    val zoomSmall = 14.sp
    val zoomLarge = 24.sp
}

object IosBrand {
    val gmail = Color(0xFFEA4335)
}

@Composable
fun menuWidth(base: Dp): Dp {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return androidx.compose.ui.unit.min(base * cfg.fontScale.coerceIn(1f, 1.3f), (cfg.screenWidthDp - 32).dp)
}

@Composable
fun Modifier.liquidGlass(shape: androidx.compose.ui.graphics.Shape = IosShape.pill, raised: Boolean = true): Modifier {
    val ios = LocalIos.current
    val lite = Adaptive.level >= 2
    val fill = remember(ios.dark) {
        Brush.verticalGradient(
            if (ios.dark) listOf(Color(0xD6202022), Color(0xC2161618)) else listOf(Color(0xDBFBFBFD), Color(0xC7EFEFF3))
        )
    }
    val sheen = remember(ios.dark) {
        Brush.verticalGradient(
            0f to (if (ios.dark) Color(0x24FFFFFF) else Color(0x8CFFFFFF)),
            0.5f to Color.Transparent,
            1f to Color.Transparent
        )
    }
    val rim = remember(ios.dark) {
        Brush.verticalGradient(
            0f to (if (ios.dark) Color(0x70FFFFFF) else Color(0xF2FFFFFF)),
            0.5f to (if (ios.dark) Color(0x14FFFFFF) else Color(0x0A000000)),
            1f to (if (ios.dark) Color(0x33FFFFFF) else Color(0x26000000))
        )
    }
    val shadow = if (ios.dark) Color(0x80000000) else Color(0x33000000)
    val lifted = if (lite || !raised) this else this.shadow(7.dp, shape, ambientColor = shadow, spotColor = shadow)
    return lifted
        .clip(shape)
        .background(fill, shape)
        .then(if (lite) Modifier else Modifier.drawBehind { drawRect(sheen) })
        .border(0.75.dp, rim, shape)
}

@Composable
fun NovaTheme(activity: ComponentActivity, content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        )
    }
    val accent = Prefs.accent
    val ios = remember(dark, accent) { (if (dark) IosDark else IosLight).accented() }
    val scheme = remember(dark, accent) { (if (dark) DarkColors else LightColors).copy(primary = ios.blue, tertiary = ios.blue) }
    MaterialTheme(colorScheme = scheme, typography = NovaTypography, shapes = NovaShapes) {
        CompositionLocalProvider(
            LocalIos provides ios,
            LocalContentColor provides ios.label,
            LocalLayoutDirection provides if (I18n.isEnglish()) LayoutDirection.Ltr else LayoutDirection.Rtl
        ) {
            content()
        }
    }
}
