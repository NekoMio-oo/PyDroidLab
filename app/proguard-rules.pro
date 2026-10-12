# Keep Chaquopy entry points
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep sora-editor
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**

# Keep terminal emulator
-keep class jackpal.androidterm.** { *; }
-dontwarn jackpal.androidterm.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }
