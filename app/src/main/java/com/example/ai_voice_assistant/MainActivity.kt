package com.example.ai_voice_assistant

import android.Manifest
import android.app.Activity
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
    private val systemPrompt = "You are a Robot. For Alarms, start with [ACTION_ALARM:HH:mm]. For Calls, start with [ACTION_CALL:number]. For Notes, start with [ACTION_NOTE:text]."
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
        resetMemory()
        enableEdgeToEdge()
        setContent {
            AI_voice_assistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GeminiTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSendPrompt = { userPrompt -> sendToGroqWithMemory(userPrompt) },
                        onSpeak = { text -> speak(text) },
                        onHandleSystemAction = { response -> handleSystemAction(response) },
                        onResetMemory = {
                            resetMemory()
                            Toast.makeText(this, "Memory Cleared", Toast.LENGTH_SHORT).show()
                        }
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
        ensureSystemPromptAtTop()
        while (chatHistory.size > 10) {
            val removeIndex = if (chatHistory.firstOrNull()?.role == "system" && chatHistory.size > 1) 1 else 0
            chatHistory.removeAt(removeIndex)
        }
        ensureSystemPromptAtTop()
    }

    private fun ensureSystemPromptAtTop() {
        val currentSystemIndex = chatHistory.indexOfFirst { it.role == "system" }
        when {
            currentSystemIndex == -1 -> chatHistory.add(0, Message(role = "system", content = systemPrompt))
            currentSystemIndex > 0 -> {
                chatHistory.removeAt(currentSystemIndex)
                chatHistory.add(0, Message(role = "system", content = systemPrompt))
            }
            currentSystemIndex == 0 && chatHistory[0].content != systemPrompt -> {
                chatHistory[0] = Message(role = "system", content = systemPrompt)
            }
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

    private fun handleSystemAction(response: String): String {
        val processedResponse = response

        val callMatch = Regex("""\[ACTION_CALL:([^\]]+)]""").find(processedResponse)
        if (callMatch != null) {
            val number = callMatch.groupValues[1].trim()
            if (number.isNotBlank()) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                if (dialIntent.resolveActivity(packageManager) != null) {
                    runOnUiThread {
                        startActivity(dialIntent)
                        Toast.makeText(this, "Opening dialer for $number", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No dialer app found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val alarmMatch = Regex("""\[ACTION_ALARM:(\d{1,2}):(\d{2})]""").find(processedResponse)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1].toIntOrNull()
            val minute = alarmMatch.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                if (alarmIntent.resolveActivity(packageManager) != null) {
                    runOnUiThread {
                        startActivity(alarmIntent)
                        Toast.makeText(this, "Setting alarm for %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No alarm app found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val noteMatch = Regex("""\[ACTION_NOTE:([^\]]+)]""").find(processedResponse)
        if (noteMatch != null) {
            val noteContent = noteMatch.groupValues[1].trim()
            if (noteContent.isNotBlank()) {
                val noteIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, noteContent)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(noteIntent, "Save note with"))
                    Toast.makeText(this, "Sharing note...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return processedResponse
            .replace(Regex("""\[ACTION_[A-Z_]+:[^\]]+]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun resetMemory() {
        chatHistory.clear()
        chatHistory.add(Message(role = "system", content = systemPrompt))
        chatHistory.add(Message(role = "user", content = "Set an alarm for 7am"))
        chatHistory.add(Message(role = "assistant", content = "[ACTION_ALARM:07:00] I have set your alarm."))
        chatHistory.add(Message(role = "user", content = "Take a note buy milk and bread"))
        chatHistory.add(Message(role = "assistant", content = "[ACTION_NOTE:buy milk and bread] I saved your note."))
    }
}

@Composable
fun GeminiTestScreen(
    modifier: Modifier = Modifier,
    onSendPrompt: suspend (String) -> String,
    onSpeak: (String) -> Unit,
    onHandleSystemAction: (String) -> String,
    onResetMemory: () -> Unit
) {
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var recognizedText by remember { mutableStateOf("Tap the button and start speaking.") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun buildDirectActionTag(userSpeechText: String): String? {
        val lower = userSpeechText.lowercase(Locale.getDefault())
        if (lower.contains("alarm") || lower.contains("alarme") || lower.contains("reveil") || lower.contains("réveil")) {
            val hhmmMatch = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(userSpeechText)
            if (hhmmMatch != null) {
                val hour = hhmmMatch.groupValues[1].toIntOrNull()
                val minute = hhmmMatch.groupValues[2].toIntOrNull()
                if (hour != null && minute != null) {
                    return "[ACTION_ALARM:${"%02d".format(hour)}:${"%02d".format(minute)}]"
                }
            }
            val amPmMatch = Regex("""\b(1[0-2]|0?[1-9])\s*(am|pm)\b""", RegexOption.IGNORE_CASE).find(userSpeechText)
            if (amPmMatch != null) {
                val baseHour = amPmMatch.groupValues[1].toIntOrNull()
                val period = amPmMatch.groupValues[2].lowercase(Locale.getDefault())
                if (baseHour != null) {
                    val hour24 = when {
                        period == "am" && baseHour == 12 -> 0
                        period == "pm" && baseHour != 12 -> baseHour + 12
                        else -> baseHour
                    }
                    return "[ACTION_ALARM:${"%02d".format(hour24)}:00]"
                }
            }
            return "[ACTION_ALARM:07:00]"
        }
        if (lower.contains("call") || lower.contains("appel") || lower.contains("appelle") || lower.contains("telephone")) {
            val number = Regex("""\+?\d[\d\s-]{2,}""")
                .find(userSpeechText)
                ?.value
                ?.replace("""[^\d+]""".toRegex(), "")
            return if (!number.isNullOrBlank()) "[ACTION_CALL:$number]" else "[ACTION_CALL:12345678]"
        }
        if (lower.contains("note") || lower.contains("rappel") || lower.contains("remember")) {
            val noteText = userSpeechText
                .replace(Regex("""(?i).*?\bnote\b[:\s-]*"""), "")
                .trim()
            return if (noteText.isNotBlank()) "[ACTION_NOTE:$noteText]" else "[ACTION_NOTE:Reminder]"
        }
        return null
    }

    fun sendToGroq(userPrompt: String) {
        coroutineScope.launch {
            isLoading = true
            aiResponse = "Thinking..."
            try {
                if (BuildConfig.GROQ_API_KEY.isBlank()) {
                    aiResponse = "Missing GROQ_API_KEY. Add it to local.properties."
                } else {
                    var aiReply = onSendPrompt(userPrompt)
                    val hasTag = Regex("""\[ACTION_(ALARM|CALL|NOTE):[^\]]+]""").containsMatchIn(aiReply)
                    val lowerUserMessage = userPrompt.lowercase(Locale.getDefault())
                    if ((lowerUserMessage.contains("alarm") || lowerUserMessage.contains("alarme") || lowerUserMessage.contains("reveil") || lowerUserMessage.contains("réveil")) && !hasTag) {
                        aiReply = "[ACTION_ALARM:07:00] $aiReply"
                    } else if ((lowerUserMessage.contains("call") || lowerUserMessage.contains("appel") || lowerUserMessage.contains("appelle") || lowerUserMessage.contains("telephone")) && !hasTag) {
                        aiReply = "[ACTION_CALL:12345678] $aiReply"
                    } else if ((lowerUserMessage.contains("note") || lowerUserMessage.contains("rappel") || lowerUserMessage.contains("remember")) && !hasTag) {
                        aiReply = "[ACTION_NOTE:Reminder] $aiReply"
                    }
                    (context as? Activity)?.runOnUiThread {
                        val cleanedReply = onHandleSystemAction(aiReply)
                        aiResponse = cleanedReply
                        onSpeak(cleanedReply)
                    } ?: run {
                        val cleanedReply = onHandleSystemAction(aiReply)
                        aiResponse = cleanedReply
                        onSpeak(cleanedReply)
                    }
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
                buildDirectActionTag(spokenText)?.let { directTag ->
                    onHandleSystemAction(directTag)
                }
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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onResetMemory() },
            enabled = !isLoading
        ) {
            Text("Reset")
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