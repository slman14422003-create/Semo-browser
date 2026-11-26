package com.nova.browser

import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch

@Composable
fun FindBar(tab: BrowserTab) {
    val ios = LocalIos.current
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(Modifier.imePadding().fillMaxWidth().background(ios.chrome).iosHairlineTop(ios.separator)) {
        Row(Modifier.navigationBarsPadding().height(52.dp).padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(36.dp).clip(IosShape.control).background(ios.fill3).padding(horizontal = 8.dp),
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
            Box(Modifier.size(44.dp).tapClickable { tab.engine?.findNext(false) }.iosButton(S(R.string.a11y_find_prev)), contentAlignment = Alignment.Center) { IosIcon(IosGlyph.ChevronUp, ios.blue, 22.dp) }
            Box(Modifier.size(44.dp).tapClickable { tab.engine?.findNext(true) }.iosButton(S(R.string.a11y_find_next)), contentAlignment = Alignment.Center) { IosIcon(IosGlyph.ChevronDown, ios.blue, 22.dp) }
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
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.weight(1f).fillMaxHeight().tapCombinedClickable(
            interactionSource = src, indication = null, enabled = enabled, onClick = onClick,
            onLongClick = if (menu != null) ({ open = true }) else null
        ),
        contentAlignment = Alignment.Center
    ) {
        val tbTint by animateColorAsState(if (enabled) ios.label else ios.label3, Motion.timed(Motion.SHORT), label = "tbTint")
        IosIcon(kind, tbTint, 25.dp, Modifier.pressScale(src, 0.86f))
        if (menu != null) IosMenu(
            expanded = open, onDismissRequest = { open = false }, shape = IosShape.menu,
            tonalElevation = 0.dp, shadowElevation = 12.dp
        ) {
            Column(Modifier.width(menuWidth(260.dp))) { menu({ open = false }) }
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
private fun BarButton(
    enabled: Boolean, onClick: () -> Unit, label: String?,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val haptic = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.width(44.dp).fillMaxHeight().pressScale(src, 0.88f).alpha(if (enabled) 1f else 0.4f).iosButton(label)
            .tapCombinedClickable(
                interactionSource = src, indication = null, enabled = enabled, onClick = onClick,
                onLongClick = if (menu != null) ({ open = true }) else null
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (menu != null) IosMenu(
            expanded = open, onDismissRequest = { open = false }, shape = IosShape.menu,
            tonalElevation = 0.dp, shadowElevation = 12.dp
        ) {
            Column(Modifier.width(menuWidth(260.dp))) { menu({ open = false }) }
        }
    }
}

@Composable
private fun SiteInfoContent(tab: BrowserTab, close: () -> Unit) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    val host = hostOf(tab.url)
    var rev by remember { mutableIntStateOf(0) }
    val jsOn = remember(rev, host) { SiteStore.jsOn(host) }
    val custom = remember(rev, host) { SiteStore.get(host).custom }
    val insecure = tab.url.startsWith("http://")
    Column(Modifier.width(menuWidth(280.dp))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (insecure) IosIcon(IosGlyph.Warning, ios.red, 16.dp) else IosIcon(IosGlyph.Lock, ios.label2, 14.dp)
            Spacer(Modifier.width(10.dp))
            Text(host, style = MaterialTheme.typography.titleSmall, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IosDivider()
        IosCell(
            S(R.string.site_js),
            { SiteStore.toggleJs(host); SiteStore.apply(tab, host); rev++ },
            trailing = { IosSwitch(checked = jsOn, onCheckedChange = null) }
        )
        IosCell(
            S(R.string.site_desktop),
            { SiteStore.toggleDesktop(host, tab.desktop); SiteStore.apply(tab, host); rev++ },
            trailing = { IosSwitch(checked = tab.desktop, onCheckedChange = null) }
        )
        IosCell(
            S(R.string.site_clear),
            { SiteStore.clearData(host); tab.engine?.reload(); toast(ctx, S(R.string.site_cleared)); close() },
            divider = custom
        )
        if (custom) IosCell(
            S(R.string.site_reset),
            { SiteStore.reset(host); SiteStore.apply(tab, host); toast(ctx, S(R.string.site_reset_done)); close() },
            divider = false
        )
    }
}

private fun resist(distance: Float, limit: Float): Float = limit * (1f - 1f / (1f + distance / limit))

@Composable
private fun RowScope.AddressSlot(
    tab: BrowserTab, hasPage: Boolean, tabIndex: Int, tabCount: Int,
    onEdit: () -> Unit, onTabs: () -> Unit, onSwitch: (Int) -> Unit, onDownloads: () -> Unit
) {
    val ios = LocalIos.current
    val haptic = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var slotW by remember { mutableIntStateOf(0) }
    var siteInfo by remember { mutableStateOf(false) }
    val tid = tab.id
    val fill = remember(tid) { Animatable(0f) }
    val fade = remember(tid) { Animatable(0f) }
    LaunchedEffect(tid) {
        snapshotFlow { tab.loading to tab.progress }.collectLatest { (loading, progress) ->
            if (loading) {
                if (fade.value == 0f) fill.snapTo(0f)
                fade.snapTo(1f)
                fill.animateTo(progress.coerceIn(0.05f, 1f), Motion.timed(Motion.SHORT))
            } else {
                fill.animateTo(1f, Motion.timed(Motion.QUICK))
                fade.animateTo(0f, Motion.timed(Motion.MEDIUM))
            }
        }
    }
    val fillColor = ios.blue
    val roomy = slotW >= with(density) { 150.dp.toPx() }
    val insecure = tab.url.startsWith("http://")
    val secure = tab.url.startsWith("https")
    val dlActive by remember { derivedStateOf { Downloader.tasks.count { it.status == Downloader.DOWNLOADING || it.status == Downloader.PREPARING } } }
    val settle = Motion.soft<Float>()

    Box(
        Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp).clipToBounds().onSizeChanged { slotW = it.width }
    ) {
        Row(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .clip(IosShape.pill).background(ios.fill3)
                .drawBehind {
                    val w = size.width * fill.value
                    if (w > 0f && fade.value > 0f) drawRect(
                        fillColor.copy(alpha = 0.18f * fade.value),
                        Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f, 0f),
                        Size(w, size.height)
                    )
                }
                .pointerInput(tabIndex, tabCount) {
                    val threshold = 48.dp.toPx()
                    val upThreshold = 24.dp.toPx()
                    var dx = 0f
                    var dy = 0f
                    var decided = false
                    var horizontal = false
                    var crossed = false
                    detectDragGestures(
                        onDragStart = {
                            dx = 0f; dy = 0f; decided = false; horizontal = false; crossed = false
                            scope.launch { offset.stop() }
                        },
                        onDrag = { change, a ->
                            dx += a.x; dy += a.y
                            if (!decided && kotlin.math.abs(dx) + kotlin.math.abs(dy) > 6f) {
                                decided = true
                                horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                            }
                            if (horizontal) {
                                change.consume()
                                val dir = if (dx < 0) 1 else -1
                                val hasNext = tabIndex + dir in 0 until tabCount
                                val w = size.width.toFloat()
                                val shown = resist(kotlin.math.abs(dx), if (hasNext) w * 0.6f else w * 0.16f)
                                scope.launch { offset.snapTo(if (dx < 0) -shown else shown) }
                                val now = hasNext && kotlin.math.abs(dx) > threshold
                                if (now && !crossed) haptic.tick()
                                crossed = now
                            }
                        },
                        onDragEnd = {
                            if (!horizontal) {
                                if (dy < -upThreshold && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                    haptic.confirm()
                                    onTabs()
                                }
                            } else if (crossed) {
                                val dir = if (dx < 0) 1 else -1
                                val w = size.width.toFloat()
                                scope.launch {
                                    offset.animateTo(-dir * w, Motion.timed(UiLayout.SWIPE_OUT_MS, easing = Motion.emphasizedAccelerate))
                                    onSwitch(dir)
                                    offset.snapTo(dir * w)
                                    offset.animateTo(0f, Motion.timed(UiLayout.SWIPE_IN_MS, easing = Motion.emphasized))
                                }
                            } else scope.launch { offset.animateTo(0f, settle) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, settle) } }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp).tapClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit
                ),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                if (hasPage && (insecure || secure)) Box(
                    Modifier.width(30.dp).fillMaxHeight().tapClickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = androidx.compose.ui.semantics.Role.Button
                    ) { siteInfo = true }.iosLabel(S(R.string.a11y_site_info)),
                    contentAlignment = Alignment.Center
                ) {
                    if (insecure) IosIcon(IosGlyph.Warning, ios.red, 14.dp) else IosIcon(IosGlyph.Lock, ios.label2, 12.dp)
                    IosMenu(
                        expanded = siteInfo, onDismissRequest = { siteInfo = false }, shape = IosShape.menu,
                        tonalElevation = 0.dp, shadowElevation = 12.dp
                    ) { SiteInfoContent(tab) { siteInfo = false } }
                } else if (!hasPage) {
                    IosIcon(IosGlyph.Magnifier, ios.label2, 15.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (!hasPage) S(R.string.bar_hint) else hostOf(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge, color = if (hasPage) ios.label else ios.label2
                )
            }
            if (hasPage && dlActive > 0 && roomy) Box(
                Modifier.width(36.dp).fillMaxHeight().tapClickable { onDownloads() }.iosButton(S(R.string.a11y_downloads)), contentAlignment = Alignment.Center
            ) { IosIcon(IosGlyph.Download, ios.blue, 19.dp) }
            if (hasPage) Box(
                Modifier.width(38.dp).fillMaxHeight().tapClickable { if (tab.loading) tab.engine?.stop() else tab.engine?.reload() }
                    .iosButton(if (tab.loading) S(R.string.a11y_stop) else S(R.string.a11y_reload)),
                contentAlignment = Alignment.Center
            ) { IosIcon(if (tab.loading) IosGlyph.Close else IosGlyph.Reload, ios.label, 17.dp) }
            else Spacer(Modifier.width(38.dp))
        }
    }
}

@Composable
private fun BarCapsule(
    tab: BrowserTab, tabCount: Int, tabIndex: Int, hasPage: Boolean, modifier: Modifier,
    onHome: () -> Unit, onTabs: () -> Unit, onSwitch: (Int) -> Unit, onEdit: () -> Unit,
    onDownloads: () -> Unit, onMenu: () -> Unit, menuHost: @Composable () -> Unit
) {
    val ios = LocalIos.current
    val home = hasPage && !tab.canBack
    val glow = remember { GlowState() }
    fun lit(key: String, action: () -> Unit): () -> Unit = { glow.active = key; action() }
    Row(
        modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp).height(48.dp)
            .liquidGlass().glowHost(glow, ios.blue).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(
            enabled = tab.canBack || hasPage, modifier = Modifier.glowAnchor(glow, "back"), onClick = lit("back") { if (tab.canBack) tab.engine?.back() else onHome() },
            label = if (home) S(R.string.a11y_home) else S(R.string.a11y_back),
            menu = if (tab.canBack) ({ close ->
                val items = historyItems(tab, true)
                items.forEachIndexed { n, p -> IosCell(p.first, { close(); tab.engine?.goRelative(p.second) }, divider = n < items.lastIndex) }
            }) else null
        ) {
            Crossfade(targetState = home, animationSpec = Motion.timed(Motion.SHORT), label = "backHome") { h ->
                IosIcon(if (h) IosGlyph.Home else IosGlyph.Back, ios.label, 22.dp)
            }
        }
        AnimatedVisibility(
            visible = tab.canForward,
            enter = expandHorizontally(Motion.timed(Motion.SHORT, easing = Motion.emphasized)) + fadeIn(Motion.timed(Motion.SHORT)),
            exit = shrinkHorizontally(Motion.timed(Motion.SHORT)) + fadeOut(Motion.timed(Motion.QUICK))
        ) {
            BarButton(
                enabled = true, modifier = Modifier.glowAnchor(glow, "forward"), onClick = lit("forward") { tab.engine?.forward() }, label = S(R.string.a11y_forward),
                menu = { close ->
                    val items = historyItems(tab, false)
                    items.forEachIndexed { n, p -> IosCell(p.first, { close(); tab.engine?.goRelative(p.second) }, divider = n < items.lastIndex) }
                }
            ) { IosIcon(IosGlyph.Forward, ios.label, 22.dp) }
        }
        AddressSlot(tab, hasPage, tabIndex, tabCount, onEdit, onTabs, onSwitch, onDownloads)
        BarButton(enabled = true, modifier = Modifier.glowAnchor(glow, "tabs"), onClick = lit("tabs", onTabs), label = S(R.string.a11y_tabs) + " " + tabCount) {
            IosIcon(IosGlyph.Tabs, ios.label, 24.dp)
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 5.dp).defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(ios.blue).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (tabCount > 99) "99+" else tabCount.toString(), style = MaterialTheme.typography.labelSmall,
                    color = Color.White, maxLines = 1
                )
            }
        }
        BarButton(enabled = true, modifier = Modifier.glowAnchor(glow, "menu"), onClick = lit("menu", onMenu), label = S(R.string.a11y_menu)) {
            IosIcon(IosGlyph.Ellipsis, ios.label, 22.dp)
            menuHost()
        }
    }
}

@Composable
private fun CompactPill(tab: BrowserTab, modifier: Modifier) {
    val ios = LocalIos.current
    val insecure = tab.url.startsWith("http://")
    val secure = tab.url.startsWith("https")
    Row(
        modifier.height(UiLayout.COMPACT_PILL_DP.dp).widthIn(max = 280.dp).liquidGlass()
            .tapClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab.setCollapsed(false) }
            .iosButton(S(R.string.a11y_expand_bar)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (insecure) IosIcon(IosGlyph.Warning, ios.red, 13.dp) else if (secure) IosIcon(IosGlyph.Lock, ios.label2, 11.dp)
        if (insecure || secure) Spacer(Modifier.width(5.dp))
        Text(hostOf(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = ios.label)
    }
}

@Composable
private fun EditCard(
    tab: BrowserTab, query: String, fill: String?,
    onQuery: (String) -> Unit, onFill: (String?) -> Unit, onGo: (String) -> Unit,
    setEditing: (Boolean) -> Unit, close: () -> Unit
) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    var field by remember { mutableStateOf(TextFieldValue(tab.url, TextRange(0, tab.url.length))) }
    val fr = remember { FocusRequester() }
    var got by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(fill) { fill?.let { f -> field = TextFieldValue(f, TextRange(f.length)); onQuery(f); onFill(null) } }
    Column(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)
            .liquidGlass(IosShape.card).animateContentSize(Motion.timed(Motion.MEDIUM, easing = Motion.emphasized))
    ) {
        SuggestionsPanel(
            query = query,
            onPick = { onGo(it); close() },
            onFill = { onFill(it) },
            glass = true
        )
        Row(Modifier.fillMaxWidth().height(52.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.fillMaxSize().clip(IosShape.pill).background(ios.fill3).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosIcon(IosGlyph.Magnifier, ios.label2, 17.dp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = field, onValueChange = { field = it; onQuery(it.text) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label), cursorBrush = SolidColor(ios.blue),
                    keyboardOptions = KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        if (field.text.isNotBlank()) onGo(field.text.trim())
                        close()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) got = true else if (got) setEditing(false) },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) Text(S(R.string.bar_hint), style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1)
                            inner()
                        }
                    }
                )
                if (field.text.isEmpty()) Text(
                    S(R.string.bar_paste), style = MaterialTheme.typography.bodyMedium, color = ios.blue,
                    modifier = Modifier.fillMaxHeight().clip(IosShape.control).tapClickable(role = androidx.compose.ui.semantics.Role.Button) {
                        val t = runCatching {
                            (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty().trim()
                        }.getOrDefault("")
                        if (t.isNotEmpty()) { field = TextFieldValue(t, TextRange(t.length)); onQuery(t) }
                    }.padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically)
                )
                Box(
                    Modifier.width(44.dp).fillMaxHeight().tapClickable {
                        if (field.text.isNotEmpty()) { field = TextFieldValue(""); onQuery("") } else close()
                    }.iosButton(if (field.text.isNotEmpty()) S(R.string.a11y_clear_text) else S(R.string.a11y_cancel_edit)),
                    contentAlignment = Alignment.Center
                ) { IosIcon(IosGlyph.XCircleFill, ios.label3, 18.dp, accent = ios.clearGlyph) }
            }
        }
    }
}

@Composable
fun BottomPill(
    tab: BrowserTab, tabCount: Int, tabIndex: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit,
    onFind: () -> Unit, onDesktop: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
    onDownloads: () -> Unit, onSwitch: (Int) -> Unit, onSettings: () -> Unit, onTranslate: () -> Unit = {},
    onPrint: () -> Unit = {}, onLibrary: () -> Unit = {}, onCloseCurrent: () -> Unit = {},
    onSummarize: () -> Unit = {}, onAskAi: () -> Unit = {}
) {
    val focus = LocalFocusManager.current
    var sheet by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val hasPage = tab.url.isNotBlank()
    var query by remember { mutableStateOf("") }
    var fill by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) {
        if (editing) {
            tab.barCollapsed = false
        } else {
            query = ""
            fill = null
        }
    }
    val collapse = animateFloatAsState(
        if (tab.barIsCompact(editing)) 1f else 0f,
        Motion.timed(UiLayout.COLLAPSE_ANIM_MS, easing = Motion.emphasized), label = "barCollapse"
    )
    val showFull by remember { derivedStateOf { collapse.value < 0.99f } }
    val showCompact by remember { derivedStateOf { collapse.value > 0.01f } }
    val close = { setEditing(false); focus.clearFocus() }

    Column(Modifier.imePadding().fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().animateContentSize(Motion.timed(Motion.MEDIUM, easing = Motion.emphasized))) {
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    (fadeIn(Motion.timed(Motion.SHORT, delayMillis = Motion.STAGGER)) + scaleIn(Motion.timed(Motion.MEDIUM, easing = Motion.emphasized), initialScale = 0.97f)) togetherWith
                        fadeOut(Motion.timed(Motion.QUICK))
                },
                label = "bar"
            ) { isEditing ->
                if (isEditing) EditCard(
                    tab = tab, query = query, fill = fill, onQuery = { query = it }, onFill = { fill = it },
                    onGo = onGo, setEditing = setEditing, close = close
                ) else Box(Modifier.fillMaxWidth().height(UiLayout.BAR_H.dp)) {
                    if (showFull) BarCapsule(
                        tab = tab, tabCount = tabCount, tabIndex = tabIndex, hasPage = hasPage,
                        modifier = Modifier.graphicsLayer {
                            val c = collapse.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            alpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                            translationY = c * 14.dp.toPx()
                            scaleX = 1f - 0.16f * c
                            scaleY = 1f - 0.16f * c
                        },
                        onHome = onHome, onTabs = onTabs, onSwitch = onSwitch, onEdit = { setEditing(true) },
                        onDownloads = onDownloads, onMenu = { pageMenu = true },
                        menuHost = {
                            PageMenu(
                                tab, pageMenu, { pageMenu = false }, onDesktop, onTranslate, onDownloads, onSettings, onSummarize, onAskAi,
                                onForward = { tab.engine?.forward() }, onShare = { sheet = true }, onLibrary = onLibrary, onTabs = onTabs, onNewTab = onNewTab
                            )
                        }
                    )
                    if (showCompact) CompactPill(
                        tab, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).graphicsLayer {
                            val c = collapse.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            alpha = ((c - 0.3f) / 0.7f).coerceIn(0f, 1f)
                            translationY = (1f - c) * 10.dp.toPx()
                        }
                    )
                }
            }
        }
    }
    if (sheet) MenuSheet(tab, { sheet = false }, onNewTab, onFind, onShare, onCopy, onPrint)
}

@Composable
fun RoundBtn(onClick: () -> Unit, enabled: Boolean = true, label: String? = null, content: @Composable () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(src, 0.88f).alpha(if (enabled) 1f else 0.4f).iosLabel(label)
            .tapClickable(interactionSource = src, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides ios.blue) { content() } }
}
