package com.nova.browser

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object PageBrain {
    private class Rule(
        val id: String,
        val kinds: Set<String>,
        val hosts: List<String>,
        val fw: String,
        val min: Int,
        val max: Int,
        val heavy: Boolean,
        val css: String,
        val js: String
    )

    private class Profile(
        val kind: String,
        val fw: String,
        val ver: String,
        val heavy: Boolean,
        val origins: List<String>,
        val seen: Long
    )

    private val kinds = setOf("video", "news", "shop", "search", "docs", "code", "forum", "social", "app", "map", "json", "xml", "text", "file", "generic")

    private val staticHosts = mapOf(
        "tiktok.com" to "social", "instagram.com" to "social", "facebook.com" to "social", "x.com" to "social",
        "twitter.com" to "social", "linkedin.com" to "social", "reddit.com" to "forum", "stackoverflow.com" to "code",
        "github.com" to "code", "gitlab.com" to "code", "netflix.com" to "video", "twitch.tv" to "video",
        "vimeo.com" to "video", "dailymotion.com" to "video", "amazon.com" to "shop", "ebay.com" to "shop",
        "aliexpress.com" to "shop", "temu.com" to "shop", "shein.com" to "shop", "noon.com" to "shop",
        "developer.mozilla.org" to "docs", "wikipedia.org" to "docs", "maps.google.com" to "map",
        "mail.google.com" to "app", "outlook.live.com" to "app", "docs.google.com" to "app", "notion.so" to "app"
    )

    private val rawKinds = setOf("json", "xml", "text", "file")

    private val extKinds = mapOf(
        "json" to "json", "xml" to "xml", "rss" to "xml", "atom" to "xml", "txt" to "text", "md" to "text",
        "log" to "text", "csv" to "text", "pdf" to "file"
    )

    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val profiles = ConcurrentHashMap<String, Profile>()
    private val offlineSet = WeakHashMap<WebView, Boolean>()
    private val warned = HashSet<String>()
    private val fwRe = Regex("[a-z0-9 ._-]{0,24}")
    private val verRe = Regex("[0-9A-Za-z._-]{0,24}")
    private val originRe = Regex("https://[A-Za-z0-9.-]{1,100}")
    private val idRe = Regex("[a-z0-9-]{1,32}")

    @Volatile private var rules: List<Rule> = emptyList()
    @Volatile private var version = 0
    private var sp: SharedPreferences? = null
    private var dir: File? = null
    private var script: String? = null
    private var pub: ByteArray? = null
    private var writes = 0

    fun init(c: Context) {
        if (sp != null) return
        val app = c.applicationContext
        sp = app.getSharedPreferences("brain", Context.MODE_PRIVATE)
        dir = File(app.filesDir, "brain").apply { mkdirs() }
        script = runCatching { app.assets.open("brain.js").bufferedReader().use { it.readText() } }.getOrNull()
        pub = runCatching { Base64.decode(app.assets.open("brain.pub").bufferedReader().use { it.readText() }.trim(), Base64.DEFAULT) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
        runCatching { app.assets.open("brain-rules.json").bufferedReader().use { it.readText() } }.getOrNull()?.let { load(it) }
        val f = File(dir, "rules.json")
        val s = File(dir, "rules.sig")
        if (f.exists() && s.exists()) runCatching {
            val body = f.readBytes()
            if (verify(body, s.readText().trim())) load(String(body, Charsets.UTF_8))
        }
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun load(text: String): Boolean {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return false
        val v = j.optInt("v", 0)
        if (v <= version) return false
        val a = j.optJSONArray("rules") ?: return false
        val out = ArrayList<Rule>()
        for (i in 0 until a.length().coerceAtMost(200)) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!idRe.matches(id)) continue
            out.add(
                Rule(
                    id, strings(o.optJSONArray("kinds")).toSet(), strings(o.optJSONArray("hosts")), o.optString("fw"),
                    o.optInt("min", 0), o.optInt("max", Int.MAX_VALUE), o.optBoolean("heavy"),
                    o.optString("css").take(20000), o.optString("js").take(20000)
                )
            )
        }
        rules = out
        version = v
        return true
    }

    fun verify(body: ByteArray, sig: String): Boolean {
        val k = pub ?: return false
        return listOf("EC" to "SHA256withECDSA", "RSA" to "SHA256withRSA").any { (alg, sa) ->
            runCatching {
                val key = KeyFactory.getInstance(alg).generatePublic(X509EncodedKeySpec(k))
                Signature.getInstance(sa).run {
                    initVerify(key)
                    update(body)
                    verify(Base64.decode(sig, Base64.DEFAULT))
                }
            }.getOrDefault(false)
        }
    }

    private fun fetch(u: String): ByteArray? = runCatching {
        val cn = URL(u).openConnection() as HttpURLConnection
        cn.connectTimeout = 8000
        cn.readTimeout = 8000
        try {
            if (cn.responseCode != 200) return@runCatching null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            cn.inputStream.use {
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 262144) return@runCatching null
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            cn.disconnect()
        }
    }.getOrNull()

    fun refresh(force: Boolean = false) {
        val s = sp ?: return
        val repo = BuildConfig.UPDATE_REPO
        if (!Prefs.brain || repo.isBlank() || pub == null) return
        val now = System.currentTimeMillis()
        if (!force && now - s.getLong("last", 0L) < 6L * 3600_000L) return
        s.edit().putLong("last", now).apply()
        Work.net {
            val base = "https://github.com/$repo/releases/download/brain/"
            val body = fetch(base + "rules.json") ?: return@net
            val sig = fetch(base + "rules.json.sig")?.toString(Charsets.UTF_8)?.trim() ?: return@net
            if (!verify(body, sig)) return@net
            if (!load(String(body, Charsets.UTF_8))) return@net
            runCatching {
                File(dir, "rules.json").writeBytes(body)
                File(dir, "rules.sig").writeText(sig)
            }
        }
    }

    fun install(wv: WebView) {
        if (!Prefs.brain || !listenerOk) return
        val js = script ?: return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaBrain", setOf("*"), WebViewCompat.WebMessageListener { view, message, origin, isMain, _ ->
                if (!isMain || !Prefs.brain) return@WebMessageListener
                val host = origin.host?.removePrefix("www.") ?: return@WebMessageListener
                if (host != hostOf(view.url ?: "")) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 2048) return@WebMessageListener
                val o = runCatching { JSONObject(data) }.getOrNull() ?: return@WebMessageListener
                val kind = o.optString("k")
                if (kind !in kinds) return@WebMessageListener
                val fw = o.optString("fw").takeIf { fwRe.matches(it) } ?: ""
                val ver = o.optString("v").takeIf { verRe.matches(it) } ?: ""
                val heavy = o.optInt("n") > 6000
                val origins = o.optJSONArray("o")?.let { a ->
                    (0 until a.length().coerceAtMost(4)).mapNotNull { i -> a.optString(i).takeIf { s -> originRe.matches(s) } }
                } ?: emptyList()
                learn(host, Profile(kind, fw, ver, heavy, origins, System.currentTimeMillis()))
                apply(view, host, kind, fw, ver, heavy)
            })
            if (docStart) ScriptBundle.add(wv, js)
        }
    }

    private fun staticKind(url: String): String? {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val host = (u.host ?: return null).removePrefix("www.")
        val ext = (u.path ?: "").substringAfterLast('.', "").lowercase()
        extKinds[ext]?.let { return it }
        return staticHosts.entries.firstOrNull { host == it.key || host.endsWith("." + it.key) }?.value
    }

    private fun profile(host: String): Profile? {
        profiles[host]?.let { return it }
        val raw = sp?.getString("p.$host", null) ?: return null
        val f = raw.split('|')
        if (f.size < 6) return null
        return Profile(f[0], f[1], f[2], f[3] == "1", f[4].split(',').filter { originRe.matches(it) }, f[5].toLongOrNull() ?: 0L)
            .also { profiles[host] = it }
    }

    private fun learn(host: String, p: Profile) {
        profiles[host] = p
        val s = sp ?: return
        s.edit().putString("p.$host", "${p.kind}|${p.fw}|${p.ver}|${if (p.heavy) 1 else 0}|${p.origins.joinToString(",")}|${p.seen}").apply()
        if (++writes % 50 == 0) Work.disk {
            val keys = s.all.filterKeys { it.startsWith("p.") }
            if (keys.size > 400) {
                val old = keys.entries.sortedBy { (it.value as? String)?.substringAfterLast('|')?.toLongOrNull() ?: 0L }.take(100).map { it.key }
                s.edit().apply { old.forEach { remove(it) } }.apply()
                old.forEach { profiles.remove(it.removePrefix("p.")) }
            }
        }
    }

    fun onStart(v: WebView, url: String) {
        if (!Prefs.brain || !url.startsWith("http")) return
        if (!docStart) script?.let { v.evaluateJavascript(it, null) }
        val host = hostOf(url)
        profile(host)?.origins?.forEach { Speculate.preconnect(v, it) }
        val offline = runCatching { v.context.getSystemService(ConnectivityManager::class.java).activeNetwork == null }.getOrDefault(false)
        runCatching {
            if (offline && offlineSet[v] != true) {
                offlineSet[v] = true
                v.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            } else if (!offline && offlineSet.remove(v) == true) {
                v.settings.cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
    }

    fun onCommit(v: WebView, url: String) {
        if (!Prefs.brain || !url.startsWith("http")) return
        val host = hostOf(url)
        val p = profile(host)
        val kind = staticKind(url) ?: p?.kind ?: return
        apply(v, host, kind, p?.fw ?: "", p?.ver ?: "", p?.heavy ?: false)
    }

    private fun fwOk(r: Rule, host: String, fw: String, ver: String): Boolean {
        if (r.fw.isEmpty()) return true
        if (r.fw != fw) return false
        val major = ver.takeWhile { it.isDigit() }.toIntOrNull() ?: return true
        if (major in r.min..r.max) return true
        if (warned.add("$host|${r.id}|$major")) Security.log("Brain", "Rule ${r.id} skipped for $host: $fw $ver is outside the verified range")
        return false
    }

    private fun apply(v: WebView, host: String, kind: String, fw: String, ver: String, heavy: Boolean) {
        if (kind !in rawKinds && SiteRules.relaxed(host)) return
        val hit = rules.filter { r ->
            (r.kinds.isEmpty() || "*" in r.kinds || kind in r.kinds) &&
                (r.hosts.isEmpty() || r.hosts.any { host == it || host.endsWith(".$it") }) &&
                (!r.heavy || heavy) && fwOk(r, host, fw, ver)
        }
        val sb = StringBuilder("(function(){try{var d=document,h=d.head||d.documentElement,keep={};")
        for (r in hit) {
            if (r.css.isNotEmpty()) {
                val id = "nova-brain-${r.id}"
                val css = JSONObject.quote(r.css)
                sb.append("(function(){keep['$id']=1;var s=d.getElementById('$id');if(!s){s=d.createElement('style');s.id='$id';h.appendChild(s)}if(s.textContent!==$css)s.textContent=$css})();")
            }
            if (r.js.isNotEmpty()) {
                sb.append("window.__novaBrainJs=window.__novaBrainJs||{};if(window.__novaBrainJs['${r.id}']!==$version){window.__novaBrainJs['${r.id}']=$version;try{(function(){${r.js}})()}catch(e){}}")
            }
        }
        sb.append("var all=d.querySelectorAll('style[id^=\"nova-brain-\"]');for(var i=0;i<all.length;i++)if(!keep[all[i].id])all[i].remove()}catch(e){}})()")
        v.evaluateJavascript(sb.toString(), null)
    }
}
