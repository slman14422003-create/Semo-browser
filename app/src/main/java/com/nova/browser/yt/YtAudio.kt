package com.nova.browser

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Audio-only playback engine used by the in-app mini player and the "Audio only" option.
 * The video's audio stream is resolved with NewPipeExtractor (YtFetch.info) on Work.media and played with
 * MediaPlayer, so the page can be left (or destroyed) without stopping the sound, and no video data is downloaded.
 * It feeds YtMedia, so the existing media notification / lock screen controls keep working unchanged.
 * When a track ends, the first related video (autoplay suggestion) is played next.
 */
object YtAudio {
    var active by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var author by mutableStateOf(""); private set
    var videoId by mutableStateOf(""); private set
    var posMs by mutableLongStateOf(0L); private set
    var durMs by mutableLongStateOf(0L); private set

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var mp: MediaPlayer? = null
    @Volatile private var gen = 0
    @Volatile private var nextUrl: String? = null
    private var app: Context? = null

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (active && playing && p != null) {
                posMs = runCatching { p.currentPosition.toLong() }.getOrDefault(posMs)
                YtMedia.pos = posMs
                main.postDelayed(this, 1000L)
            }
        }
    }

    /** Watch URL that resumes the video at the current position. */
    fun watchUrl(): String = "https://m.youtube.com/watch?v=$videoId&t=${posMs / 1000}s"

    private fun releasePlayer() {
        main.removeCallbacks(ticker)
        val p = mp; mp = null
        if (p != null) runCatching { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null); p.release() }
    }

    /** Frees the player and resets state WITHOUT touching YtMedia (YtMedia.stop calls this). */
    fun release() {
        gen++
        releasePlayer()
        active = false; playing = false; loading = false; nextUrl = null
    }

    /** User closed the mini player: stop everything including the notification. */
    fun close() {
        release()
        YtMedia.stop(false)
    }

    private fun sync(a: Context) {
        YtMedia.title = title.ifBlank { "YouTube" }; YtMedia.artist = author
        YtMedia.playing = playing; YtMedia.pos = posMs; YtMedia.dur = durMs; YtMedia.vw = 0; YtMedia.vh = 0
        if (videoId.isNotBlank()) YtMedia.loadArt("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        val svc = MediaService.instance
        if (svc != null) svc.refresh()
        else if (playing) runCatching { ContextCompat.startForegroundService(a, Intent(a, MediaService::class.java)) }
    }

    private fun fail(a: Context) {
        val was = active
        release()
        if (was) { toast(a, "Audio playback failed"); YtMedia.stop(false) }
    }

    /** Starts audio-only playback of [url] from [startMs]. Safe to call again to switch videos. */
    fun start(ctx: Context, url: String, startMs: Long, title0: String = "", author0: String = "") {
        val a = ctx.applicationContext; app = a
        val id = runCatching { YtFetch.canonical(url).substringAfter("v=") }.getOrNull() ?: return
        val g = ++gen
        releasePlayer()
        videoId = id; title = title0; author = author0
        posMs = startMs; durMs = 0L; active = true; loading = true; playing = false; nextUrl = null
        sync(a)
        Work.media {
            val info = runCatching { YtFetch.info(url) }.getOrNull()
            val opt = info?.audios?.let { l -> l.firstOrNull { it.ext == "m4a" } ?: l.firstOrNull() }
            if (g != gen) return@media
            if (info == null || opt == null) { Work.ui { if (g == gen) fail(a) }; return@media }
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                p.setWakeMode(a, PowerManager.PARTIAL_WAKE_LOCK)
                p.setDataSource(a, Uri.parse(opt.url), mapOf("User-Agent" to YT_UA))
                p.setOnPreparedListener { m ->
                    if (g != gen) { runCatching { m.release() } }
                    else {
                        durMs = runCatching { m.duration.toLong() }.getOrDefault(0L)
                        if (startMs > 0) runCatching { m.seekTo(startMs.toInt()) }
                        runCatching { m.start() }
                        loading = false; playing = true
                        main.removeCallbacks(ticker); main.postDelayed(ticker, 1000L)
                        sync(a)
                    }
                }
                p.setOnCompletionListener {
                    if (g == gen) {
                        val n = nextUrl
                        if (n != null) start(a, n, 0L) else { playing = false; posMs = 0L; sync(a) }
                    }
                }
                p.setOnErrorListener { _, _, _ -> if (g == gen) fail(a); true }
                Work.ui {
                    if (g != gen) { runCatching { p.release() } }
                    else {
                        title = info.title; author = info.author; nextUrl = info.next
                        mp = p
                        sync(a)
                        runCatching { p.prepareAsync() }.onFailure { fail(a) }
                    }
                }
            } catch (e: Throwable) {
                runCatching { p.release() }
                Work.ui { if (g == gen) fail(a) }
            }
        }
    }

    /** Same command names as the page player (play, pause, fwd, back, seek:<ms>, next, prev). */
    fun control(cmd: String) {
        Work.ui {
            val p = mp ?: return@ui
            when {
                cmd == "play" -> { runCatching { p.start() }; playing = true; main.removeCallbacks(ticker); main.postDelayed(ticker, 1000L) }
                cmd == "pause" -> { runCatching { p.pause() }; playing = false }
                cmd == "fwd" || cmd == "back" -> {
                    val t = (runCatching { p.currentPosition }.getOrDefault(0) + if (cmd == "fwd") 10_000 else -10_000).coerceIn(0, maxOf(0L, durMs).toInt())
                    runCatching { p.seekTo(t) }; posMs = t.toLong()
                }
                cmd.startsWith("seek:") -> cmd.substring(5).toLongOrNull()?.let { runCatching { p.seekTo(it.toInt()) }; posMs = it }
                cmd == "next" -> nextUrl?.let { n -> app?.let { start(it, n, 0L) } }
                cmd == "prev" -> { runCatching { p.seekTo(0) }; posMs = 0L }
            }
            app?.let { sync(it) }
        }
    }
}
