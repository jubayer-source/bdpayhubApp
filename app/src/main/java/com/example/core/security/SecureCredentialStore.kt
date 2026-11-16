package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contract for secure credential storage.
 * All tokens and device credentials MUST be encrypted before persistence.
 * Plaintext storage of access tokens or device credentials is strictly prohibited.
 */
interface SecureCredentialStore {
    fun saveSupabaseSession(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochSeconds: Long,
        userEmail: String,
        merchantId: String?
    )
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getSessionExpiresAt(): Long
    fun getSignedInEmail(): String?
    fun getMerchantId(): String?
    fun isAccessTokenExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean

    fun saveDeviceCredential(deviceId: String, deviceCredential: String, deviceName: String)
    fun getDeviceId(): String?
    fun getDeviceCredential(): String?
    fun getDeviceName(): String?
    fun clearDeviceCredential()

    fun clearAllCredentials()
    fun inspectRawPersistedValue(key: String): String?
}

/**
 * Android Keystore-backed AES-256-GCM encrypted credential store.
 *
 * Uses `AndroidKeyStore` hardware/TEE-backed master key when running on Android devices,
 * with a per-process ephemeral AES-256-GCM key fallback in local JVM unit tests where
 * the hardware KeyStore provider is not mounted, ensuring credentials are ALWAYS
 * stored as AES-GCM ciphertext (`ivBase64:ciphertextBase64`) and never in plaintext.
 */
class KeystoreCredentialStore(context: Context) : SecureCredentialStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Local JVM / Robolectric fallback still uses real AES-256-GCM encryption
            synchronized(this) {
                jvmFallbackKey ?: run {
                    val kg = KeyGenerator.getInstance("AES")
                    kg.init(256, SecureRandom())
                    kg.generateKey().also { jvmFallbackKey = it }
                }
            }
        }
    }

    private fun encrypt(plaintext: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "ENCv1:$ivB64:$cipherB64"
    }

    private fun decrypt(payload: String?): String? {
        if (payload.isNullOrBlank() || !payload.startsWith("ENCv1:")) return null
        return try {
            val parts = payload.split(":")
            if (parts.size != 3) return null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun saveSupabaseSession(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochSeconds: Long,
        userEmail: String,
        merchantId: String?
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, encrypt(accessToken))
            putString(KEY_REFRESH_TOKEN, encrypt(refreshToken))
            putString(KEY_EXPIRES_AT, encrypt(expiresAtEpochSeconds.toString()))
            putString(KEY_USER_EMAIL, encrypt(userEmail))
            if (merchantId != null) {
                putString(KEY_MERCHANT_ID, encrypt(merchantId))
            } else {
                remove(KEY_MERCHANT_ID)
            }
        }.apply()
    }

    override fun getAccessToken(): String? = decrypt(prefs.getString(KEY_ACCESS_TOKEN, null))

    override fun getRefreshToken(): String? = decrypt(prefs.getString(KEY_REFRESH_TOKEN, null))

    override fun getSessionExpiresAt(): Long =
        decrypt(prefs.getString(KEY_EXPIRES_AT, null))?.toLongOrNull() ?: 0L

    override fun getSignedInEmail(): String? = decrypt(prefs.getString(KEY_USER_EMAIL, null))

    override fun getMerchantId(): String? = decrypt(prefs.getString(KEY_MERCHANT_ID, null))

    override fun isAccessTokenExpired(nowEpochSeconds: Long): Boolean {
        val token = getAccessToken() ?: return true
        if (token.isBlank()) return true
        val expiresAt = getSessionExpiresAt()
        return expiresAt <= nowEpochSeconds
    }

    override fun saveDeviceCredential(deviceId: String, deviceCredential: String, deviceName: String) {
        prefs.edit().apply {
            putString(KEY_DEVICE_ID, encrypt(deviceId))
            putString(KEY_DEVICE_CREDENTIAL, encrypt(deviceCredential))
            putString(KEY_DEVICE_NAME, encrypt(deviceName))
        }.apply()
    }

    override fun getDeviceId(): String? = decrypt(prefs.getString(KEY_DEVICE_ID, null))

    override fun getDeviceCredential(): String? =
        decrypt(prefs.getString(KEY_DEVICE_CREDENTIAL, null))

    override fun getDeviceName(): String? = decrypt(prefs.getString(KEY_DEVICE_NAME, null))

    override fun clearDeviceCredential() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_CREDENTIAL)
            .apply()
    }

    override fun clearAllCredentials() {
        prefs.edit().clear().apply()
    }

    override fun inspectRawPersistedValue(key: String): String? = prefs.getString(key, null)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "bdpayhub_master_aes_gcm_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFS_FILE_NAME = "bdpayhub_keystore_vault"

        const val KEY_ACCESS_TOKEN = "vault_supabase_access_token"
        const val KEY_REFRESH_TOKEN = "vault_supabase_refresh_token"
        const val KEY_EXPIRES_AT = "vault_supabase_expires_at"
        const val KEY_USER_EMAIL = "vault_user_email"
        const val KEY_MERCHANT_ID = "vault_merchant_id"
        const val KEY_DEVICE_ID = "vault_device_id"
        const val KEY_DEVICE_CREDENTIAL = "vault_device_credential"
        const val KEY_DEVICE_NAME = "vault_device_name"
    }
}
