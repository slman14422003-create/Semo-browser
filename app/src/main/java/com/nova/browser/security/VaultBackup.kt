package com.nova.browser

object VaultBackup {
    @JvmStatic
    fun rotate(b1: String?, b2: String?, current: String?): List<String?> =
        if (current == null) listOf(b1, b2, null) else listOf(current, b1, b2)
}
