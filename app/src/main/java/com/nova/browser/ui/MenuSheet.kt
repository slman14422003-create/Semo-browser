package com.nova.browser

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign

@Composable
private fun ShareHeader(tab: BrowserTab, onClose: () -> Unit) {
    val ios = LocalIos.current
    val host = hostOf(tab.url)
    val fvTick = Favicons.watch(host)
    val ic = remember(host, fvTick) { Favicons.get(host) }
    Row(Modifier.fillMaxWidth().padding(start = UiLayout.dp16, end = UiLayout.dp12, top = UiLayout.dp4, bottom = UiLayout.dp12), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(UiLayout.dp44).clip(RoundedCornerShape(UiLayout.dp10)).background(ios.cell), contentAlignment = Alignment.Center) {
            if (ic != null) Image(remember(ic) { ic.asImageBitmap() }, null, Modifier.size(UiLayout.dp28).clip(RoundedCornerShape(UiLayout.dp6)))
            else Text(host.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ios.label2)
        }
        Spacer(Modifier.width(UiLayout.dp12))
        Column(Modifier.weight(1f)) {
            Text(tab.title.ifBlank { host }, style = MaterialTheme.typography.titleSmall, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(UiLayout.dp8))
        Box(Modifier.size(UiLayout.dp30).clip(CircleShape).background(ios.fill2).tapClickable(onClick = onClose), contentAlignment = Alignment.Center) {
            IosIcon(IosGlyph.Close, ios.label2, UiLayout.dp13, weight = 1.2f)
        }
    }
}

private class ShareTarget(val label: String, val icon: android.graphics.Bitmap?, val pkg: String, val cls: String)

private var shareCache: List<ShareTarget> = emptyList()

private fun appIconBitmap(d: android.graphics.drawable.Drawable, sz: Int): android.graphics.Bitmap {
    val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    d.setBounds(0, 0, sz, sz); d.draw(c)
    return b
}

/** صف تطبيقات المشاركة بتصميم Nova بدل نافذة المشاركة الخاصة بالنظام. */
@Composable
private fun ShareApps(url: String, title: String, act: (() -> Unit) -> Unit) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    val targets by produceState(shareCache, url) {
        value = kotlinx.coroutines.withContext(Work.ioD) {
            val pm = ctx.packageManager
            val probe = Intent(Intent.ACTION_SEND).setType("text/plain")
            runCatching {
                pm.queryIntentActivities(probe, 0)
                    .filter { it.activityInfo.packageName != ctx.packageName }
                    .map { ShareTarget(it.loadLabel(pm).toString(), runCatching { appIconBitmap(it.loadIcon(pm), 128) }.getOrNull(), it.activityInfo.packageName, it.activityInfo.name) }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
        shareCache = value
    }
    LazyRow(Modifier.fillMaxWidth().padding(bottom = UiLayout.dp16), contentPadding = PaddingValues(horizontal = UiLayout.dp12), horizontalArrangement = Arrangement.spacedBy(UiLayout.dp4)) {
        items(targets, key = { it.pkg + "/" + it.cls }) { t ->
            Column(
                Modifier.width(UiLayout.dp72).clip(RoundedCornerShape(UiLayout.dp10)).tapClickable {
                    act {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
                                    .setClassName(t.pkg, t.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }.padding(vertical = UiLayout.dp4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(UiLayout.dp56).clip(RoundedCornerShape(UiLayout.dp18)).background(ios.cell), contentAlignment = Alignment.Center) {
                    val ic = t.icon
                    if (ic != null) Image(remember(ic) { ic.asImageBitmap() }, null, Modifier.fillMaxSize())
                    else Text(t.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ios.label2)
                }
                Spacer(Modifier.height(UiLayout.dp5))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = ios.label2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        item {
            Column(
                Modifier.width(UiLayout.dp72).clip(RoundedCornerShape(UiLayout.dp10)).tapClickable { act { shareText(ctx, url) } }.padding(vertical = UiLayout.dp4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(UiLayout.dp56).clip(RoundedCornerShape(UiLayout.dp18)).background(ios.cell), contentAlignment = Alignment.Center) {
                    IosIcon(IosGlyph.Ellipsis, ios.label, UiLayout.dp24)
                }
                Spacer(Modifier.height(UiLayout.dp5))
                Text(L("المزيد"), style = MaterialTheme.typography.labelSmall, color = ios.label2, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    tab: BrowserTab, onDismiss: () -> Unit, onNewTab: () -> Unit, onFind: () -> Unit,
    onShare: () -> Unit, onCopy: () -> Unit, onPrint: () -> Unit = {}
) {
    val ios = LocalIos.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val hasPage = tab.url.isNotBlank()
    fun act(a: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); a() } }
    val marked = hasPage && Library.isBookmarked(tab.url)
    val reading = hasPage && Library.isInReadingList(tab.url)
    val startFav = hasPage && Library.isFavorite(tab.url)
    NovaSheet(onDismiss, state) {
            ShareHeader(tab) { act {} }
            if (hasPage) ShareApps(tab.url, tab.title) { a -> act(a) }
            IosGroup {
                IosCell(L("نسخ الرابط"), { act(onCopy) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Copy, ios.label, UiLayout.dp22) })
            }
            Spacer(Modifier.height(UiLayout.dp20))
            IosGroup {
                IosCell(L("تبويب جديد"), { act(onNewTab) }, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, UiLayout.dp22) })
                IosCell(
                    if (reading) L("إزالة من قائمة القراءة") else L("إضافة إلى قائمة القراءة"),
                    { act { toast(ctx, if (Library.toggleReading(tab.url, tab.title)) L("أُضيفت إلى قائمة القراءة") else L("أُزيلت من قائمة القراءة")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Book, ios.label, UiLayout.dp22) }
                )
                IosCell(
                    if (marked) L("إزالة من المفضلة") else L("إضافة إلى المفضلة"),
                    { act { toast(ctx, if (Library.toggleBookmark(tab.url, tab.title)) L("أُضيفت إلى المفضلة") else L("أُزيلت من المفضلة")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Book, ios.label, UiLayout.dp22) }
                )
                IosCell(
                    if (startFav) L("إزالة من صفحة البداية") else L("إضافة إلى صفحة البداية"),
                    { act { toast(ctx, if (Library.toggleFavorite(tab.url, tab.title)) L("أُضيفت إلى صفحة البداية") else L("أُزيلت من صفحة البداية")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Plus, ios.label, UiLayout.dp22) }
                )
                IosCell(L("بحث في الصفحة"), { act(onFind) }, enabled = hasPage, trailing = { IosIcon(IosGlyph.Magnifier, ios.label, UiLayout.dp22) })
                IosCell(L("إضافة إلى الشاشة الرئيسية"), { act { PageExtras.pinShortcut(ctx, tab) } }, enabled = hasPage, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, UiLayout.dp22) })
                IosCell(L("طباعة / حفظ PDF"), { act(onPrint) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Printer, ios.label, UiLayout.dp22) })
            }
        }
}

@Composable
fun PageMenu(
    tab: BrowserTab, expanded: Boolean, onDismiss: () -> Unit, onDesktop: () -> Unit,
    onTranslate: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit,
    onSummarize: () -> Unit = {}, onAskAi: () -> Unit = {},
    onForward: (() -> Unit)? = null, onShare: (() -> Unit)? = null, onLibrary: (() -> Unit)? = null,
    onTabs: (() -> Unit)? = null, onNewTab: (() -> Unit)? = null
) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf(false) }
    val bounce = rememberRubberBand()
    val screenDp = LocalConfiguration.current.screenHeightDp
    LaunchedEffect(expanded) { if (!expanded) sub = false else FrameLog.sample("page_menu_open") }
    val zoomNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    fun act(a: () -> Unit) { onDismiss(); a() }
    IosMenu(
        expanded = expanded, onDismissRequest = onDismiss, shape = IosShape.menu,
        tonalElevation = UiLayout.dp0, shadowElevation = UiLayout.dp12
    ) {
        Column(Modifier.width(menuWidth(UiLayout.dp260)).heightIn(max = menuMaxHeight(screenDp)).verticalScroll(rememberScrollState(), overscrollEffect = bounce)) {
            if (!sub) {
                onNewTab?.let { IosCell(L("تبويب جديد"), { act(it) }) }
                onTabs?.let { IosCell(L("التبويبات"), { act(it) }) }
                onLibrary?.let { IosCell(L("المفضلة والمكتبة"), { act(it) }) }
                onShare?.let { IosCell(L("مشاركة"), { act(it) }, enabled = tab.url.isNotBlank()) }
                onForward?.let { IosCell(L("الصفحة التالية"), { act(it) }, enabled = tab.canForward) }
                if (onNewTab != null || onTabs != null) Box(Modifier.fillMaxWidth().height(UiLayout.dp6).background(ios.fill4))
                Row(Modifier.fillMaxWidth().height(UiLayout.dp48).padding(horizontal = UiLayout.dp4), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().tapClickable(enabled = Prefs.textZoom > 0) { Prefs.pickTextZoom(Prefs.textZoom - 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = IosText.zoomSmall, color = if (Prefs.textZoom > 0) ios.label else ios.label3) }
                    Text(zoomNames.getOrElse(Prefs.textZoom) { "" }, style = MaterialTheme.typography.bodyMedium, color = ios.label2, maxLines = 1)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().tapClickable(enabled = Prefs.textZoom < zoomNames.lastIndex) { Prefs.pickTextZoom(Prefs.textZoom + 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = IosText.zoomLarge, color = if (Prefs.textZoom < zoomNames.lastIndex) ios.label else ios.label3) }
                }
                IosDivider()
                IosCell(L("نسخة سطح المكتب"), { act(onDesktop) }, trailing = if (tab.desktop) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null)
                IosCell(L("وضع القراءة"), { act { GeckoPage.exec(tab, "reader.js") { r -> if (r == null) toast(ctx, L("تعذّر تشغيل هذه الميزة على الصفحة الحالية. أعد تحميل الصفحة وجرّب مجدداً")) } } })
                IosCell(L("ترجمة الصفحة"), { act(onTranslate) })
                if (Prefs.smart) {
                    IosCell(S(R.string.smart_summarize), { act(onSummarize) }, enabled = tab.url.isNotBlank())
                    IosCell(S(R.string.smart_ask_page), { act(onAskAi) }, enabled = tab.url.isNotBlank())
                }
                IosCell(L("أدوات الصفحة"), { sub = true }, chevron = true)
                Box(Modifier.fillMaxWidth().height(UiLayout.dp6).background(ios.fill4))
                IosCell(L("التنزيلات"), { act(onDownloads) })
                IosCell(L("الإعدادات"), { act(onSettings) }, divider = false)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(UiLayout.dp44).tapClickable { sub = false }.padding(horizontal = UiLayout.dp10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosIcon(IosGlyph.Back, ios.blue, UiLayout.dp20, weight = 1.1f)
                    Spacer(Modifier.width(UiLayout.dp4))
                    Text(L("أدوات الصفحة"), style = MaterialTheme.typography.titleSmall, color = ios.label)
                }
                IosDivider()
                if (tab.url.startsWith("http")) {
                    val sh = hostOf(tab.url)
                    IosCell(
                        S(R.string.site_js),
                        { act { val on = SiteStore.toggleJs(sh); SiteStore.apply(tab, sh); toast(ctx, if (on) S(R.string.site_js_on) else S(R.string.site_js_off)) } }
                    )
                    IosCell(
                        S(R.string.site_desktop),
                        { act { val on = SiteStore.toggleDesktop(sh, tab.desktop); SiteStore.apply(tab, sh); toast(ctx, if (on) S(R.string.site_desktop_on) else S(R.string.site_desktop_off)) } }
                    )
                    IosCell(
                        S(R.string.site_clear),
                        { act { SiteStore.clearData(sh); tab.engine?.reload(); toast(ctx, S(R.string.site_cleared)) } }
                    )
                    if (SiteStore.get(sh).custom) IosCell(
                        S(R.string.site_reset),
                        { act { SiteStore.reset(sh); toast(ctx, S(R.string.site_reset_done)) } }
                    )
                }
                IosCell(L("الأعلى"), { act { tab.engine?.scrollTop() } }, divider = false)
            }
        }
    }
}
