# Volta ProGuard rules

# ARCore
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**

# CameraX
-keep class androidx.camera.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
