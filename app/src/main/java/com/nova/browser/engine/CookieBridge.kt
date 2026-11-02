package com.nova.browser

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object CookieBridge : WebExtension.MessageDelegate {
    const val APP = "semo"
    private const val TTL = 15_000L
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()
    private val seq = AtomicLong()

    @Volatile private var port: WebExtension.Port? = null
    @Volatile var userAgent: String = ""
        private set

    override fun onConnect(port: WebExtension.Port) {
        this.port = port
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                handle(message)
            }

            override fun onDisconnect(port: WebExtension.Port) {
                if (this@CookieBridge.port === port) this@CookieBridge.port = null
                pending.values.forEach { it.complete("") }
                pending.clear()
            }
        })
    }

    private fun handle(message: Any) {
        val o = message as? JSONObject ?: return
        when (o.optString("type")) {
            "hello" -> userAgent = o.optString("ua")
            "cookies" -> pending.remove(o.optString("id"))?.complete(o.optString("header"))
        }
    }

    fun header(url: String, timeoutMs: Long = 2500L): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val now = System.currentTimeMillis()
        cache[url]?.let { if (now - it.first < TTL) return it.second.ifEmpty { null } }
        val p = port ?: return null
        val id = seq.incrementAndGet().toString()
        val f = CompletableFuture<String>()
        pending[id] = f
        main.post {
            runCatching {
                p.postMessage(JSONObject().put("type", "cookies").put("id", id).put("url", url))
            }.onFailure { f.complete("") }
        }
        val r = try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        } finally {
            pending.remove(id)
        }
        if (r != null) {
            if (cache.size > 64) cache.clear()
            cache[url] = System.currentTimeMillis() to r
        }
        return r?.ifEmpty { null }
    }
}
