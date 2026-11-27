package com.nova.browser

object SignatureCheck {
    @JvmStatic
    fun accepts(
        installed: List<ByteArray>,
        installedMulti: Boolean,
        candidate: List<ByteArray>,
        candidateMulti: Boolean,
        candidateHistory: List<ByteArray>
    ): Boolean {
        if (installed.isEmpty() || candidate.isEmpty()) return false
        val iMulti = installedMulti || installed.size > 1
        val cMulti = candidateMulti || candidate.size > 1
        if (iMulti || cMulti) return iMulti && cMulti && hex(installed) == hex(candidate)
        val mine = hex(installed).first()
        return mine == hex(candidate).first() || mine in hex(candidateHistory)
    }

    private fun hex(list: List<ByteArray>): Set<String> =
        list.map { b -> b.joinToString("") { "%02x".format(it) } }.toSet()
}
