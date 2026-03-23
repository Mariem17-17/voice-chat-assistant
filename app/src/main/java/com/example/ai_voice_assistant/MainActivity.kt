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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

private var isTtsReady by mutableStateOf(false)
private var isTtsSpeaking by mutableStateOf(false)
private var currentLanguage by mutableStateOf("en-US")

class MainActivity : ComponentActivity() {
    private val systemPrompt = "You are a multilingual assistant. Always respond in the user's language (English, French, or Arabic). For actions, ALWAYS keep tags in English and never translate anything inside brackets. Valid tags are: [ACTION_ALARM:HH:mm], [ACTION_CALL:number], [ACTION_NOTE:text], [ACTION_YOUTUBE:search query], and [ACTION_CAMERA]. If an action is requested, start your reply with the correct action tag."
    private val chatHistory = mutableListOf<Message>()
    private var textToSpeech: TextToSpeech? = null

    // CRITICAL FIX: Ensure these are reactive
    private var isTtsReady by mutableStateOf(false)
    private var isTtsSpeaking by mutableStateOf(false)
    private var currentLanguage by mutableStateOf("en-US")

    private val languages = listOf(
        "English" to "en-US",
        "Français" to "fr-FR",
        "العربية" to "ar-SA"
    )

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

        // Initialize TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true // IMPORTANT: Mark as ready
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isTtsSpeaking = true // Button will appear
                    }
                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking = false // Button will disappear
                    }
                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking = false
                    }
                })
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
                        speechLanguage = currentLanguage,
                        isSpeaking = isTtsSpeaking,
                        onStopSpeaking = {
                            textToSpeech?.stop()
                            isTtsSpeaking = false
                        },
                        languages = languages,
                        onLanguageSelected = { newLang -> currentLanguage = newLang },
                        onResetMemory = { resetMemory() }
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
        if (text.isEmpty()) {
            textToSpeech?.stop()
            isTtsSpeaking = false
            return
        }

        if (!isTtsReady || text.isBlank()) return

        textToSpeech?.apply {
            language = Locale.forLanguageTag(currentLanguage)
            // Adding "groq_reply" as the Utterance ID is what triggers the listener
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "groq_reply")
        }
    }

    private fun handleSystemAction(response: String): String {
        val processedResponse = response

        val callMatch = Regex("""\[ACTION_CALL:([^\]]+)]""").find(processedResponse)
        if (callMatch != null) {
            val number = callMatch.groupValues[1].trim()
            if (number.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                runOnUiThread {
                    try {
                        startActivity(intent)
                        Toast.makeText(this, "Opening dialer for $number", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val alarmMatch = Regex("""\[ACTION_ALARM:(\d{1,2}):(\d{2})]""").find(processedResponse)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1].toIntOrNull()
            val minutes = alarmMatch.groupValues[2].toIntOrNull()
            if (hour != null && minutes != null && hour in 0..23 && minutes in 0..59) {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
                intent.putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                runOnUiThread {
                    try {
                        startActivity(intent)
                        Toast.makeText(this, "Setting alarm for %02d:%02d".format(hour, minutes), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Alarm failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val noteMatch = Regex("""\[ACTION_NOTE:([^\]]+)]""").find(processedResponse)
        if (noteMatch != null) {
            val noteContent = noteMatch.groupValues[1].trim()
            if (noteContent.isNotBlank()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, noteContent)
                }
                runOnUiThread {
                    try {
                        startActivity(intent)
                        Toast.makeText(this, "Sharing note...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Note failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
            // --- NEW: YOUTUBE ACTION ---
        val youtubeMatch = Regex("""\[ACTION_YOUTUBE:(.*?)\]""").find(processedResponse)
        if (youtubeMatch != null) {
            val query = youtubeMatch.groupValues[1].trim()
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runOnUiThread {
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
                }
            }
        }

            // --- NEW: CAMERA ACTION ---
        // --- CAMERA ACTION (Reliable Version) ---
        if (processedResponse.contains("[ACTION_CAMERA]")) {
            // This opens the system camera app directly
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            runOnUiThread {
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // If the above fails, try the basic capture intent
                    try {
                        val captureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivity(captureIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "Device has no camera app", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

            // ... Keep your existing CALL, ALARM, and NOTE logic here ...
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
    isSpeaking: Boolean,          // New
    onStopSpeaking: () -> Unit,   // New
    speechLanguage: String,
    languages: List<Pair<String, String>>, // <--- New Ingredient 1
    onLanguageSelected: (String) -> Unit,  // <--- New Ingredient 2
    onResetMemory: () -> Unit
) {
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var recognizedText by remember { mutableStateOf("Tap the button and start speaking.") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun buildDirectActionTag(userSpeechText: String): String? {
        val lower = userSpeechText.lowercase(Locale.getDefault())

        // Alarms: English, French, Arabic
        if (lower.contains("alarm") || lower.contains("alarme") || lower.contains("réveil") || lower.contains("تنبيه")) {
            val hhmmMatch = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(userSpeechText)
            return if (hhmmMatch != null) {
                "[ACTION_ALARM:${hhmmMatch.groupValues[1].padStart(2, '0')}:${hhmmMatch.groupValues[2]}]"
            } else {
                "[ACTION_ALARM:07:00]"
            }
        }

        // Calls: English, French, Arabic
        if (lower.contains("call") || lower.contains("appel") || lower.contains("appelle") || lower.contains("téléphone") || lower.contains("اتصل")) {
            val number = Regex("""\+?\d[\d\s-]{2,}""")
                .find(userSpeechText)
                ?.value
                ?.replace("""[^\d+]""".toRegex(), "")
            return if (!number.isNullOrBlank()) "[ACTION_CALL:$number]" else "[ACTION_CALL:12345678]"
        }

        // Camera: English, French, Arabic
        if (lower.contains("camera") || lower.contains("photo") || lower.contains("picture") || lower.contains("صورة") || lower.contains("صور")) {
            return "[ACTION_CAMERA]"
        }

        // YouTube: English, French
        // Change this line in GeminiTestScreen:
        if (lower.contains("youtube") || (lower.contains("play") && lower.contains("video"))) {
            // This ensures "Tell me about..." doesn't trigger it,
            // but "Play a video of..." does.
            val search = userSpeechText.replace(Regex("(?i).*?\\b(play|youtube|video)\\b"), "").trim()
            return "[ACTION_YOUTUBE:${if(search.isNotBlank()) search else "trending"}]"
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
                    // Add these after your existing "note" check:
                    else if ((lowerUserMessage.contains("youtube") || lowerUserMessage.contains("play")) && !hasTag) {
                        // Instead of grabbing from the AI reply, we grab the song from what YOU said
                        val search = userPrompt
                            .replace("play", "", true)
                            .replace("on youtube", "", true)
                            .replace("youtube", "", true)
                            .trim()

                        // We force the tag with YOUR words, not the AI's "please"
                        aiReply = "[ACTION_YOUTUBE:$search] $aiReply"
                    }
                    else if ((lowerUserMessage.contains("camera") || lowerUserMessage.contains("photo") || lowerUserMessage.contains("picture")) && !hasTag) {
                        aiReply = "[ACTION_CAMERA] $aiReply"
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
            startListening(context, speechLauncher::launch, speechLanguage)
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

        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = languages.find { lang -> lang.second == speechLanguage }?.first ?: "English"

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Lang: $selectedLabel")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { pair ->
                    DropdownMenuItem(
                        text = { Text(pair.first) },
                        onClick = {
                            onLanguageSelected(pair.second)
                            expanded = false
                            onResetMemory()
                        }
                    )
                }
            }
        }
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

        // Wrap the buttons in a Row so they sit side-by-side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Your existing Speak Button
            Button(
                onClick = {
                    onStopSpeaking() // Interrupt any current speech before listening
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

            // The New Stop Button (Only shows if AI is thinking or speaking)
            if (isLoading || isSpeaking) {
                Spacer(modifier = Modifier.width(12.dp)) // Space between buttons
                IconButton(
                    onClick = { onStopSpeaking() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop AI",
                        tint = MaterialTheme.colorScheme.error // Professional red color
                    )
                }
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