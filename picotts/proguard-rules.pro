# The native library looks these up by name.
-keepclasseswithmembernames class com.mg4link.picotts.PicoEngine { native <methods>; }
-keep interface com.mg4link.picotts.PicoEngine$Sink { boolean onAudio(byte[], int); }
-keepclassmembers class * implements com.mg4link.picotts.PicoEngine$Sink { boolean onAudio(byte[], int); }
