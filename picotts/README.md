# Pico TTS

A standard Android speech engine (TextToSpeechService) built from the SVOX Pico sources, with its
voices: English (US, UK), German, Spanish, French and Italian.

The MG4 has no speech engine. MG4Link bundles this APK (`assets/picotts/picotts.apk`) and
installs it on the car, so it can say a sentence to the phone's assistant (#71). Any other app
can use it through Android's `TextToSpeech`. It becomes the default engine only when none is set.

- `src/main/cpp/pico/` and `src/main/assets/lang/`: SVOX Pico engine and voices, unchanged, from
  the AOSP `external/svox` sources as kept in [naggety/picotts](https://github.com/naggety/picotts)
  (commit 21089d22). Apache License 2.0, see `NOTICE`.
- `src/main/cpp/pico_jni.c` and the Kotlin code: the JNI binding and the Android engine.

Raise `versionCode` in `build.gradle.kts` on every change: MG4Link installs the bundled engine
only when it is newer than the one on the car.
