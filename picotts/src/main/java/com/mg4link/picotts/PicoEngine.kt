package com.mg4link.picotts

import android.content.Context
import java.io.File

/**
 * SVOX Pico voices and synthesis. Output is 16 kHz mono 16-bit PCM, handed over in chunks.
 */
object PicoEngine {
    const val SAMPLE_RATE = 16_000

    /** A Pico voice: its locale (ISO 639-2 / 3166 alpha-3, as Android's TTS API uses) and files. */
    class Voice(val language: String, val country: String, val tag: String, val ta: String, val sg: String)

    val VOICES = listOf(
        Voice("eng", "USA", "en-US", "en-US_ta.bin", "en-US_lh0_sg.bin"),
        Voice("eng", "GBR", "en-GB", "en-GB_ta.bin", "en-GB_kh0_sg.bin"),
        Voice("deu", "DEU", "de-DE", "de-DE_ta.bin", "de-DE_gl0_sg.bin"),
        Voice("spa", "ESP", "es-ES", "es-ES_ta.bin", "es-ES_zl0_sg.bin"),
        Voice("fra", "FRA", "fr-FR", "fr-FR_ta.bin", "fr-FR_nk0_sg.bin"),
        Voice("ita", "ITA", "it-IT", "it-IT_ta.bin", "it-IT_cm0_sg.bin"),
    )

    /** The voice for a locale: the exact country if Pico has it, else the language's first voice. */
    fun voiceFor(language: String, country: String): Voice? {
        val lang = language.lowercase()
        val sameLanguage = VOICES.filter { it.language == lang }
        return sameLanguage.firstOrNull { it.country.equals(country, ignoreCase = true) } ?: sameLanguage.firstOrNull()
    }

    /** Pico reads markup in angle brackets: a plain text keeps none, with speed and pitch around it. */
    fun markup(text: String, speechRate: Int, pitch: Int): String {
        val plain = text.replace('<', ' ').replace('>', ' ')
        val speed = speechRate.coerceIn(20, 500)
        val tone = pitch.coerceIn(50, 200)
        return "<speed level=\"$speed\"><pitch level=\"$tone\">$plain</pitch></speed>"
    }

    fun interface Sink {
        /** Returns false to stop the synthesis. */
        fun onAudio(buffer: ByteArray, length: Int): Boolean
    }

    private var loaded = false

    /** 0 when done, 1 when [sink] stopped it, -1 on error. */
    @Synchronized
    fun synthesize(context: Context, voice: Voice, text: String, sink: Sink): Int {
        if (!loaded) {
            System.loadLibrary("picotts")
            loaded = true
        }
        val dir = File(context.filesDir, "lang").apply { mkdirs() }
        val ta = copyAsset(context, dir, voice.ta)
        val sg = copyAsset(context, dir, voice.sg)
        return nativeSynthesize(ta.absolutePath, sg.absolutePath, text, sink)
    }

    private fun copyAsset(context: Context, dir: File, name: String): File {
        val out = File(dir, name)
        if (!out.exists() || out.length() == 0L) {
            val tmp = File(dir, "$name.tmp")
            context.assets.open("lang/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(out)
        }
        return out
    }

    @JvmStatic
    private external fun nativeSynthesize(ta: String, sg: String, text: String, sink: Sink): Int
}
