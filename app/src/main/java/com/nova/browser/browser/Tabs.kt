package com.nova.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.content.*
import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun hostColor(host: String): Color = Color.hsv((host.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat(), 0.55f, 0.72f)

val TabTagColors = listOf(Color.Transparent, IosLight.red, IosLight.orange, IosLight.green, IosLight.blue, IosLight.purple, IosLight.pink)

/** معاينة مصغّرة لصفحة البداية (كما يفعل سفاري) بدل نص «صفحة البداية». */
@Composable
private fun MiniStartPage() {
    val ios = LocalIos.current
    val cols = listOf(IosLight.green, IosLight.blue, IosLight.red, IosBrand.gmail, IosLight.gray, IosDark.gray4)
    Column(Modifier.fillMaxSize().background(ios.groupedBg).padding(horizontal = UiLayout.dp10, vertical = UiLayout.dp12), verticalArrangement = Arrangement.spacedBy(UiLayout.dp8)) {
        Box(Modifier.width(UiLayout.dp44).height(UiLayout.dp6).clip(CircleShape).background(ios.fill2))
        cols.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiLayout.dp6)) {
                row.forEach { c -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(UiLayout.dp6)).background(c)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(UiLayout.dp16).clip(RoundedCornerShape(UiLayout.dp5)).background(ios.cell))
        Box(Modifier.width(UiLayout.dp60).height(UiLayout.dp6).clip(CircleShape).background(ios.fill2))
        Box(Modifier.fillMaxWidth().height(UiLayout.dp26).clip(RoundedCornerShape(UiLayout.dp5)).background(ios.cell))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(UiLayout.dp5)).background(ios.cell))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSwitcher(
    tabs: List<BrowserTab>, current: Int,
    onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onNew: (Int) -> Unit, onCloseAll: () -> Unit, onBack: () -> Unit,
    onDuplicate: (Int) -> Unit = {}, onCloseOthers: (Int) -> Unit = {}, onCloseTag: (Int) -> Unit = {},
    canReopen: Boolean = false, onReopen: () -> Unit = {}, onCloseDuplicates: () -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> }
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
    val shown by remember(tabs, gNow, filter, tq) {
        derivedStateOf {
            tabs.withIndex().filter { (_, t) ->
                (when (gNow) { Groups.ALL -> !t.isPrivate; Groups.PRIVATE -> t.isPrivate; else -> !t.isPrivate && t.group == gNow }) &&
                    (when (filter) { -1 -> true; -2 -> t.pinned; else -> t.tag == filter }) &&
                    (tq.isBlank() || t.title.contains(tq, true) || t.url.contains(tq, true))
            }
        }
    }
    val showSearch = tabs.size >= 4
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 0)
    var filterMenu by remember { mutableStateOf(false) }
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val dens = LocalDensity.current.density
    val moveNow by rememberUpdatedState(onMove)
    val reorder = remember(gridState, dens) { TabReorder(gridState, scope, dens, haptic) { from, to -> moveNow(from, to) } }
        val source = rememberGlassSource()
        val barShape = RoundedCornerShape(UiLayout.dp28)
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState, columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().glassSource(source).background(ios.tabsBg).statusBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiLayout.dp16), verticalArrangement = Arrangement.spacedBy(UiLayout.dp16),
            contentPadding = PaddingValues(start = UiLayout.dp16, end = UiLayout.dp16, top = UiLayout.dp16, bottom = UiLayout.dp88 + navBottom)
        ) {
            item(key = "groups", contentType = "groups", span = { GridItemSpan(maxLineSpan) }) {
                val names = Groups.names.toList()
                val keys = listOf(Groups.ALL) + names.map { it.first } + Groups.PRIVATE
                val segments = listOf(Segment(L("الكل"))) + names.map { g ->
                    Segment(g.second, onLong = {
                        tabs.filter { it.group == g.first }.forEach { it.group = Groups.NONE }
                        Groups.remove(g.first)
                        grp = Groups.ALL
                    })
                } + Segment(L("خاص"), lock = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiLayout.dp8)) {
                    GlassSegments(segments, keys.indexOf(gNow).coerceAtLeast(0), { grp = keys[it] }, Modifier.weight(1f, fill = false), fill = false)
                    Box(
                        Modifier.size(UiLayout.dp44).tapClickable { grp = Groups.add(L("مجموعة") + " " + (Groups.names.size + 1)) }.iosButton(S(R.string.a11y_add)),
                        contentAlignment = Alignment.Center
                    ) { IosIcon(IosGlyph.Plus, ios.label, UiLayout.dp20) }
                }
            }
            if (shown.isEmpty()) item(key = "empty", contentType = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = UiLayout.dp48), contentAlignment = Alignment.Center) {
                    Text(if (gNow == Groups.PRIVATE) L("التصفح الخاص: لا يُحفظ السجل ولا الكوكيز") else L("لا توجد تبويبات هنا"), color = ios.label2, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (showSearch) item(key = "search", contentType = "search", span = { GridItemSpan(maxLineSpan) }) { PanelSearch(tabQuery, { tabQuery = it }, L("بحث في التبويبات")) }
            items(shown, key = { it.value.id }, contentType = { "tab" }) { (i, t) ->
                val blank = t.url.isBlank()
                val url = t.url
                val host = remember(url) { if (blank) "nova" else hostOf(url) }
                val accent = remember(host) { hostColor(host) }
                var menu by remember { mutableStateOf(false) }
                val cardShape = RoundedCornerShape(UiLayout.dp16)
                var vis by remember { mutableStateOf(false) }
                LaunchedEffect(t.id) { delay(Adaptive.ms(minOf(i, 8) * 28).toLong()); vis = true }
                val pop by animateFloatAsState(if (vis) 1f else 0.82f, Motion.bouncy(), label = "tabPop")
                val curIdx by rememberUpdatedState(i)
                val closeNow by rememberUpdatedState(onClose)
                val lifted = reorder.activeId == t.id
                val cardScope = rememberCoroutineScope()
                val magnet = remember(t.id) {
                    MagneticDrag(cardScope, haptic, UiLayout.MAGNET_DETACH_DP * dens, UiLayout.MAGNET_ATTACH_DP * dens, UiLayout.MAGNET_PULL)
                }
                val release = remember { Animatable(0f) }
                var swiping by remember { mutableStateOf(false) }
                var base by remember { mutableFloatStateOf(0f) }
                val cardSrc = remember { MutableInteractionSource() }
                val zoom = LocalTabZoom.current
                DisposableEffect(t.id, zoom) { onDispose { zoom?.detachCard(t.id) } }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.74f)
                        .animateItem(fadeInSpec = Motion.soft(), placementSpec = if (lifted) null else Motion.soft(), fadeOutSpec = Motion.soft())
                        .zIndex(if (lifted) 1f else 0f)
                        .pointerInput(t.id) {
                            val flingMin = UiLayout.SWIPE_FLING_DP_S * dens
                            val travelMin = UiLayout.SWIPE_FLING_MIN_DP * dens
                            tabCardGestures(
                                onSwipeStart = { base = release.value; magnet.start(); swiping = true },
                                onSwipe = { magnet.move(it) },
                                onSwipeEnd = { travel, velocity, cancelled ->
                                    magnet.finish()
                                    val from = base + magnet.position
                                    val close = !cancelled && (magnet.detached || MagnetPolicy.flingCloses(velocity, flingMin, travel, travelMin))
                                    val exit = (if (travel < 0f) -1f else 1f) * size.width * UiLayout.SWIPE_EXIT_SHARE
                                    cardScope.launch {
                                        release.snapTo(from)
                                        swiping = false
                                        if (close) {
                                            haptic.confirm()
                                            release.animateTo(exit, Motion.timed(UiLayout.SWIPE_OUT_MS, Motion.emphasizedAccelerate))
                                            closeNow(curIdx)
                                            delay(Motion.millis(UiLayout.SWIPE_RETURN_WAIT_MS))
                                        }
                                        release.animateTo(0f, Motion.drop())
                                    }
                                },
                                onLift = { reorder.begin(t.id, it) },
                                onLiftDrag = { reorder.dragBy(it) },
                                onLiftEnd = { stayed -> reorder.end(stayed) { menu = true } }
                            )
                        }
                        .pressScale(cardSrc, 0.96f)
                        .graphicsLayer {
                            val w = size.width
                            val dx = if (swiping) base + magnet.position else release.value
                            val d = reorder.displacement(t.id)
                            val lift = reorder.liftOf(t.id)
                            val away = (abs(dx) / w).coerceIn(0f, 1f)
                            translationX = dx + d.x
                            translationY = d.y
                            rotationZ = dx / w * UiLayout.SWIPE_TILT_DEG
                            val sc = pop * (1f - UiLayout.SWIPE_DEPTH * away) * (1f + (UiLayout.LIFT_SCALE - 1f) * lift)
                            scaleX = sc
                            scaleY = sc
                            alpha = ((1f - abs(dx) / (w * UiLayout.SWIPE_EXIT_SHARE)) * (if (vis) 1f else 0f)).coerceIn(0f, 1f)
                            shadowElevation = UiLayout.LIFT_SHADOW.toPx() * lift
                            shape = cardShape
                            clip = false
                        }
                        .tabBloom(i == current, ios.blue)
                ) {
                    Column(
                        Modifier.fillMaxSize().clip(cardShape).background(ios.cell)
                            .border(if (i == current) UiLayout.dp2_5 else hairlineDp(), if (i == current) ios.blue else ios.separator, cardShape)
                            .semantics { onLongClick { menu = true; true } }
                            .tapClickable(interactionSource = cardSrc, indication = null) { onSelect(i) }
                    ) {
                        if (t.tag > 0) Box(Modifier.fillMaxWidth().height(UiLayout.dp3).background(TabTagColors[t.tag]))
                        Row(Modifier.fillMaxWidth().height(UiLayout.dp44).padding(start = UiLayout.dp10), verticalAlignment = Alignment.CenterVertically) {
                            val fvTick = Favicons.watch(host)
                            val ic = if (blank) null else remember(host, fvTick) { Favicons.get(host) }
                            if (t.loading) NovaSpinner(size = UiLayout.dp17, color = ios.blue)
                            else Box(Modifier.size(UiLayout.dp16).clip(RoundedCornerShape(UiLayout.dp4)).background(if (ic != null) Color.White else accent), contentAlignment = Alignment.Center) {
                                if (ic != null) {
                                    val ib = remember(ic) { ic.asImageBitmap() }
                                    Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else Text(host.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.width(UiLayout.dp6))
                            Text(
                                if (blank) L("صفحة البداية") else if (t.title.isBlank() || t.title == L("تبويب جديد")) host else t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = ios.label, modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.size(UiLayout.dp44).tapClickable { onClose(i) }.iosButton(S(R.string.a11y_close_tab)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(UiLayout.dp22).clip(CircleShape).background(ios.fill2), contentAlignment = Alignment.Center) {
                                    IosIcon(IosGlyph.Close, ios.label2, UiLayout.dp10, weight = 1.3f)
                                }
                            }
                        }
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .onGloballyPositioned { c -> zoom?.attachCard(t.id, c) }
                                .graphicsLayer { alpha = if (zoom != null && zoom.hiding == t.id) 0f else 1f }
                                .background(ios.groupedBg)
                        ) {
                            val th = t.thumb
                            SkeletonReveal(
                                ready = blank || th != null || !t.loading,
                                modifier = Modifier.fillMaxSize(),
                                skeleton = { SkeletonThumb(Modifier.fillMaxSize()) }
                            ) {
                                if (th != null && !blank) {
                                    val img = remember(th) { th.asImageBitmap() }
                                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                                } else {
                                    if (blank) MiniStartPage()
                                    else Box(Modifier.fillMaxSize().background(ios.bg), contentAlignment = Alignment.Center) {
                                        val fv2 = Favicons.watch(host)
                                        val ic2 = remember(host, fv2) { Favicons.get(host) }
                                        if (ic2 != null) {
                                            Box(Modifier.size(UiLayout.dp52).clip(RoundedCornerShape(UiLayout.dp12)).background(Color.White), contentAlignment = Alignment.Center) {
                                                val ib2 = remember(ic2) { ic2.asImageBitmap() }
                                                Image(ib2, null, Modifier.fillMaxSize().padding(UiLayout.dp8), contentScale = ContentScale.Fit)
                                            }
                                        } else Text(host.take(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = accent)
                                    }
                                }
                            }
                        }
                    }
                    IosMenu(
                        expanded = menu, onDismissRequest = { menu = false }, shape = IosShape.menu,
                        tonalElevation = UiLayout.dp0, shadowElevation = UiLayout.dp12
                    ) {
                        Column(Modifier.width(menuWidth(UiLayout.dp280))) {
                            IosCell(if (t.pinned) L("إلغاء التثبيت") else L("تثبيت"), { t.pinned = !t.pinned; menu = false })
                            IosCell(L("نسخ التبويب"), { menu = false; onDuplicate(i) })
                            if (!t.isPrivate) Groups.names.toList().forEach { g -> IosCell(L("نقل إلى ") + g.second, { t.group = g.first; menu = false }, trailing = if (t.group == g.first) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null) }
            if (!t.isPrivate && t.group > 0) IosCell(L("إزالة من المجموعة"), { t.group = Groups.NONE; menu = false })
                            IosCell(L("إغلاق التبويبات الأخرى"), { menu = false; onCloseOthers(i) }, divider = t.tag > 0)
                            if (t.tag > 0) IosCell(L("إغلاق كل تبويبات هذا اللون"), { menu = false; onCloseTag(t.tag) }, divider = false)
                            IosDivider()
                            Row(Modifier.padding(horizontal = UiLayout.dp8), verticalAlignment = Alignment.CenterVertically) {
                                for (g in 0..6) Box(
                                    Modifier.weight(1f).height(UiLayout.dp44).tapClickable { t.tag = g; menu = false }
                                        .iosButton(S(if (g == 0) R.string.a11y_no_color else R.string.a11y_tab_color)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(UiLayout.dp26).clip(CircleShape)
                                            .background(if (g == 0) ios.fill2 else TabTagColors[g])
                                            .border(if (t.tag == g) UiLayout.dp2 else UiLayout.dp0, ios.blue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { if (g == 0) IosIcon(IosGlyph.Close, ios.label2, UiLayout.dp12) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = UiLayout.dp12, vertical = UiLayout.dp8).fillMaxWidth().height(UiLayout.dp56)
                .glass(GlassStyles.Bar, barShape, source).padding(horizontal = UiLayout.dp6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(UiLayout.dp44).tapClickable { onNew(if (gNow == Groups.ALL) Groups.NONE else gNow) }.iosButton(S(R.string.a11y_new_tab)), contentAlignment = Alignment.Center) { IosIcon(IosGlyph.Plus, ios.label, UiLayout.dp26) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(UiLayout.dp32).clip(CircleShape).background(ios.fill3).tapClickable { filterMenu = true }.padding(horizontal = UiLayout.dp14),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "" + tabs.count(inGroup) + " " + L("التبويبات"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1
                    )
                    Spacer(Modifier.width(UiLayout.dp4))
                    IosIcon(IosGlyph.ChevronDown, ios.label2, UiLayout.dp11, weight = 1.3f)
                }
                IosMenu(
                    expanded = filterMenu, onDismissRequest = { filterMenu = false }, shape = IosShape.menu,
                    tonalElevation = UiLayout.dp0, shadowElevation = UiLayout.dp12
                ) {
                    Column(Modifier.width(menuWidth(UiLayout.dp240))) {
                        IosCell(L("الكل"), { picked = -1; filterMenu = false }, trailing = if (filter == -1) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null)
                        if (tabs.any { it.pinned }) IosCell(L("المثبّتة"), { picked = -2; filterMenu = false }, trailing = if (filter == -2) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null)
                        usedTags.forEach { g ->
                            IosCell(
                                "" + tabs.count { it.tag == g }, { picked = g; filterMenu = false },
                                leading = { Box(Modifier.size(UiLayout.dp14).clip(CircleShape).background(TabTagColors[g])) },
                                trailing = if (filter == g) ({ IosIcon(IosGlyph.Check, ios.blue, UiLayout.dp20) }) else null
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

