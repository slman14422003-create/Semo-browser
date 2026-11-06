package com.nova.browser

import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.webkit.HttpAuthHandler
import android.webkit.JsResult
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object PageExtras {
    private val main = Handler(Looper.getMainLooper())
    private var warm: WebView? = null
    private var used = false

    private val blockedSchemes = setOf(
        "javascript", "file", "content", "ftp", "view-source", "chrome", "about", "data", "blob", "http", "https", "intent"
    )
    private val schemeRe = Regex("[a-z][a-z0-9+.-]{1,30}")

    fun preheat(c: Context) {
        val app = c.applicationContext
        main.postDelayed({
            if (used || warm != null) return@postDelayed
            runCatching {
                val w = WebView(MutableContextWrapper(app))
                w.loadUrl("about:blank")
                warm = w
                main.postDelayed({ release() }, 20_000)
            }
        }, 1500)
    }

    fun engineInUse() {
        used = true
        if (warm != null) main.postDelayed({ release() }, 1500)
    }

    private fun release() {
        val w = warm ?: return
        warm = null
        runCatching { w.destroy() }
    }

    fun httpAuth(ctx: Context, handler: HttpAuthHandler, host: String?, realm: String?) {
        val key = host.orEmpty()
        if (!Shield.dialogAllowed(key)) {
            handler.cancel()
            return
        }
        val density = ctx.resources.displayMetrics.density
        val user = EditText(ctx).apply {
            hint = "Username"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val pass = EditText(ctx).apply {
            hint = "Password"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(user)
            addView(pass)
        }
        novaDialog(ctx)
            .setTitle(key)
            .setMessage(realm?.take(120).orEmpty())
            .setView(box)
            .setPositiveButton("Sign in") { _, _ -> handler.proceed(user.text.toString(), pass.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    fun beforeUnload(ctx: Context, url: String, r: JsResult) {
        val host = hostOf(url)
        if (!Shield.dialogAllowed(host)) {
            r.confirm()
            return
        }
        novaDialog(ctx)
            .setTitle("Leave this page?")
            .setMessage("Changes you made may not be saved.")
            .setPositiveButton("Leave") { _, _ -> r.confirm() }
            .setNegativeButton("Stay") { _, _ -> r.cancel() }
            .setOnCancelListener { r.cancel() }
            .show()
    }

    fun openExternal(ctx: Context, u: Uri) {
        val scheme = u.scheme?.lowercase() ?: return
        if (scheme in blockedSchemes || !schemeRe.matches(scheme)) return
        if (!Shield.dialogAllowed(scheme)) return
        novaDialog(ctx)
            .setTitle("Open in another app?")
            .setMessage(u.toString().take(160))
            .setPositiveButton("Open") { _, _ ->
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, u)
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { toast(ctx, "No app found to open this link") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun pinShortcut(ctx: Context, tab: BrowserTab) {
        val url = tab.url
        if (url.isBlank()) return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast(ctx, "Not supported by this launcher")
            return
        }
        val host = hostOf(url)
        val icon = Favicons.get(host)?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)
        val label = tab.title.take(24).ifBlank { host }
        val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url))
        val info = ShortcutInfoCompat.Builder(ctx, "site:" + host + ":" + url.hashCode())
            .setShortLabel(label)
            .setLongLabel(tab.title.ifBlank { host })
            .setIcon(icon)
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}
