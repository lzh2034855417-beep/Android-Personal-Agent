package com.aegis.apa.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredApiKey(
    val provider: String,
    val apiKey: String,
    val expiresAt: Long
)

object ApiKeyStore {
    private const val PREFS_NAME = "apa_api_credentials"
    private const val KEY_ALIAS = "apa_api_key_aes"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_CIPHERTEXT = "ciphertext"
    private const val KEY_IV = "iv"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_LAST_PROVIDER = "last_provider"
    private const val DEFAULT_VALID_DAYS = 7

    fun save(
        context: Context,
        provider: String,
        apiKey: String,
        validDays: Int = DEFAULT_VALID_DAYS
    ): StoredApiKey? = runCatching {
        require(apiKey.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        val expiresAt = System.currentTimeMillis() + validDays * 24L * 60L * 60L * 1000L
        val slot = providerSlot(provider)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PROVIDER, provider)
            .putString("${KEY_CIPHERTEXT}_$slot", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("${KEY_IV}_$slot", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong("${KEY_EXPIRES_AT}_$slot", expiresAt)
            .apply()

        StoredApiKey(provider, apiKey, expiresAt)
    }.getOrNull()

    fun load(context: Context): StoredApiKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val provider = prefs.getString(KEY_LAST_PROVIDER, null)
            ?: prefs.getString(KEY_PROVIDER, null)
            ?: return null
        return load(context, provider)
    }

    fun load(context: Context, provider: String): StoredApiKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val slot = providerSlot(provider)
        val hasProviderSlot = prefs.contains("${KEY_CIPHERTEXT}_$slot")
        val useLegacySlot = !hasProviderSlot && prefs.getString(KEY_PROVIDER, null) == provider
        val ciphertextKey = if (useLegacySlot) KEY_CIPHERTEXT else "${KEY_CIPHERTEXT}_$slot"
        val ivKey = if (useLegacySlot) KEY_IV else "${KEY_IV}_$slot"
        val expiresKey = if (useLegacySlot) KEY_EXPIRES_AT else "${KEY_EXPIRES_AT}_$slot"
        val expiresAt = prefs.getLong(expiresKey, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            clear(context, provider)
            return null
        }

        return runCatching {
            val encrypted = Base64.decode(
                requireNotNull(prefs.getString(ciphertextKey, null)),
                Base64.NO_WRAP
            )
            val iv = Base64.decode(
                requireNotNull(prefs.getString(ivKey, null)),
                Base64.NO_WRAP
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, iv)
            )
            val apiKey = String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
            StoredApiKey(provider, apiKey, expiresAt)
        }.getOrElse {
            clear(context, provider)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        ApiSession.update(null)
    }

    fun clear(context: Context, provider: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val slot = providerSlot(provider)
        val editor = prefs.edit()
            .remove("${KEY_CIPHERTEXT}_$slot")
            .remove("${KEY_IV}_$slot")
            .remove("${KEY_EXPIRES_AT}_$slot")
        if (prefs.getString(KEY_PROVIDER, null) == provider) {
            editor.remove(KEY_PROVIDER)
                .remove(KEY_CIPHERTEXT)
                .remove(KEY_IV)
                .remove(KEY_EXPIRES_AT)
        }
        if (prefs.getString(KEY_LAST_PROVIDER, null) == provider) {
            editor.remove(KEY_LAST_PROVIDER)
        }
        editor.apply()
        if (ApiSession.provider == provider) {
            ApiSession.update(null)
        }
    }

    private fun providerSlot(provider: String): String = when (provider) {
        "DeepSeek" -> "deepseek"
        "OpenAI · GPT" -> "openai"
        "Anthropic · Claude" -> "anthropic"
        "Xiaomi · MiMo" -> "mimo"
        "Moonshot · Kimi" -> "kimi"
        else -> provider.hashCode().toString().replace("-", "n")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
