package com.nova.browser

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class UpdPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, INSTALLING, ERROR }

class UpdateInfo(val version: String, val notes: String, val size: Long, val apkUrl: String?, val page: String, val sha256: String = "")

/** تحديث من داخل التطبيق: فحص GitHub Releases ← تنزيل APK بشريط تقدّم ← تثبيت عبر PackageInstaller (يتطلب نفس مفتاح التوقيع). */
object Updater {
    var phase by mutableStateOf(UpdPhase.IDLE); private set
    var info by mutableStateOf<UpdateInfo?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf(""); private set
    /** هل تُعرض نافذة «يوجد تحديث» الآن. */
    var prompt by mutableStateOf(false); private set
    var unverified by mutableStateOf(false); private set

    private val main = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }
    private fun apkFile(c: Context, v: String = info?.version ?: "x") =
        File(File(c.cacheDir, "update").apply { mkdirs() }, "nova-update-" + v.filter { it.isLetterOrDigit() || it == '.' } + ".apk")

    /** True once per installed build: the caller then wipes caches so a fresh install never reuses stale files. */
    fun versionChanged(c: Context): Boolean {
        val sp = c.applicationContext.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val cur = BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE
        if (sp.getString("build", null) == cur) return false
        sp.edit().putString("build", cur).putLong("last", 0L).apply()
        return true
    }

    private class UntrustedUpdate(m: String) : java.io.IOException(m)

    private fun sigs(si: android.content.pm.SigningInfo): List<ByteArray> = si.apkContentsSigners.map { it.toByteArray() }

    private fun history(si: android.content.pm.SigningInfo): List<ByteArray> =
        if (si.hasPastSigningCertificates()) (si.signingCertificateHistory?.map { it.toByteArray() } ?: emptyList()) else emptyList()

    private fun verifyApk(c: Context, f: File): String? = try {
        val pm = c.packageManager
        val flag = PackageManager.GET_SIGNING_CERTIFICATES
        val arch = pm.getPackageArchiveInfo(f.absolutePath, flag)
        if (arch == null) "invalid package"
        else if (arch.packageName != c.packageName) "package mismatch"
        else {
            val mine = pm.getPackageInfo(c.packageName, flag).signingInfo
            val theirs = arch.signingInfo
            if (mine == null || theirs == null) "signature unreadable"
            else if (!SignatureCheck.accepts(sigs(mine), mine.hasMultipleSigners(), sigs(theirs), theirs.hasMultipleSigners(), history(theirs))) "signature mismatch"
            else null
        }
    } catch (_: Exception) { "verification failed" }

    private fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val buf = ByteArray(256 * 1024); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun verifyHash(f: File, expected: String): String? =
        if (expected.isEmpty()) "checksum missing"   // لا نثبّت ملفاً بلا بصمة SHA-256 منشورة مع الإصدار
        else if (sha256Hex(f) == expected) null else "checksum mismatch"

    private fun fetchSha(url: String): String {
        val cn = open(url, null, null, 8000, 8000)
        try {
            if (cn.responseCode != 200) return ""
            return UpdateTrust.sha256Of(String(cn.inputStream.use { it.readNBytes(4096) }, Charsets.UTF_8))
        } finally { cn.disconnect() }
    }

    private fun reject(f: File, why: String) {
        runCatching { f.delete() }
        error = why; phase = UpdPhase.ERROR
    }

    /** يقارن الإصدارات رقماً رقماً (1.10.0 أحدث من 1.9.0). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    @Volatile private var cacheRoot: File = File("/nonexistent")

    fun dismissPrompt() { prompt = false }
    fun showPrompt() { if (info != null) prompt = true }

    /** فحص التحديث؛ التلقائي (force=false) يحترم الإعداد وفاصل 12 ساعة ولا يُظهر أخطاء. */
    fun check(c: Context, force: Boolean = false) {
        val repo = BuildConfig.UPDATE_REPO
        if (phase == UpdPhase.CHECKING || phase == UpdPhase.DOWNLOADING || phase == UpdPhase.INSTALLING) return
        if (repo.isBlank()) { if (force) { error = "no repo"; phase = UpdPhase.ERROR }; return }
        val app = c.applicationContext
        val sp = app.getSharedPreferences("updater", Context.MODE_PRIVATE)
        if (!force) {
            if (!Prefs.autoUpdate) return
            if (System.currentTimeMillis() - sp.getLong("last", 0L) < 12L * 3600_000L) return
        }
        phase = UpdPhase.CHECKING; error = ""
        Work.net {
            try {
                val cn = open("https://api.github.com/repos/$repo/releases/latest", null, "application/vnd.github+json", 8000, 8000)
                if (cn.responseCode != 200) throw java.io.IOException("HTTP " + cn.responseCode)
                val j = JSONObject(cn.inputStream.bufferedReader().use { it.readText() })
                cn.disconnect()
                val tag = j.optString("tag_name")
                var apk: String? = null; var size = 0L; var sha = ""
                val assets = j.optJSONArray("assets")
                if (assets != null) {
                    var fallback: JSONObject? = null; var best: JSONObject? = null
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i); val n = a.optString("name").lowercase()
                        if (!n.endsWith(".apk")) continue
                        if (fallback == null) fallback = a
                        if (best == null && n.contains("release")) best = a
                    }
                    (best ?: fallback)?.let {
                        apk = it.optString("browser_download_url").takeIf { u -> UpdateTrust.trusted(u) }
                        size = it.optLong("size")
                        sha = UpdateTrust.sha256Of(it.optString("digest"))
                        val want = it.optString("name").lowercase() + ".sha256"
                        var comp = ""; var declared = false
                        for (k in 0 until assets.length()) {
                            val a = assets.getJSONObject(k)
                            if (a.optString("name").lowercase() != want) continue
                            val u = a.optString("browser_download_url")
                            if (!UpdateTrust.trusted(u)) throw java.io.IOException("untrusted checksum url")
                            declared = true; comp = fetchSha(u)
                            break
                        }
                        if (declared && comp.isEmpty()) throw java.io.IOException("checksum unreadable")
                        if (comp.isNotEmpty()) { if (sha.isNotEmpty() && sha != comp) throw java.io.IOException("checksum mismatch"); sha = comp }
                    }
                }
                sp.edit().putLong("last", System.currentTimeMillis()).apply()
                val newer = tag.isNotBlank() && isNewer(tag, BuildConfig.VERSION_NAME)
                ui {
                    if (newer) {
                        info = UpdateInfo(tag.removePrefix("v"), j.optString("body"), size, apk, j.optString("html_url"), sha)
                        val cf = apkFile(app, tag.removePrefix("v"))
                        unverified = sha.isEmpty()
                        phase = if (cf.exists() && cf.length() == size && size > 0) UpdPhase.READY else UpdPhase.AVAILABLE
                        prompt = true
                        Notif.updateAvailable(app, info?.version ?: tag)
                    } else { info = null; phase = UpdPhase.UP_TO_DATE }
                }
            } catch (e: Exception) {
                ui { if (force) { error = e.message ?: "error"; phase = UpdPhase.ERROR } else phase = UpdPhase.IDLE }
            }
        }
    }

    /** تنزيل الـ APK إلى كاش التطبيق ثم التثبيت تلقائياً. */
    fun download(c: Context) {
        val i = info ?: return
        val app = c.applicationContext
        val url = i.apkUrl
        if (url.isNullOrBlank() || !UpdateTrust.trusted(url)) { // لا ملف APK موثوق في الإصدار: نفتح صفحة الإصدار
            if (UpdateTrust.trusted(i.page)) runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        if (phase == UpdPhase.DOWNLOADING) return
        phase = UpdPhase.DOWNLOADING; progress = 0f; error = ""
        Work.net {
            try {
                val f = apkFile(app, i.version); val tmp = File(f.parentFile, f.name + ".part")
                f.parentFile?.listFiles()?.forEach { if (it != f && it != tmp) runCatching { it.delete() } }
                val prog = java.util.concurrent.atomic.AtomicLong(0)
                var lastUi = 0L
                val report = {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastUi > 200) { lastUi = now; val t = if (i.size > 0) i.size else 1L; val p = (prog.get().toFloat() / t).coerceIn(0f, 1f); ui { progress = p } }
                }
                val total = fetch(url, tmp, i.size, prog, report)
                if ((i.size > 0 && total != i.size) || tmp.length() != total) {
                    tmp.delete()
                    ui { error = "checksum mismatch"; phase = UpdPhase.ERROR }
                    return@net
                }
                val badHash = verifyHash(tmp, i.sha256)
                if (badHash != null) {
                    tmp.delete()
                    ui { error = badHash; phase = UpdPhase.ERROR }
                    return@net
                }
                if (f.exists()) f.delete()
                if (!tmp.renameTo(f)) throw java.io.IOException("rename failed")
                val bad = verifyApk(app, f)
                if (bad != null) { ui { reject(f, bad) }; return@net }
                val verified = i.sha256.isNotEmpty()
                ui { progress = 1f; unverified = !verified; phase = UpdPhase.READY; if (verified) install(app) }
            } catch (e: Exception) {
                ui { error = e.message ?: "error"; phase = UpdPhase.ERROR }
            }
        }
    }

    private fun open(u: String, range: String?, accept: String? = null, connect: Int = 10000, read: Int = 20000): HttpURLConnection {
        var cur = u
        var hops = 0
        while (true) {
            if (!UpdateTrust.trusted(cur)) throw UntrustedUpdate("untrusted redirect")
            val cn = URL(cur).openConnection() as HttpURLConnection
            cn.connectTimeout = connect; cn.readTimeout = read; cn.instanceFollowRedirects = false
            cn.setRequestProperty("Accept-Encoding", "identity")
            if (accept != null) cn.setRequestProperty("Accept", accept)
            if (range != null) cn.setRequestProperty("Range", range)
            val code = cn.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = cn.getHeaderField("Location")
                cn.disconnect()
                if (++hops > UpdateTrust.MAX_HOPS) throw UntrustedUpdate("too many redirects")
                cur = UpdateTrust.next(cur, loc) ?: throw UntrustedUpdate("untrusted redirect")
                continue
            }
            return cn
        }
    }

    /** Parallel ranged download (6 connections for big files, 4 for medium) with per-segment retry; falls back to one stream. Returns the byte count. */
    private fun fetch(url: String, tmp: File, expected: Long, prog: java.util.concurrent.atomic.AtomicLong, report: () -> Unit): Long {
        var finalUrl = url; var total = -1L
        try {
            val cn = open(url, "bytes=0-0")
            try {
                if (cn.responseCode == 206) {
                    total = cn.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                    finalUrl = cn.url.toString()
                }
            } finally { runCatching { cn.inputStream.close() }; cn.disconnect() }
        } catch (e: UntrustedUpdate) { throw e } catch (_: Exception) { }
        if (expected > 0 && total > 0 && total != expected) total = -1L
        if (total >= 1_000_000L) {
            val parts = if (total >= 8_000_000L) 6 else 4
            val seg = (total + parts - 1) / parts
            java.io.RandomAccessFile(tmp, "rw").use { raf ->
                raf.setLength(total)
                val ch = raf.channel
                val jobs = (0 until parts).map { k ->
                    val start = k * seg; val end = minOf(total - 1, start + seg - 1)
                    Work.dlPool.submit(java.util.concurrent.Callable {
                        var pos = start; var tries = 0
                        val buf = ByteArray(128 * 1024)
                        while (pos <= end) {
                            try {
                                val cn = open(finalUrl, "bytes=$pos-$end")
                                try {
                                    if (cn.responseCode != 206) throw java.io.IOException("HTTP " + cn.responseCode)
                                    cn.inputStream.use { ins ->
                                        while (pos <= end) {
                                            val n = ins.read(buf, 0, minOf(buf.size.toLong(), end - pos + 1).toInt()); if (n < 0) break
                                            var off = 0
                                            while (off < n) off += ch.write(java.nio.ByteBuffer.wrap(buf, off, n - off), pos + off)
                                            pos += n; prog.addAndGet(n.toLong()); report()
                                        }
                                    }
                                } finally { cn.disconnect() }
                                if (pos <= end) throw java.io.IOException("short read")
                            } catch (e: java.io.IOException) {
                                if (e is UntrustedUpdate || ++tries >= 4) throw e
                                Thread.sleep(400L * tries)
                            }
                        }
                    })
                }
                try { jobs.forEach { it.get() } } catch (e: Exception) { jobs.forEach { it.cancel(true) }; throw (e.cause as? java.io.IOException ?: java.io.IOException(e.message ?: "download failed")) }
            }
            return total
        }
        // single stream fallback
        prog.set(0)
        val cn = open(url, null)
        if (cn.responseCode != 200) throw java.io.IOException("HTTP " + cn.responseCode)
        var done = 0L
        cn.inputStream.use { ins -> tmp.outputStream().use { out ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                if (expected > 0 && done + n > expected + 1024) throw java.io.IOException("size mismatch")
                out.write(buf, 0, n); done += n; prog.set(done); report()
            }
        } }
        cn.disconnect()
        return done
    }

    /** يثبّت الملف المنزَّل عبر PackageInstaller؛ يطلب إذن «التثبيت من هذا المصدر» أولاً إن لزم. */
    fun install(c: Context, confirmed: Boolean = false) {
        val app = c.applicationContext
        cacheRoot = app.cacheDir
        val f = apkFile(app)
        if (!f.exists()) { error = "file missing"; phase = UpdPhase.AVAILABLE; return }
        val expected = info?.sha256.orEmpty()
        if (expected.isEmpty() && !confirmed) { showPrompt(); return }
        if (!app.packageManager.canRequestPackageInstalls()) {
            error = L("فعّل «السماح بالتثبيت من هذا المصدر» ثم اضغط تثبيت")
            phase = UpdPhase.READY
            runCatching { app.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        phase = UpdPhase.INSTALLING; error = ""
        Work.disk {
            try {
                val bad = verifyApk(app, f) ?: verifyHash(f, expected)
                if (bad != null) { ui { reject(f, bad) }; return@disk }
                val pi = app.packageManager.packageInstaller
                pi.mySessions.forEach { runCatching { pi.abandonSession(it.sessionId) } }   // drop stuck sessions from earlier attempts
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    s.openWrite("nova.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                    val intent = Intent(app, UpdateReceiver::class.java).setPackage(app.packageName)
                    val pend = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    s.commit(pend.intentSender)
                }
            } catch (e: Exception) {
                ui { error = e.message ?: "error"; phase = UpdPhase.READY }
            }
        }
    }

    /** نتيجة جلسة التثبيت من UpdateReceiver. عند النجاح يُعاد تشغيل التطبيق من النظام. */
    fun onInstallResult(status: Int, msg: String?) {
        ui {
            if (status == PackageInstaller.STATUS_SUCCESS) { phase = UpdPhase.IDLE; error = ""; Work.disk { runCatching { File(cacheRoot, "update").deleteRecursively() } } }
            else { error = msg ?: ("code " + status); phase = UpdPhase.READY }
        }
    }
}
