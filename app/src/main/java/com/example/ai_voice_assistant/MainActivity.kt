package com.example.ai_voice_assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import java.util.Locale


class MainActivity : ComponentActivity() {
    private val chatHistory = mutableListOf<Message>()
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
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
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                isTtsReady = false
            }
        }
        if (chatHistory.isEmpty()) {
            chatHistory.add(
                Message(
                    role = "system",
                    content = """
                        You are a helpful Android Voice Assistant. If the user asks to do a system action, respond ONLY with a specific command tag before your natural response.
                        
                        To call someone: [ACTION_CALL:phonenumber]
                        
                        To set an alarm: [ACTION_ALARM:HH:mm]
                        
                        To open an app: [ACTION_OPEN:appname]
                        Example: if I say "Call 911", you reply: "[ACTION_CALL:911] I am dialing emergency services for you now."
                    """.trimIndent()
                )
            )
        }
        enableEdgeToEdge()
        setContent {
            AI_voice_assistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GeminiTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSendPrompt = { userPrompt -> sendToGroqWithMemory(userPrompt) },
                        onSpeak = { text -> speak(text) },
                        onHandleSystemActions = { response -> handleSystemActions(response) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
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

    private fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "groq_reply")
    }

    private fun handleSystemActions(response: String) {
        val callMatch = Regex("""\[ACTION_CALL:([^\]]+)]""").find(response)
        if (callMatch != null) {
            val number = callMatch.groupValues[1].trim()
            if (number.isNotBlank()) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                if (dialIntent.resolveActivity(packageManager) != null) {
                    startActivity(dialIntent)
                }
            }
            return
        }

        val alarmMatch = Regex("""\[ACTION_ALARM:(\d{2}):(\d{2})]""").find(response)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1].toIntOrNull()
            val minute = alarmMatch.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                }
                if (alarmIntent.resolveActivity(packageManager) != null) {
                    startActivity(alarmIntent)
                }
            }
        }
    }
}

@Composable
fun GeminiTestScreen(
    modifier: Modifier = Modifier,
    onSendPrompt: suspend (String) -> String,
    onSpeak: (String) -> Unit,
    onHandleSystemActions: (String) -> Unit
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
                    val aiReply = onSendPrompt(userPrompt)
                    aiResponse = aiReply
                    onHandleSystemActions(aiReply)
                    onSpeak(aiReply)
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