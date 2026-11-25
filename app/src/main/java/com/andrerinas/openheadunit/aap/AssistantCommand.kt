package com.andrerinas.openheadunit.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.utils.AppLog
import java.util.Locale

/**
 * Says a sentence to the phone's voice assistant for another app (#71): a destination ("Navigate
 * to …"), or any command ("Play … on YouTube Music").
 *
 * Android Auto has no message that lets the head unit hand text to the phone. What it does let the
 * head unit do is press the voice key and supply the microphone: MG4Link opens a voice session and,
 * instead of the car's microphone, sends the sentence spoken by the car's speech engine (Pico TTS,
 * which MG4Link installs, see [SpeechSynthesis]). Nothing is played in the car while the phone's
 * mic is open for it (not even the assistant's beeps) and no audio focus is asked of the car, so
 * the music or the radio carries on; the steering wheel's voice key is ignored. Unless the caller
 * allows it, the assistant's answer and the guidance's first words are dropped too.
 *
 * Gemini's end-of-speech detection does not fire on synthesized speech: the phone closes the mic
 * only after the audio stops, so a short noise trail is sent and then nothing (measured on the car,
 * 2026-11-25: the answer about 9 s after the mic opened; cut sooner, the phone waited much longer).
 */
object AssistantCommand {
    /** Longest a command may hold the voice path, synthesis included. */
    const val TIMEOUT_MS = 20_000L
    /** After the voice session stops, the assistant's or guidance's answer may still be arriving. */
    const val REPLY_TAIL_MS = 4_000L
    /** Noise before the sentence (the phone's mic opening) and after it. */
    const val LEAD_SILENCE_MS = 300
    const val TRAIL_SILENCE_MS = 1_800
    /** Once the sentence and its trail are sent, noise for this long more, then nothing. */
    const val MAX_AFTER_SPEECH_MS = 2_000L
    /** Audio sent per mic frame. */
    const val FRAME_MS = 20

    const val SOURCE = "assistant-command"

    @Volatile var active = false
        private set
    @Volatile var speakReply = false
        private set
    @Volatile private var pcm: ByteArray? = null
    @Volatile private var activeUntilMs = 0L
    @Volatile private var synthesizing = false
    @Volatile private var requestToken = 0
    @Volatile private var replyMuteUntilMs = 0L
    /** From the voice key until the phone closes its mic: nothing played, no focus asked of the car. */
    @Volatile var listening = false
        private set

    /** What another app asks: a sentence, or a place to navigate to, in a language. */
    data class Command(
        val phrase: String? = null,
        val label: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val language: String? = null,
        val speakReply: Boolean = false,
    ) {
        val hasSomethingToSay: Boolean get() = !phrase.isNullOrBlank() || phraseFor("en", label, lat, lon) != null

        /** The sentence in [language], the one the speech engine will use. */
        fun sentence(language: String): String? = phrase?.trim()?.takeIf { it.isNotEmpty() } ?: phraseFor(language, label, lat, lon)
    }

    /** "Navigate to [label or coordinates]" in [language] (English for the languages not listed). */
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
        (channel == com.andrerinas.openheadunit.aap.protocol.Channel.ID_AU1 ||
            channel == com.andrerinas.openheadunit.aap.protocol.Channel.ID_AU2) &&
            (listening || !speakReply && (active || SystemClock.elapsedRealtime() < replyMuteUntilMs))

    /** Whether a synthesized sentence is waiting for the phone to open the mic. */
    fun readyToFeed(): Boolean = active && pcm != null

    /**
     * No assistant or speech focus is asked of the car while the sentence is said, nor at all
     * when the answer is muted: the music or the radio is not lowered for it.
     */
    fun holdsCarFocus(): Boolean =
        listening || !speakReply && (active || SystemClock.elapsedRealtime() < replyMuteUntilMs)

    /** The phone closed its mic: the answer, if allowed, may now take the car's focus. */
    fun onMicClosed() {
        listening = false
    }

    /** Whether the voice key from the steering wheel is to be ignored right now. */
    fun blocksWheelVoiceKey(): Boolean = active

    fun onVoiceSessionStopped() {
        if (!active) return
        finish("voice session stopped")
    }

    private fun finish(reason: String) {
        if (!active) return
        active = false
        listening = false
        pcm = null
        replyMuteUntilMs = SystemClock.elapsedRealtime() + REPLY_TAIL_MS
        AppLog.i("AssistantCommand: done ($reason)")
    }

    /** Starts a command; returns false when one is running or no session is up. */
    fun request(context: Context, command: Command): Boolean {
        val comm = App.provide(context).commManager
        if (comm.connectionState.value !is CommManager.ConnectionState.TransportStarted) {
            AppLog.i("AssistantCommand: no Android Auto session - ignored")
            return false
        }
        if (active && (synthesizing || pcm != null) && SystemClock.elapsedRealtime() < activeUntilMs) {
            AppLog.i("AssistantCommand: one is already running - ignored")
            return false
        }
        active = true
        synthesizing = true
        val token = ++requestToken
        speakReply = command.speakReply
        activeUntilMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        val app = context.applicationContext
        Thread({
            val spoken = try {
                SpeechSynthesis.synthesize(app, command.language ?: Locale.getDefault().language) { command.sentence(it) }
            } catch (e: Throwable) {
                AppLog.w("AssistantCommand: speech synthesis failed: ${e.message}"); null
            }
            synthesizing = false
            if (spoken == null) { finish("no synthesized speech"); return@Thread }
            pcm = normalize(SpeechSynthesis.Wav.toMono16(spoken.pcm, settingsMicRate(app)))
            AppLog.i("AssistantCommand: \"${spoken.text}\" (${spoken.language}, answer ${if (command.speakReply) "played" else "dropped"}) - opening the assistant")
            // From the key on: the phone asks for audio focus as it opens the assistant, before its mic.
            listening = true
            comm.sendKey(KeyEvent.KEYCODE_SEARCH, true, null, SOURCE)
            comm.sendKey(KeyEvent.KEYCODE_SEARCH, false, null, SOURCE)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (active && token == requestToken && SystemClock.elapsedRealtime() >= activeUntilMs) finish("timeout")
            }, TIMEOUT_MS)
        }, "AssistantCommand").start()
        return true
    }

    private fun settingsMicRate(context: Context): Int =
        com.andrerinas.openheadunit.utils.Settings(context).micSampleRate.takeIf { it > 0 } ?: 16_000

    /**
     * Feeds the synthesized sentence to the phone in place of the car's microphone, at real-time
     * pace, then a little noise. Returns false when no command is pending, so the caller records
     * from the car's microphone as usual.
     */
    fun feedMic(micRate: Int, isOpen: () -> Boolean, send: (ByteArray, Int) -> Unit): Boolean {
        val speech = pcm ?: return false
        if (!active) return false
        pcm = null // one sentence per command: a follow-up question from the assistant gets the car's mic
        listening = true
        Thread({
            val frameBytes = micRate * FRAME_MS / 1000 * 2
            val stream = withSilence(speech, micRate, LEAD_SILENCE_MS, TRAIL_SILENCE_MS)
            var offset = 0
            val silence = ByteArray(frameBytes).also { addNoise(it, 0, frameBytes) }
            val startMs = SystemClock.elapsedRealtime()
            var frames = 0L
            var speechEndMs = 0L
            while (isOpen() && active && SystemClock.elapsedRealtime() < activeUntilMs) {
                val frame = if (offset < stream.size) {
                    val n = minOf(frameBytes, stream.size - offset)
                    val f = ByteArray(frameBytes)
                    System.arraycopy(stream, offset, f, 0, n)
                    offset += n
                    f
                } else {
                    if (speechEndMs == 0L) speechEndMs = SystemClock.elapsedRealtime()
                    if (SystemClock.elapsedRealtime() - speechEndMs >= MAX_AFTER_SPEECH_MS) break
                    silence
                }
                send(frame, frameBytes)
                frames++
                val wait = startMs + frames * FRAME_MS - SystemClock.elapsedRealtime()
                if (wait > 0) Thread.sleep(wait)
            }
            AppLog.i("AssistantCommand: mic fed ${frames * FRAME_MS} ms")
        }, "AssistantCommandMic").start()
        return true
    }

    /** [speech] with [leadMs] of noise before and [trailMs] after, 16-bit mono at [rate]. */
    fun withSilence(speech: ByteArray, rate: Int, leadMs: Int, trailMs: Int): ByteArray {
        val lead = rate * leadMs / 1000 * 2
        val trail = rate * trailMs / 1000 * 2
        val out = ByteArray(lead + speech.size + trail)
        System.arraycopy(speech, 0, out, lead, speech.size)
        addNoise(out, 0, lead)
        addNoise(out, lead + speech.size, trail)
        return out
    }

    /** A faint noise floor (about -50 dBFS), as a real microphone has, instead of digital silence. */
    private val noise = java.util.Random(7)
    fun addNoise(buf: ByteArray, from: Int, length: Int) {
        var i = from
        while (i + 1 < from + length && i + 1 < buf.size) {
            val v = noise.nextInt(201) - 100
            buf[i] = (v and 0xff).toByte(); buf[i + 1] = ((v shr 8) and 0xff).toByte()
            i += 2
        }
    }

    /** Scales 16-bit mono PCM so its loudest sample reaches about 90 % of full scale. */
    fun normalize(pcm: ByteArray): ByteArray {
        var peak = 1
        for (i in 0 until pcm.size / 2) {
            val v = ((pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)).toShort().toInt()
            peak = maxOf(peak, kotlin.math.abs(v))
        }
        val gain = 29_000.0 / peak
        val out = ByteArray(pcm.size)
        for (i in 0 until pcm.size / 2) {
            val v = ((pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)).toShort().toInt()
            val n = (v * gain).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (n and 0xff).toByte(); out[i * 2 + 1] = ((n shr 8) and 0xff).toByte()
        }
        return out
    }

    /** Reads a command from an intent: see the EXTRA_ keys. */
    fun commandFrom(intent: Intent): Command = Command(
        phrase = intent.getStringExtra(EXTRA_PHRASE),
        label = intent.getStringExtra(EXTRA_LABEL),
        lat = if (intent.hasExtra(EXTRA_LAT)) intent.getDoubleExtra(EXTRA_LAT, Double.NaN) else null,
        lon = if (intent.hasExtra(EXTRA_LON)) intent.getDoubleExtra(EXTRA_LON, Double.NaN) else null,
        language = intent.getStringExtra(EXTRA_LANG)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
        speakReply = intent.getBooleanExtra(EXTRA_SPEAK_REPLY, false),
    )

    fun handle(context: Context, intent: Intent) {
        val command = commandFrom(intent)
        if (!command.hasSomethingToSay) {
            AppLog.w("AssistantCommand: no phrase, label or valid coordinates - ignored")
            return
        }
        request(context, command)
    }

    /** Broadcast action, for apps signed with the platform key (permission ASSISTANT_COMMAND). */
    const val ACTION_COMMAND = "com.allgo.app.androidauto.ASSISTANT_COMMAND"
    /** The sentence to say, as is. */
    const val EXTRA_PHRASE = "phrase"
    /** Without a phrase: a place, said as "Navigate to <label>" (or "lat"/"lon" in degrees). */
    const val EXTRA_LABEL = "label"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LON = "lon"
    /** Language of the voice and of "Navigate to", e.g. "fr"; the car's language by default. */
    const val EXTRA_LANG = "lang"
    /** True to hear the assistant's answer in the car; false (default) drops it. */
    const val EXTRA_SPEAK_REPLY = "speakReply"
}

/** ASSISTANT_COMMAND from apps signed like MG4Link (platform key), see [AssistantCommand]. */
class AssistantCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AssistantCommand.handle(context, intent)
}
