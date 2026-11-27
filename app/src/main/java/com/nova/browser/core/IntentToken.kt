package com.nova.browser

import java.security.MessageDigest
import java.security.SecureRandom

object IntentToken {
    const val EXTRA = "tok"
    private const val BYTES = 32
    private val hex = Regex("[0-9a-f]{64}")

    @JvmStatic
    fun generate(rnd: SecureRandom): String {
        val b = ByteArray(BYTES)
        rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @JvmStatic
    fun wellFormed(t: String?): Boolean = t != null && hex.matches(t)

    @JvmStatic
    fun matches(expected: String?, given: String?): Boolean {
        if (!wellFormed(expected) || !wellFormed(given)) return false
        return MessageDigest.isEqual(expected!!.toByteArray(Charsets.UTF_8), given!!.toByteArray(Charsets.UTF_8))
    }
}
