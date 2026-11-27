package com.nova.browser

import android.graphics.Color as AColor
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object PageTint {
    private val RGB = Regex("rgba?\\(\\s*(\\d+(?:\\.\\d+)?)[,\\s]+(\\d+(?:\\.\\d+)?)[,\\s]+(\\d+(?:\\.\\d+)?)(?:\\s*[,/]\\s*([\\d.]+)(%?))?")
    private val HSL = Regex("hsla?\\(\\s*(-?[\\d.]+)(?:deg)?[,\\s]+([\\d.]+)%[,\\s]+([\\d.]+)%(?:\\s*[,/]\\s*([\\d.]+)(%?))?")
    private val CSRGB = Regex("color\\(\\s*(?:srgb|display-p3)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)(?:\\s*/\\s*([\\d.]+)(%?))?")
    private val OK = Regex("(oklab|oklch)\\(\\s*([\\d.]+)(%?)\\s+(-?[\\d.]+)\\s+(-?[\\d.]+)(?:deg)?(?:\\s*/\\s*([\\d.]+)(%?))?")

    fun onStart(tab: BrowserTab, url: String) {
        // Keep the previous color for http(s) pages until the new one arrives; internal and error pages never report a color
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            tab.topColor = null
            tab.botColor = null
        }
    }

    private fun alpha(v: String, pct: String): Float {
        val a = v.toFloatOrNull() ?: return 1f
        return if (pct.isNotEmpty()) a / 100f else a
    }

    private fun c255(f: Float) = f.coerceIn(0f, 255f).toInt()

    fun parse(raw: String?): Int? {
        val t = raw?.trim()?.trim('"')?.trim() ?: return null
        if (t.isEmpty() || t == "null") return null
        if (t.startsWith("#")) {
            val h = t.substring(1)
            val full = when (h.length) {
                3 -> h.map { "" + it + it }.joinToString("")
                4 -> h.take(3).map { "" + it + it }.joinToString("")
                8 -> h.take(6)
                else -> h
            }
            return runCatching { AColor.parseColor("#" + full) or (0xFF shl 24) }.getOrNull()
        }
        RGB.find(t)?.let { m ->
            if (alpha(m.groupValues[4], m.groupValues[5]) < 0.5f) return null
            return AColor.rgb(c255(m.groupValues[1].toFloat()), c255(m.groupValues[2].toFloat()), c255(m.groupValues[3].toFloat()))
        }
        HSL.find(t)?.let { m ->
            if (alpha(m.groupValues[4], m.groupValues[5]) < 0.5f) return null
            val h = ((m.groupValues[1].toFloat() % 360f) + 360f) % 360f
            val s = m.groupValues[2].toFloat().coerceIn(0f, 100f) / 100f
            val l = m.groupValues[3].toFloat().coerceIn(0f, 100f) / 100f
            return AColor.HSVToColor(hslToHsv(h, s, l))
        }
        CSRGB.find(t)?.let { m ->
            if (alpha(m.groupValues[4], m.groupValues[5]) < 0.5f) return null
            fun ch(i: Int) = c255(m.groupValues[i].toFloat() * 255f)
            return AColor.rgb(ch(1), ch(2), ch(3))
        }
        OK.find(t)?.let { m ->
            if (alpha(m.groupValues[6], m.groupValues[7]) < 0.5f) return null
            var l = m.groupValues[2].toFloat(); if (m.groupValues[3].isNotEmpty()) l /= 100f
            val x = m.groupValues[4].toFloat(); val y = m.groupValues[5].toFloat()
            val (a, b) = if (m.groupValues[1] == "oklch") Pair(x * cos(Math.toRadians(y.toDouble())).toFloat(), x * sin(Math.toRadians(y.toDouble())).toFloat()) else Pair(x, y)
            return oklab(l, a, b)
        }
        return runCatching { AColor.parseColor(t) or (0xFF shl 24) }.getOrNull()
    }

    private fun hslToHsv(h: Float, s: Float, l: Float): FloatArray {
        val v = l + s * minOf(l, 1f - l)
        val sv = if (v == 0f) 0f else 2f * (1f - l / v)
        return floatArrayOf(h, sv.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    }

    private fun oklab(l: Float, a: Float, b: Float): Int {
        val l_ = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val m_ = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val s_ = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        val r = 4.0767416621f * l_ - 3.3077115913f * m_ + 0.2309699292f * s_
        val g = -1.2684380046f * l_ + 2.6097574011f * m_ - 0.3413193965f * s_
        val bl = -0.0041960863f * l_ - 0.7034186147f * m_ + 1.7076147010f * s_
        fun enc(c: Float): Int {
            val v = c.coerceIn(0f, 1f)
            val g2 = if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
            return c255(g2 * 255f)
        }
        return AColor.rgb(enc(r), enc(g), enc(bl))
    }
}
