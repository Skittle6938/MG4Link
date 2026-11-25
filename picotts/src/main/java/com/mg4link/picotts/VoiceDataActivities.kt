package com.mg4link.picotts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/** Answers the TTS settings' "which voices are installed" check: all of them ship in the APK. */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val voices = ArrayList(PicoEngine.VOICES.map { "${it.language}-${it.country}" })
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, Intent()
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, voices)
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList()))
        finish()
    }
}

/** The sentence the TTS settings play for "Listen to an example". */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sample = when (intent.getStringExtra("language")) {
            "fra" -> "Ceci est un exemple de synthèse vocale en français."
            "deu" -> "Dies ist ein Beispiel für Sprachsynthese auf Deutsch."
            "spa" -> "Este es un ejemplo de síntesis de voz en español."
            "ita" -> "Questo è un esempio di sintesi vocale in italiano."
            else -> "This is an example of speech synthesis in English."
        }
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra("sampleText", sample))
        finish()
    }
}
