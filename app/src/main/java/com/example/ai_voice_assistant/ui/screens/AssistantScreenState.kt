package com.example.ai_voice_assistant.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.ai_voice_assistant.MainActivity
import kotlinx.coroutines.launch

@Composable
fun AssistantScreenState() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity ?: return

    var aiResponse by remember { mutableStateOf("Press the microphone to talk to your AI assistant.") }
    var recognizedText by remember { mutableStateOf("Press to speak...") }
    
    val isListening = mainActivity.isVoskListening
    val isLoading = mainActivity.isLoading
    val isSpeaking = mainActivity.isTtsSpeaking
    val coroutineScope = rememberCoroutineScope()

    fun onSendMessage(message: String) {
        if (message.isBlank()) return
        recognizedText = message
        coroutineScope.launch {
            try {
                val cleanedResponse = mainActivity.processTextMessage(message)
                aiResponse = cleanedResponse
                mainActivity.speak(cleanedResponse)
            } catch (e: Exception) {
                aiResponse = "Error: ${e.message}"
            }
        }
    }

    fun startListening() {
        recognizedText = if (mainActivity.isNetworkAvailable()) "I'm listening (Online)..." else "I'm listening (Offline)..."
        
        mainActivity.startUnifiedSpeechRecognition(
            onPartial = { partial: String ->
                recognizedText = if (partial.isBlank()) "I'm listening..." else partial
            },
            onComplete = { spoken: String, response: String ->
                recognizedText = spoken
                aiResponse = response
            },
            onError = { msg: String ->
                recognizedText = "Error: $msg"
            }
        )
    }

    fun onStopEverything() {
        mainActivity.stopAllSTT()
        mainActivity.stopSpeaking()
    }

    fun onToggleSpeech() {
        if (isSpeaking || isListening) {
            onStopEverything()
        } else {
            startListening()
        }
    }

    AssistantScreen(
        aiResponse = aiResponse,
        recognizedText = recognizedText,
        isLoading = isLoading,
        isSpeaking = isSpeaking || isListening,
        onSpeak = { onToggleSpeech() },
        onStopSpeaking = { onStopEverything() },
        onResetMemory = { /* Handle memory reset if needed */ },
        onSendMessage = { message -> onSendMessage(message) },
        currentLanguage = mainActivity.currentSettings.languageTag,
        onLanguageChange = { tag ->
            coroutineScope.launch {
                mainActivity.settingsDataStore.updateLanguage(tag)
            }
        }
    )
}
