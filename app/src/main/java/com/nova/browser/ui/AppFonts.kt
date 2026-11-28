package com.nova.browser

import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

object AppFonts {
    private val families = listOf("sans-serif", "serif", "sans-serif-condensed", "monospace", "casual")
    val names = listOf(R.string.font_system, R.string.font_serif, R.string.font_condensed, R.string.font_mono, R.string.font_casual)
    val last = families.lastIndex
    private val cache = HashMap<Int, FontFamily>()

    fun family(index: Int): FontFamily = if (index <= 0 || index > last) FontFamily.SansSerif else cache.getOrPut(index) {
        FontFamily(Typeface.create(families[index], Typeface.NORMAL))
    }

    fun apply(t: Typography, index: Int): Typography {
        val f = family(index)
        return t.copy(
            displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
            displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
            headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
            titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
            titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
            bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
            labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
            labelSmall = t.labelSmall.copy(fontFamily = f)
        )
    }
}

@Composable
fun FontPage() {
    val ios = LocalIos.current
    IosCard {
        Column(Modifier.padding(UiLayout.dp16), verticalArrangement = Arrangement.spacedBy(UiLayout.dp8)) {
            Text(S(R.string.font_sample_en), style = MaterialTheme.typography.titleLarge, color = ios.label)
            Text(S(R.string.font_sample_ar), style = MaterialTheme.typography.titleLarge, color = ios.label)
            Text("0123456789  https://example.com", style = MaterialTheme.typography.bodyMedium, color = ios.label2)
        }
    }
    SettingsHeader(S(R.string.set_font))
    IosCard {
        AppFonts.names.forEachIndexed { i, id ->
            IosCell(
                S(id), { Prefs.pickFont(i) }, divider = i < AppFonts.last,
                trailing = if (Prefs.font == i) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null
            )
        }
    }
    SettingsNote(S(R.string.font_note))
}
