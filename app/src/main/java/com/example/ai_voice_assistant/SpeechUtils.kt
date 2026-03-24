package com.example.ai_voice_assistant

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun startListening(
    context: Context,
    launchVoiceOverlay: (Intent) -> Unit,
    currentLanguage: String
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
    }
    try {
        launchVoiceOverlay(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
    }
}
