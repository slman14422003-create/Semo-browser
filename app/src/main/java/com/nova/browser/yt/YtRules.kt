package com.nova.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * محدّدات CSS الخاصة بصفحات يوتيوب (إخفاء Shorts، وضع التركيز) في ملف JSON منفصل بدل أن تكون مكتوبة داخل yt.js.
 * يوتيوب يغيّر أسماء عناصره من وقت لآخر، فيكفي تعديل assets/yt_rules.json في المستودع (ورفع رقم v) لتصل القواعد الجديدة
 * إلى التطبيقات المثبّتة دون إصدار جديد: يُجلب الملف مرة كل 12 ساعة كحد أقصى ويُستعمل عند فتح صفحة يوتيوب التالية.
 * أي ملف بعيد غير صالح يُتجاهل ويبقى الملف المضمَّن؛ والمحدّدات تُنقّى من الرموز التي قد تكسر CSS أو تحقن شيئاً.
 */
object YtRules {
    private const val FILE = "yt_rules.json"
    private const val SIG = "yt_rules.sig"
    private const val MAX = 32 * 1024
    private const val KEY_T = "ytrules_t"
    private const val EVERY = 12 * 3600_000L
    private val keys = listOf("shorts", "shortsHas", "focus")
    private val bad = Regex("[{};<>\\\\@]|url\\(|expression|import", RegexOption.IGNORE_CASE)

    @Volatile private var cached: JSONObject? = null

    private fun ok(s: String) = s.length in 2..140 && !bad.containsMatchIn(s)

    /** يبقي المفاتيح المعروفة فقط، ومحدّدات سليمة فقط. */
    private fun sanitize(o: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("v", o.optInt("v", 0))
        for (k in keys) {
            val src = o.optJSONArray(k)
            val clean = JSONArray()
            if (src != null) for (i in 0 until minOf(src.length(), 40)) {
                val s = src.optString(i, "").trim()
                if (ok(s)) clean.put(s)
            }
            out.put(k, clean)
        }
        return out
    }

    private fun load(c: Context): JSONObject {
        val bundled = runCatching { sanitize(JSONObject(Perf.asset(FILE))) }.getOrNull() ?: sanitize(JSONObject())
        val f = File(c.filesDir, FILE)
        val sf = File(c.filesDir, SIG)
        val local = if (f.exists() && sf.exists()) runCatching {
            val body = f.readBytes()
            if (PageBrain.verify(body, sf.readText().trim())) sanitize(JSONObject(String(body, Charsets.UTF_8))) else null
        }.getOrNull() else null
        val use = if (local != null && local.optInt("v") > bundled.optInt("v")) {
            // مفتاح فارغ في الملف المحدَّث يرجع إلى المضمَّن بدل أن يلغي القاعدة كلها
            for (k in keys) if (local.optJSONArray(k)?.length() == 0) local.put(k, bundled.optJSONArray(k))
            local
        } else bundled
        cached = use
        return use
    }

    private fun current(c: Context): JSONObject = cached ?: load(c)

    /** النص الذي يُحقن مكان __RULES__ في yt.js (JSON صالح كحرفية JavaScript). */
    fun json(c: Context): String = current(c).toString()

    fun version(c: Context): Int = current(c).optInt("v", 0)

    private fun fetch(u: String): ByteArray? {
        val cn = URL(u).openConnection() as HttpURLConnection
        cn.connectTimeout = 8000
        cn.readTimeout = 8000
        return try {
            if (cn.responseCode != 200) null
            else cn.inputStream.use { it.readNBytes(MAX + 1) }.takeIf { it.size <= MAX }
        } finally {
            cn.disconnect()
        }
    }

    /** يجلب نسخة أحدث إن وُجدت (مرة كل 12 ساعة). لا يعمل إن لم يُعرَّف مستودع التحديث. */
    fun refresh(c: Context) {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank()) return
        val conf = ConfStore.open(c)
        val now = System.currentTimeMillis()
        val last = conf.getString(KEY_T, "0").toLongOrNull() ?: 0L
        if (now - last < EVERY) return
        conf.putString(KEY_T, now.toString())
        val app = c.applicationContext
        Work.net {
            runCatching {
                val base = "https://github.com/$repo/releases/download/brain/"
                val body = fetch(base + FILE) ?: return@runCatching
                val sig = fetch(base + FILE + ".sig")?.toString(Charsets.UTF_8)?.trim() ?: return@runCatching
                if (!PageBrain.verify(body, sig)) return@runCatching
                val fresh = sanitize(JSONObject(String(body, Charsets.UTF_8)))
                if (fresh.optInt("v") <= version(app)) return@runCatching
                val tmp = File(app.filesDir, "$FILE.tmp")
                val stmp = File(app.filesDir, "$SIG.tmp")
                tmp.writeBytes(body)
                stmp.writeText(sig)
                val okSig = stmp.renameTo(File(app.filesDir, SIG))
                val okBody = tmp.renameTo(File(app.filesDir, FILE))
                if (!okSig || !okBody) { tmp.delete(); stmp.delete(); return@runCatching }
                cached = null
            }
        }
    }
}
