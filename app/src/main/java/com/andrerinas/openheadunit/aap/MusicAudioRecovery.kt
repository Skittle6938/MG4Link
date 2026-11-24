package com.andrerinas.openheadunit.aap

/**
 * Music that the phone reports as playing while its sound never comes to the car (#36). After a
 * Bluetooth wake-up, Android Auto sometimes starts the music by itself on the phone's own speaker:
 * the media sink is set up and the playback status says "playing", but the phone never starts the
 * audio stream. A pause then play from the car (what the user does by hand) brings it over.
 *
 * Only when the media sink was set up (so the music is meant to come over Wi-Fi), the phone says
 * it plays, no audio started since the session began, and [WAIT_MS] went by; once per session.
 */
object MusicAudioRecovery {
    /**
     * Healthy starts bring the sound at most about 1.5 s after the phone says it plays (warm
     * starts; cold starts before it), so 2 s still leaves those alone.
     */
    const val WAIT_MS = 2_000L

    /** The phone reports the pause about 150 ms after it; the play follows with some margin. */
    const val PAUSE_TO_PLAY_MS = 300L

    /** Slowest healthy start and pause acknowledgement seen in the logs (#36). */
    const val SLOWEST_HEALTHY_START_MS = 1_500L
    const val PAUSE_ACK_MS = 150L

    /** When the music sink (the AUDIO channel) was last set up, elapsedRealtime. */
    @Volatile var musicSinkSetupAtMs = 0L

    fun shouldRecover(
        sessionStartMs: Long,
        musicSinkSetupAtMs: Long,
        musicStartedAtMs: Long,
        phoneSaysPlaying: Boolean,
        alreadyDone: Boolean,
    ): Boolean =
        !alreadyDone && phoneSaysPlaying && sessionStartMs > 0L &&
            musicSinkSetupAtMs >= sessionStartMs && musicStartedAtMs < sessionStartMs
}
