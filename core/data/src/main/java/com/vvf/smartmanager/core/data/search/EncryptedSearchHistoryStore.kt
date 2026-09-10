package com.vvf.smartmanager.core.data.search

import android.content.Context
import android.util.Base64
import com.vvf.smartmanager.core.security.CryptoSecurityManager

/**
 * Persists search-history queries encrypted at rest (AndroidKeyStore AES-GCM).
 * Migrates legacy plaintext "history_items" once, then removes the plain key.
 */
class EncryptedSearchHistoryStore(
    context: Context,
    private val crypto: CryptoSecurityManager
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val enc = prefs.getString(KEY_ENC, null)
        if (!enc.isNullOrBlank()) {
            return runCatching { decrypt(enc) }.getOrDefault(emptyList())
        }
        val legacy = prefs.getString(KEY_PLAIN, "") ?: ""
        val items = if (legacy.isBlank()) {
            emptyList()
        } else {
            legacy.split(SEP).filter { it.isNotBlank() }
        }
        if (items.isNotEmpty()) {
            save(items)
            prefs.edit().remove(KEY_PLAIN).apply()
        }
        return items
    }

    fun save(history: List<String>) {
        val plain = history.joinToString(SEP).toByteArray(Charsets.UTF_8)
        val (cipher, iv) = crypto.encryptBytes(plain)
        val blob = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher, Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_ENC, blob)
            .remove(KEY_PLAIN)
            .apply()
    }

    private fun decrypt(blob: String): List<String> {
        val parts = blob.split(":", limit = 2)
        require(parts.size == 2) { "bad history blob" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipher = Base64.decode(parts[1], Base64.NO_WRAP)
        val plain = crypto.decryptBytes(cipher, iv).toString(Charsets.UTF_8)
        return if (plain.isBlank()) emptyList() else plain.split(SEP).filter { it.isNotBlank() }
    }

    companion object {
        private const val PREFS_NAME = "vvf_search_history_prefs"
        private const val KEY_PLAIN = "history_items"
        private const val KEY_ENC = "history_items_enc"
        private const val SEP = "\u001F"
    }
}
