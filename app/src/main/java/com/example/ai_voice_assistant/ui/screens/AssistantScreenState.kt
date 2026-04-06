package com.example.ai_voice_assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ai_voice_assistant.MainActivity
import kotlinx.coroutines.launch

@Composable
fun AssistantScreenState() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity ?: return
    
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var recognizedText by remember { mutableStateOf("Tap the button and start speaking.") }
    var isListening by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Use states from MainActivity
    val isLoading = mainActivity.isLoading
    val isSpeaking = mainActivity.isTtsSpeaking

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

    // Silent background SpeechRecognizer (no system popup/dialog).
    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(mainActivity)
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                recognizedText = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                recognizedText = "Processing..."
            }
            override fun onError(error: Int) {
                isListening = false
                Log.e("AssistantScreenState", "SpeechRecognizer error code: $error")
                recognizedText = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic and speak again."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during recognition."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                    else -> "Speech recognition error ($error)."
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    onSendMessage(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    recognizedText = text
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    fun startSpeechRecognizerListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, mainActivity.currentSettings.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognizerListening()
        } else {
            recognizedText = "Microphone permission denied."
        }
    }
    
    fun startListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startSpeechRecognizerListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    fun onStopSpeaking() {
        mainActivity.onStopSpeaking()
        isListening = false
        speechRecognizer.stopListening()
    }
    
    fun onSpeak() {
        if (isSpeaking || isListening) {
            onStopSpeaking()
        } else {
            startListening()
        }
    }
    
    AssistantScreen(
        aiResponse = aiResponse,
        recognizedText = recognizedText,
        isLoading = isLoading,
        isSpeaking = isSpeaking || isListening,
        onSpeak = { onSpeak() },
        onStopSpeaking = { onStopSpeaking() },
        onResetMemory = { mainActivity.resetMemory() },
        onSendMessage = { message -> onSendMessage(message) },
        currentLanguage = mainActivity.currentSettings.languageTag,
        onLanguageChange = { _ -> }
    )
}
