package com.example.ai_voice_assistant

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads the new English-only NLU model and handles classification for:
 * CALL, SMS, ALARM, CONTACT, YOUTUBE.
 */
class NluManager(context: Context) {

    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var flexDelegate: FlexDelegate? = null
    private var wordToId: Map<String, Int> = emptyMap()
    private var indexToIntent: Map<Int, String> = emptyMap()

    private val gson = Gson()
    private val seqLen = 20

    init {
        try {
            loadAssets()
            loadInterpreter()
        } catch (e: Exception) {
            Log.e(TAG, "NLU initialization failed", e)
        }
    }

    private fun loadAssets() {
        try {
            wordToId = appContext.assets.open(VOCAB_FILE).bufferedReader().use { reader ->
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson<Map<String, Int>>(reader, type) ?: emptyMap()
            }
            val nameToIdx = appContext.assets.open(INTENT_MAP_FILE).bufferedReader().use { reader ->
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson<Map<String, Int>>(reader, type) ?: emptyMap()
            }
            indexToIntent = nameToIdx.entries.associate { (name, idx) -> idx to name }
            Log.d(TAG, "Assets loaded successfully. Vocab size: ${wordToId.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading assets", e)
        }
    }

    private fun loadModelBuffer(): MappedByteBuffer {
        val fd = appContext.assets.openFd(MODEL_FILE)
        FileInputStream(fd.fileDescriptor).use { inputStream ->
            val channel = inputStream.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun loadInterpreter() {
        val buffer = loadModelBuffer()
        flexDelegate = FlexDelegate()
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            addDelegate(flexDelegate)
        }
        interpreter = Interpreter(buffer, options)
    }

    /**
     * Preprocesses text and predicts intent.
     * Uses OOV_ID for unknown words.
     */
    fun predict(text: String): Pair<String, Float> {
        val interp = interpreter ?: return Pair(FALLBACK_INTENT, 0f)
        
        // Final cleaned text sent to NLU logic
        val cleanText = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") 
            .trim()
        
        Log.d("NLU_DIAGNOSTIC", "Final cleaned text sent to NLU: '$cleanText'")
            
        val tokens = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        val inputTensor = interp.getInputTensor(0)
        val shape = inputTensor.shape()
        val length = if (shape.size >= 2) shape[1] else seqLen

        return try {
            val row = IntArray(length) { PAD_ID }
            tokens.take(length).forEachIndexed { i, tok ->
                // Ensure OOV handling (assigns OOV_ID if word is missing)
                row[i] = wordToId[tok] ?: OOV_ID
            }
            
            val input = arrayOf(row)
            val numClasses = interp.getOutputTensor(0).shape().last()
            val output = Array(1) { FloatArray(numClasses) }
            
            interp.run(input, output)
            val (intent, confidence) = logitsToIntent(output[0])
            
            Log.d("NLU_DIAGNOSTIC", "Model predicted: $intent with score: $confidence")
            Pair(intent, confidence)
            
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            Pair(FALLBACK_INTENT, 0f)
        }
    }

    private fun logitsToIntent(logits: FloatArray): Pair<String, Float> {
        if (logits.isEmpty()) return Pair(FALLBACK_INTENT, 0f)
        val probs = softmax(logits)
        var bestIdx = 0
        var bestP = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > bestP) {
                bestP = probs[i]
                bestIdx = i
            }
        }
        val name = indexToIntent[bestIdx] ?: FALLBACK_INTENT
        return Pair(name, bestP)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0f
        val exp = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = kotlin.math.exp(logits[i] - max)
            exp[i] = e
            sum += e
        }
        if (sum == 0f) return FloatArray(logits.size) { 1f / logits.size }
        for (i in exp.indices) exp[i] /= sum
        return exp
    }

    fun close() {
        interpreter?.close()
        flexDelegate?.close()
    }

    companion object {
        private const val TAG = "NluManager"
        private const val MODEL_FILE = "nlu_model.tflite"
        private const val VOCAB_FILE = "vocab.json"
        private const val INTENT_MAP_FILE = "intent_map.json"
        
        private const val PAD_ID = 0  // Padding
        private const val OOV_ID = 1  // Out-of-vocabulary
        private const val FALLBACK_INTENT = "UNKNOWN"
    }
}
