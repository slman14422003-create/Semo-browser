package com.nova.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BrowserTab(val id: Int, startUrl: String = "") {
    var topColor by mutableStateOf<Int?>(null)
    var botColor by mutableStateOf<Int?>(null)
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf(L("تبويب جديد"))
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(Prefs.desktop)
    var finding by mutableStateOf(false)
    var findInfo by mutableStateOf("")
    var lastUsed = 0L
    var thumb by mutableStateOf<Bitmap?>(null)
    var tag by mutableIntStateOf(0)
    var pinned by mutableStateOf(false)
    var group by mutableIntStateOf(0)
    var epoch by mutableIntStateOf(0)
    var barCollapsed by mutableStateOf(false)
    private var scrollAcc = 0
    private var scrollLockUntil = 0L

    fun noteScroll(y: Int, dy: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (y <= UiLayout.SCROLL_TOP_PX) {
            scrollAcc = 0
            if (barCollapsed) setCollapsed(false, now)
            return
        }
        if (now < scrollLockUntil || dy == 0) return
        if ((dy > 0) != (scrollAcc > 0)) scrollAcc = 0
        scrollAcc += dy
        if (!barCollapsed && scrollAcc > UiLayout.SCROLL_COLLAPSE_PX) setCollapsed(true, now)
        else if (barCollapsed && -scrollAcc > UiLayout.SCROLL_EXPAND_PX) setCollapsed(false, now)
    }

    fun setCollapsed(on: Boolean, now: Long = android.os.SystemClock.elapsedRealtime()) {
        scrollAcc = 0
        scrollLockUntil = now + UiLayout.TOGGLE_LOCK_MS
        barCollapsed = on
    }

    fun barIsCompact(editing: Boolean): Boolean = barCollapsed && url.isNotBlank() && !editing && !finding

    fun resetScroll() {
        scrollAcc = 0
        barCollapsed = false
    }

    var engine: EngineTab? = null
    var engineView: View? = null
    var engineState: org.mozilla.geckoview.GeckoSession.SessionState? = null
    var crashes = 0
    var lastCrash = 0L
    var holdLoad = false
    var parentId = -1
    var popup = false
    @Volatile var failed = false
    @Volatile var failedAt = 0L
    fun markFailed() { failedAt = android.os.SystemClock.elapsedRealtime(); failed = true }
    fun clearFailedIfNew() { if (android.os.SystemClock.elapsedRealtime() - failedAt > 1500L) failed = false }
    @Volatile var noPull = false
    val isPrivate: Boolean get() = group == Groups.PRIVATE
}

class Handlers(
    val activity: ComponentActivity,
    val openTab: (String) -> Unit,
    val onDownload: (String, String?, String?, String?, String?, Boolean) -> Unit,
    val filePick: (Intent, (Array<Uri>?) -> Unit) -> Unit = { _, cb -> cb(null) },
    val androidPerm: (List<String>, () -> Unit) -> Unit = { _, c -> c() },
    val sitePerm: (String, String, List<String>, (Boolean) -> Unit) -> Unit = { _, _, _, d -> d(false) },
    val onGeckoFullScreen: (Boolean) -> Unit = {},
    val newPopup: (String) -> BrowserTab? = { null },
    val closePopup: (BrowserTab) -> Unit = {}
)
