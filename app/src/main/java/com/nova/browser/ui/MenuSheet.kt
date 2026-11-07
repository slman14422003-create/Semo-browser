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
    val fvTick = Favicons.version
    val ic = remember(host, fvTick) { Favicons.get(host) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ios.cell), contentAlignment = Alignment.Center) {
            if (ic != null) Image(remember(ic) { ic.asImageBitmap() }, null, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
            else Text(host.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ios.label2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.title.ifBlank { host }, style = MaterialTheme.typography.titleSmall, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(ios.fill2).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            IosIcon(IosGlyph.Close, ios.label2, 13.dp, weight = 1.2f)
        }
    }
}

private class ShareTarget(val label: String, val icon: android.graphics.Bitmap?, val pkg: String, val cls: String)

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
    val targets = remember(url) {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_SEND).setType("text/plain")
        runCatching {
            pm.queryIntentActivities(probe, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ShareTarget(it.loadLabel(pm).toString(), runCatching { appIconBitmap(it.loadIcon(pm), 128) }.getOrNull(), it.activityInfo.packageName, it.activityInfo.name) }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(targets, key = { it.pkg + "/" + it.cls }) { t ->
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    act {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
                                    .setClassName(t.pkg, t.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)).background(ios.cell), contentAlignment = Alignment.Center) {
                    val ic = t.icon
                    if (ic != null) Image(remember(ic) { ic.asImageBitmap() }, null, Modifier.fillMaxSize())
                    else Text(t.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ios.label2)
                }
                Spacer(Modifier.height(5.dp))
                Text(t.label, fontSize = 11.sp, color = ios.label2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        item {
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(10.dp)).clickable { act { shareText(ctx, url) } }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)).background(ios.cell), contentAlignment = Alignment.Center) {
                    IosIcon(IosGlyph.Ellipsis, ios.label, 24.dp)
                }
                Spacer(Modifier.height(5.dp))
                Text(L("المزيد"), fontSize = 11.sp, color = ios.label2, maxLines = 1)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = ios.groupedBg, shape = IosShape.sheet,
        dragHandle = { Box(Modifier.padding(top = 6.dp, bottom = 8.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(ios.gray3)) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            ShareHeader(tab) { act {} }
            if (hasPage) ShareApps(tab.url, tab.title) { a -> act(a) }
            IosGroup {
                IosCell(L("نسخ الرابط"), { act(onCopy) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Copy, ios.label, 22.dp) })
            }
            Spacer(Modifier.height(20.dp))
            IosGroup {
                IosCell(L("تبويب جديد"), { act(onNewTab) }, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, 22.dp) })
                IosCell(
                    if (reading) L("إزالة من قائمة القراءة") else L("إضافة إلى قائمة القراءة"),
                    { act { toast(ctx, if (Library.toggleReading(tab.url, tab.title)) L("أُضيفت إلى قائمة القراءة") else L("أُزيلت من قائمة القراءة")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Book, ios.label, 22.dp) }
                )
                IosCell(
                    if (marked) L("إزالة من المفضلة") else L("إضافة إلى المفضلة"),
                    { act { toast(ctx, if (Library.toggleBookmark(tab.url, tab.title)) L("أُضيفت إلى المفضلة") else L("أُزيلت من المفضلة")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Book, ios.label, 22.dp) }
                )
                IosCell(
                    if (startFav) L("إزالة من صفحة البداية") else L("إضافة إلى صفحة البداية"),
                    { act { toast(ctx, if (Library.toggleFavorite(tab.url, tab.title)) L("أُضيفت إلى صفحة البداية") else L("أُزيلت من صفحة البداية")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Plus, ios.label, 22.dp) }
                )
                IosCell(L("بحث في الصفحة"), { act(onFind) }, enabled = hasPage, trailing = { IosIcon(IosGlyph.Magnifier, ios.label, 22.dp) })
                IosCell(L("إضافة إلى الشاشة الرئيسية"), { act { PageExtras.pinShortcut(ctx, tab) } }, enabled = hasPage, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, 22.dp) })
                IosCell(L("طباعة / حفظ PDF"), { act(onPrint) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Printer, ios.label, 22.dp) })
            }
        }
    }
}

@Composable
fun PageMenu(
    tab: BrowserTab, expanded: Boolean, onDismiss: () -> Unit, onDesktop: () -> Unit,
    onTranslate: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit,
    onSummarize: () -> Unit = {}, onAskAi: () -> Unit = {}
) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) sub = false }
    val zoomNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    fun act(a: () -> Unit) { onDismiss(); a() }
    IosMenu(
        expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp, shadowElevation = 12.dp
    ) {
        Column(Modifier.width(260.dp)) {
            if (!sub) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = Prefs.textZoom > 0) { Prefs.pickTextZoom(Prefs.textZoom - 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = 14.sp, color = if (Prefs.textZoom > 0) ios.label else ios.label3) }
                    Text(zoomNames.getOrElse(Prefs.textZoom) { "" }, style = MaterialTheme.typography.bodyMedium, color = ios.label2, maxLines = 1)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = Prefs.textZoom < zoomNames.lastIndex) { Prefs.pickTextZoom(Prefs.textZoom + 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = 24.sp, color = if (Prefs.textZoom < zoomNames.lastIndex) ios.label else ios.label3) }
                }
                IosDivider()
                IosCell(L("نسخة سطح المكتب"), { act(onDesktop) }, trailing = if (tab.desktop) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                IosCell(L("وضع القراءة"), { act { GeckoPage.exec(tab, "reader.js") { r -> if (r == null) toast(ctx, L("تعذّر تشغيل هذه الميزة على الصفحة الحالية. أعد تحميل الصفحة وجرّب مجدداً")) } } })
                IosCell(L("ترجمة الصفحة"), { act(onTranslate) })
                if (Prefs.smart) {
                    IosCell(S(R.string.smart_summarize), { act(onSummarize) }, enabled = tab.url.isNotBlank())
                    IosCell(S(R.string.smart_ask_page), { act(onAskAi) }, enabled = tab.url.isNotBlank())
                }
                IosCell(L("أدوات الصفحة"), { sub = true }, chevron = true)
                Box(Modifier.fillMaxWidth().height(6.dp).background(ios.fill4))
                IosCell(L("التنزيلات"), { act(onDownloads) })
                IosCell(L("الإعدادات"), { act(onSettings) }, divider = false)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clickable { sub = false }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosIcon(IosGlyph.Back, ios.blue, 20.dp, weight = 1.1f)
                    Spacer(Modifier.width(4.dp))
                    Text(L("أدوات الصفحة"), style = MaterialTheme.typography.titleSmall, color = ios.label)
                }
                IosDivider()
                if (tab.url.startsWith("https://")) IosCell(
                    if (Pwa.isInstalled(hostOf(tab.url))) "Remove app" else "Install as app",
                    { act { Pwa.togglePin(ctx, tab.url, tab.title) } }
                )
                if (tab.url.startsWith("https://") && Pwa.isInstalled(hostOf(tab.url))) IosCell(
                    "Open as app",
                    { act { Pwa.launch(ctx, tab.url) } }
                )
                if (tab.url.startsWith("https://") && Pwa.isInstalled(hostOf(tab.url))) IosCell(
                    "Add to home screen",
                    { act { Pwa.pinShortcut(ctx, tab.url) } }
                )
                if (tab.url.startsWith("http")) {
                    val sh = hostOf(tab.url)
                    IosCell(
                        "Site JavaScript",
                        { act { val on = SiteStore.toggleJs(sh); SiteStore.apply(tab, sh); toast(ctx, if (on) "JavaScript on" else "JavaScript off") } }
                    )
                    IosCell(
                        "Site desktop mode",
                        { act { val on = SiteStore.toggleDesktop(sh, tab.desktop); SiteStore.apply(tab, sh); toast(ctx, if (on) "Desktop" else "Mobile") } }
                    )
                    IosCell(
                        "Clear site data",
                        { act { SiteStore.clearData(sh); tab.engine?.reload(); toast(ctx, "Site data cleared") } }
                    )
                    if (SiteStore.get(sh).custom) IosCell(
                        "Reset site settings",
                        { act { SiteStore.reset(sh); toast(ctx, "Site settings reset") } }
                    )
                }
                IosCell(L("الأعلى"), { act { tab.engine?.scrollTop() } }, divider = false)
            }
        }
    }
}
