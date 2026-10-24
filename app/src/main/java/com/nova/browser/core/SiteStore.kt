package com.nova.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

object SiteStore {
    data class Site(val zoom: Int = 0, val js: Int = -1, val images: Int = -1, val desktop: Int = -1, val safari: Int = -1, val fix: Int = -1) {
        val custom get() = zoom > 0 || js >= 0 || images >= 0 || desktop >= 0 || safari >= 0 || fix >= 0
    }

    val zoomSteps = intArrayOf(85, 100, 115, 130, 150, 175)

    private val mem = ConcurrentHashMap<String, Site>()
    @Volatile private var conf: ConfStore? = null

    fun init(c: Context) {
        conf = ConfStore.open(c)
    }

    private fun key(host: String) = "site.$host"

    private fun parse(raw: String): Site {
        if (raw.isBlank()) return Site()
        val m = raw.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: return@mapNotNull null) else null
        }.toMap()
        return Site(m["z"] ?: 0, m["j"] ?: -1, m["i"] ?: -1, m["d"] ?: -1, m["s"] ?: -1, m["f"] ?: -1)
    }

    private fun encode(s: Site) = "z=${s.zoom};j=${s.js};i=${s.images};d=${s.desktop};s=${s.safari};f=${s.fix}"

    fun get(host: String): Site {
        if (host.isBlank()) return Site()
        mem[host]?.let { return it }
        val s = parse(conf?.getString(key(host), "") ?: "")
        mem[host] = s
        return s
    }

    fun put(host: String, s: Site) {
        if (host.isBlank()) return
        mem[host] = s
        conf?.putString(key(host), if (s.custom) encode(s) else "")
    }

    fun reset(host: String) = put(host, Site())

    fun nextZoom(host: String): Int {
        val cur = get(host).zoom.let { if (it > 0) it else Prefs.zoomValues[Prefs.textZoom] }
        val i = zoomSteps.indexOfFirst { it > cur }
        val z = if (i < 0) zoomSteps.first() else zoomSteps[i]
        put(host, get(host).copy(zoom = z))
        return z
    }

    fun toggleJs(host: String): Boolean {
        val s = get(host)
        val on = if (s.js >= 0) s.js == 1 else Prefs.js
        put(host, s.copy(js = if (on) 0 else 1))
        return !on
    }

    fun toggleImages(host: String): Boolean {
        val s = get(host)
        val on = if (s.images >= 0) s.images == 1 else !Prefs.dataSaver
        put(host, s.copy(images = if (on) 0 else 1))
        return !on
    }

    fun toggleDesktop(host: String, current: Boolean): Boolean {
        put(host, get(host).copy(desktop = if (current) 0 else 1))
        return !current
    }

    var globalSafari: Boolean
        get() = conf?.getBoolean("uasafari", false) ?: false
        set(v) { conf?.putBoolean("uasafari", v) }

    fun wantSafari(host: String): Boolean {
        val s = get(host)
        return if (s.safari >= 0) s.safari == 1 else globalSafari
    }

    fun toggleFix(host: String): Boolean {
        val on = get(host).fix == 1
        put(host, get(host).copy(fix = if (on) 0 else 1))
        return !on
    }

    fun toggleSafari(host: String, current: Boolean): Boolean {
        put(host, get(host).copy(safari = if (current) 0 else 1))
        return !current
    }

    fun toggleGlobalSafari(): Boolean {
        globalSafari = !globalSafari
        return globalSafari
    }

    private fun sync(tab: BrowserTab, wv: WebView, host: String): Boolean {
        val s = get(host)
        tab.siteCustom = s.custom
        val st = wv.settings
        st.textZoom = if (s.zoom > 0) s.zoom else Prefs.zoomValues[Prefs.textZoom]
        st.javaScriptEnabled = if (s.js >= 0) s.js == 1 else Prefs.js
        val img = if (s.images >= 0) s.images == 1 else !Prefs.dataSaver
        st.loadsImagesAutomatically = img
        st.blockNetworkImage = !img
        var uaChanged = false
        if (s.desktop >= 0 && (s.desktop == 1) != tab.desktop) {
            tab.desktop = s.desktop == 1
            uaChanged = true
        }
        val safari = wantSafari(host)
        if (safari != tab.safari) {
            tab.safari = safari
            SafariMode.sync(wv, safari)
            uaChanged = true
        }
        if (uaChanged) applyUa(wv, tab.desktop, tab.safari)
        return uaChanged
    }

    fun apply(tab: BrowserTab, wv: WebView, host: String) {
        val s = get(host)
        if (!s.custom && !tab.siteCustom && tab.safari == globalSafari) return
        if (sync(tab, wv, host)) wv.reload()
    }

    fun refresh(tab: BrowserTab, wv: WebView?, host: String) {
        if (wv == null) return
        sync(tab, wv, host)
        wv.reload()
    }

    fun clearData(host: String) {
        if (host.isBlank()) return
        val ws = WebStorage.getInstance()
        val cm = CookieManager.getInstance()
        for (h in listOf(host, "www.$host")) {
            runCatching { ws.deleteOrigin("https://$h") }
            runCatching { ws.deleteOrigin("http://$h") }
            val url = "https://$h/"
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val name = part.substringBefore('=').trim()
                if (name.isEmpty()) continue
                val dead = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                cm.setCookie(url, dead)
                cm.setCookie(url, "$dead; Domain=$h")
                cm.setCookie(url, "$dead; Domain=.${h.removePrefix("www.")}")
            }
        }
        cm.flush()
    }
}
