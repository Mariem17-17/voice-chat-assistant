package com.example.ai_voice_assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_voice_assistant.data.ChatDatabase
import com.example.ai_voice_assistant.data.ChatEntity
import com.example.ai_voice_assistant.data.SettingsDataStore
import com.example.ai_voice_assistant.data.UserSettings
import com.example.ai_voice_assistant.ui.navigation.BottomNavigationBar
import com.example.ai_voice_assistant.ui.navigation.BottomNavItem
import com.example.ai_voice_assistant.ui.screens.AssistantScreenState
import com.example.ai_voice_assistant.ui.screens.HistoryScreen
import com.example.ai_voice_assistant.ui.screens.PersonalizationScreen
import com.example.ai_voice_assistant.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class MainActivity : ComponentActivity() {

    private val TAG = "AI_ASSISTANT_DEBUG"
    private val nluManagerDelegate = lazy { NluManager(this) }
    private val nluManager by nluManagerDelegate

    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var googleSpeechRecognizer: SpeechRecognizer? = null
    
    private var voskPartialHandler: ((String) -> Unit)? = null
    private var voskCompleteHandler: ((String, String) -> Unit)? = null
    private var voskErrorHandler: ((String) -> Unit)? = null

    var isVoskModelReady by mutableStateOf(false)
        private set
    var isVoskListening by mutableStateOf(false)
        private set

    private val requestRecordAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startUnifiedSpeechRecognitionInternal()
        else {
            isVoskListening = false
            runOnUiThread { Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_SHORT).show() }
        }
    }

    private val systemPrompt = "You are an English AI Assistant. If the user asks for CALL, SMS, ALARM, YOUTUBE, CONTACT, or CAMERA, your response MUST include [ACTION_CALL:number], [ACTION_SMS:number:message], [ACTION_ALARM:HH:mm], [ACTION_YOUTUBE:query], [ACTION_CONTACT:name:number], or [ACTION_CAMERA] at the beginning. Be concise."
    private val chatHistory = mutableListOf<Message>()

    private val groqApi: GroqApi by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    suspend fun processTextMessage(text: String): String {
        Log.d(TAG, "processTextMessage: $text")
        return if (isNetworkAvailable()) {
            isLoading = true
            try {
                chatHistory.add(Message(role = "user", content = text))
                val request = GroqRequest(model = "llama-3.3-70b-versatile", messages = chatHistory.toList())
                val response = groqApi.getCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val assistantText = response.choices.firstOrNull()?.message?.content ?: "Processing..."
                chatHistory.add(Message(role = "assistant", content = assistantText))
                
                val cleanedReply = handleSystemAction(assistantText)
                withContext(Dispatchers.IO) {
                    chatDao.insertChat(ChatEntity(userPrompt = text, aiResponse = cleanedReply))
                }
                cleanedReply
            } catch (e: Exception) {
                processOffline(text)
            } finally {
                isLoading = false
            }
        } else {
            processOffline(text)
        }
    }

    private suspend fun processOffline(text: String): String {
        val nluPredict = nluManager.predict(text)
        val reply = processAssistantUserMessageWithNlu(text, nluPredict)
        withContext(Dispatchers.IO) {
            chatDao.insertChat(ChatEntity(userPrompt = text, aiResponse = reply))
        }
        return reply
    }

    private fun handleSystemAction(response: String): String {
        Log.d(TAG, "handleSystemAction: $response")
        var responseHasAction = false
        
        Regex("""\[ACTION_CONTACT:([^:]+):([^\]]+)\]""").find(response)?.let {
            responseHasAction = true
            launchNluContactSave(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        Regex("""\[ACTION_YOUTUBE:(.+?)\]""").find(response)?.let {
            responseHasAction = true
            launchNluYoutubeSearch(it.groupValues[1].trim())
        }
        
        Regex("""\[ACTION_ALARM:(\d{1,2})[:](\d{2})\]""").find(response)?.let {
            responseHasAction = true
            setAlarm(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        
        Regex("""\[ACTION_SMS:([^:]+):(.+?)\]""").find(response)?.let {
            responseHasAction = true
            launchNluSmsComposer(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        
        Regex("""\[ACTION_CALL:([^\]]+)\]""").find(response)?.let {
            responseHasAction = true
            launchNluCallComposer(it.groupValues[1].trim())
        }
        
        if (response.contains("[ACTION_CAMERA]")) {
            responseHasAction = true
            launchSystemIntentWithDelay { startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        }

        val cleaned = response.replace(Regex("""\[ACTION_[A-Z_]+:[^\]]+\]"""), "").replace("[ACTION_CAMERA]", "").trim()
        return if (responseHasAction) "Action prepared. Please tap to confirm." else cleaned
    }

    private suspend fun processAssistantUserMessageWithNlu(userPrompt: String, nluPredict: Pair<String, Float>): String {
        val (intentName, confidence) = nluPredict
        // Diagnostic Log already in NluManager, but lowered threshold here
        if (confidence < 0.3f) return "I didn't quite catch that. Could you repeat?"

        return when (intentName) {
            "CONTACT" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                val name = userPrompt.replace(Regex("(?i)save|contact|add|number|with|$number"), "").trim()
                launchNluContactSave(name, number); "Action prepared. Please tap to confirm."
            }
            "YOUTUBE" -> {
                val query = userPrompt.replace(Regex("(?i)search|youtube|on|for"), "").trim()
                launchNluYoutubeSearch(query); "Action prepared. Please tap to confirm."
            }
            "ALARM" -> {
                val time = Regex("(\\d{1,2})[:\\s](\\d{2})").find(userPrompt)
                if (time != null) setAlarm(time.groupValues[1].toInt(), time.groupValues[2].toInt()) else setAlarmFallback()
                "Action prepared. Please tap to confirm."
            }
            "CALL" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                launchNluCallComposer(number); "Action prepared. Please tap to confirm."
            }
            "SMS" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                launchNluSmsComposer(number, ""); "Action prepared. Please tap to confirm."
            }
            else -> "Command recognized: $intentName"
        }
    }

    private fun setAlarm(hour: Int, min: Int) {
        launchSystemIntentWithDelay {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, min)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AI Assistant Alarm")
            }
            startActivity(intent)
        }
    }

    private fun setAlarmFallback() {
        val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 10) }
        setAlarm(calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
    }

    fun processNlu(text: String) {
        val cleaned = text.trim().lowercase()
        if (cleaned.isBlank()) return
        
        Log.d("NLU_DIAGNOSTIC", "Final text received in processNlu: '$cleaned'")
        stopAllSTT()

        // Handle Small Talk
        if (cleaned == "hello" || cleaned == "hi") {
            val reply = "Hello! How can I help you today?"
            voskCompleteHandler?.invoke(cleaned, reply)
            speak(reply)
            return
        }

        lifecycleScope.launch {
            val reply = processTextMessage(cleaned)
            runOnUiThread {
                voskCompleteHandler?.invoke(cleaned, reply)
                speak(reply)
            }
        }
    }

    private val voskRecognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val partial = voskExtractPartialForUi(hypothesis)
            runOnUiThread { voskPartialHandler?.invoke(partial) }
        }
        override fun onResult(hypothesis: String) {
            val text = try { JSONObject(hypothesis).optString("text", "").trim().lowercase() } catch(_: Exception) { "" }
            if (text.isNotEmpty()) processNlu(text)
        }
        override fun onFinalResult(hypothesis: String) {
            val text = try { JSONObject(hypothesis).optString("text", "").trim().lowercase() } catch(_: Exception) { "" }
            if (text.isNotEmpty()) processNlu(text)
        }
        override fun onError(e: Exception) { runOnUiThread { isVoskListening = false } }
        override fun onTimeout() = stopAllSTT()
    }

    private var textToSpeech: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
    var isTtsSpeaking by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    private val database by lazy { ChatDatabase.getDatabase(this) }
    private val chatDao by lazy { database.chatDao() }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    var currentSettings by mutableStateOf(UserSettings("en-US", "Female", "", 1.0f, 1.0f))

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        textToSpeech?.apply {
            setSpeechRate(currentSettings.speechRate)
            setPitch(currentSettings.pitch)
            language = Locale.ENGLISH
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        isTtsSpeaking = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        unpackVoskModel()
        chatHistory.add(Message(role = "system", content = systemPrompt))
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) { isTtsSpeaking = true }
                    override fun onDone(id: String?) { isTtsSpeaking = false }
                    override fun onError(id: String?) { isTtsSpeaking = false }
                })
            }
        }
        enableEdgeToEdge()
        setContent {
            AI_voice_assistantTheme {
                val navController = rememberNavController()
                val settings by settingsDataStore.settingsFlow.collectAsState(initial = UserSettings("en-US", "Female", "", 1.0f, 1.0f))
                LaunchedEffect(settings) { currentSettings = settings }
                Box(modifier = Modifier.fillMaxSize().background(brush = Brush.radialGradient(colors = listOf(BackgroundStart, BackgroundEnd)))) {
                    Scaffold(containerColor = Color.Transparent, bottomBar = { BottomNavigationBar(navController = navController) }) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(navController = navController, startDestination = BottomNavItem.Assistant.route) {
                                composable(BottomNavItem.Assistant.route) { AssistantScreenState() }
                                composable(BottomNavItem.History.route) {
                                    val chats by chatDao.getAllChats().collectAsState(initial = emptyList())
                                    HistoryScreen(chats = chats, onReSpeak = { speak(it) }, onDeleteAll = { lifecycleScope.launch(Dispatchers.IO) { chatDao.deleteAllChats() } })
                                }
                                composable(BottomNavItem.Personalization.route) {
                                    PersonalizationScreen(settings = settings, tts = textToSpeech, onBack = { navController.popBackStack() },
                                        onLanguageChange = { tag -> lifecycleScope.launch { settingsDataStore.updateLanguage(tag) } },
                                        onPersonaChange = { p -> lifecycleScope.launch { settingsDataStore.updateVoicePersona(p) } },
                                        onVoiceChange = { v -> lifecycleScope.launch { settingsDataStore.updateSelectedVoiceName(v) } },
                                        onRateChange = { r -> lifecycleScope.launch { settingsDataStore.updateSpeechRate(r) } },
                                        onPitchChange = { pi -> lifecycleScope.launch { settingsDataStore.updatePitch(pi) } },
                                        onDeleteHistory = { lifecycleScope.launch(Dispatchers.IO) { chatDao.deleteAllChats() } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopAllSTT()
        if (nluManagerDelegate.isInitialized()) nluManager.close()
        textToSpeech?.shutdown()
        googleSpeechRecognizer?.destroy()
        super.onDestroy()
    }

    private fun unpackVoskModel() = StorageService.unpack(this, "vosk-model", "model", { voskModel = it; isVoskModelReady = true }, { isVoskModelReady = false })

    fun startUnifiedSpeechRecognition(onPartial: (String) -> Unit, onComplete: (String, String) -> Unit, onError: (String) -> Unit) {
        voskPartialHandler = onPartial; voskCompleteHandler = onComplete; voskErrorHandler = onError
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startUnifiedSpeechRecognitionInternal()
        } else {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startUnifiedSpeechRecognitionInternal() {
        stopAllSTT()
        if (isNetworkAvailable()) startGoogleSTT() else startVoskSTT()
    }

    private fun startGoogleSTT() {
        runOnUiThread {
            if (googleSpeechRecognizer == null) googleSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            googleSpeechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isVoskListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isVoskListening = false }
                override fun onError(error: Int) { isVoskListening = false; voskErrorHandler?.invoke("Speech error ($error)") }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) processNlu(matches[0])
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) voskPartialHandler?.invoke(matches[0])
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            googleSpeechRecognizer?.startListening(intent)
        }
    }

    private fun startVoskSTT() {
        if (!isVoskModelReady) { voskErrorHandler?.invoke("Offline model not ready yet."); return }
        try {
            voskSpeechService = SpeechService(Recognizer(voskModel!!, 16000.0f), 16000.0f)
            isVoskListening = true
            voskSpeechService?.startListening(voskRecognitionListener)
        } catch (e: Exception) { isVoskListening = false; voskErrorHandler?.invoke("Offline error.") }
    }

    fun stopAllSTT() {
        runOnUiThread {
            isVoskListening = false
            voskSpeechService?.stop(); voskSpeechService?.shutdown(); voskSpeechService = null
            googleSpeechRecognizer?.stopListening()
        }
    }

    private fun launchNluCallComposer(number: String = "") = launchSystemIntentWithDelay { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    private fun launchNluSmsComposer(number: String = "", message: String = "") = launchSystemIntentWithDelay { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply { putExtra("sms_body", message) }) }
    private fun launchNluYoutubeSearch(q: String) = try { launchSystemIntentWithDelay { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))) } } catch (e: Exception) { launchSystemIntentWithDelay { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))) } }
    
    private fun launchNluContactSave(name: String, number: String) = launchSystemIntentWithDelay {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        startActivity(intent)
    }

    private fun launchSystemIntentWithDelay(action: () -> Unit) = lifecycleScope.launch { delay(500); try { action() } catch (_: Exception) {} }
    private fun voskExtractTextField(json: String) = try { JSONObject(json).optString("text", "").trim() } catch (_: Exception) { "" }
    private fun voskExtractPartialForUi(json: String) = try { JSONObject(json).optString("partial", "").trim() } catch (_: Exception) { "" }
}
