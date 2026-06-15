# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- ARCore ---
# Keep ARCore classes to prevent stripping of JNI-bound symbols
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**

# --- OpenCV ---
# Keep OpenCV native bindings
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# --- Timber ---
# Keep Timber so log tags are preserved in stack traces
-keep class timber.log.** { *; }
-dontwarn timber.log.**

# --- Kotlin / Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# --- Jetpack Compose ---
# Compose tooling should only be in debug builds; release keeps are minimal
-keep class androidx.compose.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
