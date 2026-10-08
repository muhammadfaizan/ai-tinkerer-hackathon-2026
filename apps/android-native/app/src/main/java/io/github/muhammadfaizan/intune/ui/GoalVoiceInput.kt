package io.github.muhammadfaizan.intune.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class GoalVoiceInput(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: () -> Unit,
    private val onProcessing: () -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "No speech recognition service is available")
            onError("Speech recognition isn't available on this device. You can type your goal instead.")
            return
        }
        runCatching {
            (recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }).apply {
                setRecognitionListener(listener)
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell me your goals")
                })
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not start speech recognition", error)
            onError("Couldn't start voice input. Please try again or type your goal.")
        }
    }

    fun stop() = runCatching { recognizer?.stopListening() }
        .onFailure { Log.e(TAG, "Could not stop speech recognition", it) }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            Log.d(TAG, "Speech recognizer ready")
            onListening()
        }

        override fun onResults(results: android.os.Bundle?) {
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (transcript.isNullOrEmpty()) onError("Didn't catch that — try again?")
            else {
                Log.d(TAG, "Speech transcript received")
                onTranscript(transcript)
            }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input needs a network connection. Try again or type your goal."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again?"
                    else -> "Voice input couldn't understand that. Try again or type your goal."
                },
            )
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onProcessing()
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
    }

    private companion object {
        const val TAG = "GoalVoiceInput"
    }
}
