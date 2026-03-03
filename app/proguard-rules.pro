# TinyOscillator ProGuard Rules

# Strip all logs in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Strip Timber debug/verbose logs in release builds
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}
-assumenosideeffects class timber.log.Timber$Forest {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.tinyoscillator.**$$serializer { *; }
-keepclassmembers class com.tinyoscillator.** {
    *** Companion;
}
-keepclasseswithmembers class com.tinyoscillator.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.tinyoscillator.core.database.entity.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Google Tink / EncryptedSharedPreferences - errorprone annotations are compile-only
-dontwarn com.google.errorprone.annotations.**

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
