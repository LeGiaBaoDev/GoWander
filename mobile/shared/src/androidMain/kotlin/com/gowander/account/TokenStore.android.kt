package com.gowander.account

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android: the token is encrypted with an AES-256 key that never leaves the Android Keystore (ADR-040 §3.4), and the
 * ciphertext (IV + data, base64) sits in a small preferences file. A key lost to a backup restore or a wipe only means
 * the person signs in again.
 */
actual object SecureToken {
    private const val FILE = "gowander_token"
    private const val KEY = "session"
    private const val ALIAS = "gowander.session.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private var prefs: SharedPreferences? = null

    /** Called once from MainActivity before setContent (like Prefs.init). Before it, reads return null. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    actual fun read(): String? {
        val stored = prefs?.getString(KEY, null) ?: return null
        return try {
            val parts = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).decodeToString()
        } catch (e: Exception) {
            write(null) // unreadable (key rotated, restored backup): drop it and ask for a sign-in
            null
        }
    }

    actual fun write(token: String?) {
        val store = prefs ?: return
        if (token == null) {
            store.edit().remove(KEY).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val data = cipher.doFinal(token.encodeToByteArray())
            store.edit()
                .putString(KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            store.edit().remove(KEY).apply()
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/** "Google Pixel 7", "samsung SM-G991B": only a label next to the session in Cài đặt. */
actual val deviceName: String
    get() = (Build.MANUFACTURER + " " + Build.MODEL).trim()
