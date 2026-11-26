package com.nova.browser

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

object FrameLog {
    private const val TAG = "SemoFrames"
    private val handler by lazy { Handler(HandlerThread("semo-frames").apply { start() }.looper) }
    private val frames = ArrayList<Float>()
    private var name: String? = null
    private var late = 0
    private val finish = Runnable { report() }

    fun attach(window: Window) {
        if (!BuildConfig.DEBUG) return
        window.addOnFrameMetricsAvailableListener({ _, m, _ ->
            if (name != null) {
                val total = m.getMetric(FrameMetrics.TOTAL_DURATION)
                frames.add(total / 1_000_000f)
                if (total > m.getMetric(FrameMetrics.DEADLINE)) late++
            }
        }, handler)
    }

    fun start(scenario: String) {
        if (!BuildConfig.DEBUG) return
        handler.post {
            handler.removeCallbacks(finish)
            report()
            name = scenario
        }
    }

    fun stop(afterMs: Long = 0L) {
        if (!BuildConfig.DEBUG) return
        handler.postDelayed(finish, afterMs)
    }

    fun sample(scenario: String) {
        start(scenario)
        stop(1000L)
    }

    private fun report() {
        val n = name ?: return
        if (frames.isNotEmpty()) {
            val s = frames.sorted()
            fun p(q: Float) = s[((s.size - 1) * q).toInt()]
            Log.d(TAG, "%s frames=%d avg=%.1f p50=%.1f p90=%.1f p99=%.1f late=%d".format(n, s.size, s.average(), p(0.5f), p(0.9f), p(0.99f), late))
        }
        frames.clear()
        late = 0
        name = null
    }
}
