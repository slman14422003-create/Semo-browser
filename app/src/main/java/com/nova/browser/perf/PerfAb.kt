package com.nova.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PerfAb {
    val groups = listOf("frames", "core", "page", "misc", "dark", "net")
    private var store: ConfStore? = null
    private var off by mutableStateOf<Set<String>>(emptySet())
    private var noFp by mutableStateOf(false)
    private var noDyn by mutableStateOf(false)

    fun init(c: Context) {
        if (!BuildConfig.DEBUG) return
        val s = ConfStore.open(c)
        store = s
        off = s.getString("ab.off", "").split(',').filter { it in groups }.toSet()
        noFp = s.getBoolean("ab.nofp", false)
        noDyn = s.getBoolean("ab.nodyn", false)
    }

    fun disabled(): List<String> = if (BuildConfig.DEBUG) off.toList().sorted() else emptyList()

    fun isOff(group: String): Boolean = BuildConfig.DEBUG && group in off

    fun fingerprintOff(): Boolean = BuildConfig.DEBUG && noFp

    fun dynamicToolbarOff(): Boolean = BuildConfig.DEBUG && noDyn

    fun toggle(group: String) {
        if (!BuildConfig.DEBUG || group !in groups) return
        off = if (group in off) off - group else off + group
        store?.putString("ab.off", off.joinToString(","))
    }

    fun toggleFingerprint() {
        if (!BuildConfig.DEBUG) return
        noFp = !noFp
        store?.putBoolean("ab.nofp", noFp)
    }

    fun toggleDynamicToolbar() {
        if (!BuildConfig.DEBUG) return
        noDyn = !noDyn
        store?.putBoolean("ab.nodyn", noDyn)
    }
}
