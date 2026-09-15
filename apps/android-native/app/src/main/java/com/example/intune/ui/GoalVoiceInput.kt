package com.example.intune.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class GoalVoiceInput(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: () -> Unit,
    private val onProcessing: () -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = onListening()
            override fun onResults(results: android.os.Bundle?) {
                val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (transcript.isNullOrEmpty()) onError("Didn't catch that — try again?") else onTranscript(transcript)
            }
            override fun onError(error: Int) = onError("Didn't catch that — try again?")
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onProcessing()
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
        })
    }

    fun start() = recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell me your goals")
    })

    fun stop() = recognizer.stopListening()

    fun destroy() = recognizer.destroy()
}
