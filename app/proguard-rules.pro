# ==============================================================================
# 인생맑음 (Sunny Life) Production R8 & ProGuard Optimization / Keep Rules
# ==============================================================================

# --- 1. General Optimization & Reflection Preservation ---
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- 2. Room Database & DAOs ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.Entity
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Query *;
    @androidx.room.Insert *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
}
-keep class com.example.data.database.** { *; }

# --- 3. Moshi & Serialization ---
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn javax.annotation.**

# --- 4. OkHttp & Retrofit ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# --- 5. Firebase, Firestore & Google Play Services ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- 6. Domain Data Models & Entities (Serialized or Persisted) ---
-keep class com.example.data.repository.EconomicIndicators { *; }
-keep class com.example.data.repository.CalculatorData { *; }
-keep class com.example.data.repository.ChatMessage { *; }
-keep class com.example.data.repository.** { *; }

# --- 7. Kotlin Coroutines & Jetpack WorkManager ---
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class com.example.data.worker.** { *; }
-keep class com.example.data.sync.** { *; }
-keep class com.example.data.network.** { *; }

# --- 8. Compose Runtime, ViewModels & UI Enums ---
-keepclassmembers enum class * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
-keep class com.example.ui.viewmodel.Screen* { *; }
-keep class com.example.ui.viewmodel.RetirementLifestyle* { *; }
-keep class com.example.ui.viewmodel.AssetRange* { *; }
-keep class com.example.ui.viewmodel.CompanionCharacter* { *; }
-keep class com.example.DashboardTab* { *; }
-keep class com.example.ui.viewmodel.RetirementViewModel { *; }
