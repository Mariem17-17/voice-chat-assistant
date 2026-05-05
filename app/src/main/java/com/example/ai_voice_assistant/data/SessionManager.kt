package com.example.ai_voice_assistant.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_session_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserUid(uid: String) {
        sharedPreferences.edit().putString("user_uid", uid).apply()
    }

    fun getUserUid(): String? {
        return sharedPreferences.getString("user_uid", null)
    }

    fun clearSession() {
        sharedPreferences.edit().remove("user_uid").apply()
    }
}
