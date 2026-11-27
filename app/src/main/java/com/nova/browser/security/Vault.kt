package com.nova.browser

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.SystemClock
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** بيانات دخول محفوظة (غير قابلة للتعديل؛ التعديل يستبدل الكائن كي تتحدّث الواجهة). */
class Cred(val id: String, val host: String, val user: String, val pass: String, val updated: Long)

enum class SaveKind { NEW, UPDATE, SAME }

object VaultKeys {
    const val WINDOW_SECONDS = 60
    private const val LEGACY_ALIAS = "nova_vault_key"
    private const val DATA_ALIAS = "nova_vault_key_v2"
    private const val GATE_ALIAS = "nova_vault_gate_v2"
    private val GATE_PROBE = ByteArray(16)

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun generate(alias: String, timeout: Int, strongBox: Boolean): SecretKey {
        val b = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(timeout, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        if (strongBox) b.setIsStrongBoxBacked(true)
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(b.build())
        return g.generateKey()
    }

    private fun obtain(alias: String, timeout: Int, create: Boolean): SecretKey {
        val ks = store()
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw IllegalStateException("key missing")
        return try {
            generate(alias, timeout, true)
        } catch (e: StrongBoxUnavailableException) {
            generate(alias, timeout, false)
        }
    }

    fun legacyKey(): SecretKey? = (store().getKey(LEGACY_ALIAS, null) as? SecretKey)

    fun dropLegacy() { runCatching { store().deleteEntry(LEGACY_ALIAS) } }

    fun encrypt(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, obtain(DATA_ALIAS, WINDOW_SECONDS, true))
        val ct = c.doFinal(plain)
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(s: String): ByteArray = open(s, obtain(DATA_ALIAS, WINDOW_SECONDS, false))

    fun decryptLegacy(s: String): ByteArray = open(s, legacyKey() ?: throw IllegalStateException("legacy key missing"))

    private fun open(s: String, k: SecretKey): ByteArray {
        val parts = s.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
    }

    fun gateCipher(): Cipher {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, obtain(GATE_ALIAS, 0, true))
        return c
    }

    fun proveGate(c: Cipher) { c.doFinal(GATE_PROBE) }
}

object Vault {
    const val UNLOCK_ID = "unlock:"
    private const val WINDOW_MS = VaultKeys.WINDOW_SECONDS * 1000L
    private const val MARGIN_MS = 5000L
    private lateinit var sp: SharedPreferences
    private var inited = false
    private var legacy = false
    private var lastAuth = 0L
    private val never = HashSet<String>()
    val items = mutableStateListOf<Cred>()
    var unlocked by mutableStateOf(false)
        private set
    var lockedError by mutableStateOf(false)
        private set
    var count by mutableIntStateOf(0)
        private set
    var lastError = 0
        private set

    @Synchronized
    fun init(c: Context) {
        if (inited) return
        inited = true
        sp = c.applicationContext.getSharedPreferences("vault", Context.MODE_PRIVATE)
        count = sp.getInt("cnt", 0)
        val s = sp.getString("data", null)
        if (s == null) {
            never.addAll(sp.getStringSet("never", emptySet())!!)
            legacy = sp.contains("never")
            unlocked = true
            return
        }
        if (sp.getInt("ver", 1) < 2) loadLegacy(s) else loadCurrent(false)
    }

    private fun fill(arr: JSONArray) {
        val next = ArrayList<Cred>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            next.add(Cred(o.getString("id"), o.getString("h"), o.getString("u"), o.getString("p"), o.optLong("t")))
        }
        items.clear()
        items.addAll(next)
        count = next.size
    }

    private fun loadLegacy(s: String) {
        runCatching {
            fill(JSONArray(String(VaultKeys.decryptLegacy(s), Charsets.UTF_8)))
            never.addAll(sp.getStringSet("never", emptySet())!!)
            legacy = true
            unlocked = true
        }.onFailure { lockedError = true }
    }

    @Synchronized
    private fun loadCurrent(afterAuth: Boolean): Boolean {
        val s = sp.getString("data", null)
        if (s == null) { unlocked = true; return true }
        return try {
            fill(JSONArray(String(VaultKeys.decrypt(s), Charsets.UTF_8)))
            never.clear()
            sp.getString("never_enc", null)?.let { n ->
                val a = JSONArray(String(VaultKeys.decrypt(n), Charsets.UTF_8))
                for (i in 0 until a.length()) never.add(a.getString(i))
            }
            unlocked = true
            true
        } catch (e: UserNotAuthenticatedException) {
            if (afterAuth) lastError = R.string.vault_unlock_failed
            false
        } catch (e: Exception) {
            lockedError = true
            lastError = R.string.vault_locked_error
            false
        }
    }

    private fun fail(res: Int) { lastError = res }

    fun errorText(): String = S(if (lastError != 0) lastError else R.string.vault_save_failed)

    fun markAuthed() { lastAuth = SystemClock.elapsedRealtime() }

    fun fresh(): Boolean = lastAuth != 0L && SystemClock.elapsedRealtime() - lastAuth < WINDOW_MS - MARGIN_MS

    fun session(activity: Activity, title: String, block: () -> Unit) {
        if (lockedError) { toast(activity, S(R.string.vault_locked_error)); return }
        val go = {
            val ready = unlocked || loadCurrent(true)
            if (!ready) toast(activity, errorText())
            else {
                if (legacy && !commit(items.toList(), never.toSet())) toast(activity, errorText())
                block()
            }
        }
        if (fresh()) go() else Auth.run(activity, title) { go() }
    }

    @Synchronized
    private fun commit(next: List<Cred>, nextNever: Set<String>): Boolean {
        if (lockedError) { fail(R.string.vault_locked_error); return false }
        if (!unlocked) { fail(R.string.vault_locked); return false }
        return try {
            val arr = JSONArray()
            next.forEach {
                arr.put(JSONObject().put("id", it.id).put("h", it.host).put("u", it.user).put("p", it.pass).put("t", it.updated))
            }
            val enc = VaultKeys.encrypt(arr.toString().toByteArray(Charsets.UTF_8))
            val encNever = VaultKeys.encrypt(JSONArray(nextNever.toList()).toString().toByteArray(Charsets.UTF_8))
            val cur = sp.getString("data", null)
            val ed = sp.edit()
            if (cur != null && !legacy) {
                val r = VaultBackup.rotate(sp.getString("data_bak1", null), sp.getString("data_bak2", null), cur)
                r.forEachIndexed { i, v -> if (v != null) ed.putString("data_bak" + (i + 1), v) else ed.remove("data_bak" + (i + 1)) }
            }
            ed.putString("data", enc).putString("never_enc", encNever).putInt("ver", 2).putInt("cnt", next.size).remove("never")
            if (!ed.commit()) throw IOException("write failed")
            if (legacy) {
                VaultKeys.decrypt(enc)
                sp.edit().remove("data_bak").apply()
                VaultKeys.dropLegacy()
                legacy = false
            }
            items.clear()
            items.addAll(next)
            never.clear()
            never.addAll(nextNever)
            count = next.size
            true
        } catch (e: UserNotAuthenticatedException) {
            fail(R.string.vault_auth_needed); false
        } catch (e: Exception) {
            fail(R.string.vault_save_failed); false
        }
    }

    fun norm(host: String) = host.trim().lowercase().removePrefix("www.")

    /** ينظّف ما يكتبه المستخدم (رابط كامل مثلاً) إلى اسم النطاق فقط. */
    fun cleanHost(raw: String) =
        norm(raw.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?'))

    /** مطابقة تامّة للنطاق فقط (لا نطاقات فرعية) لتقليل خطر التصيّد. */
    fun forHost(host: String): List<Cred> { val h = norm(host); return items.filter { it.host == h } }

    fun classify(host: String, user: String, pass: String): SaveKind {
        val h = norm(host)
        val same = items.firstOrNull { it.host == h && it.user == user } ?: return SaveKind.NEW
        return if (same.pass == pass) SaveKind.SAME else SaveKind.UPDATE
    }

    private fun merged(base: MutableList<Cred>, id: String, host: String, user: String, pass: String) {
        val h = norm(host)
        val idx = base.indexOfFirst { if (id.isNotEmpty()) it.id == id else it.host == h && it.user == user }
        val c = Cred(if (idx >= 0) base[idx].id else UUID.randomUUID().toString(), h, user, pass, System.currentTimeMillis())
        if (idx >= 0) base[idx] = c else base.add(c)
    }

    /** إضافة أو تحديث؛ id فارغ = جديد (يحدّث الموجود إن تطابق الموقع والمستخدم). */
    fun upsert(id: String, host: String, user: String, pass: String): Boolean {
        val next = items.toMutableList()
        merged(next, id, host, user, pass)
        return commit(next, never.toSet())
    }

    fun importAll(creds: List<Triple<String, String, String>>): Boolean {
        val next = items.toMutableList()
        creds.forEach { (h, u, p) -> merged(next, "", h, u, p) }
        return commit(next, never.toSet())
    }

    fun delete(id: String): Boolean = commit(items.filter { it.id != id }, never.toSet())

    // مواقع اختار المستخدم عدم حفظ كلمات المرور لها
    fun isNever(host: String) = never.contains(norm(host))
    fun neverSave(host: String): Boolean = commit(items.toList(), never + norm(host))
}

object Auth {
    fun run(activity: Activity, title: String, onOk: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) { toast(activity, S(R.string.auth_no_lock)); return }
        val cipher = runCatching { VaultKeys.gateCipher() }.getOrNull()
        if (cipher == null) { toast(activity, S(R.string.auth_failed)); return }
        val b = BiometricPrompt.Builder(activity).setTitle(title)
        b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        runCatching {
            b.build().authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    val c = result?.cryptoObject?.cipher
                    if (c != null && runCatching { VaultKeys.proveGate(c) }.isSuccess) { Vault.markAuthed(); onOk() }
                    else toast(activity, S(R.string.auth_failed))
                }
            })
        }.onFailure { toast(activity, S(R.string.auth_failed)) }
    }
}
