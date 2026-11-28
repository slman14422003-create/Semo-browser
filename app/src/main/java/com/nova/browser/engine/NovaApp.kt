package com.nova.browser

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: NovaApp
        private var rt: GeckoRuntime? = null

        private val mainH by lazy { Handler(Looper.getMainLooper()) }

        private fun installShield(r: GeckoRuntime, attempt: Int) {
            fun fail(why: String) {
                CrashLog.note("shield extension install failed (attempt " + (attempt + 1) + "): " + why)
                val d = ShieldRetry.delayMs(attempt)
                if (d != null) mainH.postDelayed({ installShield(r, attempt + 1) }, d)
                else {
                    ExtensionHealth.failed = true
                    Snacks.show(L("تعذّر تشغيل إضافة الحماية: حجب المتتبعات متوقف حالياً"), S(R.string.snack_retry)) { installShield(r, 0) }
                }
            }
            runCatching {
                r.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/semo/", "shield@semo.browser")
                    .accept({ ext ->
                        if (ext == null) fail("null extension")
                        else {
                            runCatching { ext.setMessageDelegate(CookieBridge, CookieBridge.APP) }
                                .onFailure { CrashLog.note("shield message delegate: " + it) }
                            ExtensionHealth.failed = false
                        }
                    }, { t -> fail(t?.toString() ?: "error") })
            }.onFailure { fail(it.toString()) }
        }

        @Synchronized
        fun runtime(): GeckoRuntime {
            rt?.let { return it }
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .aboutConfigEnabled(false)
                .build()
            val created = GeckoRuntime.create(instance, settings)
            created.settings.contentBlocking
                .setAntiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            GeckoSettings.update(created.settings)
            installShield(created, 0)
            rt = created
            return created
        }
    }
}

object ExtensionHealth {
    @Volatile var failed = false
}

object ShieldRetry {
    private val delays = longArrayOf(1000, 3000, 8000)

    @JvmStatic
    fun delayMs(attempt: Int): Long? = delays.getOrNull(attempt)
}
