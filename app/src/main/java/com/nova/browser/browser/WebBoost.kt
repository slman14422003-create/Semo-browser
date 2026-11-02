package com.nova.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.webkit.WebView
import java.util.Collections
import java.util.WeakHashMap

object WebBoost {
    @Volatile var slow = false
        private set
    private var started = false
    private val deferred: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    fun init(c: Context) {
        if (started) return
        started = true
        val cm = c.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            slow = isSlow(cm.getNetworkCapabilities(cm.activeNetwork))
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { slow = isSlow(caps) }
                override fun onLost(network: Network) { slow = false }
            })
        }
    }

    private fun isSlow(caps: NetworkCapabilities?): Boolean {
        val kbps = caps?.linkDownstreamBandwidthKbps ?: return false
        return kbps in 1..1500
    }

    fun onPageStart(v: WebView) {
        if (!Prefs.smart || !slow || Prefs.dataSaver) return
        val s = v.settings
        if (s.loadsImagesAutomatically && !s.blockNetworkImage) {
            s.loadsImagesAutomatically = false
            deferred.add(v)
        }
    }

    fun onProgress(v: WebView, p: Int) {
        if (p >= 70) restore(v)
    }

    fun onPageDone(v: WebView) = restore(v)

    private fun restore(v: WebView) {
        if (deferred.remove(v)) runCatching { v.settings.loadsImagesAutomatically = !Prefs.dataSaver }
    }
}
