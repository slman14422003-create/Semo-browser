package com.nova.browser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * مستمع جلسة الوسائط في Gecko. وجوده يبقي Gecko يعامل الصفحة كمشغّل وسائط (يمنع تعليق الصوت أثناء الخلفية).
 * نوفّر أوامر تحكم احتياطية بالتشغيل والإيقاف.
 */
class GeckoMedia(private val tab: BrowserTab) : MediaSession.Delegate {
    @Volatile private var media: MediaSession? = null

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        media = mediaSession
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        if (media === mediaSession) media = null
    }

    override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
    }

    override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
    }

    override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
        media = null
    }

    fun play() { media?.play() }
    fun pause() { media?.pause() }
}
