


# -------------------------------------------------------------------------
# FinTrack ProGuard/R8 Rules
# -------------------------------------------------------------------------

# General Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Mapping file and debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, Signature, EnclosingMethod

# Kotlin specific
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.internal.PlatformImplementationsKt

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
  @androidx.room.* <fields>;
  @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Kotlin Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Biometric & Security
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

# Ktor & Netty (Server & Network)
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keep class io.netty.** { *; }

# JWT Security (Auth0) - CRITICAL: Prevent TypeReference errors
-keep class com.auth0.** { *; }
-keep interface com.auth0.** { *; }
-keep class com.auth0.jwt.** { *; }
-dontwarn com.auth0.**

# Suppress warnings for missing classes that are not needed at runtime in Android
-dontwarn com.google.j2objc.annotations.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn reactor.blockhound.**

# More Netty optional dependencies
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.lz4.**
-dontwarn net.jpountz.xxhash.**
-dontwarn org.jboss.marshalling.**
-dontwarn sun.security.x509.**

-dontwarn io.ktor.**
-dontwarn okio.**
-dontwarn org.slf4j.**

# ZXing (QR Code)
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Remove unused warnings
-dontwarn androidx.test.**
-dontwarn org.junit.**
-dontwarn com.google.devtools.ksp.**

# Optimization flags
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
