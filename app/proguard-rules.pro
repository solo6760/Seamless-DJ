# Project ProGuard rules for Party DJ Automix App

# Keep app models, DAOs, and entities
-keep class com.example.data.local.** { *; }
-keep class com.example.data.model.** { *; }

# Keep Room Database classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep OkHttp for Gemini REST API calls
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Coroutines internals
-keepclassmembers class * {
    @kotlinx.coroutines.InternalCoroutinesApi *;
}

# Optimize and remove unused attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.annotation.**

