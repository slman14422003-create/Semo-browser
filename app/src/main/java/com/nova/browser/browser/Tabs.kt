package com.nova.browser

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

private fun hostColor(host: String): Color = Color.hsv((host.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat(), 0.55f, 0.72f)

val TabTagColors = listOf(Color.Transparent, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55))

/** معاينة مصغّرة لصفحة البداية (كما يفعل سفاري) بدل نص «صفحة البداية». */
@Composable
private fun MiniStartPage() {
    val ios = LocalIos.current
    val cols = listOf(Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFFF3B30), Color(0xFFEA4335), Color(0xFF8E8E93), Color(0xFF3A3A3C))
    Column(Modifier.fillMaxSize().background(ios.groupedBg).padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(44.dp).height(6.dp).clip(CircleShape).background(ios.fill2))
        cols.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(c)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp)).background(ios.cell))
        Box(Modifier.width(60.dp).height(6.dp).clip(CircleShape).background(ios.fill2))
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(5.dp)).background(ios.cell))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp)).background(ios.cell))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSwitcher(
    tabs: List<BrowserTab>, current: Int,
    onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onNew: (Int) -> Unit, onCloseAll: () -> Unit, onBack: () -> Unit,
    onDuplicate: (Int) -> Unit = {}, onCloseOthers: (Int) -> Unit = {}, onCloseTag: (Int) -> Unit = {},
    canReopen: Boolean = false, onReopen: () -> Unit = {}, onCloseDuplicates: () -> Unit = {}
) {
    val ios = LocalIos.current
    var picked by remember { mutableIntStateOf(-1) }
    val usedTags = tabs.map { it.tag }.filter { it > 0 }.distinct().sorted()
    val filter = if ((picked > 0 && picked !in usedTags) || (picked == -2 && tabs.none { it.pinned })) -1 else picked
    var grp by remember { mutableIntStateOf(tabs.getOrNull(current)?.group ?: Groups.NONE) }
    val gNow = if (grp > 0 && grp != Groups.PRIVATE && Groups.names.none { it.first == grp }) Groups.ALL else if (grp == Groups.NONE) Groups.ALL else grp
    val inGroup: (BrowserTab) -> Boolean = { t -> when (gNow) { Groups.ALL -> !t.isPrivate; Groups.PRIVATE -> t.isPrivate; else -> !t.isPrivate && t.group == gNow } }
    var tabQuery by remember { mutableStateOf("") }
    val tq = tabQuery.trim()
    val shown = tabs.withIndex().filter { (_, t) -> inGroup(t) }.filter { (_, t) -> when (filter) { -1 -> true; -2 -> t.pinned; else -> t.tag == filter } }
        .filter { (_, t) -> tq.isBlank() || t.title.contains(tq, true) || t.url.contains(tq, true) }
    val showSearch = tabs.size >= 4
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 0)
    var filterMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val appear by animateFloatAsState(if (opened) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "tabsAppear")
    Column(Modifier.fillMaxSize().background(ios.tabsBg)) {
        LazyVerticalGrid(
            state = gridState, columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).statusBarsPadding().graphicsLayer { val k = 0.94f + 0.06f * appear; scaleX = k; scaleY = k; alpha = appear },
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item {
                        GroupChip(L("الكل"), gNow == Groups.ALL, false, { grp = Groups.ALL }, null)
                    }
                    items(Groups.names.toList(), key = { it.first }) { g ->
                        GroupChip(g.second, gNow == g.first, false, { grp = g.first }) {
                            tabs.filter { it.group == g.first }.forEach { it.group = Groups.NONE }
                            Groups.remove(g.first)
                            grp = Groups.ALL
                        }
                    }
                    item { GroupChip(L("خاص"), gNow == Groups.PRIVATE, true, { grp = Groups.PRIVATE }, null) }
                    item { GroupChip("+", false, false, { grp = Groups.add(L("مجموعة") + " " + (Groups.names.size + 1)) }, null) }
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(if (gNow == Groups.PRIVATE) L("التصفح الخاص: لا يُحفظ السجل ولا الكوكيز") else L("لا توجد تبويبات هنا"), color = ios.label2, fontSize = 14.sp)
                }
            }
            if (showSearch) item(span = { GridItemSpan(maxLineSpan) }) { PanelSearch(tabQuery, { tabQuery = it }, L("بحث في التبويبات")) }
            items(shown, key = { it.value.id }) { (i, t) ->
                val blank = t.url.isBlank()
                val host = if (blank) "nova" else hostOf(t.url)
                val accent = remember(host) { hostColor(host) }
                var menu by remember { mutableStateOf(false) }
                val cardShape = RoundedCornerShape(16.dp)
                var vis by remember { mutableStateOf(false) }
                LaunchedEffect(t.id) { delay(minOf(i, 8) * 28L); vis = true }
                val pop by animateFloatAsState(if (vis) 1f else 0.82f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "tabPop")
                var dx by remember { mutableFloatStateOf(0f) }
                var dragging by remember { mutableStateOf(false) }
                // pointerInput يبقى مفتوحاً بنفس المفتاح: الفهرس i يتغيّر عند إغلاق تبويب سابق، فبدون هذا كان السحب يغلق تبويباً خاطئاً
                val curIdx by rememberUpdatedState(i)
                var closeReq by remember { mutableIntStateOf(0) }
                LaunchedEffect(closeReq) { if (closeReq > 0) { delay(400); dx = 0f } }   // لو لم يُغلق التبويب (مثبّت/أخير) تعود البطاقة لمكانها
                val dxShown by animateFloatAsState(dx, if (dragging) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "swipeClose")
                val cardSrc = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.74f).animateItem().pressScale(cardSrc, 0.96f)
                        .graphicsLayer {
                            translationX = dxShown; rotationZ = (dxShown / size.width) * 10f
                            val sc = pop * (1f - 0.08f * (kotlin.math.abs(dxShown) / size.width).coerceIn(0f, 1f)); scaleX = sc; scaleY = sc
                            alpha = ((1f - kotlin.math.abs(dxShown) / (size.width * 1.2f)) * (if (vis) 1f else 0f)).coerceIn(0f, 1f)
                        }
                        .pointerInput(t.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { dragging = false; if (kotlin.math.abs(dx) > size.width * 0.35f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); closeReq++; onClose(curIdx) } else dx = 0f },
                                onDragCancel = { dragging = false; dx = 0f },
                                onHorizontalDrag = { c, d -> c.consume(); dx += d }
                            )
                        }
                ) {
                    Column(
                        Modifier.fillMaxSize().shadow(if (i == current) 10.dp else 4.dp, cardShape).clip(cardShape).background(ios.cell)
                            .border(if (i == current) 2.5.dp else 0.dp, ios.blue, cardShape)
                            .combinedClickable(interactionSource = cardSrc, indication = null, onClick = { onSelect(i) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
                    ) {
                        if (t.tag > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(TabTagColors[t.tag]))
                        Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val fvTick = Favicons.version
                            val ic = if (blank) null else remember(host, fvTick) { Favicons.get(host) }
                            if (t.loading) NovaSpinner(size = 17.dp, color = ios.blue)
                            else Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (ic != null) Color.White else accent), contentAlignment = Alignment.Center) {
                                if (ic != null) {
                                    val ib = remember(ic) { ic.asImageBitmap() }
                                    Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else Text(host.take(1).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (blank) L("صفحة البداية") else if (t.title.isBlank() || t.title == L("تبويب جديد")) host else t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ios.label, modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.size(30.dp).clickable { onClose(i) }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(ios.fill2), contentAlignment = Alignment.Center) {
                                    IosIcon(IosGlyph.Close, ios.label2, 10.dp, weight = 1.3f)
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().background(ios.groupedBg)) {
                            val th = t.thumb
                            if (th != null && !blank) {
                                val img = remember(th) { th.asImageBitmap() }
                                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            } else {
                                if (blank) MiniStartPage()
                                else Box(Modifier.fillMaxSize().background(ios.bg), contentAlignment = Alignment.Center) {
                                    val fv2 = Favicons.version
                                    val ic2 = remember(host, fv2) { Favicons.get(host) }
                                    if (ic2 != null) {
                                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                            val ib2 = remember(ic2) { ic2.asImageBitmap() }
                                            Image(ib2, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                                        }
                                    } else Text(host.take(1).uppercase(), fontSize = 34.sp, color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    IosMenu(
                        expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(22.dp),
                        tonalElevation = 0.dp, shadowElevation = 12.dp
                    ) {
                        Column(Modifier.width(240.dp)) {
                            IosCell(if (t.pinned) L("إلغاء التثبيت") else L("تثبيت"), { t.pinned = !t.pinned; menu = false })
                            IosCell(L("نسخ التبويب"), { menu = false; onDuplicate(i) })
                            if (!t.isPrivate) Groups.names.toList().forEach { g -> IosCell(L("نقل إلى ") + g.second, { t.group = g.first; menu = false }, trailing = if (t.group == g.first) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null) }
            if (!t.isPrivate && t.group > 0) IosCell(L("إزالة من المجموعة"), { t.group = Groups.NONE; menu = false })
                            IosCell(L("إغلاق التبويبات الأخرى"), { menu = false; onCloseOthers(i) }, divider = t.tag > 0)
                            if (t.tag > 0) IosCell(L("إغلاق كل تبويبات هذا اللون"), { menu = false; onCloseTag(t.tag) }, divider = false)
                            IosDivider()
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                for (g in 0..6) Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .background(if (g == 0) ios.fill2 else TabTagColors[g])
                                        .border(if (t.tag == g) 2.dp else 0.dp, ios.blue, CircleShape)
                                        .clickable { t.tag = g; menu = false },
                                    contentAlignment = Alignment.Center
                                ) { if (g == 0) IosIcon(IosGlyph.Close, ios.label2, 12.dp) }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ios.chrome).iosHairlineTop(ios.separator).navigationBarsPadding().height(48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clickable { onNew(if (gNow == Groups.ALL) Groups.NONE else gNow) }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.Plus, ios.blue, 26.dp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(32.dp).clip(CircleShape).background(ios.fill3).clickable { filterMenu = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "" + tabs.count(inGroup) + " " + L("التبويبات"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    IosIcon(IosGlyph.ChevronDown, ios.label2, 11.dp, weight = 1.3f)
                }
                IosMenu(
                    expanded = filterMenu, onDismissRequest = { filterMenu = false }, shape = RoundedCornerShape(22.dp),
                    tonalElevation = 0.dp, shadowElevation = 12.dp
                ) {
                    Column(Modifier.width(240.dp)) {
                        IosCell(L("الكل"), { picked = -1; filterMenu = false }, trailing = if (filter == -1) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                        if (tabs.any { it.pinned }) IosCell(L("المثبّتة"), { picked = -2; filterMenu = false }, trailing = if (filter == -2) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                        usedTags.forEach { g ->
                            IosCell(
                                "" + tabs.count { it.tag == g }, { picked = g; filterMenu = false },
                                leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(TabTagColors[g])) },
                                trailing = if (filter == g) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null
                            )
                        }
                        IosDivider()
                        if (canReopen) IosCell(L("إعادة فتح تبويب مغلق"), { filterMenu = false; onReopen() })
                        if (Prefs.smart && tabs.size > 1) IosCell(S(R.string.smart_close_dups), { filterMenu = false; onCloseDuplicates() })
                        IosCell(L("إغلاق الكل"), { filterMenu = false; onCloseAll() }, tint = ios.red, divider = false)
                    }
                }
            }
            IosTextButton(L("إغلاق الكل"), onCloseAll, color = ios.red, enabled = tabs.any { !it.pinned })
            IosTextButton(L("تم"), onBack, bold = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(label: String, selected: Boolean, lock: Boolean, onClick: () -> Unit, onLong: (() -> Unit)?) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val bgc = if (selected) (if (lock) ios.label else ios.blue) else ios.fill3
    val fg = if (selected) (if (lock) ios.bg else Color.White) else ios.label
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(bgc)
            .combinedClickable(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }, onLongClick = onLong?.let { f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); f() } })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lock) { IosIcon(IosGlyph.Lock, fg, 13.dp); Spacer(Modifier.width(5.dp)) }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}
