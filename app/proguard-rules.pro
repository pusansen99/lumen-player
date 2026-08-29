# R8 shrinking is on for the release build. Most keeps come from the libraries'
# own consumer rules (media3, okhttp, okio, datastore, coroutines). The rules
# below are belt-and-suspenders for the reflective paths we rely on.

# media3 loads HLS/DASH/SmoothStreaming source factories by reflection.
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory { *; }
-dontwarn androidx.media3.**

# OkHttp / Okio platform shims reference optional JVM classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# org.json is provided by the platform at runtime.
-dontwarn org.json.**

# Keep our BuildConfig fields (read at runtime by the updater).
-keep class com.lumen.player.BuildConfig { *; }
