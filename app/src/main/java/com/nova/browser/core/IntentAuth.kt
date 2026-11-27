package com.nova.browser

import android.content.Context
import android.content.Intent
import java.security.SecureRandom

object IntentAuth {
    private const val PREFS = "intent_auth"
    private const val KEY = "tok"
    private val rnd = SecureRandom()

    @Synchronized
    private fun token(c: Context): String {
        val sp = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = sp.getString(KEY, null)
        if (IntentToken.wellFormed(cur)) return cur!!
        val t = IntentToken.generate(rnd)
        sp.edit().putString(KEY, t).commit()
        return t
    }

    fun attach(c: Context, i: Intent): Intent = i.putExtra(IntentToken.EXTRA, token(c))

    fun trusted(c: Context, i: Intent?): Boolean =
        i != null && IntentToken.matches(token(c), i.getStringExtra(IntentToken.EXTRA))
}
