package com.andrerinas.openheadunit.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.utils.AppLog
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hands a destination to the phone's navigation through its voice assistant (#71).
 *
 * Android Auto has no message that lets the head unit start navigation on the phone. What it does
 * let the head unit do is press the voice key and supply the microphone: MG4Link opens a voice
 * session and, instead of the car's microphone, sends a spoken sentence ("Navigate to …")
 * synthesized in memory. Nothing is played in the car. While it runs, MG4Link asks the car for no
 * audio focus and ignores the steering wheel's voice key; unless the caller allows it, the
 * assistant's answer and the guidance's first words are dropped instead of played.
 */
object VoiceNavigation {
    /** Longest an injection may hold the voice path, synthesis included. */
    const val TIMEOUT_MS = 20_000L
    /** After the voice session stops, the assistant's or guidance's answer may still be arriving. */
    const val REPLY_TAIL_MS = 4_000L
    /** Silence before the sentence (the phone's mic opening) and after it (end of speech). */
    const val LEAD_SILENCE_MS = 300
    const val TRAIL_SILENCE_MS = 1_200
    /** Audio sent per mic frame. */
    const val FRAME_MS = 20

    @Volatile var active = false
        private set
    @Volatile var speakReply = false
        private set
    @Volatile private var pcm: ByteArray? = null
    @Volatile private var activeUntilMs = 0L
    @Volatile private var replyMuteUntilMs = 0L

    /** The sentence to say, in the language the phone's assistant most likely speaks. */
    fun phraseFor(language: String, label: String?, lat: Double?, lon: Double?): String? {
        val place = label?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0)
                String.format(Locale.ROOT, "%.5f, %.5f", lat, lon) else null
        place ?: return null
        val verb = when (language) {
            "fr" -> "Navigue vers"
            "de" -> "Navigiere zu"
            "es" -> "Navega a"
            "it" -> "Naviga verso"
            "nl" -> "Navigeer naar"
            "pt" -> "Navegar para"
            else -> "Navigate to"
        }
        return "$verb $place"
    }

    /** Whether the assistant's or guidance's audio on [channel] is to be dropped right now. */
    fun dropsReply(channel: Int): Boolean =
        !speakReply && (channel == com.andrerinas.openheadunit.aap.protocol.Channel.ID_AU1 ||
            channel == com.andrerinas.openheadunit.aap.protocol.Channel.ID_AU2) &&
            (active || SystemClock.elapsedRealtime() < replyMuteUntilMs)

    /** Whether a synthesized sentence is waiting for the phone to open the mic. */
    fun readyToFeed(): Boolean = active && pcm != null

    /** No assistant or speech focus is asked of the car while an injection or its answer runs. */
    fun holdsCarFocus(): Boolean = active || SystemClock.elapsedRealtime() < replyMuteUntilMs

    /** Whether the voice key from the steering wheel is to be ignored right now. */
    fun blocksWheelVoiceKey(): Boolean = active

    fun onVoiceSessionStopped() {
        if (!active) return
        finish("voice session stopped")
    }

    private fun finish(reason: String) {
        if (!active) return
        active = false
        pcm = null
        replyMuteUntilMs = SystemClock.elapsedRealtime() + REPLY_TAIL_MS
        AppLog.i("VoiceNavigation: done ($reason) (#71)")
    }

    /** Starts an injection; returns false when one is running or no session is up. */
    fun request(context: Context, phrase: String, allowReply: Boolean): Boolean {
        val comm = App.provide(context).commManager
        if (comm.connectionState.value !is CommManager.ConnectionState.TransportStarted) {
            AppLog.i("VoiceNavigation: no Android Auto session - ignored (#71)")
            return false
        }
        if (active && SystemClock.elapsedRealtime() < activeUntilMs) {
            AppLog.i("VoiceNavigation: one is already running - ignored (#71)")
            return false
        }
        active = true
        speakReply = allowReply
        activeUntilMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        val app = context.applicationContext
        Thread({
            val samples = try { synthesize(app, phrase, settingsMicRate(app)) } catch (e: Throwable) {
                AppLog.w("VoiceNavigation: speech synthesis failed: ${e.message}"); null
            }
            if (samples == null) { finish("no synthesized speech"); return@Thread }
            pcm = samples
            AppLog.i("VoiceNavigation: \"$phrase\" (${samples.size / 2} samples, reply ${if (allowReply) "played" else "dropped"}) - opening the assistant (#71)")
            comm.sendKey(KeyEvent.KEYCODE_SEARCH, true, null, SOURCE)
            comm.sendKey(KeyEvent.KEYCODE_SEARCH, false, null, SOURCE)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (active && SystemClock.elapsedRealtime() >= activeUntilMs) finish("timeout")
            }, TIMEOUT_MS)
        }, "VoiceNavigation").start()
        return true
    }

    const val SOURCE = "voice-navigation"

    private fun settingsMicRate(context: Context): Int =
        com.andrerinas.openheadunit.utils.Settings(context).micSampleRate.takeIf { it > 0 } ?: 16_000

    /**
     * Feeds the synthesized sentence to the phone in place of the car's microphone, at real-time
     * pace, then silence until the phone closes the mic (or the timeout). Returns false when no
     * injection is pending, so the caller records from the car's microphone as usual.
     */
    fun feedMic(micRate: Int, isOpen: () -> Boolean, send: (ByteArray, Int) -> Unit): Boolean {
        val speech = pcm ?: return false
        if (!active) return false
        Thread({
            val frameBytes = micRate * FRAME_MS / 1000 * 2
            val stream = withSilence(speech, micRate, LEAD_SILENCE_MS, TRAIL_SILENCE_MS)
            var offset = 0
            val silence = ByteArray(frameBytes)
            val startMs = SystemClock.elapsedRealtime()
            var frames = 0L
            while (isOpen() && active && SystemClock.elapsedRealtime() < activeUntilMs) {
                val frame = if (offset < stream.size) {
                    val n = minOf(frameBytes, stream.size - offset)
                    val f = ByteArray(frameBytes)
                    System.arraycopy(stream, offset, f, 0, n)
                    offset += n
                    f
                } else silence
                send(frame, frameBytes)
                frames++
                val due = startMs + frames * FRAME_MS
                val wait = due - SystemClock.elapsedRealtime()
                if (wait > 0) Thread.sleep(wait)
            }
            AppLog.i("VoiceNavigation: mic fed ${frames * FRAME_MS} ms (#71)")
        }, "VoiceNavigationMic").start()
        return true
    }

    /** [speech] with [leadMs] of silence before and [trailMs] after, 16-bit mono at [rate]. */
    fun withSilence(speech: ByteArray, rate: Int, leadMs: Int, trailMs: Int): ByteArray {
        val lead = rate * leadMs / 1000 * 2
        val trail = rate * trailMs / 1000 * 2
        val out = ByteArray(lead + speech.size + trail)
        System.arraycopy(speech, 0, out, lead, speech.size)
        return out
    }

    private fun synthesize(context: Context, phrase: String, micRate: Int): ByteArray? {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        lateinit var tts: TextToSpeech
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tts = TextToSpeech(context) { s -> status = s; ready.countDown() }
        }
        if (!ready.await(5, TimeUnit.SECONDS) || status != TextToSpeech.SUCCESS) {
            AppLog.w("VoiceNavigation: no speech synthesis engine on this car (status=$status)")
            return null
        }
        try {
            val locale = Locale.getDefault()
            val lang = tts.setLanguage(locale)
            AppLog.i("VoiceNavigation: TTS engine ${tts.defaultEngine}, language $locale -> $lang")
            val file = File(context.cacheDir, "voice-navigation.wav")
            file.delete()
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { done.countDown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { done.countDown() }
            })
            tts.synthesizeToFile(phrase, null, file, "nav")
            if (!done.await(8, TimeUnit.SECONDS) || !file.exists()) return null
            val wav = Wav.parse(file.readBytes()) ?: return null
            return Wav.toMono16(wav, micRate)
        } finally {
            try { tts.shutdown() } catch (_: Throwable) {}
        }
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
                    val end = minOf(bytes.size, body + size)
                    return Pcm(rate, channels, bytes.copyOfRange(body, end))
                }
                pos = body + size + (size and 1)
            }
            return null
        }

        /** Mixes down to mono and resamples linearly to [rate]. */
        fun toMono16(pcm: Pcm, rate: Int): ByteArray {
            val frames = pcm.data.size / (2 * pcm.channels)
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

    /** Handles NAVIGATE: "label" (place name) or "lat"/"lon", optional "phrase", "speakReply". */
    fun handle(context: Context, intent: Intent) {
        val lat = if (intent.hasExtra(EXTRA_LAT)) intent.getDoubleExtra(EXTRA_LAT, Double.NaN) else null
        val lon = if (intent.hasExtra(EXTRA_LON)) intent.getDoubleExtra(EXTRA_LON, Double.NaN) else null
        val phrase = intent.getStringExtra(EXTRA_PHRASE)?.takeIf { it.isNotBlank() }
            ?: phraseFor(Locale.getDefault().language, intent.getStringExtra(EXTRA_LABEL), lat, lon)
        if (phrase == null) {
            AppLog.w("VoiceNavigation: NAVIGATE without a label, a phrase or valid coordinates - ignored (#71)")
            return
        }
        request(context, phrase, intent.getBooleanExtra(EXTRA_SPEAK_REPLY, false))
    }

    const val ACTION_NAVIGATE = "com.allgo.app.androidauto.NAVIGATE"
    const val EXTRA_LABEL = "label"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LON = "lon"
    const val EXTRA_PHRASE = "phrase"
    const val EXTRA_SPEAK_REPLY = "speakReply"
}

/** NAVIGATE from apps signed like MG4Link (platform key), see [VoiceNavigation]. */
class VoiceNavigationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = VoiceNavigation.handle(context, intent)
}
