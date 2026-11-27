package com.nova.browser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** نتيجة الاستيراد: أعداد العناصر المضافة وملاحظات (ملفات لم يُتعرَّف عليها مثلاً). */
class ImportResult {
    var bookmarks = 0
    var history = 0
    var passwords = 0
    var skipped = 0
    val notes = ArrayList<String>()
}

/**
 * استيراد بيانات Chrome من الملفات التي يصدّرها Chrome نفسه (أندرويد يمنع قراءتها مباشرة):
 * CSV كلمات المرور، Bookmarks.html، ملف Bookmarks (JSON)، ملف History (SQLite) أو History.json، وأرشيف ZIP من Google Takeout.
 */
object ChromeImport {
    private const val MAX_FILE = 64L * 1024 * 1024      // حدّ أمان لحجم الملف الواحد
    private const val CHROME_EPOCH_MS = 11644473600000L  // الفرق بين 1601 و1970 بالمللي ثانية

    /** يعالج كل الملفات المختارة ويعيد ملخصاً؛ يُستدعى من خيط خلفي. */
    fun run(c: Context, uris: List<Uri>): ImportResult {
        val r = ImportResult()
        for (u in uris) {
            val name = displayName(c, u)
            val bytes = runCatching { read(c, u) }.getOrNull()
            if (bytes == null) { r.skipped++; r.notes.add("$name: غير قابل للقراءة / unreadable"); continue }
            runCatching { handle(c, name, bytes, r) }.onFailure { r.skipped++; r.notes.add(name + ": " + (it.message ?: "error")) }
            bytes.fill(0)
        }
        return r
    }

    fun needsAuth(c: Context, uris: List<Uri>): Boolean = uris.any {
        val n = displayName(c, it).lowercase()
        !(n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".json") || n == "history" || n == "bookmarks")
    }

    private fun displayName(c: Context, u: Uri): String = runCatching {
        c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: (u.lastPathSegment ?: "file")

    private fun read(c: Context, u: Uri): ByteArray {
        c.contentResolver.openInputStream(u)!!.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024); var total = 0L
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                total += n; if (total > MAX_FILE) throw java.io.IOException("file too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    private fun isSqlite(b: ByteArray) = b.size > 16 && String(b, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3"

    private fun handle(c: Context, name: String, b: ByteArray, r: ImportResult) {
        val low = name.lowercase()
        when {
            isZip(b) -> handleZip(c, b, r)
            isSqlite(b) -> importHistoryDb(c, b, r)
            else -> {
                val head = String(b, 0, minOf(b.size, 400), Charsets.UTF_8).removePrefix("\uFEFF").lowercase()
                if (low.endsWith(".csv") || head.startsWith("name,url,username,password") || (head.contains("url") && head.contains("password") && head.contains(","))) {
                    importPasswords(b, r)
                    return
                }
                val text = String(b, Charsets.UTF_8).removePrefix("\uFEFF")
                when {
                    head.contains("<!doctype netscape-bookmark") || low.endsWith(".html") || low.endsWith(".htm") -> importBookmarksHtml(text, r)
                    text.trimStart().startsWith("{") && text.contains("\"roots\"") -> importBookmarksJson(text, r)
                    text.trimStart().startsWith("{") && text.contains("Browser History") -> importHistoryJson(text, r)
                    else -> { r.skipped++; r.notes.add("$name: صيغة غير معروفة / unknown format") }
                }
            }
        }
    }

    private fun handleZip(c: Context, b: ByteArray, r: ImportResult) {
        ZipInputStream(ByteArrayInputStream(b)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val n = e.name.lowercase()
                val interesting = n.endsWith("bookmarks.html") || n.endsWith("history.json") || n.endsWith("/bookmarks") || n == "bookmarks" || n.endsWith("passwords.csv")
                if (!interesting) { z.closeEntry(); continue }
                val data = z.readBytes()
                runCatching { handle(c, e.name.substringAfterLast('/'), data, r) }.onFailure { r.skipped++ }
                data.fill(0)
            }
        }
    }

    // ───────────── كلمات المرور (CSV) ─────────────
    private fun records(ch: CharArray, n: Int, onRow: (List<String>) -> Unit) {
        val row = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        var any = false
        while (i < n) {
            val x = ch[i]
            when {
                x == '"' && q && i + 1 < n && ch[i + 1] == '"' -> { sb.append('"'); i++ }
                x == '"' -> { q = !q; any = true }
                x == ',' && !q -> { row.add(sb.toString()); sb.setLength(0); any = true }
                (x == '\n' || x == '\r') && !q -> {
                    if (x == '\r' && i + 1 < n && ch[i + 1] == '\n') i++
                    if (any || sb.isNotEmpty()) { row.add(sb.toString()); onRow(ArrayList(row)) }
                    row.clear(); sb.setLength(0); any = false
                }
                else -> sb.append(x)
            }
            i++
        }
        if (any || sb.isNotEmpty()) { row.add(sb.toString()); onRow(ArrayList(row)) }
        row.clear()
        sb.setLength(0)
    }

    private fun importPasswords(b: ByteArray, r: ImportResult) {
        if (!Vault.fresh() || !Vault.unlocked) { r.skipped++; r.notes.add(S(R.string.import_auth_required)); return }
        val dec = Charsets.UTF_8.newDecoder()
        val buf = java.nio.CharBuffer.allocate(b.size + 1)
        dec.decode(java.nio.ByteBuffer.wrap(b), buf, true)
        val ch = buf.array()
        val n = buf.position()
        var start = 0
        if (n > 0 && ch[0] == '\uFEFF') start = 1
        val body = if (start == 0) ch else ch.copyOfRange(start, n)
        val len = if (start == 0) n else n - start
        val creds = ArrayList<Triple<String, String, String>>()
        var iUrl = -1; var iUser = -1; var iPass = -1
        var header = true
        var bad = false
        try {
            records(body, len) { f ->
                if (bad) return@records
                if (header) {
                    header = false
                    val h = f.map { it.trim().lowercase() }
                    iUrl = h.indexOf("url"); iUser = h.indexOf("username"); iPass = h.indexOf("password")
                    if (iUrl < 0 || iUser < 0 || iPass < 0) { bad = true; r.skipped++; r.notes.add(S(R.string.import_columns_missing)) }
                    return@records
                }
                val host = Vault.cleanHost(f.getOrElse(iUrl) { "" })
                val user = f.getOrElse(iUser) { "" }; val pass = f.getOrElse(iPass) { "" }
                if (host.isBlank() || pass.isEmpty()) { r.skipped++; return@records }
                creds.add(Triple(host, user, pass))
            }
            if (bad || creds.isEmpty()) return
            var ok = false
            onMain { ok = Vault.importAll(creds) }
            if (ok) r.passwords += creds.size else { r.skipped += creds.size; r.notes.add(S(R.string.import_save_failed) + ": " + Vault.errorText()) }
        } finally {
            creds.clear()
            ch.fill('\u0000')
            body.fill('\u0000')
        }
    }

    // ───────────── المفضلة ─────────────
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").trim()

    private val htmlToken = Regex("<H3[^>]*>(.*?)</H3>|</DL>|<A\\s[^>]*?HREF=\"([^\"]*)\"[^>]*>(.*?)</A>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun importBookmarksHtml(text: String, r: ImportResult) {
        val stack = ArrayList<String>(); val list = ArrayList<Bookmark>(); val now = System.currentTimeMillis()
        for (m in htmlToken.findAll(text)) {
            val tag = m.value
            when {
                tag.startsWith("</", true) -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                tag.startsWith("<H3", true) -> stack.add(unescape(m.groupValues[1]))
                else -> {
                    val url = unescape(m.groupValues[2])
                    if (url.startsWith("http")) list.add(Bookmark(url, unescape(m.groupValues[3]).ifBlank { url }, stack.joinToString(" / "), now))
                    else r.skipped++
                }
            }
        }
        r.bookmarks += Library.addBookmarks(list)
    }

    private fun importBookmarksJson(text: String, r: ImportResult) {
        val list = ArrayList<Bookmark>(); val now = System.currentTimeMillis()
        fun walk(o: JSONObject, folder: String) {
            when (o.optString("type")) {
                "url" -> { val u = o.optString("url"); if (u.startsWith("http")) list.add(Bookmark(u, o.optString("name").ifBlank { u }, folder, now)) else r.skipped++ }
                "folder" -> {
                    val f = if (folder.isEmpty()) o.optString("name") else folder + " / " + o.optString("name")
                    val ch = o.optJSONArray("children") ?: return
                    for (i in 0 until ch.length()) ch.optJSONObject(i)?.let { walk(it, f) }
                }
            }
        }
        val roots = JSONObject(text).optJSONObject("roots") ?: return
        for (k in roots.keys()) roots.optJSONObject(k)?.let { walk(it, "") }
        r.bookmarks += Library.addBookmarks(list)
    }

    // ───────────── السجل ─────────────
    private fun importHistoryJson(text: String, r: ImportResult) {
        val arr: JSONArray = JSONObject(text).optJSONArray("Browser History") ?: return
        val list = ArrayList<HistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("url")
            if (!u.startsWith("http")) { r.skipped++; continue }
            list.add(HistoryItem(u, o.optString("title").ifBlank { u }, o.optLong("time_usec") / 1000L))
        }
        r.history += Library.addHistory(list)
    }

    private fun importHistoryDb(c: Context, b: ByteArray, r: ImportResult) {
        val f = File(c.cacheDir, "import-history.db")
        try {
            f.writeBytes(b)
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(f.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            val list = ArrayList<HistoryItem>()
            try {
                db.rawQuery("SELECT url, title, last_visit_time FROM urls ORDER BY last_visit_time DESC LIMIT 5000", null).use { cur ->
                    while (cur.moveToNext()) {
                        val u = cur.getString(0) ?: continue
                        if (!u.startsWith("http")) { r.skipped++; continue }
                        list.add(HistoryItem(u, cur.getString(1)?.ifBlank { null } ?: u, cur.getLong(2) / 1000L - CHROME_EPOCH_MS))
                    }
                }
            } finally { db.close() }
            r.history += Library.addHistory(list)
        } finally { f.delete() }
    }

    /** تعديل مخزن كلمات المرور على الخيط الرئيسي (قائمة Compose) وانتظار انتهائه. */
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { block(); return }
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post { try { block() } finally { latch.countDown() } }
        latch.await()
    }
}
