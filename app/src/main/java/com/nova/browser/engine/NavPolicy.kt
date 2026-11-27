package com.nova.browser

object NavPolicy {
    private val internal = setOf("data", "resource", "moz-extension")

    @JvmStatic
    fun blocksPageNavigation(scheme: String?, direct: Boolean): Boolean =
        !direct && scheme != null && scheme.lowercase() in internal

    @JvmStatic
    fun mayInjectHint(direct: Boolean, current: Boolean, popup: Boolean): Boolean = direct && current && !popup

    @JvmStatic
    fun mayRewrite(direct: Boolean, current: Boolean): Boolean = direct && current
}
