package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Base64InputStream
import android.webkit.MimeTypeMap
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object FileSaver {
    private const val BUF = 128 * 1024
    private const val MAX_DATA_BYTES = 200L * 1024 * 1024

    fun save(ctx: Context, url: String, body: InputStream?, cd: String?, mime: String?, length: Long = -1L) {
        val app = ctx.applicationContext
        Downloader.init(app)
        val data = url.startsWith("data:", true)
        val first = if (data) nameFor(cd, mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()) else Downloader.smartName(url, cd, mime)
        val task = Downloader.startStream(app, first, Downloader.smartMime(first, mime), length)
        Snacks.show(L("بدأ التنزيل — القائمة ⋮ ثم التنزيلات"), S(R.string.snack_open)) { openPanel(it, "downloads") }
        Work.io {
            runCatching {
                if (data) {
                    runCatching { body?.close() }
                    saveData(app, task, url, cd, mime)
                } else {
                    val input = body ?: throw IOException("no body")
                    input.use {
                        val name = task.name
                        write(app, task, name, Downloader.smartMime(name, mime), it, Long.MAX_VALUE, length)
                    }
                }
            }.onFailure { e ->
                Downloader.failStream(task, e.message ?: "error")
                Work.ui { toast(app, L("تعذّر حفظ الملف")) } }
        }
    }

    private fun saveData(app: Context, task: DlTask, url: String, cd: String?, mime: String?) {
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("bad data url")
        val head = url.substring(5, comma)
        val type = head.substringBefore(';').trim().ifBlank { mime?.substringBefore(';')?.trim().orEmpty() }
            .lowercase().ifBlank { "application/octet-stream" }
        val raw = PercentStream(url, comma + 1)
        val input = if (head.contains(";base64", true)) Base64InputStream(raw, Base64.DEFAULT) else raw
        input.use { write(app, task, nameFor(cd, type), type, it, MAX_DATA_BYTES, -1L) }
    }

    private fun nameFor(cd: String?, mime: String): String {
        val n = FileKind.nameFromHeader(cd)?.takeIf { it.isNotBlank() } ?: "download"
        if (FileKind.hasExt(n)) return n
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return n
        return "$n.$ext"
    }

    private fun write(app: Context, task: DlTask, name: String, mime: String, input: InputStream, limit: Long, expected: Long) {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nova")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert failed")
        var total = 0L
        try {
            val out: OutputStream = resolver.openOutputStream(uri, "w") ?: throw IOException("open failed")
            out.use {
                val buf = ByteArray(BUF)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (task.stopReason == 2) throw IOException("cancelled")
                    total += n
                    if (total > limit) throw IOException("too large")
                    it.write(buf, 0, n)
                    task.downloaded = total
                }
            }
            if (expected >= 0 && total != expected) throw IOException("truncated")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        Downloader.finishStream(task, Downloader.displayName(app, uri, name), uri, total)
        Work.ui { toast(app, L("تم الحفظ — القائمة ⋮ ثم التنزيلات")) }
    }

    private class PercentStream(private val s: String, private var i: Int) : InputStream() {
        private var pending = ByteArray(0)
        private var p = 0

        override fun read(): Int {
            if (p < pending.size) return pending[p++].toInt() and 0xFF
            if (i >= s.length) return -1
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) { i += 3; return hi * 16 + lo }
            }
            if (c.code < 128) { i++; return c.code }
            val n = Character.charCount(s.codePointAt(i))
            pending = s.substring(i, i + n).toByteArray(Charsets.UTF_8)
            i += n
            p = 1
            return pending[0].toInt() and 0xFF
        }
    }
}
