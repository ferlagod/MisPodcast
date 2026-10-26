# ==============================================================================
# Reglas de optimización y ofuscación R8 / ProGuard para MisPodcast
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Atributos generales necesarios para Reflection, Serialización y Debugging
# ------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ------------------------------------------------------------------------------
# 2. Room Database
# ------------------------------------------------------------------------------
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep class com.ferlagod.mispodcast.data.local.entities.** { *; }
-keep class com.ferlagod.mispodcast.data.local.model.** { *; }
-keep class com.ferlagod.mispodcast.data.local.dao.** { *; }
-keep class com.ferlagod.mispodcast.data.local.PodcastDatabase* { *; }
-keep class com.ferlagod.mispodcast.data.backup.** { *; }

# ------------------------------------------------------------------------------
# 3. WorkManager (Background jobs por Reflection)
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.ferlagod.mispodcast.worker.** { *; }

# ------------------------------------------------------------------------------
# 4. Gson & DTOs (Modelos remotos de Apple Search y Gemini AI)
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class com.google.gson.** { *; }
-keep class com.ferlagod.mispodcast.data.remote.api.** { *; }
-keep class com.ferlagod.mispodcast.ai.GeminiPodcastRecommendation { *; }
-keep class com.ferlagod.mispodcast.ai.GeminiCategoryRecommendation { *; }
-keepclassmembers class com.ferlagod.mispodcast.ai.** { *; }

# ------------------------------------------------------------------------------
# 5. Media3, ExoPlayer y Servicios de Reproducción en Foreground
# ------------------------------------------------------------------------------
-keep class androidx.media3.session.** { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class com.ferlagod.mispodcast.playback.** { *; }

# ------------------------------------------------------------------------------
# 6. Retrofit 2, OkHttp 3 & Okio
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ------------------------------------------------------------------------------
# 7. Google Generative AI (Gemini Client SDK & Kotlinx Serialization)
# ------------------------------------------------------------------------------
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**
-keepattributes *Annotation*,InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ------------------------------------------------------------------------------
# 8. Jetpack Compose, Navegación y ViewModels
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.ferlagod.mispodcast.ui.navigation.Screen* { *; }
-keepclassmembers enum * { *; }

# ------------------------------------------------------------------------------
# 9. Coil (Carga y Decodificación de Portadas)
# ------------------------------------------------------------------------------
-dontwarn coil.**
-keep class coil.** { *; }
