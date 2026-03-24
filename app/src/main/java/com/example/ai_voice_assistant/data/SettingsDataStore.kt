package com.example.ai_voice_assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val VOICE_PERSONA = stringPreferencesKey("voice_persona")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            languageTag = preferences[LANGUAGE_TAG] ?: "en-US",
            voicePersona = preferences[VOICE_PERSONA] ?: "Female",
            selectedVoiceName = preferences[SELECTED_VOICE_NAME] ?: "",
            speechRate = preferences[SPEECH_RATE] ?: 1.0f,
            pitch = preferences[PITCH] ?: 1.0f
        )
    }

    suspend fun updateLanguage(tag: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_TAG] = tag
        }
    }

    suspend fun updateVoicePersona(persona: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_PERSONA] = persona
        }
    }

    suspend fun updateSelectedVoiceName(voiceName: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_VOICE_NAME] = voiceName
        }
    }

    suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[SPEECH_RATE] = rate
        }
    }

    suspend fun updatePitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[PITCH] = pitch
        }
    }
}

data class UserSettings(
    val languageTag: String,
    val voicePersona: String,
    val selectedVoiceName: String,
    val speechRate: Float,
    val pitch: Float
)
