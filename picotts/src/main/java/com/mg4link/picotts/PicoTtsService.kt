package com.mg4link.picotts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log

/** The Android speech engine: any app's TextToSpeech can use Pico through it. */
class PicoTtsService : TextToSpeechService() {
    @Volatile private var stopped = false
    @Volatile private var current: PicoEngine.Voice? = null

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val voice = PicoEngine.voiceFor(lang ?: return TextToSpeech.LANG_NOT_SUPPORTED, country.orEmpty())
            ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (voice.country.equals(country, ignoreCase = true)) TextToSpeech.LANG_COUNTRY_AVAILABLE
            else TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> {
        val voice = current ?: PicoEngine.VOICES.first()
        return arrayOf(voice.language, voice.country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result >= TextToSpeech.LANG_AVAILABLE) current = PicoEngine.voiceFor(lang!!, country.orEmpty())
        return result
    }

    override fun onStop() {
        stopped = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val voice = PicoEngine.voiceFor(request.language, request.country)
        if (voice == null) {
            callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
            return
        }
        current = voice
        stopped = false
        if (callback.start(PicoEngine.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        val max = callback.maxBufferSize
        val text = PicoEngine.markup(request.charSequenceText?.toString() ?: request.text.orEmpty(),
            request.speechRate, request.pitch)
        val result = try {
            PicoEngine.synthesize(this, voice, text) { buffer, length ->
                var offset = 0
                while (offset < length && !stopped) {
                    val n = minOf(max, length - offset)
                    if (callback.audioAvailable(buffer, offset, n) != TextToSpeech.SUCCESS) return@synthesize false
                    offset += n
                }
                !stopped
            }
        } catch (e: Throwable) {
            Log.w(TAG, "synthesis failed: ${e.message}")
            -1
        }
        if (result < 0) callback.error(TextToSpeech.ERROR_SYNTHESIS) else callback.done()
    }

    private companion object {
        const val TAG = "PicoTts"
    }
}
