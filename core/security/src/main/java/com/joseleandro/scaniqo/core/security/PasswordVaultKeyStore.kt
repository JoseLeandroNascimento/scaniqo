package com.joseleandro.scaniqo.core.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class PasswordVaultKeyStore(context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    fun requireSecureDevice() {
        if (!keyguard.isDeviceSecure) throw PasswordVaultUnavailableException()
    }

    @Suppress("DEPRECATION")
    fun generate(alias: String, mode: VaultAuthenticationMode) {
        requireSecureDevice()
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)

        when (mode) {
            VaultAuthenticationMode.COMBINED -> {
                check(Build.VERSION.SDK_INT >= 30)
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            }
            VaultAuthenticationMode.BIOMETRIC -> {
                builder.setUserAuthenticationValidityDurationSeconds(-1)
                builder.setInvalidatedByBiometricEnrollment(true)
            }
            VaultAuthenticationMode.DEVICE_CREDENTIAL -> {
                // No Android antigo, a janela curta é autorizada pela credencial da tela.
                builder.setUserAuthenticationValidityDurationSeconds(30)
            }
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(builder.build())
            generateKey()
        }
    }

    fun cipher(
        alias: String,
        encrypted: VaultCiphertext? = null,
    ): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(alias, null) as? SecretKey
            ?: throw PasswordVaultRecoveryException()
        return PasswordVaultKeyCipher.prepare(key, encrypted)
    }

    fun removeUnused(alias: String) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}
