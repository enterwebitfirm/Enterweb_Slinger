// === FILE: app/src/main/java/com/enterwebslinger/network/core/security/CredentialVault.kt ===
package com.enterwebslinger.network.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Production-grade hardware-backed Credential Vault.
 * Uses AndroidKeyStore AES-256-GCM authenticated encryption for:
 * - SSH Private Keys / Passphrases
 * - SNMPv3 Auth & Priv Keys
 * - FTP/FTPS/SFTP Passwords
 * - Redfish BMC & VMware vCenter Session Tokens
 * - WireGuard & IPsec Pre-Shared Keys
 */
class CredentialVault(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("omninet_encrypted_vault", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "CredentialVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "OmniNetMasterKey_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        const val KEY_GEMINI_FALLBACK_API_KEY = "gemini_fallback_api_key"
    }

    init {
        ensureMasterKeyExists()
    }

    private fun ensureMasterKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Initialized new AES-256 Master Key in AndroidKeyStore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AndroidKeyStore master key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving master secret key: ${e.message}")
            null
        }
    }

    @Synchronized
    fun putSecret(key: String, secret: String): Boolean {
        if (secret.isEmpty()) {
            removeSecret(key)
            return true
        }
        return try {
            val masterKey = getSecretKey() ?: return false
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64Payload = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(key, base64Payload).commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt secret for key '$key': ${e.message}")
            false
        }
    }

    @Synchronized
    fun getSecret(key: String): String? {
        val base64Payload = prefs.getString(key, null) ?: return null
        return try {
            val masterKey = getSecretKey() ?: return null
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null

            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt secret for key '$key': ${e.message}")
            null
        }
    }

    fun hasSecret(key: String): Boolean {
        return prefs.contains(key)
    }

    fun saveGeminiFallbackApiKey(apiKey: String): Boolean = putSecret(KEY_GEMINI_FALLBACK_API_KEY, apiKey.trim())

    fun getGeminiFallbackApiKey(): String? = getSecret(KEY_GEMINI_FALLBACK_API_KEY)?.takeIf { it.isNotBlank() }

    fun hasGeminiFallbackApiKey(): Boolean = !getGeminiFallbackApiKey().isNullOrBlank()

    fun clearGeminiFallbackApiKey() {
        removeSecret(KEY_GEMINI_FALLBACK_API_KEY)
    }

    @Synchronized
    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    @Synchronized
    fun clearAllSecrets() {
        prefs.edit().clear().apply()
    }
}
