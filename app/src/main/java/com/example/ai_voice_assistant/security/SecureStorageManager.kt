package com.example.ai_voice_assistant.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserEmail(email: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    fun savePitch(pitch: Float) {
        sharedPreferences.edit()
            .putFloat(KEY_PITCH, pitch)
            .apply()
    }

    fun getPitch(): Float {
        return sharedPreferences.getFloat(KEY_PITCH, 1.0f)
    }

    fun saveSpeechRate(rate: Float) {
        sharedPreferences.edit()
            .putFloat(KEY_SPEECH_RATE, rate)
            .apply()
    }

    fun getSpeechRate(): Float {
        return sharedPreferences.getFloat(KEY_SPEECH_RATE, 1.0f)
    }

    fun saveVoiceName(name: String) {
        sharedPreferences.edit()
            .putString(KEY_VOICE_NAME, name)
            .apply()
    }

    fun getVoiceName(): String? {
        return sharedPreferences.getString(KEY_VOICE_NAME, null)
    }

    companion object {
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PITCH = "voice_pitch"
        private const val KEY_SPEECH_RATE = "voice_speech_rate"
        private const val KEY_VOICE_NAME = "voice_name"
    }
}
