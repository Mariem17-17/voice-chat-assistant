package com.example.ai_voice_assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ai_voice_assistant.ui.theme.AI_voice_assistantTheme
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : ComponentActivity() {
    private val chatHistory = mutableListOf<Message>()
    private val groqApi: GroqApi by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (chatHistory.isEmpty()) {
            chatHistory.add(
                Message(
                    role = "system",
                    content = "You are a helpful Voice Assistant for a PFE project. Keep responses concise."
                )
            )
        }
        enableEdgeToEdge()
        setContent {
            AI_voice_assistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GeminiTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSendPrompt = { userPrompt -> sendToGroqWithMemory(userPrompt) }
                    )
                }
            }
        }
    }

    private fun trimHistoryToTenMessages() {
        while (chatHistory.size > 10) {
            val removeIndex = if (chatHistory.firstOrNull()?.role == "system" && chatHistory.size > 1) 1 else 0
            chatHistory.removeAt(removeIndex)
        }
    }

    private suspend fun sendToGroqWithMemory(userPrompt: String): String {
        chatHistory.add(Message(role = "user", content = userPrompt))
        trimHistoryToTenMessages()

        val request = GroqRequest(
            model = "llama-3.3-70b-versatile",
            messages = chatHistory.toList()
        )
        val response = groqApi.getCompletion(
            authorization = "Bearer ${BuildConfig.GROQ_API_KEY}",
            request = request
        )
        val assistantText = response.choices.firstOrNull()?.message?.content
            ?: "The AI returned an empty response."

        chatHistory.add(Message(role = "assistant", content = assistantText))
        trimHistoryToTenMessages()
        return assistantText
    }
}

@Composable
fun GeminiTestScreen(
    modifier: Modifier = Modifier,
    onSendPrompt: suspend (String) -> String
) {
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var recognizedText by remember { mutableStateOf("Tap the button and start speaking.") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun sendToGroq(userPrompt: String) {
        coroutineScope.launch {
            isLoading = true
            aiResponse = "Thinking..."
            try {
                if (BuildConfig.GROQ_API_KEY.isBlank()) {
                    aiResponse = "Missing GROQ_API_KEY. Add it to local.properties."
                } else {
                    aiResponse = onSendPrompt(userPrompt)
                }
            } catch (e: Exception) {
                aiResponse = "Error: ${e.message ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                recognizedText = spokenText
                sendToGroq(spokenText)
            } else {
                aiResponse = "I didn't catch that. Please try speaking again."
            }
        } else {
            aiResponse = "Voice input cancelled."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening(context, speechLauncher::launch)
        } else {
            aiResponse = "Microphone permission is required for speech input."
        }
    }

    fun startListening() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = aiResponse,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You said: $recognizedText",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (BuildConfig.GROQ_API_KEY.isBlank()) {
                    aiResponse = "Missing GROQ_API_KEY. Add it to local.properties."
                    return@Button
                }
                startListening()
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Speak to Assistant")
            }
        }
    }
}

private fun startListening(
    context: android.content.Context,
    launchVoiceOverlay: (Intent) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
    }
    try {
        launchVoiceOverlay(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
    }
}