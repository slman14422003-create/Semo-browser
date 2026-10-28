package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.OutputStream

/**
 * تنزيل روابط blob: وdata: (ملفات تُنشأ داخل الصفحة نفسها، مثل التصدير والصور المضمّنة).
 * data: يُفكّ محلياً. blob: يُقرأ من الصفحة على دفعات (الصفحة وحدها ترى الـ blob) بلا أي جسر دائم:
 * التطبيق هو من يبدأ كل طلب عبر evaluateJavascript ثم يسأل عن النتيجة، فلا يستطيع موقع استدعاء شيء من التطبيق.
 */
object BlobDownload {
    private const val CHUNK = 768 * 1024          // بايت لكل دفعة (≈1MB بعد base64)
    private const val POLL_MS = 120L
    private const val LOAD_TIMEOUT = 120_000L
    private const val CHUNK_TIMEOUT = 40_000L
    private val main = Handler(Looper.getMainLooper())

    fun start(ctx: Context, wv: WebView?, url: String, cd: String?, mime: String?) {
        val app = ctx.applicationContext
        Downloader.init(app)
        when {
            url.startsWith("data:", true) -> {
                toast(app, L("جارٍ حفظ الملف…"))
                Work.io { runCatching { saveData(app, url, cd, mime) }.onFailure { Work.ui { toast(app, L("تعذّر حفظ الملف")) } } }
            }
            url.startsWith("blob:", true) && wv != null -> {
                toast(app, L("جارٍ حفظ الملف…"))
                fromBlob(app, wv, url, cd, mime)
            }
            else -> toast(app, L("تعذّر حفظ الملف"))
        }
    }

    // ───────────── data: ─────────────
    private fun saveData(app: Context, url: String, cd: String?, mime: String?) {
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("bad data url")
        val head = url.substring(5, comma)
        val body = Uri.decode(url.substring(comma + 1))
        val m = head.substringBefore(';').trim().ifBlank { mime.orEmpty() }.ifBlank { "application/octet-stream" }
        val bytes = if (head.contains(";base64", true)) Base64.decode(body, Base64.DEFAULT) else body.toByteArray(Charsets.UTF_8)
        val sink = Sink.open(app, nameFor(cd, m), m)
        try { sink.out.write(bytes); sink.finish() } catch (e: Throwable) { sink.abort(); throw e }
        done(app, sink)
    }

    // ───────────── blob: ─────────────
    private class Job(val app: Context, val wv: WebView, val url: String, val cd: String?, val mime: String?) {
        var id = ""
        var total = 0L
        var type = ""
        var off = 0L
        var sink: Sink? = null
        var deadline = 0L
    }

    private fun js(wv: WebView, code: String, cb: (String?) -> Unit) {
        runCatching { wv.evaluateJavascript(code) { r -> cb(unq(r)) } }.onFailure { cb(null) }
    }

    private fun unq(r: String?): String? {
        if (r == null || r == "null") return null
        return runCatching { JSONTokener(r).nextValue() as? String }.getOrNull()
    }

    private fun fromBlob(app: Context, wv: WebView, url: String, cd: String?, mime: String?) {
        val j = Job(app, wv, url, cd, mime)
        val code = "(function(u){var S=window.__novaBlob=window.__novaBlob||{};var id=Date.now().toString(36)+Math.random().toString(36).slice(2,8);" +
            "var st=S[id]={s:'load'};fetch(u).then(function(r){return r.blob()}).then(function(b){st.b=b;st.n=b.size;st.t=b.type||'';st.s='ready'})" +
            ".catch(function(e){st.s='err'});return id})(" + JSONObject.quote(url) + ")"
        js(wv, code) { id ->
            if (id.isNullOrEmpty()) fail(j, null) else { j.id = id; j.deadline = System.currentTimeMillis() + LOAD_TIMEOUT; waitReady(j) }
        }
    }

    private fun waitReady(j: Job) {
        js(j.wv, "(function(i){var s=window.__novaBlob&&window.__novaBlob[i];return s?s.s+'|'+(s.n||0)+'|'+(s.t||''):'gone'})(" + JSONObject.quote(j.id) + ")") { r ->
            val p = r?.split('|', limit = 3)
            when {
                p == null || p[0] == "gone" || p[0] == "err" -> fail(j, null)
                p[0] == "ready" -> {
                    j.total = p.getOrNull(1)?.toLongOrNull() ?: 0L
                    j.type = p.getOrNull(2).orEmpty()
                    val m = j.type.ifBlank { j.mime.orEmpty() }.ifBlank { "application/octet-stream" }
                    val sink = runCatching { Sink.open(j.app, nameFor(j.cd, m), m) }.getOrNull()
                    if (sink == null) fail(j, null)
                    else {
                        j.sink = sink
                        if (j.total <= 0L) finishBlob(j) else { j.deadline = System.currentTimeMillis() + CHUNK_TIMEOUT; requestChunk(j) }
                    }
                }
                System.currentTimeMillis() > j.deadline -> fail(j, null)
                else -> main.postDelayed({ waitReady(j) }, POLL_MS)
            }
        }
    }

    private fun requestChunk(j: Job) {
        val code = "(function(i,o,l){var s=window.__novaBlob&&window.__novaBlob[i];if(!s)return 0;s.c=null;var r=new FileReader();" +
            "r.onload=function(){var d=String(r.result);s.c=d.slice(d.indexOf(',')+1)};r.onerror=function(){s.c='!'};r.readAsDataURL(s.b.slice(o,o+l));return 1})(" +
            JSONObject.quote(j.id) + "," + j.off + "," + CHUNK + ")"
        js(j.wv, code) { waitChunk(j) }
    }

    private fun waitChunk(j: Job) {
        js(j.wv, "(function(i){var s=window.__novaBlob&&window.__novaBlob[i];if(!s)return '!';return (s.c===null||s.c===undefined)?'?':s.c})(" + JSONObject.quote(j.id) + ")") { r ->
            when {
                r == null || r == "!" -> fail(j, null)
                r == "?" -> if (System.currentTimeMillis() > j.deadline) fail(j, null) else main.postDelayed({ waitChunk(j) }, POLL_MS)
                else -> {
                    Work.io {
                        val n = runCatching {
                            val bytes = Base64.decode(r, Base64.DEFAULT)
                            j.sink!!.out.write(bytes)
                            bytes.size
                        }.getOrDefault(-1)
                        Work.ui {
                            if (n < 0) fail(j, null)
                            else {
                                j.off += CHUNK
                                if (j.off >= j.total) finishBlob(j)
                                else { j.deadline = System.currentTimeMillis() + CHUNK_TIMEOUT; requestChunk(j) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun release(j: Job) {
        runCatching { j.wv.evaluateJavascript("(function(i){try{delete window.__novaBlob[i]}catch(e){}})(" + JSONObject.quote(j.id) + ")", null) }
    }

    private fun finishBlob(j: Job) {
        release(j)
        val s = j.sink ?: return fail(j, null)
        Work.io {
            val ok = runCatching { s.finish() }.isSuccess
            if (ok) done(j.app, s) else { s.abort(); Work.ui { toast(j.app, L("تعذّر حفظ الملف")) } }
        }
    }

    private fun fail(j: Job, msg: String?) {
        release(j)
        j.sink?.let { s -> Work.io { s.abort() } }
        toast(j.app, msg ?: L("تعذّر حفظ الملف"))
    }

    // ───────────── مشترك ─────────────
    private fun nameFor(cd: String?, mime: String): String {
        var n = FileKind.nameFromHeader(cd)?.takeIf { it.isNotBlank() } ?: "download"
        if (!FileKind.hasExt(n)) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
            if (ext != null) n = "$n.$ext"
        }
        return n
    }

    private fun done(app: Context, s: Sink) {
        val name = Downloader.displayName(app, s.uri, s.name)
        Downloader.addFinished(name, s.uri, s.mime, s.size(app))
        Work.ui { toast(app, L("تم الحفظ — القائمة ⋮ ثم التنزيلات")) }
    }

    /** ملف قيد الكتابة في Download/Nova (IS_PENDING حتى الانتهاء). */
    private class Sink(val app: Context, val uri: Uri, val out: OutputStream, val name: String, val mime: String) {
        fun finish() {
            out.flush(); out.close()
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
        fun abort() {
            runCatching { out.close() }
            runCatching { app.contentResolver.delete(uri, null, null) }
        }
        fun size(c: Context): Long = runCatching { c.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)

        companion object {
            fun open(app: Context, name: String, mime: String): Sink {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nova")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val u = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("insert failed")
                val o = app.contentResolver.openOutputStream(u, "w") ?: run {
                    runCatching { app.contentResolver.delete(u, null, null) }
                    throw IOException("open failed")
                }
                return Sink(app, u, o.buffered(256 * 1024), name, mime)
            }
        }
    }
}
