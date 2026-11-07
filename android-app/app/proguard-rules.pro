# Proguard rules for Swiftify Android

# JNA & Native C-API reflection
-keepclassmembers class * extends com.sun.jna.Library {
    public <methods>;
}
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Swiftify JNA interfaces and bridging
-keep class org.swiftify.app.SwiftifyCoreLib { *; }
-keep class org.swiftify.app.SpotifyBridge { *; }
-keepclassmembers class org.swiftify.app.SpotifyBridge { *; }

# Data models
-keep class org.swiftify.app.TrackItem { *; }
-keep class org.swiftify.app.PlaylistItem { *; }
-keep class org.swiftify.app.ArtistItem { *; }
-keep class org.swiftify.app.LyricLine { *; }
-keep class org.swiftify.app.AuthState { *; }

# Media & Background Playback Service
-keep class org.swiftify.app.PlaybackService { *; }
-keep class org.swiftify.app.PlaybackEventListener { *; }

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# AndroidX Crypto & Security
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Compose runtime
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
