package com.nova.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay

object Bilingual {
    private val reverse by lazy { I18n.en.entries.associate { it.value to it.key } }
    private val resources by lazy {
        val out = HashMap<String, String>()
        listOf(
            R.string.set_appearance, R.string.set_appearance_sub, R.string.set_accent, R.string.set_effects, R.string.set_effects_sub,
            R.string.glass_quality, R.string.glass_lowered, R.string.https_first_sub, R.string.x_zoom_title, R.string.x_zoom_sub,
            R.string.x_copy_title, R.string.x_copy_sub, R.string.x_draft_title, R.string.x_draft_sub, R.string.smart_settings_title,
            R.string.smart_settings_sub, R.string.set_haptics, R.string.set_haptics_sub, R.string.set_font, R.string.set_start,
            R.string.set_storage, R.string.set_storage_sub, R.string.font_system, R.string.font_serif, R.string.font_condensed,
            R.string.font_mono, R.string.font_casual, R.string.start_fav, R.string.start_ai, R.string.start_privacy,
            R.string.start_shortcuts, R.string.start_recent, R.string.start_downloads, R.string.storage_cache,
            R.string.storage_downloads, R.string.storage_sites
        ).forEach { id ->
            val (en, ar) = Strings.both(id)
            out[en] = ar
            out[ar] = en
        }
        out
    }

    fun other(text: String): String? = I18n.en[text] ?: reverse[text] ?: resources[text]
}

class SettingsHit(val shown: String, val alternate: String?)

fun settingsHit(title: String, sub: String?, query: String): SettingsHit? {
    if (TextMatch.find(title, query) != null || (sub != null && TextMatch.find(sub, query) != null)) return SettingsHit(title, null)
    val alternate = listOfNotNull(Bilingual.other(title), sub?.let { Bilingual.other(it) }).firstOrNull { TextMatch.find(it, query) != null }
    return alternate?.let { SettingsHit(title, it) }
}

@Composable
private fun marked(text: String, query: String): AnnotatedString {
    val range = TextMatch.find(text, query) ?: return AnnotatedString(text)
    val ios = LocalIos.current
    return buildAnnotatedString {
        append(text.substring(0, range[0]))
        withStyle(SpanStyle(color = ios.blue, background = ios.blue.copy(alpha = UiLayout.HIGHLIGHT_ALPHA))) { append(text.substring(range[0], range[1])) }
        append(text.substring(range[1]))
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = LocalIos.current.label2,
        modifier = Modifier.padding(start = UiLayout.dp32, end = UiLayout.dp32, top = UiLayout.dp18, bottom = UiLayout.dp6)
    )
}

@Composable
fun SettingsNote(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = LocalIos.current.label2,
        modifier = Modifier.padding(start = UiLayout.dp32, end = UiLayout.dp32, top = UiLayout.dp6)
    )
}

@Composable
fun SettingsResult(hit: SettingsHit, sub: String?, query: String, divider: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    Column(Modifier.fillMaxWidth().tapClickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().heightIn(min = UiLayout.dp44).padding(horizontal = UiLayout.dp16, vertical = UiLayout.dp8)) {
            Text(marked(hit.shown, query), style = MaterialTheme.typography.bodyLarge, color = ios.label)
            if (sub != null) Text(marked(sub, query), style = MaterialTheme.typography.bodySmall, color = ios.label2)
            if (hit.alternate != null) Text(marked(hit.alternate, query), style = MaterialTheme.typography.bodySmall, color = ios.label2)
        }
        if (divider) IosDivider(UiLayout.dp16)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun settingsTarget(active: Boolean, onShown: () -> Unit): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val ios = LocalIos.current
    val color by animateColorAsState(
        if (active) ios.blue.copy(alpha = UiLayout.HIGHLIGHT_ALPHA) else Color.Transparent, Motion.timed(Motion.MEDIUM), label = "target"
    )
    LaunchedEffect(active) {
        if (active) {
            delay(UiLayout.JUMP_WAIT_MS)
            requester.bringIntoView()
            delay(UiLayout.JUMP_HOLD_MS)
            onShown()
        }
    }
    return Modifier.bringIntoViewRequester(requester).background(color)
}

fun startSectionName(id: String): String = S(
    when (id) {
        "fav" -> R.string.start_fav
        "ai" -> R.string.start_ai
        "privacy" -> R.string.start_privacy
        "shortcuts" -> R.string.start_shortcuts
        "recent" -> R.string.start_recent
        else -> R.string.start_downloads
    }
)

@Composable
fun StartLayoutPage() {
    val ios = LocalIos.current
    val tokens = StartLayout.parse(Prefs.startLayout)
    SettingsHeader(S(R.string.set_start))
    IosCard {
        tokens.forEachIndexed { i, token ->
            val name = startSectionName(StartLayout.id(token))
            IosCell(
                name, { Prefs.pickStartLayout(StartLayout.toggle(Prefs.startLayout, i)) }, divider = i < tokens.lastIndex, toggle = true,
                trailing = {
                    IosTextButton("↑", { Prefs.pickStartLayout(StartLayout.move(Prefs.startLayout, i, -1)) }, enabled = i > 0)
                    IosTextButton("↓", { Prefs.pickStartLayout(StartLayout.move(Prefs.startLayout, i, 1)) }, enabled = i < tokens.lastIndex)
                    IosSwitch(checked = StartLayout.visible(token))
                }
            )
        }
    }
    SettingsNote(S(R.string.start_note))
    IosCard(Modifier.padding(top = UiLayout.dp18)) {
        IosCell(S(R.string.start_reset), { Prefs.pickStartLayout(StartLayout.DEFAULT) }, divider = false, tint = ios.blue)
    }
}
