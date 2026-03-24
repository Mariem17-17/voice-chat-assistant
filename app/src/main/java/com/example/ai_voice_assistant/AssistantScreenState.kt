package com.example.ai_voice_assistant

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.ai_voice_assistant.ui.screens.AssistantScreen
import kotlinx.coroutines.launch

@Composable
fun AssistantScreenState() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity ?: return
    
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var recognizedText by remember { mutableStateOf("Tap the button and start speaking.") }
    val coroutineScope = rememberCoroutineScope()
    
    // Use states from MainActivity
    val isLoading = mainActivity.isLoading
    val isSpeaking = mainActivity.isTtsSpeaking
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mainActivity.handleSpeechResult(result) { spoken, response ->
            recognizedText = spoken
            aiResponse = response
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, mainActivity.currentSettings.languageTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            speechLauncher.launch(intent)
        }
    }
    
    fun startListening() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    fun onStopSpeaking() {
        mainActivity.onStopSpeaking()
    }
    
    fun onSpeak() {
        if (isSpeaking) {
            onStopSpeaking()
        } else {
            startListening()
        }
    }
    
    fun onSendMessage(message: String) {
        recognizedText = message
        coroutineScope.launch {
            try {
                val response = mainActivity.sendToGroq(message)
                val cleanedResponse = mainActivity.handleSystemAction(response)
                aiResponse = cleanedResponse
                mainActivity.speak(cleanedResponse)
            } catch (e: Exception) {
                aiResponse = "Error: ${e.message}"
            }
        }
    }
    
    AssistantScreen(
        aiResponse = aiResponse,
        recognizedText = recognizedText,
        isLoading = isLoading,
        isSpeaking = isSpeaking,
        onSpeak = { onSpeak() },
        onStopSpeaking = { onStopSpeaking() },
        onResetMemory = { mainActivity.resetMemory() },
        onSendMessage = { message -> onSendMessage(message) },
        currentLanguage = mainActivity.currentSettings.languageTag,
        onLanguageChange = { _ -> }
    )
}
