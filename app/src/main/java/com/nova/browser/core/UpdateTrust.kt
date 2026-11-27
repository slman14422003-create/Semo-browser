package com.nova.browser

import java.net.URI

object UpdateTrust {
    const val MAX_HOPS = 5

    private val hosts = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val hash = Regex("[0-9a-f]{64}")
    private val spaces = Regex("\\s+")

    @JvmStatic
    fun trusted(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val u = try { URI(url.trim()) } catch (e: Exception) { return false }
        if (!"https".equals(u.scheme, ignoreCase = true)) return false
        if (u.rawUserInfo != null) return false
        if (u.port != -1 && u.port != 443) return false
        val h = u.host?.lowercase() ?: return false
        return h in hosts
    }

    @JvmStatic
    fun next(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val resolved = try { URI(current).resolve(location.trim()).toString() } catch (e: Exception) { return null }
        return if (trusted(resolved)) resolved else null
    }

    @JvmStatic
    fun sha256Of(text: String?): String {
        if (text == null) return ""
        val token = text.trim().split(spaces).firstOrNull().orEmpty().lowercase().removePrefix("sha256:")
        return if (hash.matches(token)) token else ""
    }
}
