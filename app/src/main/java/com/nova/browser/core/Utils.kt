package com.nova.browser

import android.app.AlertDialog
import android.content.*
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import java.net.URLEncoder

fun normalize(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> Prefs.engines[Prefs.engine].second + URLEncoder.encode(t, "UTF-8")
    }
}

fun hostOf(u: String): String = runCatching { java.net.URI(u).host?.removePrefix("www.") }.getOrNull() ?: u

fun toast(c: Context, m: String) = Snacks.show(m)

/**
 * نسخ إلى الحافظة. sensitive=true (كلمات المرور): يُعلَّم المحتوى حساساً فلا يظهر في معاينة الحافظة ولا في سجل لوحة المفاتيح،
 * ويُمسح تلقائياً بعد 30 ثانية إن بقي كما هو (كان يبقى نصاً صريحاً تقرؤه أي لوحة مفاتيح/تطبيق مراقب للحافظة).
 */
fun copyText(c: Context, t: String, sensitive: Boolean = false) {
    val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("url", t)
    if (sensitive) runCatching {
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    cm.setPrimaryClip(clip)
    toast(c, L("تم النسخ"))
    if (sensitive) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        runCatching {
            val cur = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (cur == null || cur == t) cm.clearPrimaryClip()
        }
    }, 30_000)
}

fun shareText(c: Context, t: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }
    c.startActivity(Intent.createChooser(i, null))
}

/** منشئ حوار أصلي بهوية Nova (حواف مدوّرة وألوان التطبيق) بدل شكل النظام الافتراضي. */
fun novaDialog(c: Context): AlertDialog.Builder {
    val dark = ThemeMode.dark(
        (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    )
    return AlertDialog.Builder(android.view.ContextThemeWrapper(c, if (dark) R.style.Theme_Nova_Dialog_Dark else R.style.Theme_Nova_Dialog_Light))
}

fun choose(c: Context, items: List<Pair<String, () -> Unit>>) {
    novaDialog(c).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
}

fun httpsBlockedUri(httpUrl: String, token: String): String {
    val html = httpsBlockedHtml(httpUrl, token)
    return "data:text/html;charset=utf-8;base64," + android.util.Base64.encodeToString(html.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
}

fun httpsBlockedHtml(httpUrl: String, token: String): String {
    val dk = GeckoSettings.appIsDark()
    val bgc = if (dk) "#000000" else "#F2F2F7"
    val fgc = if (dk) "#FFFFFF" else "#000000"
    val subc = if (dk) "#EBEBF599" else "#3C3C4399"
    val lnk = if (dk) "#0A84FF" else "#007AFF"
    val warn = if (dk) "#FF453A" else "#FF3B30"
    val csch = if (dk) "dark" else "light"
    val u = android.text.TextUtils.htmlEncode(httpUrl)
    val t = android.text.TextUtils.htmlEncode(HttpsGate.CONTINUE_PREFIX + token)
    val dir = if (I18n.isEnglish()) "ltr" else "rtl"
    return """
<html data-nova-err><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
<style>
:root{color-scheme:$csch}body{font-family:-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;
justify-content:center;height:100vh;margin:0;padding:24px;text-align:center;direction:$dir;background:$bgc;color:$fgc}
h2{margin:8px;font-size:20px;font-weight:600}p{color:$subc;margin:6px;font-size:15px;max-width:420px}p.u{word-break:break-all}
button{margin-top:18px;padding:0;border:0;background:none;color:$lnk;font-size:17px}button.w{color:$warn}
</style></head><body data-c="$t"><div style="font-size:56px;opacity:.5">⚠︎</div><h2>${S(R.string.gk_https_title)}</h2>
<p class="u">$u</p><p>${S(R.string.gk_https_msg)}</p>
<button id="b">${S(R.string.gk_https_back)}</button><button id="c" class="w">${S(R.string.gk_https_continue)}</button>
<script>document.getElementById('b').onclick=function(){history.back()};document.getElementById('c').onclick=function(){location.href=document.body.getAttribute('data-c')}</script></body></html>"""
}

fun errorHtml(url: String, desc: String): String {
    val dk = GeckoSettings.appIsDark()
    val bgc = if (dk) "#000000" else "#F2F2F7"
    val fgc = if (dk) "#FFFFFF" else "#000000"
    val subc = if (dk) "#EBEBF599" else "#3C3C4399"
    val lnk = if (dk) "#0A84FF" else "#007AFF"
    val warn = if (dk) "#FF453A" else "#FF3B30"
    val csch = if (dk) "dark" else "light"
    // كل ما يأتي من الشبكة يُشفَّر قبل وضعه في الصفحة. الرابط يُحفظ في سمة data (مشفّرة) ويُقرأ بالسكربت،
    // بدل حقنه داخل onclick كما كان — فالرابط الخبيث لم يعد يستطيع كسر السمة وتنفيذ سكربت بأصل الموقع.
    val u = android.text.TextUtils.htmlEncode(url)
    val d = android.text.TextUtils.htmlEncode(desc)
    val dir = if (I18n.isEnglish()) "ltr" else "rtl"
    return """
<html data-nova-err><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
<style>
:root{color-scheme:$csch}body{font-family:-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;
justify-content:center;height:100vh;margin:0;padding:24px;text-align:center;direction:$dir;background:$bgc;color:$fgc}
h2{margin:8px;font-size:20px;font-weight:600}p{color:$subc;word-break:break-all;margin:4px;font-size:15px}
button{margin-top:22px;padding:0;border:0;background:none;color:$lnk;font-size:17px}
</style></head><body data-u="$u"><div style="font-size:56px;opacity:.5">⚠︎</div><h2>${L("تعذّر فتح الصفحة")}</h2>
<p>$d</p><p>$u</p>
<button id="r">${L("إعادة المحاولة")}</button>
<script>document.getElementById('r').onclick=function(){location.replace(document.body.getAttribute('data-u'))}</script></body></html>"""
}
