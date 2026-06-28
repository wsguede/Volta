# Volta ProGuard rules

# ARCore does not ship consumer ProGuard rules
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**
