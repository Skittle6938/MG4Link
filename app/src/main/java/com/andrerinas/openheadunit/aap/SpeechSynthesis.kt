package com.andrerinas.openheadunit.aap

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.PicoTtsInstaller
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Speech through Android's TextToSpeech: the user's engine if it can say the language (Google's,
 * when installed), else Pico TTS, which MG4Link installs on the car ([PicoTtsInstaller]).
 */
object SpeechSynthesis {
    private const val INIT_TIMEOUT_S = 5L
    private const val SYNTH_TIMEOUT_S = 10L

    class Spoken(val text: String, val language: String, val pcm: Wav.Pcm)

    /**
     * Says the sentence [sentenceFor] gives for the language used: [language] when an engine can
     * say it, else English. Must not run on the main thread.
     */
    fun synthesize(context: Context, language: String, preferredEngine: String? = null, sentenceFor: (String) -> String?): Spoken? {
        for (engine in engines(context, preferredEngine)) {
            val tts = open(context, engine) ?: continue
            try {
                val lang = listOf(language, "en").distinct().firstOrNull { available(tts, it) } ?: continue
                val text = sentenceFor(lang) ?: return null
                val pcm = toPcm(context, tts, text) ?: continue
                AppLog.i("SpeechSynthesis: ${engine ?: "default engine"} said it in $lang (${pcm.rate} Hz)")
                return Spoken(text, lang, pcm)
            } finally {
                tts.shutdown()
            }
        }
        AppLog.w("SpeechSynthesis: no speech engine could say it")
        PicoTtsInstaller.ensureInstalled(context)
        return null
    }

    /** [preferred] (tests) or the default engine first, then Pico if it is installed. */
    private fun engines(context: Context, preferred: String?): List<String?> = buildList {
        if (preferred != null) add(preferred)
        add(null)
        if (PicoTtsInstaller.isInstalled(context)) add(PicoTtsInstaller.PACKAGE)
    }

    private fun open(context: Context, engine: String?): TextToSpeech? {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val listener = TextToSpeech.OnInitListener { status = it; ready.countDown() }
        val tts = if (engine == null) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
        if (!ready.await(INIT_TIMEOUT_S, TimeUnit.SECONDS) || status != TextToSpeech.SUCCESS) {
            tts.shutdown()
            return null
        }
        if (engine == null && tts.engines.isEmpty()) {
            tts.shutdown()
            return null
        }
        return tts
    }

    private fun available(tts: TextToSpeech, language: String): Boolean {
        val locale = localeFor(language)
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        return tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    /** The locale for a language code, with the car's country when it speaks that language. */
    fun localeFor(language: String): Locale {
        val system = Locale.getDefault()
        return if (system.language == language && system.country.isNotEmpty()) Locale(language, system.country)
        else Locale(language)
    }

    private fun toPcm(context: Context, tts: TextToSpeech, text: String): Wav.Pcm? {
        val file = File(context.cacheDir, "assistant-command.wav")
        file.delete()
        val done = CountDownLatch(1)
        var ok = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { ok = true; done.countDown() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { done.countDown() }
            override fun onError(utteranceId: String?, errorCode: Int) { done.countDown() }
        })
        if (tts.synthesizeToFile(text, Bundle(), file, "assistant-command") != TextToSpeech.SUCCESS) return null
        if (!done.await(SYNTH_TIMEOUT_S, TimeUnit.SECONDS) || !ok || !file.exists()) return null
        return Wav.parse(file.readBytes()).also { file.delete() }?.takeIf { it.data.isNotEmpty() }
    }

    /**
     * Test: plays [phrase] on the car's speakers as the phone's mic gets it, like Android Auto
     * media (usage MEDIA, tagged as the Android Auto source, the path the car routes).
     */
    fun preview(context: Context, language: String, phrase: String, engine: String? = null) {
        val app = context.applicationContext
        Thread({
            try {
                val spoken = synthesize(app, language, engine) { phrase } ?: return@Thread
                val out = AssistantCommand.normalize(Wav.toMono16(spoken.pcm, 48_000))
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                com.evsuite.hardware.saic.SaicCarAudioSource.attachSourceType(attributes,
                    com.evsuite.hardware.saic.SaicCarAudioSource.SRC_ANDROID_AUTO)
                val track = android.media.AudioTrack.Builder().setAudioAttributes(attributes.build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(48_000)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(48_000 * 2 / 4)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
                track.play()
                track.write(out, 0, out.size)
                track.write(ByteArray(48_000), 0, 48_000) // 0.5 s of silence so the end is not cut
                track.stop()
                Thread.sleep(300)
                track.release()
            } catch (e: Throwable) { AppLog.w("SpeechSynthesis: preview failed: ${e.message}") }
        }, "SpeechPreview").start()
    }

    /** Minimal WAV reading: 16-bit PCM, any rate and channel count. */
    object Wav {
        class Pcm(val rate: Int, val channels: Int, val data: ByteArray)

        fun parse(bytes: ByteArray): Pcm? {
            if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
            var pos = 12
            var rate = 0
            var channels = 0
            var bits = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4, Charsets.US_ASCII)
                val size = le32(bytes, pos + 4)
                val body = pos + 8
                if (id == "fmt " && size >= 16) {
                    channels = le16(bytes, body + 2)
                    rate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                } else if (id == "data") {
                    if (bits != 16 || rate <= 0 || channels <= 0) return null
                    // Streaming writers can leave the size unset (0 or -1): read to the end then.
                    val end = if (size <= 0) bytes.size else minOf(bytes.size, body + size)
                    return Pcm(rate, channels, bytes.copyOfRange(body, end))
                }
                if (size < 0) return null
                pos = body + size + (size and 1)
            }
            return null
        }

        /** Mixes down to mono and resamples linearly to [rate]. */
        fun toMono16(pcm: Pcm, rate: Int): ByteArray {
            val frames = pcm.data.size / (2 * pcm.channels)
            if (frames == 0) return ByteArray(0)
            val mono = ShortArray(frames) { i ->
                var sum = 0
                for (c in 0 until pcm.channels) sum += le16s(pcm.data, (i * pcm.channels + c) * 2)
                (sum / pcm.channels).toShort()
            }
            val outFrames = (frames.toLong() * rate / pcm.rate).toInt()
            val out = ByteArray(outFrames * 2)
            for (i in 0 until outFrames) {
                val src = i.toDouble() * pcm.rate / rate
                val i0 = src.toInt().coerceAtMost(frames - 1)
                val i1 = (i0 + 1).coerceAtMost(frames - 1)
                val f = src - i0
                val v = (mono[i0] * (1 - f) + mono[i1] * f).toInt()
                out[i * 2] = (v and 0xff).toByte()
                out[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
            }
            return out
        }

        private fun le16(b: ByteArray, p: Int) = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)
        private fun le16s(b: ByteArray, p: Int) = le16(b, p).toShort().toInt()
        private fun le32(b: ByteArray, p: Int) = le16(b, p) or (le16(b, p + 2) shl 16)
    }
}
