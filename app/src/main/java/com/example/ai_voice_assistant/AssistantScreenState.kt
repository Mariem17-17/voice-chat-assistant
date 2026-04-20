package com.example.ai_voice_assistant

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.ai_voice_assistant.ui.screens.AssistantScreen
import kotlinx.coroutines.launch

@Composable
fun AssistantScreenState() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity ?: return
    
    var aiResponse by remember { mutableStateOf("Appuyez sur le micro pour parler à votre assistant PFE.") }
    var recognizedText by remember { mutableStateOf("Appuyez pour commencer...") }
    val coroutineScope = rememberCoroutineScope()
    
    val isLoading = mainActivity.isLoading
    val isSpeaking = mainActivity.isTtsSpeaking
    val isListening = mainActivity.isVoskListening
    
    fun toggleListening() {
        if (isListening) {
            mainActivity.stopAllSTT()
        } else {
            recognizedText = "J'écoute..."
            // Appel de la logique unifiée avec Fallback
            mainActivity.startUnifiedSpeechRecognition(
                onPartial = { partial ->
                    recognizedText = partial
                },
                onComplete = { spoken, response ->
                    recognizedText = spoken
                    aiResponse = response
                },
                onError = { error ->
                    aiResponse = "Erreur : $error"
                    recognizedText = "Réessayez"
                }
            )
        }
    }
    
    fun onSendMessage(message: String) {
        if (message.isBlank()) return
        recognizedText = message
        coroutineScope.launch {
            val reply = mainActivity.processTextMessage(message)
            aiResponse = reply
            mainActivity.speak(reply)
        }
    }
    
    AssistantScreen(
        aiResponse = aiResponse,
        recognizedText = recognizedText,
        isLoading = isLoading,
        isSpeaking = isSpeaking,
        onSpeak = { toggleListening() },
        onStopSpeaking = { 
            mainActivity.stopSpeaking() 
            mainActivity.stopAllSTT()
        },
        onResetMemory = { /* Logic for resetting memory if applicable */ },
        onSendMessage = { message -> onSendMessage(message) },
        currentLanguage = mainActivity.currentSettings.languageTag,
        onLanguageChange = { tag -> 
            coroutineScope.launch {
                mainActivity.settingsDataStore.updateLanguage(tag)
            }
        }
    )
}
