package com.nova.browser

import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

object GeckoSettings {
    fun update(s: GeckoRuntimeSettings) {
        val zooms = Prefs.zoomValues
        val z = zooms[Prefs.textZoom.coerceIn(0, zooms.lastIndex)]
        s.setJavaScriptEnabled(Prefs.js)
        s.setAutomaticFontSizeAdjustment(false)
        s.setFontSizeFactor(z / 100f)
        s.setPreferredColorScheme(
            if (Prefs.siteDark) GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        )
    }

    fun update() {
        update(NovaApp.runtime().settings)
    }

    fun clearData() {
        NovaApp.runtime().storageController.clearData(StorageController.ClearFlags.ALL)
    }

    fun clearCache() {
        NovaApp.runtime().storageController.clearData(
            StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
        )
    }
}
