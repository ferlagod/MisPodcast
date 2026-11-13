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
# 4. DTOs remotos (Apple Search y Podcast Index, deserializados con kotlinx.serialization)
# ------------------------------------------------------------------------------
-keep class com.ferlagod.mispodcast.data.remote.api.** { *; }

# ------------------------------------------------------------------------------
# 5. Servicio de reproducción (Media3 incluye sus propias reglas para la biblioteca)
# ------------------------------------------------------------------------------
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
# 7. Kotlinx Serialization (la biblioteca incluye sus reglas; se conservan los campos serializables)
# ------------------------------------------------------------------------------
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
# 9. Coil
# ------------------------------------------------------------------------------
# Necesaria: Coil descubre su componente de red (coil-network-okhttp) con ServiceLoader y, sin
# esta regla, R8 lo elimina y ninguna portada remota carga en release (comprobado en dispositivo).
-dontwarn coil3.**
-keep class coil3.** { *; }

# ------------------------------------------------------------------------------
# 10. Eliminar logs de depuración en release (Log.e se conserva para diagnosticar fallos)
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
