package com.nova.browser

import android.content.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/** دخول متتابع لأقسام الصفحة: كل قسم يظهر متأخراً قليلاً عن سابقه (على طبقة الرسم فقط، بلا إعادة تخطيط). */
@Composable
private fun Modifier.stagger(index: Int, shown: Boolean): Modifier {
    val a by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(Adaptive.ms(360), delayMillis = Adaptive.ms(index * 55), easing = NovaMotion.emphasized), label = "stagger"
    )
    return this.graphicsLayer { alpha = a; translationY = (1f - a) * 24.dp.toPx() }
}

private class Site(val name: String, val host: String, val url: String, val glyph: String, val color: Long, val icon: ImageVector?, val tint: Color? = null)

const val AI_MODE_URL = "https://www.google.com/search?udm=50"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteItem(st: Site, modifier: Modifier, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val ver = Favicons.version
    val fav = if (st.tint != null) remember(ver, st.host) { Favicons.get(st.host) } else null
    val favImage = remember(fav) { fav?.asImageBitmap() }
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.pressScale(src, 0.92f).combinedClickable(interactionSource = src, indication = null, role = Role.Button, onLongClick = if (onRemove != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }) else null, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(if (fav != null) Color.White else (st.tint ?: Color(st.color))), contentAlignment = Alignment.Center) {
            if (favImage != null) Image(favImage, null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
            else if (st.icon != null) Icon(st.icon, null, Modifier.size(28.dp), tint = Color.White)
            else Text(st.glyph, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(st.name, style = MaterialTheme.typography.labelMedium, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (onRemove != null) IosMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(22.dp), tonalElevation = 0.dp) {
            DropdownMenuItem(text = { Text(L("حذف")) }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun StartHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = LocalIos.current.label, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
fun StartPage(
    tabsCount: Int, activeDl: Int, onSearchClick: () -> Unit, onAi: () -> Unit, onOpen: (String) -> Unit,
    onTabs: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit = {}, onLibrary: () -> Unit = {}
) {
    val ios = LocalIos.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(SplashGate.revealed) { if (SplashGate.revealed) shown = true }
    val defaultSites = remember {
        listOf(
            Site(L("الخرائط"), "maps.google.com", "https://maps.google.com", "", 0xFF34C759, Icons.Default.Place),
            Site(L("ترجمة جوجل"), "translate.google.com", "https://translate.google.com", "文A", 0xFF007AFF, null),
            Site("YouTube", "youtube.com", "https://m.youtube.com", "", 0xFFFF3B30, Icons.Default.PlayArrow),
            Site("Gmail", "mail.google.com", "https://mail.google.com", "", 0xFFEA4335, Icons.Default.Email),
            Site("Wikipedia", "wikipedia.org", "https://www.wikipedia.org", "W", 0xFF8E8E93, null),
            Site("GitHub", "github.com", "https://github.com", "</>", 0xFF3A3A3C, null)
        )
    }
    val rows by remember(defaultSites) {
        derivedStateOf {
            val userFavs = Library.favorites.map { b ->
                val h = hostOf(b.url)
                Site(b.title, h, b.url, h.take(1).uppercase(), 0L, null, Color.hsv((h.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat(), 0.55f, 0.72f))
            }
            (defaultSites.filter { it.host !in Prefs.hiddenFavs } + userFavs).chunked(4)
        }
    }
    val latestName by remember { derivedStateOf { Downloader.tasks.firstOrNull()?.name } }
    val ln = latestName
    val dlSub = when {
        activeDl > 0 -> ("" + activeDl + L(" قيد التنزيل"))
        ln != null -> ln
        else -> L("لا توجد تنزيلات بعد")
    }

    Box(Modifier.fillMaxSize().background(ios.groupedBg)) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        StartHeader(L("المفضلة"), Modifier.stagger(1, shown))
        Column(Modifier.stagger(2, shown).padding(horizontal = 12.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { st -> FavoriteItem(st, Modifier.weight(1f), if (st.tint != null) ({ Library.favorites.firstOrNull { it.url == st.url }?.let { Library.toggleFavorite(it.url, it.title) }; Unit }) else ({ Prefs.hideFav(st.host) })) { onOpen(st.url) } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (Prefs.hiddenFavs.isNotEmpty()) {
            Text(
                L("استعادة المواقع الافتراضية"), style = MaterialTheme.typography.bodySmall, color = ios.blue,
                modifier = Modifier.stagger(2, shown).padding(horizontal = 20.dp, vertical = 4.dp).clickable { Prefs.restoreFavs() }
            )
        }
        Spacer(Modifier.height(12.dp))
        IosGroup(Modifier.stagger(3, shown)) {
            IosCell(
                L("وضع الذكاء الاصطناعي"), onAi, sub = L("اسأل Google أي شيء"), chevron = true, divider = false,
                leading = { IosTile(ios.indigo) { Text("✦", fontSize = 15.sp, color = Color.White) } }
            )
        }
        StartHeader(L("تقرير الخصوصية"), Modifier.stagger(3, shown))
        Row(
            Modifier.stagger(4, shown).padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(ios.cell).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIcon(IosGlyph.ShieldFill, ios.blue, 40.dp, accent = ios.cell)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Shield.total.toString(), style = MaterialTheme.typography.headlineMedium, color = ios.label)
                Text(L("متتبعات تم منعها من تتبعك"), style = MaterialTheme.typography.bodySmall, color = ios.label2)
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.stagger(5, shown)) {
            ListRow(groupShape(0, 5), L("التنزيلات"), dlSub, onDownloads, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.blue) { IosIcon(IosGlyph.Download, Color.White, 18.dp) } }
            ListRow(groupShape(1, 5), L("التبويبات"), ("" + tabsCount + L(" مفتوحة")), onTabs, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.gray) { IosIcon(IosGlyph.Tabs, Color.White, 18.dp) } }
            ListRow(groupShape(2, 5), L("قائمة القراءة"), (Library.readingList.size.toString() + L(" صفحة")), { Library.initialTab = 2; onLibrary() }, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.blue) { IosIcon(IosGlyph.Book, Color.White, 18.dp) } }
            ListRow(groupShape(3, 5), L("المكتبة"), null, onLibrary, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.orange) { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) } }
            ListRow(groupShape(4, 5), L("الإعدادات"), null, onSettings, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.gray) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) } }
        }
    }
    }
}
