package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object PageExtras {
    private val blockedSchemes = setOf(
        "javascript", "file", "content", "ftp", "view-source", "chrome", "about", "data", "blob", "http", "https", "intent"
    )
    private val schemeRe = Regex("[a-z][a-z0-9+.-]{1,30}")

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
