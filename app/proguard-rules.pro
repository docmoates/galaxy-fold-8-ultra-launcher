# ProGuard / R8 rules for FoldOS release builds (isMinifyEnabled + isShrinkResources, see build.gradle.kts).
# Used together with proguard-android-optimize.txt, i.e. R8 in full mode with optimisation on.
#
# Bisecting an R8 problem: ./gradlew assembleRelease -Pfoldos.minify=false builds an unshrunk release.
# Components declared in AndroidManifest.xml (activities, receivers, the notification listener) are kept
# automatically by AGP's generated aapt rules, so they are not repeated here.

# ---------------------------------------------------------------------------------------------
# Stack traces
# ---------------------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations / generics that reflection-based libraries below read at runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# ---------------------------------------------------------------------------------------------
# kotlinx.serialization (LauncherSettings, Profile, DockConfig, ColorSchemeConfig, GestureAction,
# IconShape, BackupFile / BackupPage / BackupItem, ... every @Serializable class)
# Mirrors the rules published in the kotlinx.serialization README.
# ---------------------------------------------------------------------------------------------
-dontnote kotlinx.serialization.**

# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Generated serializers and the descriptors they reference.
-keep,includedescriptorclasses class com.foldos.launcher.**$$serializer { *; }
-keepclassmembers class com.foldos.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.foldos.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Settings, profiles and backups store enums by name (valueOf / values / entries).
-keepclassmembers enum com.foldos.launcher.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------------------------
# Room (LayoutDatabase, PageEntity, ItemEntity, LayoutDao and the generated *_Impl classes)
# ---------------------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------------------------
# DataStore (Preferences) uses the lite protobuf runtime that is bundled inside the artifact.
# ---------------------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---------------------------------------------------------------------------------------------
# AppAuth (Gmail sign-in in mail/): authorization service, token requests, redirect receiver.
# ---------------------------------------------------------------------------------------------
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# Encrypted token storage (androidx.security.crypto -> Tink, which uses a shaded protobuf-lite).
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.checkerframework.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.**

# ---------------------------------------------------------------------------------------------
# Compose: the runtime, UI and Material 3 artifacts ship their own consumer rules, nothing is
# reflected on by name. @Preview functions only exist for the debug tooling.
# ---------------------------------------------------------------------------------------------
-dontwarn androidx.compose.ui.tooling.**

# ---------------------------------------------------------------------------------------------
# Logging: drop verbose / debug calls from release builds (info, warning and error stay, so the
# FoldOsStartup timings and failure reports in Logcat keep working).
# ---------------------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
