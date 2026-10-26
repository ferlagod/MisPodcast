/*
 * Mis Podcast - Gestor y reproductor libre de podcasts para Android
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General de GNU tal como fue
 * publicada por la Free Software Foundation, ya sea la versión 3 de la
 * Licencia, o (a tu elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil,
 * pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
 * COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.
 * Consulta la Licencia Pública General de GNU para más detalles.
 *
 * Deberías haber recibido una copia de la Licencia Pública General de GNU
 * junto con este programa. Si no es así, consulta <https://www.gnu.org/licenses/>.
 */

package com.ferlagod.mispodcast.sync

import android.content.Context
import android.util.Log
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

@Serializable
data class NextcloudSyncData(
    @SerialName("version") val version: Int = 2,
    @SerialName("app") val app: String = "MisPodcast",
    @SerialName("lastSyncTimestamp") val lastSyncTimestamp: Long = System.currentTimeMillis(),
    @SerialName("subscriptions") val subscriptions: List<NextcloudSubscription> = emptyList(),
    @SerialName("queue") val queue: List<NextcloudQueueItem> = emptyList(),
    @SerialName("favorites") val favorites: List<NextcloudFavoriteItem> = emptyList(),
    @SerialName("episodesProgress") val episodesProgress: List<NextcloudEpisodeProgress> = emptyList(),
    @SerialName("episodes") val episodes: List<NextcloudEpisodeProgress> = emptyList(),
    @SerialName("playbackStats") val playbackStats: List<NextcloudPlaybackStats> = emptyList(),
    @SerialName("history") val history: List<NextcloudHistoryItem> = emptyList()
)

@Serializable
data class NextcloudSubscription(
    @SerialName("feedUrl") val feedUrl: String,
    @SerialName("title") val title: String? = null,
    @SerialName("subscribedAt") val subscribedAt: Long = 0L,
    @SerialName("unsubscribedAt") val unsubscribedAt: Long = 0L,
    @SerialName("isSubscribed") val isSubscribed: Boolean = true
)

@Serializable
data class NextcloudQueueItem(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("podcastId") val podcastId: String? = null,
    @SerialName("podcastTitle") val podcastTitle: String? = null,
    @SerialName("podcastArtworkUrl") val podcastArtworkUrl: String? = null,
    @SerialName("episodeTitle") val episodeTitle: String? = null,
    @SerialName("episodeDuration") val episodeDuration: Long = 0L,
    @SerialName("positionIndex") val positionIndex: Int = 0,
    @SerialName("isCurrentlyPlaying") val isCurrentlyPlaying: Boolean = false,
    @SerialName("addedAt") val addedAt: Long = 0L
)

@Serializable
data class NextcloudFavoriteItem(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("isFavorite") val isFavorite: Boolean = true
)

@Serializable
data class NextcloudEpisodeProgress(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("currentPositionMs") val currentPositionMs: Long = 0L,
    @SerialName("isPlayed") val isPlayed: Boolean = false,
    @SerialName("lastUpdatedTimestamp") val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class NextcloudPlaybackStats(
    @SerialName("podcastId") val podcastId: String,
    @SerialName("podcastTitle") val podcastTitle: String,
    @SerialName("totalTimeListenedMs") val totalTimeListenedMs: Long = 0L,
    @SerialName("episodesCompletedCount") val episodesCompletedCount: Int = 0,
    @SerialName("lastListenedAt") val lastListenedAt: Long = 0L
)

@Serializable
data class NextcloudHistoryItem(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String? = null,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("listenedAt") val listenedAt: Long = 0L,
    @SerialName("durationListenedMs") val durationListenedMs: Long = 0L,
    @SerialName("completed") val completed: Boolean = true
)

class NextcloudSyncManager(
    private val database: PodcastDatabase,
    private val repository: PodcastRepository,
    private val preferencesManager: UserPreferencesManager,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }
) {

    private val syncMutex = Mutex()
    private val needsSubsequentSync = AtomicBoolean(false)
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debouncedSyncJob: Job? = null

    @Volatile
    private var isConfiguredCached: Boolean = false

    init {
        syncScope.launch {
            preferencesManager.userSettingsFlow.collect { settings ->
                isConfiguredCached = settings.nextcloudServerUrl.isNotBlank() &&
                        settings.nextcloudUsername.isNotBlank() &&
                        settings.nextcloudAppPassword.isNotBlank()
            }
        }
    }

    /**
     * Comprueba de manera instantánea y en memoria si Nextcloud está configurado.
     */
    fun isConfigured(): Boolean = isConfiguredCached

    /**
     * Lanza una sincronización liviana en segundo plano con debounce para agrupar múltiples
     * eventos seguidos (cambio de suscripción, progreso de escucha, etc.) en una sola petición.
     */
    fun triggerSyncIfConfigured(debounceMs: Long = 2000L) {
        if (!isConfiguredCached) return

        synchronized(this) {
            debouncedSyncJob?.cancel()
            debouncedSyncJob = syncScope.launch {
                if (debounceMs > 0) {
                    delay(debounceMs)
                }
                if (isConfiguredCached) {
                    Log.d(TAG, "Ejecutando sincronización automática liviana con Nextcloud...")
                    syncNow()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NextcloudSyncManager"
        private const val APP_FOLDER = "MisPodcast"
        private const val SYNC_FILE_NAME = "sync_state.json"

        @Volatile
        private var INSTANCE: NextcloudSyncManager? = null

        fun getInstance(
            context: Context,
            customRepository: PodcastRepository? = null,
            customClient: OkHttpClient? = null
        ): NextcloudSyncManager {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE != null) return INSTANCE!!
                val appContext = context.applicationContext
                val app = appContext as? com.ferlagod.mispodcast.MisPodcastApplication
                val container = app?.container

                val db = container?.database ?: PodcastDatabase.getInstance(appContext)
                val repo = customRepository ?: container?.podcastRepository ?: PodcastRepository(db.podcastDao())
                val prefs = container?.preferencesManager ?: UserPreferencesManager.getInstance(appContext)
                val baseClient = customClient ?: container?.okHttpClient

                val client = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(35, TimeUnit.SECONDS)
                    .writeTimeout(35, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val instance = NextcloudSyncManager(
                    database = db,
                    repository = repo,
                    preferencesManager = prefs,
                    httpClient = client
                )
                INSTANCE = instance
                instance
            }
        }
        fun isSecureServerUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim().lowercase()
            if (trimmed.startsWith("http://")) {
                val host = trimmed.removePrefix("http://").substringBefore('/').substringBefore(':')
                return host == "localhost" || host == "127.0.0.1" || host == "::1"
            }
            return true
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        return url.trimEnd('/')
    }

    private fun buildWebDavBaseUrl(serverUrl: String, username: String): String {
        val cleanServer = normalizeUrl(serverUrl)
        val cleanUser = username.trim()
        return "$cleanServer/remote.php/dav/files/$cleanUser"
    }

    private fun buildAppFolderUrl(serverUrl: String, username: String): String {
        return "${buildWebDavBaseUrl(serverUrl, username)}/$APP_FOLDER"
    }

    private fun buildSyncFileUrl(serverUrl: String, username: String): String {
        return "${buildAppFolderUrl(serverUrl, username)}/$SYNC_FILE_NAME"
    }

    /**
     * Prueba la conexión WebDAV con el servidor Nextcloud e intenta crear la carpeta MisPodcast si no existe.
     */
    suspend fun testConnection(
        serverUrlParam: String? = null,
        usernameParam: String? = null,
        appPasswordParam: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val settings = preferencesManager.userSettingsFlow.firstOrNull()
        val serverUrl = serverUrlParam ?: settings?.nextcloudServerUrl.orEmpty()
        val username = usernameParam ?: settings?.nextcloudUsername.orEmpty()
        val appPassword = appPasswordParam ?: settings?.nextcloudAppPassword.orEmpty()

        if (serverUrl.isBlank() || username.isBlank() || appPassword.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Por favor, introduce la URL del servidor, usuario y contraseña de aplicación.")
            )
        }

        if (!isSecureServerUrl(serverUrl)) {
            return@withContext Result.failure(
                SecurityException("Se requiere conexión segura HTTPS para sincronizar con Nextcloud (salvo localhost).")
            )
        }

        try {
            val baseDavUrl = buildWebDavBaseUrl(serverUrl, username)
            val credentials = Credentials.basic(username.trim(), appPassword.trim())

            // 1. Probar acceso al WebDAV del usuario mediante PROPFIND con Depth 0
            val emptyBody = ByteArray(0).toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull())
            val testRequest = Request.Builder()
                .url(baseDavUrl)
                .method("PROPFIND", emptyBody)
                .header("Depth", "0")
                .header("Authorization", credentials)
                .build()

            httpClient.newCall(testRequest).execute().use { response ->
                val code = response.code
                when (code) {
                    in 200..299 -> {
                        // Acceso verificado. Asegurar que existe la subcarpeta MisPodcast/
                        ensureAppFolderExists(serverUrl, username, credentials)
                        Result.success("¡Conexión exitosa con Nextcloud! Autenticado como $username")
                    }
                    401 -> Result.failure(
                        Exception("Error de autenticación (401). Verifica tu usuario y contraseña de aplicación de Nextcloud.")
                    )
                    403 -> Result.failure(
                        Exception("Acceso denegado (403). Tu usuario de Nextcloud no tiene permisos suficientes.")
                    )
                    404 -> Result.failure(
                        Exception("Ruta no encontrada (404). Verifica que la URL del servidor sea correcta.")
                    )
                    else -> Result.failure(
                        Exception("Error al conectar con Nextcloud: HTTP $code (${response.message})")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en testConnection", e)
            Result.failure(Exception("No se pudo conectar: ${e.localizedMessage ?: "Comprueba la conexión de red o el servidor"}"))
        }
    }

    private fun ensureAppFolderExists(serverUrl: String, username: String, credentials: String) {
        try {
            val folderUrl = buildAppFolderUrl(serverUrl, username)
            val emptyBody = ByteArray(0).toRequestBody(null)
            val checkFolderReq = Request.Builder()
                .url(folderUrl)
                .method("PROPFIND", emptyBody)
                .header("Depth", "0")
                .header("Authorization", credentials)
                .build()

            val folderExists = httpClient.newCall(checkFolderReq).execute().use { it.code != 404 }
            if (!folderExists) {
                // Crear carpeta usando MKCOL
                val mkcolReq = Request.Builder()
                    .url(folderUrl)
                    .method("MKCOL", null)
                    .header("Authorization", credentials)
                    .build()
                httpClient.newCall(mkcolReq).execute().use { mkcolResp ->
                    Log.d(TAG, "Creación de carpeta WebDAV MisPodcast: HTTP ${mkcolResp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso comprobando/creando carpeta WebDAV: ${e.message}")
        }
    }

    /**
     * Sincronización completa con Nextcloud (Download & Merge + Upload).
     * Emplea un Mutex y coalescencia para prevenir ejecuciones concurrentes y saturación del sistema.
     */
    suspend fun syncNow(): Result<String> = withContext(Dispatchers.IO) {
        val settings = preferencesManager.userSettingsFlow.firstOrNull()
        val serverUrl = settings?.nextcloudServerUrl.orEmpty()
        val username = settings?.nextcloudUsername.orEmpty()
        val appPassword = settings?.nextcloudAppPassword.orEmpty()

        if (serverUrl.isBlank() || username.isBlank() || appPassword.isBlank()) {
            Log.d(TAG, "Nextcloud no configurado. Sincronización omitida limpiamente.")
            return@withContext Result.success("Sincronización omitida: Nextcloud no configurado")
        }

        if (!isSecureServerUrl(serverUrl)) {
            Log.w(TAG, "Conexión insegura rechazada para Nextcloud: $serverUrl")
            return@withContext Result.failure(
                SecurityException("Se requiere conexión segura HTTPS para sincronizar con Nextcloud (salvo localhost).")
            )
        }

        if (!syncMutex.tryLock()) {
            needsSubsequentSync.set(true)
            Log.d(TAG, "Sincronización ya en curso. Se ha encolado una sincronización adicional.")
            return@withContext Result.success("Sincronización ya en curso")
        }

        try {
            var lastResult: Result<String>
            do {
                needsSubsequentSync.set(false)
                lastResult = executeSyncInternal(serverUrl, username, appPassword)
            } while (needsSubsequentSync.compareAndSet(true, false))
            lastResult
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun executeSyncInternal(
        serverUrl: String,
        username: String,
        appPassword: String
    ): Result<String> {
        return try {
            val credentials = Credentials.basic(username.trim(), appPassword.trim())
            ensureAppFolderExists(serverUrl, username, credentials)

            val syncFileUrl = buildSyncFileUrl(serverUrl, username)
            val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            var attempts = 0
            val maxAttempts = 3
            var lastPutCode = 0
            var isPutSuccess = false

            while (attempts < maxAttempts && !isPutSuccess) {
                attempts++

                // PASO 1: Descargar estado remoto y fusionar con Room
                val getRequest = Request.Builder()
                    .url(syncFileUrl)
                    .get()
                    .header("Authorization", credentials)
                    .build()

                var remoteEtag: String? = null

                httpClient.newCall(getRequest).execute().use { getResponse ->
                    if (getResponse.isSuccessful) {
                        remoteEtag = getResponse.header("ETag")
                        val jsonBody = getResponse.body?.string()
                        if (!jsonBody.isNullOrBlank()) {
                            try {
                                val remoteData = json.decodeFromString<NextcloudSyncData>(jsonBody)
                                mergeRemoteStateIntoLocal(remoteData)
                                Log.d(TAG, "Datos remotos de Nextcloud fusionados con éxito (intento $attempts, ETag=$remoteEtag).")
                            } catch (parseEx: Exception) {
                                Log.e(TAG, "Error deserializando JSON de Nextcloud", parseEx)
                            }
                        }
                    } else if (getResponse.code == 404) {
                        Log.d(TAG, "Archivo de sincronización no existe aún en Nextcloud (primer sync).")
                    } else {
                        Log.w(TAG, "GET sync_state.json devolvió HTTP ${getResponse.code}")
                    }
                }

                // PASO 2: Recopilar estado local completo (incluyendo lo fusionado) y subir a Nextcloud
                val currentSyncData = collectLocalSyncData()
                val uploadJson = json.encodeToString(currentSyncData)
                val uploadBody = uploadJson.toRequestBody(jsonMediaType)

                val putRequestBuilder = Request.Builder()
                    .url(syncFileUrl)
                    .put(uploadBody)
                    .header("Content-Type", "application/json")
                    .header("Authorization", credentials)

                // Si el archivo ya existía y recibimos su ETag, usamos If-Match para garantizar concurrencia optimista
                if (!remoteEtag.isNullOrBlank()) {
                    putRequestBuilder.header("If-Match", remoteEtag)
                }

                httpClient.newCall(putRequestBuilder.build()).execute().use { putResponse ->
                    lastPutCode = putResponse.code
                    isPutSuccess = putResponse.isSuccessful || putResponse.code == 201 || putResponse.code == 204
                    if (putResponse.code == 412) {
                        Log.w(TAG, "Conflicto de concurrencia WebDAV (HTTP 412 Precondition Failed). Reintentando merge y upload...")
                    }
                }
            }

            if (isPutSuccess) {
                val now = System.currentTimeMillis()
                preferencesManager.setLastSyncTimestamp(now)
                Log.d(TAG, "Estado de MisPodcast subido exitosamente a Nextcloud tras $attempts intento(s).")
                Result.success("¡Sincronizado con éxito con Nextcloud!")
            } else if (lastPutCode == 412) {
                Result.failure(Exception("Conflicto de concurrencia con Nextcloud tras $maxAttempts reintentos (HTTP 412). Vuelve a sincronizar."))
            } else {
                Result.failure(Exception("Error al subir estado a Nextcloud (HTTP $lastPutCode)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en executeSyncInternal", e)
            Result.failure(Exception("Fallo en sincronización Nextcloud: ${e.localizedMessage ?: "Error de red"}"))
        }
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
    internal suspend fun mergeRemoteStateIntoLocal(remote: NextcloudSyncData) {
        val dao = database.podcastDao()

        // 1. SUSCRIPCIONES Y BAJAS (Podcasts seguidos y lápidas/tombstones)
        for (sub in remote.subscriptions) {
            if (sub.feedUrl.isBlank()) continue
            try {
                val existing = dao.getPodcastByIdOrFeedUrl(sub.feedUrl, sub.feedUrl)
                if (sub.isSubscribed) {
                    if (existing != null) {
                        if (!existing.isSubscribed) {
                            // Si localmente estaba desuscrito, reactivar solo si la suscripción remota es posterior
                            if (sub.subscribedAt > existing.unsubscribedAt) {
                                dao.updateSubscriptionWithTimestamps(
                                    podcastId = existing.id,
                                    isSubscribed = true,
                                    subscribedAt = sub.subscribedAt,
                                    unsubscribedAt = 0L
                                )
                                Log.d(TAG, "Re-suscripción remota aplicada para ${sub.feedUrl}")
                            }
                        } else if (sub.subscribedAt > existing.subscribedAt) {
                            dao.updateSubscriptionWithTimestamps(
                                podcastId = existing.id,
                                isSubscribed = true,
                                subscribedAt = sub.subscribedAt,
                                unsubscribedAt = existing.unsubscribedAt
                            )
                        }
                    } else {
                        repository.subscribeToFeed(sub.feedUrl)
                    }
                } else {
                    // Lápida remota (unsubscription): el usuario canceló la suscripción en otro dispositivo
                    if (existing != null && existing.isSubscribed) {
                        if (sub.unsubscribedAt > existing.subscribedAt) {
                            dao.updateSubscriptionWithTimestamps(
                                podcastId = existing.id,
                                isSubscribed = false,
                                subscribedAt = existing.subscribedAt,
                                unsubscribedAt = sub.unsubscribedAt
                            )
                            Log.d(TAG, "Baja sincronizada desde remoto (tombstone) para ${sub.feedUrl}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando suscripción/baja remota de ${sub.feedUrl}: ${e.message}")
            }
        }

        // 2. PUNTO DE ESCUCHA Y CAPÍTULOS REPRODUCIDOS (Resolución sin pérdida con positionUpdatedAt)
        val progressList = if (remote.episodesProgress.isNotEmpty()) remote.episodesProgress else remote.episodes
        for (item in progressList) {
            if (item.episodeId.isBlank() && item.audioUrl.isNullOrBlank()) continue
            val localEp = dao.getEpisodeById(item.episodeId)
                ?: (if (!item.audioUrl.isNullOrBlank()) dao.getEpisodeByAudioUrl(item.audioUrl) else null)

            if (localEp != null) {
                val remoteTimestamp = item.lastUpdatedTimestamp
                val localTimestamp = if (localEp.positionUpdatedAt > 0L) {
                    localEp.positionUpdatedAt
                } else {
                    dao.getLatestListenedAtForEpisode(localEp.id) ?: 0L
                }

                val (shouldMarkPlayed, newPosition, chosenTimestamp) = when {
                    // Si el progreso remoto es explícitamente más reciente que el local (permite rebobinados y desmarcar escuchado)
                    remoteTimestamp > 0L && remoteTimestamp > localTimestamp -> {
                        val pos = if (item.isPlayed) {
                            if (localEp.duration > 0) localEp.duration else item.currentPositionMs
                        } else {
                            item.currentPositionMs
                        }
                        Triple(item.isPlayed, pos, remoteTimestamp)
                    }
                    // Si el progreso local es posterior al remoto, conservamos el local
                    localTimestamp > 0L && localTimestamp >= remoteTimestamp -> {
                        Triple(localEp.isPlayed, localEp.currentPositionMs, localTimestamp)
                    }
                    // Sin marcas de tiempo precisas: unión conservadora
                    else -> {
                        val played = item.isPlayed || localEp.isPlayed
                        val pos = if (played && item.isPlayed) {
                            maxOf(localEp.duration, localEp.currentPositionMs, item.currentPositionMs)
                        } else {
                            maxOf(localEp.currentPositionMs, item.currentPositionMs)
                        }
                        Triple(played, pos, System.currentTimeMillis())
                    }
                }

                val hasChanges = shouldMarkPlayed != localEp.isPlayed || newPosition != localEp.currentPositionMs
                if (hasChanges) {
                    dao.updateEpisodePlaybackState(
                        episodeId = localEp.id,
                        isPlayed = shouldMarkPlayed,
                        positionMs = newPosition,
                        now = chosenTimestamp
                    )
                }

                // Si fue completado y no hay entrada en el historial, añadirlo
                if (shouldMarkPlayed && !dao.hasHistoryForEpisode(localEp.id)) {
                    dao.insertListeningHistory(
                        ListeningHistoryEntity(
                            episodeId = localEp.id,
                            podcastId = localEp.podcastId,
                            listenedAt = if (chosenTimestamp > 0) chosenTimestamp else System.currentTimeMillis(),
                            durationListenedMs = localEp.duration,
                            completed = true
                        )
                    )
                }
            }
        }

        // 3. HISTORIAL DE REPRODUCCIÓN
        for (hItem in remote.history) {
            if (hItem.episodeId.isBlank() && hItem.audioUrl.isNullOrBlank()) continue
            val localEp = dao.getEpisodeById(hItem.episodeId)
                ?: (if (!hItem.audioUrl.isNullOrBlank()) dao.getEpisodeByAudioUrl(hItem.audioUrl) else null)

            if (localEp != null) {
                if (!dao.hasHistoryForEpisode(localEp.id)) {
                    dao.insertListeningHistory(
                        ListeningHistoryEntity(
                            episodeId = localEp.id,
                            podcastId = localEp.podcastId,
                            listenedAt = if (hItem.listenedAt > 0) hItem.listenedAt else System.currentTimeMillis(),
                            durationListenedMs = if (hItem.durationListenedMs > 0) hItem.durationListenedMs else localEp.duration,
                            completed = hItem.completed
                        )
                    )
                }
                if (hItem.completed && !localEp.isPlayed) {
                    dao.markEpisodeAsPlayed(localEp.id, true)
                }
            }
        }

        // 4. ESTADÍSTICAS DE REPRODUCCIÓN (Playback stats)
        for (rStat in remote.playbackStats) {
            if (rStat.podcastId.isBlank()) continue
            val localStat = dao.getStatsForPodcast(rStat.podcastId)
            if (localStat != null) {
                val merged = PlaybackStatsEntity(
                    podcastId = rStat.podcastId,
                    podcastTitle = if (localStat.podcastTitle.isNotBlank()) localStat.podcastTitle else rStat.podcastTitle,
                    totalTimeListenedMs = maxOf(localStat.totalTimeListenedMs, rStat.totalTimeListenedMs),
                    episodesCompletedCount = maxOf(localStat.episodesCompletedCount, rStat.episodesCompletedCount),
                    lastListenedAt = maxOf(localStat.lastListenedAt, rStat.lastListenedAt)
                )
                dao.upsertPlaybackStats(merged)
            } else {
                dao.upsertPlaybackStats(
                    PlaybackStatsEntity(
                        podcastId = rStat.podcastId,
                        podcastTitle = rStat.podcastTitle,
                        totalTimeListenedMs = rStat.totalTimeListenedMs,
                        episodesCompletedCount = rStat.episodesCompletedCount,
                        lastListenedAt = if (rStat.lastListenedAt > 0) rStat.lastListenedAt else System.currentTimeMillis()
                    )
                )
            }
        }

        // 5. EPISODIOS FAVORITOS
        for (fav in remote.favorites) {
            if (fav.episodeId.isBlank() && fav.audioUrl.isNullOrBlank()) continue
            val localEp = dao.getEpisodeById(fav.episodeId)
                ?: (if (!fav.audioUrl.isNullOrBlank()) dao.getEpisodeByAudioUrl(fav.audioUrl) else null)
            if (localEp != null && fav.isFavorite && !localEp.isFavorite) {
                dao.updateFavoriteStatus(localEp.id, true)
            }
        }

        // 6. COLA DE REPRODUCCIÓN COMPLETA
        if (remote.queue.isNotEmpty()) {
            val localQueue = dao.getQueueList()
            if (localQueue.isEmpty()) {
                // Cola local vacía: restaurar íntegramente la cola remota conservando índices y estado
                remote.queue.sortedBy { it.positionIndex }.forEach { qItem ->
                    if (qItem.episodeId.isNotBlank()) {
                        val canonicalEp = ensureQueueEpisodeExists(qItem)
                        if (canonicalEp != null) {
                            dao.insertQueueItem(
                                QueueItemEntity(
                                    episodeId = canonicalEp.id,
                                    positionIndex = qItem.positionIndex,
                                    isCurrentlyPlaying = qItem.isCurrentlyPlaying,
                                    addedAt = if (qItem.addedAt > 0) qItem.addedAt else System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                dao.reindexQueue()
            } else {
                // Cola local con elementos: fusionar sin duplicar y agregando al final los nuevos remotos
                val existingIds = localQueue.map { it.episode.id }.toMutableSet()
                val existingAudioUrls = localQueue.map { it.episode.audioUrl }.filter { it.isNotBlank() }.toMutableSet()
                var nextPos = (localQueue.maxOfOrNull { it.positionIndex } ?: -1) + 1

                remote.queue.sortedBy { it.positionIndex }.forEach { qItem ->
                    val canonicalEp = ensureQueueEpisodeExists(qItem)
                    if (canonicalEp != null) {
                        val alreadyInQueue = existingIds.contains(canonicalEp.id) ||
                            (canonicalEp.audioUrl.isNotBlank() && existingAudioUrls.contains(canonicalEp.audioUrl))
                        if (!alreadyInQueue) {
                            dao.insertQueueItem(
                                QueueItemEntity(
                                    episodeId = canonicalEp.id,
                                    positionIndex = nextPos++,
                                    isCurrentlyPlaying = false,
                                    addedAt = if (qItem.addedAt > 0) qItem.addedAt else System.currentTimeMillis()
                                )
                            )
                            existingIds.add(canonicalEp.id)
                            if (canonicalEp.audioUrl.isNotBlank()) {
                                existingAudioUrls.add(canonicalEp.audioUrl)
                            }
                        }
                    }
                }
                dao.reindexQueue()
            }
        }
    }

    private suspend fun ensureQueueEpisodeExists(qItem: NextcloudQueueItem): EpisodeEntity? {
        val dao = database.podcastDao()
        var ep = dao.getEpisodeById(qItem.episodeId)
        if (ep == null && !qItem.audioUrl.isNullOrBlank()) {
            ep = dao.getEpisodeByAudioUrl(qItem.audioUrl)
        }
        if (ep != null) return ep

        val podcastId = qItem.podcastId ?: "synced_podcast"
        val existingPodcast = dao.getPodcastById(podcastId)
        if (existingPodcast == null) {
            dao.insertPodcast(
                PodcastEntity(
                    id = podcastId,
                    title = qItem.podcastTitle ?: "Podcast",
                    author = "",
                    description = "",
                    feedUrl = podcastId,
                    artworkUrl = qItem.podcastArtworkUrl.orEmpty(),
                    isSubscribed = false
                )
            )
        }

        val newEp = EpisodeEntity(
            id = qItem.episodeId,
            podcastId = podcastId,
            title = qItem.episodeTitle ?: "Episodio en cola",
            description = "",
            audioUrl = qItem.audioUrl.orEmpty(),
            imageUrl = qItem.podcastArtworkUrl.orEmpty(),
            pubDate = qItem.addedAt,
            duration = qItem.episodeDuration,
            currentPositionMs = 0L,
            isPlayed = false
        )
        dao.insertOrUpdateEpisode(newEp)
        return newEp
    }

    private suspend fun collectLocalSyncData(): NextcloudSyncData {
        val dao = database.podcastDao()

        // 1. Subscriptions con lápidas tombstones
        val syncPodcasts = dao.getSyncablePodcasts()
        val subscriptions = syncPodcasts.map {
            NextcloudSubscription(
                feedUrl = it.feedUrl,
                title = it.title,
                subscribedAt = it.subscribedAt,
                unsubscribedAt = it.unsubscribedAt,
                isSubscribed = it.isSubscribed
            )
        }

        // 2. Playback progress (Puntos de escucha y capítulos reproducidos con positionUpdatedAt)
        val playedOrProgress = dao.getPlayedOrInProgressEpisodes()
        val episodesProgress = playedOrProgress.map {
            val timestamp = if (it.positionUpdatedAt > 0L) {
                it.positionUpdatedAt
            } else {
                dao.getLatestListenedAtForEpisode(it.id) ?: it.pubDate
            }
            NextcloudEpisodeProgress(
                episodeId = it.id,
                audioUrl = it.audioUrl,
                currentPositionMs = it.currentPositionMs,
                isPlayed = it.isPlayed,
                lastUpdatedTimestamp = timestamp
            )
        }

        // 3. Historial de reproducción
        val historyList = dao.getListeningHistoryFlow(limit = 200).firstOrNull() ?: emptyList()
        val history = historyList.map {
            NextcloudHistoryItem(
                episodeId = it.episode.id,
                podcastId = it.episode.podcastId,
                audioUrl = it.episode.audioUrl,
                listenedAt = it.listenedAt,
                durationListenedMs = it.durationListenedMs,
                completed = it.completed
            )
        }

        // 4. Estadísticas completas de escucha
        val statsList = dao.getPodcastStatsList()
        val playbackStats = statsList.map {
            NextcloudPlaybackStats(
                podcastId = it.podcastId,
                podcastTitle = it.podcastTitle,
                totalTimeListenedMs = it.totalTimeListenedMs,
                episodesCompletedCount = it.episodesCompletedCount,
                lastListenedAt = it.lastListenedAt
            )
        }

        // 5. Favoritos
        val favoritesList = dao.getFavoriteEpisodesFlow().firstOrNull() ?: emptyList()
        val favorites = favoritesList.map {
            NextcloudFavoriteItem(
                episodeId = it.episode.id,
                audioUrl = it.episode.audioUrl,
                isFavorite = true
            )
        }

        // 6. Cola de reproducción completa
        val queueList = dao.getQueueList()
        val queue = queueList.map {
            NextcloudQueueItem(
                episodeId = it.episode.id,
                audioUrl = it.episode.audioUrl,
                podcastId = it.episode.podcastId,
                podcastTitle = it.podcastTitle,
                podcastArtworkUrl = it.podcastArtworkUrl,
                episodeTitle = it.episode.title,
                episodeDuration = it.episode.duration,
                positionIndex = it.positionIndex,
                isCurrentlyPlaying = it.isCurrentlyPlaying,
                addedAt = it.addedAt
            )
        }

        return NextcloudSyncData(
            version = 2,
            app = "MisPodcast",
            lastSyncTimestamp = System.currentTimeMillis(),
            subscriptions = subscriptions,
            queue = queue,
            favorites = favorites,
            episodesProgress = episodesProgress,
            episodes = episodesProgress,
            playbackStats = playbackStats,
            history = history
        )
    }

    /**
     * Soporte opcional para consultar si el servidor cuenta con el plugin Nextcloud GPodder Sync activo.
     */
    suspend fun checkGpodderSyncSupport(): Boolean = withContext(Dispatchers.IO) {
        val settings = preferencesManager.userSettingsFlow.firstOrNull() ?: return@withContext false
        val serverUrl = settings.nextcloudServerUrl
        if (serverUrl.isBlank()) return@withContext false

        try {
            val cleanServer = normalizeUrl(serverUrl)
            val gpodderUrl = "$cleanServer/index.php/apps/gpoddersync/api/2/devices/${settings.nextcloudUsername}/"
            val credentials = Credentials.basic(settings.nextcloudUsername, settings.nextcloudAppPassword)
            val request = Request.Builder()
                .url(gpodderUrl)
                .get()
                .header("Authorization", credentials)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
