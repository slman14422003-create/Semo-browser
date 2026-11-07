package com.nova.browser

import android.content.*
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi

@Composable
fun FindBar(tab: BrowserTab) {
    val ios = LocalIos.current
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(Modifier.imePadding().fillMaxWidth().background(ios.chrome).iosHairlineTop(ios.separator)) {
        Row(Modifier.navigationBarsPadding().height(52.dp).padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(ios.fill3).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosIcon(IosGlyph.Magnifier, ios.label2, 16.dp)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = q, onValueChange = { q = it; tab.engine?.find(it) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label), cursorBrush = SolidColor(ios.blue),
                    modifier = Modifier.weight(1f).focusRequester(fr),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Text(L("بحث في الصفحة"), style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1)
                            inner()
                        }
                    }
                )
                if (tab.findInfo.isNotEmpty()) Text(tab.findInfo, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 1)
            }
            Box(Modifier.size(40.dp).clickable { tab.engine?.findNext(false) }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.ChevronUp, ios.blue, 22.dp) }
            Box(Modifier.size(40.dp).clickable { tab.engine?.findNext(true) }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.ChevronDown, ios.blue, 22.dp) }
            IosTextButton(L("تم"), { tab.engine?.clearFind(); tab.finding = false }, bold = true)
        }
    }
}

class SitePrompt(val title: String, val message: String, val onAllow: () -> Unit, val onDeny: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.ToolbarBtn(
    kind: IosGlyph, enabled: Boolean, onClick: () -> Unit,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null
) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.weight(1f).fillMaxHeight().combinedClickable(
            interactionSource = src, indication = null, enabled = enabled, onClick = onClick,
            onLongClick = if (menu != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); open = true }) else null
        ),
        contentAlignment = Alignment.Center
    ) {
        val tbTint by animateColorAsState(if (enabled) ios.label else ios.label3, tween(Adaptive.ms(200)), label = "tbTint")
        IosIcon(kind, tbTint, 25.dp, Modifier.pressScale(src, 0.86f))
        if (menu != null) IosMenu(
            expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp, shadowElevation = 12.dp
        ) {
            Column(Modifier.width(260.dp)) { menu({ open = false }) }
        }
    }
}

private fun historyItems(tab: BrowserTab, back: Boolean): List<Pair<String, Int>> {
    val g = tab.engine
    if (g != null) {
        val cur = g.historyIndex
        val hl = g.history
        val rg = if (back) (cur - 1 downTo 0) else (cur + 1 until hl.size)
        return rg.filter { it >= 0 && it < hl.size }.map { i ->
            val e = hl[i]
            (if (e.first.isNotBlank()) e.first else hostOf(e.second)) to (i - cur)
        }.take(12)
    }
    return emptyList()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomPill(
    tab: BrowserTab, tabCount: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit,
    onFind: () -> Unit, onDesktop: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
    onDownloads: () -> Unit, onSwitch: (Int) -> Unit, onSettings: () -> Unit, onTranslate: () -> Unit = {},
    onPrint: () -> Unit = {}, onLibrary: () -> Unit = {}, onCloseCurrent: () -> Unit = {},
    onSummarize: () -> Unit = {}, onAskAi: () -> Unit = {}
) {
    val ios = LocalIos.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val hasPage = tab.url.isNotBlank()
    var query by remember { mutableStateOf("") }
    var fill by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) { if (!editing) { query = ""; fill = null } }

    Column(Modifier.imePadding().fillMaxWidth().then(if (editing) Modifier.background(ios.chrome).iosHairlineTop(ios.separator) else Modifier)) {
        Column(Modifier.navigationBarsPadding().animateContentSize(tween(Adaptive.ms(240), easing = NovaMotion.emphasized))) {
            if (editing) SuggestionsPanel(
                query = query,
                onPick = { onGo(it); setEditing(false); focus.clearFocus() },
                onFill = { fill = it }
            )
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    (fadeIn(tween(Adaptive.ms(200), delayMillis = Adaptive.ms(40))) + scaleIn(tween(Adaptive.ms(260), easing = NovaMotion.emphasized), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(Adaptive.ms(110)))
                },
                label = "bar"
            ) { isEditing ->
                if (isEditing) {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        var field by remember { mutableStateOf(TextFieldValue(tab.url, TextRange(0, tab.url.length))) }
                        val fr = remember { FocusRequester() }
                        var got by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { fr.requestFocus() }
                        LaunchedEffect(fill) { fill?.let { f -> field = TextFieldValue(f, TextRange(f.length)); query = f; fill = null } }
                        Row(
                            Modifier.weight(1f).height(40.dp).clip(IosShape.pill).background(ios.fill3).padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IosIcon(IosGlyph.Magnifier, ios.label2, 17.dp)
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = field, onValueChange = { field = it; query = it.text }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label), cursorBrush = SolidColor(ios.blue),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = {
                                    if (field.text.isNotBlank()) onGo(field.text.trim())
                                    setEditing(false); focus.clearFocus()
                                }),
                                modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) got = true else if (got) setEditing(false) },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (field.text.isEmpty()) Text(L("ابحث أو اكتب عنوان"), style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1)
                                        inner()
                                    }
                                }
                            )
                            if (field.text.isNotEmpty()) Box(
                                Modifier.size(32.dp).clickable { field = TextFieldValue(""); query = "" }, contentAlignment = Alignment.Center
                            ) { IosIcon(IosGlyph.XCircleFill, ios.label3, 18.dp, accent = if (ios.dark) Color(0xFF3A3A3C) else Color.White) }
                            else Text(
                                L("لصق"), style = MaterialTheme.typography.bodyMedium, color = ios.blue,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    val t = runCatching {
                                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty().trim()
                                    }.getOrDefault("")
                                    if (t.isNotEmpty()) { field = TextFieldValue(t, TextRange(t.length)); query = t }
                                }.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        IosTextButton(L("إلغاء"), { setEditing(false); focus.clearFocus() })
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = if (tab.loading) tab.progress.coerceIn(0.05f, 1f) else 0f,
                                animationSpec = tween(180, easing = FastOutSlowInEasing), label = "loadBar"
                            )
                            Box(
                                Modifier.fillMaxSize().liquidGlass()
                                    .pointerInput(Unit) {
                                        var dx = 0f; var dy = 0f
                                        detectDragGestures(
                                            onDragStart = { dx = 0f; dy = 0f },
                                            onDrag = { _, a -> dx += a.x; dy += a.y },
                                            onDragEnd = {
                                                if (dy < -60f && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTabs()
                                                } else if (kotlin.math.abs(dx) > 120f) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSwitch(if (dx < 0) 1 else -1)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                    if (hasPage) Box(Modifier.width(44.dp).fillMaxHeight().clickable { pageMenu = true }, contentAlignment = Alignment.Center) {
                                        Row {
                                            Text("A", fontSize = 12.sp, color = ios.label, modifier = Modifier.alignByBaseline())
                                            Text("A", fontSize = 19.sp, color = ios.label, modifier = Modifier.alignByBaseline())
                                        }
                                        PageMenu(tab, pageMenu, { pageMenu = false }, onDesktop, onTranslate, onDownloads, onSettings, onSummarize, onAskAi)
                                    } else Spacer(Modifier.width(44.dp))
                                    Row(
                                        Modifier.weight(1f).fillMaxHeight().clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null
                                        ) { setEditing(true) },
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                                    ) {
                                        val insecure = tab.url.startsWith("http://")
                                        if (hasPage) {
                                            if (insecure) Icon(Icons.Default.Warning, L("اتصال غير مشفّر"), Modifier.size(13.dp), tint = ios.red)
                                            else if (tab.url.startsWith("https")) IosIcon(IosGlyph.Lock, ios.label2, 12.dp)
                                            Spacer(Modifier.width(5.dp))
                                        } else {
                                            IosIcon(IosGlyph.Magnifier, ios.label2, 15.dp)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            if (!hasPage) L("ابحث أو اكتب عنوان") else hostOf(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge, color = if (hasPage) ios.label else ios.label2
                                        )
                                    }
                                    val dlActive = Downloader.tasks.count { it.status == Downloader.DOWNLOADING || it.status == Downloader.PREPARING }
                                    if (hasPage && dlActive > 0) Box(
                                        Modifier.width(36.dp).fillMaxHeight().clickable { onDownloads() }, contentAlignment = Alignment.Center
                                    ) { IosIcon(IosGlyph.Download, ios.blue, 19.dp) }
                                    if (hasPage) Box(
                                        Modifier.width(44.dp).fillMaxHeight().clickable { if (tab.loading) tab.engine?.stop() else tab.engine?.reload() },
                                        contentAlignment = Alignment.Center
                                    ) { IosIcon(if (tab.loading) IosGlyph.Close else IosGlyph.Reload, ios.label, 17.dp) } else Spacer(Modifier.width(44.dp))
                                }
                                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(animatedProgress).height(2.dp).background(ios.blue))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp).height(52.dp).liquidGlass(), verticalAlignment = Alignment.CenterVertically) {
                            ToolbarBtn(
                                IosGlyph.Back, tab.canBack || hasPage, { if (tab.canBack) tab.engine?.back() else onHome() },
                                menu = if (tab.canBack) ({ close ->
                                    val items = historyItems(tab, true)
                                    items.forEachIndexed { n, p -> IosCell(p.first, { close(); tab.engine?.goRelative(p.second) }, divider = n < items.lastIndex) }
                                }) else null
                            )
                            ToolbarBtn(
                                IosGlyph.Forward, tab.canForward, { tab.engine?.forward() },
                                menu = if (tab.canForward) ({ close ->
                                    val items = historyItems(tab, false)
                                    items.forEachIndexed { n, p -> IosCell(p.first, { close(); tab.engine?.goRelative(p.second) }, divider = n < items.lastIndex) }
                                }) else null
                            )
                            ToolbarBtn(IosGlyph.Share, hasPage, { sheet = true })
                            ToolbarBtn(IosGlyph.Book, true, { onLibrary() })
                            ToolbarBtn(IosGlyph.Tabs, true, { onTabs() }, menu = { close ->
                                IosCell(L("تبويب جديد"), { close(); onNewTab() })
                                IosCell(L("إغلاق التبويب"), { close(); onCloseCurrent() }, tint = ios.red, divider = false)
                            })
                        }
                    }
                }
            }
        }
    }
    if (sheet) MenuSheet(tab, { sheet = false }, onNewTab, onFind, onShare, onCopy, onPrint)
}

@Composable
fun RoundBtn(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(src, 0.88f).alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides ios.blue) { content() } }
}
