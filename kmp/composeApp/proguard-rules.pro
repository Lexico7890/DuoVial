# DuoVial ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AWS SDK
-keep class aws.smithy.kotlin.** { *; }
-keep class aws.sdk.kotlin.** { *; }

# Compose
-keep class androidx.compose.** { *; }
