# ScanMate AI Pro safe release rules.
# R8/resource shrinking are enabled for release. Keep rules are limited to
# dependencies actually used by this project: Room, ML Kit, CameraX, ZXing,
# DataStore, Moshi/Retrofit/OkHttp, and app data/domain models.

# App models used by Room, widgets, and serialization/reflection paths.
-keep class com.synthbyte.scanmate.data.** { *; }
-keep class com.synthbyte.scanmate.domain.** { *; }
-keep class com.synthbyte.scanmate.widgets.** { *; }

# Room generates implementation classes and may read annotations/signatures.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes InnerClasses,EnclosingMethod

# ML Kit Text Recognition + Barcode Scanning.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# CameraX lifecycle/camera-view internals.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ZXing QR generation.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Retrofit/Moshi/OkHttp/Gson-style reflection safety for Gemini integration.
-keep class retrofit2.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-dontwarn javax.annotation.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.Unit
