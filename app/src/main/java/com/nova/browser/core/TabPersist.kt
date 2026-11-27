package com.nova.browser

object TabPersist {
    class Entry(val url: String, val tag: Int, val pinned: Boolean, val isPrivate: Boolean)

    data class Result(val tabs: String, val meta: String, val cur: Int)

    @JvmStatic
    fun build(entries: List<Entry>, cur: Int): Result {
        val kept = entries.filter { !it.isPrivate }
        if (kept.isEmpty()) return Result("", "", 0)
        val at = cur.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        val before = entries.take(at).count { !it.isPrivate }
        val next = if (entries.getOrNull(at)?.isPrivate == false) before else (before - 1).coerceAtLeast(0)
        return Result(
            kept.joinToString("\n") { it.url.ifBlank { "-" } },
            kept.joinToString(",") { it.tag.toString() + ":" + (if (it.pinned) "1" else "0") },
            next.coerceIn(0, kept.lastIndex)
        )
    }
}
