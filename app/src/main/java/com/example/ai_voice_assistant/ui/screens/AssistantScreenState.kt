package com.example.ai_voice_assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.ai_voice_assistant.MainActivity
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

    // Direct Speech Recognizer for Offline Mode
    val speechRecognizer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                recognizedText = "Listening (Offline)..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                recognizedText = "Processing..."
            }
            override fun onError(error: Int) {
                recognizedText = "Error: Recognition failed ($error)"
            }
            override fun onResults(results: Bundle?) {
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
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mainActivity.handleSpeechResult(result) { spoken, response ->
            recognizedText = spoken
            aiResponse = response
        }
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, mainActivity.currentSettings.languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            if (isOnline(context)) {
                // Online Mode: Use Google STT Overlay (Better UI/UX)
                speechLauncher.launch(intent)
            } else {
                // Offline Mode: Use On-Device SpeechRecognizer directly (Requirement EF-04)
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                speechRecognizer.startListening(intent)
            }
        }
    }
    
    fun startListening() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    fun onStopSpeaking() {
        mainActivity.onStopSpeaking()
        speechRecognizer.stopListening()
    }
    
    fun onSpeak() {
        if (isSpeaking) {
            onStopSpeaking()
        } else {
            startListening()
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
