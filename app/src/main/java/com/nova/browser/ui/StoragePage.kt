package com.nova.browser

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.StorageController

private val siteDataNames = setOf("storage", "cookies.sqlite", "cookies.sqlite-wal", "webappsstore.sqlite", "webappsstore.sqlite-wal")

private fun treeSize(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

private fun siteDataSize(c: Context): Long = runCatching {
    File(c.filesDir, "mozilla").listFiles().orEmpty().filter { it.isDirectory }.sumOf { profile ->
        profile.listFiles().orEmpty().filter { it.name in siteDataNames }.sumOf { treeSize(it) }
    }
}.getOrDefault(0L)

private fun doneDownloads() = Downloader.tasks.filter { it.status == Downloader.DONE }

@Composable
fun StoragePage(onClearCache: () -> Unit) {
    val ctx = LocalContext.current
    val ios = LocalIos.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableIntStateOf(-1) }
    val measured by produceState<Pair<Long, Long>?>(null, tick) {
        value = withContext(Work.ioD) { CacheCleaner.size(ctx) to siteDataSize(ctx) }
    }
    val downloads = doneDownloads().sumOf { if (it.total > 0) it.total else it.downloaded }
    val cache = measured?.first ?: 0L
    val sites = measured?.second ?: 0L
    val total = (cache + downloads + sites).coerceAtLeast(1L)
    val titles = listOf(S(R.string.storage_cache), S(R.string.storage_downloads), S(R.string.storage_sites))
    val notes = listOf(S(R.string.storage_cache_note), S(R.string.storage_downloads_note), S(R.string.storage_sites_note))
    val sizes = listOf(cache, downloads, sites)
    val colors = listOf(ios.orange, ios.blue, ios.green)
    sizes.forEachIndexed { i, size ->
        SettingsHeader(titles[i])
        IosCard {
            Column(Modifier.padding(UiLayout.dp16), verticalArrangement = Arrangement.spacedBy(UiLayout.dp10)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (measured == null) "…" else fmtSize(size), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = ios.label)
                    IosPillButton(S(R.string.storage_clear), { confirm = i })
                }
                Box(Modifier.fillMaxWidth().height(UiLayout.dp8).clip(CircleShape).background(ios.fill3)) {
                    Box(Modifier.fillMaxWidth((size.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(colors[i]))
                }
            }
        }
        SettingsNote(notes[i])
    }
    val which = confirm
    if (which >= 0) NovaDialog(
        title = titles[which], icon = Icons.Default.Delete, danger = true, onDismiss = { confirm = -1 },
        confirmText = S(R.string.storage_clear), dismissText = S(R.string.storage_cancel),
        onConfirm = {
            confirm = -1
            when (which) {
                0 -> onClearCache()
                1 -> doneDownloads().forEach { Downloader.remove(it, true) }
                else -> runCatching {
                    NovaApp.runtime().storageController.clearData(
                        StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS
                    )
                }
            }
            scope.launch { delay(UiLayout.STORAGE_REMEASURE_MS); tick++ }
        }
    ) { DialogText(notes[which]) }
}
