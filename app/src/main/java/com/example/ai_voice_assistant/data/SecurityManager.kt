package com.example.ai_voice_assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(private val context: Context) {
    private val keyAlias = "master_db_key"
    private val provider = "AndroidKeyStore"
    private val prefsName = "security_prefs"
    private val encryptedKeyPath = "encrypted_db_passphrase"

    fun getDatabaseKey(): ByteArray {
        val keyStore = KeyStore.getInstance(provider)
        keyStore.load(null)

        // 1. Ensure Master Key exists in Keystore
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                provider
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val masterKey = keyStore.getKey(keyAlias, null) as SecretKey
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val encryptedData = prefs.getString(encryptedKeyPath, null)

        return if (encryptedData == null) {
            // 2. Generate a new random 32-byte passphrase
            val passphrase = ByteArray(32)
            SecureRandom().nextBytes(passphrase)

            // 3. Encrypt it with the Master Key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encryptedPassphrase = cipher.doFinal(passphrase)

            // 4. Store IV + Ciphertext
            val combined = iv + encryptedPassphrase
            prefs.edit().putString(encryptedKeyPath, Base64.encodeToString(combined, Base64.DEFAULT)).apply()
            passphrase
        } else {
            // 5. Decrypt existing passphrase
            val combined = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = combined.sliceArray(0 until 12) // GCM IV is 12 bytes
            val ciphertext = combined.sliceArray(12 until combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
            cipher.doFinal(ciphertext)
        }
    }
}
