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
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.BookmarkEntity
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.local.entities.SyncStateEntity
import com.ferlagod.mispodcast.data.local.entities.SyncStatsContributionEntity
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Versión del formato de sync_state.json. La v3 añade marcadores, ajustes por podcast, sesiones de historial y estadísticas por dispositivo. */
const val NEXTCLOUD_SYNC_FORMAT_VERSION = 3

@Serializable
data class NextcloudSyncData(
    @SerialName("version") val version: Int = NEXTCLOUD_SYNC_FORMAT_VERSION,
    @SerialName("app") val app: String = "MisPodcast",
    @SerialName("lastSyncTimestamp") val lastSyncTimestamp: Long = System.currentTimeMillis(),
    @SerialName("deviceId") val deviceId: String? = null,
    @SerialName("subscriptions") val subscriptions: List<NextcloudSubscription> = emptyList(),
    @SerialName("queue") val queue: List<NextcloudQueueItem> = emptyList(),
    @SerialName("favorites") val favorites: List<NextcloudFavoriteItem> = emptyList(),
    @SerialName("episodesProgress") val episodesProgress: List<NextcloudEpisodeProgress> = emptyList(),
    // Campo heredado de versiones antiguas: solo se lee si episodesProgress viene vacío
    @SerialName("episodes") val episodes: List<NextcloudEpisodeProgress> = emptyList(),
    @SerialName("playbackStats") val playbackStats: List<NextcloudPlaybackStats> = emptyList(),
    @SerialName("history") val history: List<NextcloudHistoryItem> = emptyList(),
    @SerialName("historyClearedAt") val historyClearedAt: Long = 0L,
    @SerialName("deviceStats") val deviceStats: List<NextcloudDeviceStats> = emptyList(),
    @SerialName("statsBaselines") val statsBaselines: List<NextcloudPlaybackStats> = emptyList(),
    @SerialName("bookmarks") val bookmarks: List<NextcloudBookmark> = emptyList()
)

@Serializable
data class NextcloudSubscription(
    @SerialName("feedUrl") val feedUrl: String,
    @SerialName("title") val title: String? = null,
    @SerialName("subscribedAt") val subscribedAt: Long = 0L,
    @SerialName("unsubscribedAt") val unsubscribedAt: Long = 0L,
    @SerialName("isSubscribed") val isSubscribed: Boolean = true,
    @SerialName("settings") val settings: NextcloudPodcastSettings? = null
)

@Serializable
data class NextcloudPodcastSettings(
    @SerialName("customSpeed") val customSpeed: Float? = null,
    @SerialName("skipIntroSeconds") val skipIntroSeconds: Int = 0,
    @SerialName("skipEndingSeconds") val skipEndingSeconds: Int = 0,
    @SerialName("autoDownloadEnabled") val autoDownloadEnabled: Boolean? = null,
    @SerialName("episodeSortOrder") val episodeSortOrder: String = "NEWEST_FIRST",
    @SerialName("autoEnqueueRule") val autoEnqueueRule: String = "DEFAULT",
    @SerialName("newEpisodeNotifications") val newEpisodeNotifications: Boolean = true,
    @SerialName("autoDeleteRule") val autoDeleteRule: String = "DEFAULT"
) {
    /** Ajustes que nadie ha tocado (los de una suscripción recién creada). */
    fun isUntouched(): Boolean = customSpeed == null && skipIntroSeconds == 0 && skipEndingSeconds == 0 &&
        autoDownloadEnabled == null && episodeSortOrder == "NEWEST_FIRST" &&
        (autoEnqueueRule == "DEFAULT" || autoEnqueueRule == "DISABLED") &&
        newEpisodeNotifications && autoDeleteRule == "DEFAULT"
}

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

/** Tiempo escuchado que aporta un dispositivo concreto a un podcast (contador que solo crece). */
@Serializable
data class NextcloudDeviceStats(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("podcastTitle") val podcastTitle: String = "",
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
    @SerialName("completed") val completed: Boolean = true,
    @SerialName("syncId") val syncId: String? = null
)

@Serializable
data class NextcloudBookmark(
    @SerialName("id") val id: String,
    @SerialName("episodeId") val episodeId: String,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("podcastId") val podcastId: String = "",
    @SerialName("podcastTitle") val podcastTitle: String = "",
    @SerialName("episodeTitle") val episodeTitle: String = "",
    @SerialName("positionMs") val positionMs: Long = 0L,
    @SerialName("note") val note: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L
)

/**
 * Lo que este dispositivo subió en la última sincronización correcta. Sirve de referencia común
 * (fusión a tres bandas) para saber si un elemento que falla en un lado se borró o es nuevo,
 * de modo que quitar de la cola, de favoritos o un marcador se propague en vez de revertirse.
 */
@Serializable
internal data class SyncBaseSnapshot(
    @SerialName("account") val account: String = "",
    @SerialName("queue") val queue: List<String> = emptyList(),
    @SerialName("favorites") val favorites: List<String> = emptyList(),
    @SerialName("bookmarks") val bookmarks: Map<String, String> = emptyMap(),
    @SerialName("podcastSettings") val podcastSettings: Map<String, NextcloudPodcastSettings> = emptyMap()
)

/**
 * Resultado de fusionar el estado remoto: elementos remotos que no se pudieron aplicar aquí
 * (p. ej. episodios que este dispositivo aún no tiene) y que se vuelven a subir tal cual para
 * no perderlos, más la instantánea del estado remoto visto.
 */
internal class SyncMergeOutcome {
    val subscriptions = mutableListOf<NextcloudSubscription>()
    val favorites = mutableListOf<NextcloudFavoriteItem>()
    val progress = mutableListOf<NextcloudEpisodeProgress>()
    val history = mutableListOf<NextcloudHistoryItem>()
    val bookmarks = mutableListOf<NextcloudBookmark>()
    var seenRemote: SyncBaseSnapshot? = null
}

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
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BASE_SNAPSHOT = "base_snapshot"
        private const val KEY_STATS_BASELINE_READY = "stats_baseline_ready"
        private const val MAX_HISTORY_SESSIONS = 3000

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

            val account = "${normalizeUrl(serverUrl)}|${username.trim()}"
            val deviceId = getOrCreateDeviceId()
            // Referencia común para la fusión a tres bandas; se descarta si cambió la cuenta de Nextcloud
            var base = loadBaseSnapshot(account)

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
                var remoteFileExists = false
                var outcome = SyncMergeOutcome()

                httpClient.newCall(getRequest).execute().use { getResponse ->
                    if (getResponse.isSuccessful) {
                        remoteFileExists = true
                        remoteEtag = getResponse.header("ETag")
                        val jsonBody = getResponse.body?.string()
                        if (!jsonBody.isNullOrBlank()) {
                            val remoteData = try {
                                json.decodeFromString<NextcloudSyncData>(jsonBody)
                            } catch (parseEx: Exception) {
                                // Nunca sobrescribir un archivo remoto que no entendemos: se perderían datos
                                Log.e(TAG, "Error deserializando JSON de Nextcloud", parseEx)
                                return Result.failure(
                                    Exception("El archivo MisPodcast/$SYNC_FILE_NAME de Nextcloud está dañado. Bórralo en Nextcloud para volver a sincronizar.")
                                )
                            }
                            ensureStatsBaseline(remoteData, deviceId)
                            outcome = mergeRemoteStateIntoLocal(remoteData, base, deviceId)
                            Log.d(TAG, "Datos remotos de Nextcloud fusionados con éxito (intento $attempts, ETag=$remoteEtag).")
                        }
                    } else if (getResponse.code == 404) {
                        Log.d(TAG, "Archivo de sincronización no existe aún en Nextcloud (primer sync).")
                    } else {
                        // Sin el estado remoto no se puede fusionar: subir ahora lo borraría
                        Log.w(TAG, "GET sync_state.json devolvió HTTP ${getResponse.code}")
                        return Result.failure(
                            Exception("No se pudo descargar el estado de Nextcloud (HTTP ${getResponse.code})")
                        )
                    }
                }

                ensureStatsBaseline(null, deviceId)

                // PASO 2: Recopilar estado local completo (incluyendo lo fusionado) y subir a Nextcloud
                val (currentSyncData, uploadedSnapshot) = collectLocalSyncData(deviceId, account, outcome)
                val uploadJson = json.encodeToString(currentSyncData)
                val uploadBody = uploadJson.toRequestBody(jsonMediaType)

                val putRequestBuilder = Request.Builder()
                    .url(syncFileUrl)
                    .put(uploadBody)
                    .header("Content-Type", "application/json")
                    .header("Authorization", credentials)

                // Concurrencia optimista: no pisar cambios que otro dispositivo suba entre nuestro GET y PUT
                if (!remoteEtag.isNullOrBlank()) {
                    putRequestBuilder.header("If-Match", remoteEtag!!)
                } else if (!remoteFileExists) {
                    putRequestBuilder.header("If-None-Match", "*")
                }

                httpClient.newCall(putRequestBuilder.build()).execute().use { putResponse ->
                    lastPutCode = putResponse.code
                    isPutSuccess = putResponse.isSuccessful
                    if (putResponse.code == 412) {
                        Log.w(TAG, "Conflicto de concurrencia WebDAV (HTTP 412 Precondition Failed). Reintentando merge y upload...")
                    }
                }

                if (isPutSuccess) {
                    saveBaseSnapshot(uploadedSnapshot)
                } else {
                    // En el reintento, lo que ya vimos en remoto es la referencia común correcta
                    base = outcome.seenRemote ?: base
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

    // ==========================================
    // ESTADO LOCAL DE SINCRONIZACIÓN
    // ==========================================

    private suspend fun getOrCreateDeviceId(): String {
        val dao = database.podcastDao()
        dao.getSyncState(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val newId = UUID.randomUUID().toString()
        dao.setSyncState(SyncStateEntity(KEY_DEVICE_ID, newId))
        return newId
    }

    /**
     * La primera vez, el acumulado existente pasa a ser la "base" heredada (que se fusiona por máximo,
     * como antes) y a partir de ahí cada dispositivo publica solo lo que escucha él.
     *
     * Lo que otros dispositivos ya publicaron se da por incluido en el acumulado local (la versión
     * anterior fusionaba por máximo), así el cambio de sistema no cuenta nada dos veces.
     */
    private suspend fun ensureStatsBaseline(remote: NextcloudSyncData?, deviceId: String) {
        val dao = database.podcastDao()
        if (dao.getSyncState(KEY_STATS_BASELINE_READY) == "1") return
        val remoteRows = if (remote != null && remote.version >= 3) {
            remote.deviceStats.filter { it.deviceId.isNotBlank() && it.podcastId.isNotBlank() && it.deviceId != deviceId }
                .distinctBy { it.deviceId to it.podcastId }
                .map {
                    SyncStatsContributionEntity(
                        it.deviceId, it.podcastId, it.podcastTitle, it.totalTimeListenedMs, it.episodesCompletedCount, it.lastListenedAt
                    )
                }
        } else emptyList()
        val baseline = dao.getPodcastStatsList().map { stat ->
            val known = remoteRows.filter { it.podcastId == stat.podcastId }
            SyncStatsContributionEntity(
                deviceId = SyncStatsContributionEntity.BASELINE_DEVICE_ID,
                podcastId = stat.podcastId,
                podcastTitle = stat.podcastTitle,
                totalTimeListenedMs = (stat.totalTimeListenedMs - known.sumOf { it.totalTimeListenedMs }).coerceAtLeast(0L),
                episodesCompletedCount = (stat.episodesCompletedCount - known.sumOf { it.episodesCompletedCount }).coerceAtLeast(0),
                lastListenedAt = stat.lastListenedAt
            )
        }
        val rows = baseline + remoteRows
        if (rows.isNotEmpty()) dao.upsertSyncStatsContributions(rows)
        dao.setSyncState(SyncStateEntity(KEY_STATS_BASELINE_READY, "1"))
    }

    private suspend fun loadBaseSnapshot(account: String): SyncBaseSnapshot? {
        val raw = database.podcastDao().getSyncState(KEY_BASE_SNAPSHOT)?.takeIf { it.isNotBlank() } ?: return null
        val snapshot = runCatching { json.decodeFromString<SyncBaseSnapshot>(raw) }.getOrNull() ?: return null
        return snapshot.takeIf { it.account == account }
    }

    private suspend fun saveBaseSnapshot(snapshot: SyncBaseSnapshot) {
        database.podcastDao().setSyncState(SyncStateEntity(KEY_BASE_SNAPSHOT, json.encodeToString(snapshot)))
    }

    private fun isRemoteUrl(url: String?): Boolean =
        url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

    /** Episodios de carpetas locales: solo existen en este dispositivo y no se sincronizan. */
    private fun isSyncableEpisode(episode: EpisodeEntity): Boolean = isRemoteUrl(episode.audioUrl)

    private suspend fun resolveEpisode(episodeId: String, audioUrl: String?): EpisodeEntity? {
        val dao = database.podcastDao()
        return (if (episodeId.isNotBlank()) dao.getEpisodeById(episodeId) else null)
            ?: (if (!audioUrl.isNullOrBlank()) dao.getEpisodeByAudioUrl(audioUrl) else null)
    }

    private fun PodcastEntity.toSyncSettings() = NextcloudPodcastSettings(
        customSpeed = customSpeed,
        skipIntroSeconds = skipIntroSeconds,
        skipEndingSeconds = skipEndingSeconds,
        autoDownloadEnabled = autoDownloadEnabled,
        episodeSortOrder = episodeSortOrder,
        autoEnqueueRule = autoEnqueueRule,
        newEpisodeNotifications = newEpisodeNotifications,
        autoDeleteRule = autoDeleteRule
    )

    private fun BookmarkEntity.fingerprint() = "$positionMs|$note"

    private fun NextcloudBookmark.fingerprint() = "$positionMs|$note"

    // ==========================================
    // FUSIÓN REMOTO → LOCAL
    // ==========================================

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
    internal suspend fun mergeRemoteStateIntoLocal(
        remote: NextcloudSyncData,
        base: SyncBaseSnapshot? = null,
        deviceId: String = ""
    ): SyncMergeOutcome {
        val dao = database.podcastDao()
        val outcome = SyncMergeOutcome()
        // Los marcadores y ajustes por podcast solo existen desde la v3: un archivo antiguo no los borra
        val remoteIsV3 = remote.version >= 3

        // 1. SUSCRIPCIONES Y BAJAS (Podcasts seguidos, lápidas/tombstones y ajustes por podcast)
        val seenSettings = HashMap<String, NextcloudPodcastSettings>()
        for (sub in remote.subscriptions) {
            if (sub.feedUrl.isBlank() || !isRemoteUrl(sub.feedUrl)) continue
            if (remoteIsV3) sub.settings?.let { seenSettings[sub.feedUrl] = it }
            try {
                val existing = dao.getPodcastByIdOrFeedUrl(sub.feedUrl, sub.feedUrl)
                var subscribedHere = false
                if (sub.isSubscribed) {
                    if (existing == null || (!existing.isSubscribed && existing.unsubscribedAt == 0L)) {
                        // Nunca seguido en este dispositivo (o solo una vista previa): descargar el feed completo
                        val subscribed = repository.subscribeToFeed(sub.feedUrl).getOrNull()
                        if (subscribed != null) {
                            // Conservar la fecha remota para que las bajas posteriores se comparen bien
                            dao.updateSubscriptionWithTimestamps(
                                podcastId = subscribed.id,
                                isSubscribed = true,
                                subscribedAt = if (sub.subscribedAt > 0L) sub.subscribedAt else subscribed.subscribedAt,
                                unsubscribedAt = 0L
                            )
                            subscribedHere = true
                        } else if (existing != null) {
                            dao.updateSubscriptionWithTimestamps(
                                podcastId = existing.id,
                                isSubscribed = true,
                                subscribedAt = sub.subscribedAt,
                                unsubscribedAt = 0L
                            )
                        } else {
                            // Feed inaccesible ahora: se reintenta en la próxima sincronización sin perderlo
                            outcome.subscriptions.add(sub)
                            continue
                        }
                    } else if (!existing.isSubscribed) {
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
                    // Lápida remota (unsubscription): el usuario canceló la suscripción en otro dispositivo
                    if (existing == null) {
                        outcome.subscriptions.add(sub)
                    } else if (existing.isSubscribed && sub.unsubscribedAt > existing.subscribedAt) {
                        dao.updateSubscriptionWithTimestamps(
                            podcastId = existing.id,
                            isSubscribed = false,
                            subscribedAt = existing.subscribedAt,
                            unsubscribedAt = sub.unsubscribedAt
                        )
                        Log.d(TAG, "Baja sincronizada desde remoto (tombstone) para ${sub.feedUrl}")
                    }
                }

                // Ajustes por podcast: se aplican los remotos si aquí no se han cambiado desde la última sincronización
                val remoteSettings = sub.settings
                if (remoteIsV3 && remoteSettings != null && sub.isSubscribed) {
                    val podcast = dao.getPodcastByIdOrFeedUrl(sub.feedUrl, sub.feedUrl)
                    if (podcast != null && podcast.isSubscribed && !podcast.isLocalFolder) {
                        val localSettings = podcast.toSyncSettings()
                        val baseSettings = base?.podcastSettings?.get(podcast.feedUrl)
                        val localUnchanged = if (baseSettings != null) {
                            localSettings == baseSettings
                        } else {
                            subscribedHere || localSettings.isUntouched()
                        }
                        if (localUnchanged && localSettings != remoteSettings) {
                            dao.updatePodcastSettings(
                                podcastId = podcast.id,
                                customSpeed = remoteSettings.customSpeed,
                                skipIntroSeconds = remoteSettings.skipIntroSeconds,
                                skipEndingSeconds = remoteSettings.skipEndingSeconds,
                                autoDownloadEnabled = remoteSettings.autoDownloadEnabled,
                                episodeSortOrder = remoteSettings.episodeSortOrder,
                                autoEnqueueRule = remoteSettings.autoEnqueueRule,
                                newEpisodeNotifications = remoteSettings.newEpisodeNotifications,
                                autoDeleteRule = remoteSettings.autoDeleteRule
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando suscripción/baja remota de ${sub.feedUrl}: ${e.message}")
                outcome.subscriptions.add(sub)
            }
        }

        // 2. PUNTO DE ESCUCHA Y CAPÍTULOS REPRODUCIDOS (Resolución sin pérdida con positionUpdatedAt)
        val progressList = if (remote.episodesProgress.isNotEmpty()) remote.episodesProgress else remote.episodes
        for (item in progressList) {
            if (item.episodeId.isBlank() && item.audioUrl.isNullOrBlank()) continue
            val localEp = resolveEpisode(item.episodeId, item.audioUrl)
            if (localEp == null) {
                outcome.progress.add(item)
                continue
            }

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

            // Los archivos antiguos no traen sesiones de historial: se crea una entrada para no perder el registro
            if (!remoteIsV3 && shouldMarkPlayed && !dao.hasHistoryForEpisode(localEp.id)) {
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

        // 3. HISTORIAL DE REPRODUCCIÓN (sesiones con identificador estable y lápida de borrado)
        val localClearedAt = dao.getSyncState(PodcastDao.SYNC_KEY_HISTORY_CLEARED_AT)?.toLongOrNull() ?: 0L
        if (remote.historyClearedAt > localClearedAt) {
            dao.deleteHistoryUpTo(remote.historyClearedAt)
            dao.setSyncState(SyncStateEntity(PodcastDao.SYNC_KEY_HISTORY_CLEARED_AT, remote.historyClearedAt.toString()))
        }
        val historyClearedAt = maxOf(localClearedAt, remote.historyClearedAt)
        for (hItem in remote.history) {
            if (hItem.episodeId.isBlank() && hItem.audioUrl.isNullOrBlank()) continue
            if (historyClearedAt > 0L && hItem.listenedAt <= historyClearedAt) continue
            val syncId = hItem.syncId
            if (!syncId.isNullOrBlank()) {
                val existingSession = dao.getHistoryBySyncId(syncId)
                if (existingSession != null) {
                    val merged = existingSession.copy(
                        listenedAt = maxOf(existingSession.listenedAt, hItem.listenedAt),
                        durationListenedMs = maxOf(existingSession.durationListenedMs, hItem.durationListenedMs),
                        completed = existingSession.completed || hItem.completed
                    )
                    if (merged != existingSession) dao.updateListeningHistory(merged)
                    continue
                }
                val localEp = resolveEpisode(hItem.episodeId, hItem.audioUrl)
                if (localEp == null) {
                    outcome.history.add(hItem)
                    continue
                }
                dao.insertListeningHistory(
                    ListeningHistoryEntity(
                        episodeId = localEp.id,
                        podcastId = localEp.podcastId,
                        listenedAt = if (hItem.listenedAt > 0) hItem.listenedAt else System.currentTimeMillis(),
                        durationListenedMs = hItem.durationListenedMs,
                        completed = hItem.completed,
                        syncId = syncId
                    )
                )
            } else {
                // Formato antiguo sin identificador: una entrada por episodio, como hasta ahora
                val localEp = resolveEpisode(hItem.episodeId, hItem.audioUrl) ?: continue
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
            }
        }

        // 4. ESTADÍSTICAS DE REPRODUCCIÓN (suma de lo escuchado en cada dispositivo)
        mergePlaybackStats(remote, deviceId)

        // 5. EPISODIOS FAVORITOS (fusión a tres bandas: los quitados en un lado se quitan en el otro)
        val baseFavorites = base?.favorites?.toHashSet()
        val localFavorites = dao.getFavoriteEpisodeIds().toHashSet()
        val remoteFavorites = HashSet<String>()
        for (fav in remote.favorites) {
            if (!fav.isFavorite || (fav.episodeId.isBlank() && fav.audioUrl.isNullOrBlank())) continue
            val localEp = resolveEpisode(fav.episodeId, fav.audioUrl)
            if (localEp == null) {
                outcome.favorites.add(fav)
                continue
            }
            remoteFavorites.add(localEp.id)
            if (localEp.id !in localFavorites && (baseFavorites == null || localEp.id !in baseFavorites)) {
                dao.updateFavoriteStatus(localEp.id, true)
            }
        }
        if (baseFavorites != null) {
            for (id in localFavorites) {
                if (id !in remoteFavorites && id in baseFavorites) {
                    dao.updateFavoriteStatus(id, false)
                }
            }
        }

        // 6. COLA DE REPRODUCCIÓN COMPLETA (fusión a tres bandas conservando el orden local)
        val localQueue = dao.getQueueList()
        val localIds = localQueue.map { it.episode.id }
        val remoteItemsById = LinkedHashMap<String, NextcloudQueueItem>()
        remote.queue.sortedBy { it.positionIndex }.forEach { qItem ->
            if (qItem.episodeId.isBlank() && qItem.audioUrl.isNullOrBlank()) return@forEach
            val canonicalEp = ensureQueueEpisodeExists(qItem)
            if (canonicalEp != null && canonicalEp.id !in remoteItemsById) {
                remoteItemsById[canonicalEp.id] = qItem
            }
        }
        val remoteIds = remoteItemsById.keys.toList()
        val localSyncableIds = localQueue.filter { isSyncableEpisode(it.episode) }.map { it.episode.id }
        // Nunca se quita por sincronización lo que suena ahora ni los audios de carpetas locales
        val pinnedIds = localQueue
            .filter { it.isCurrentlyPlaying || !isSyncableEpisode(it.episode) }
            .map { it.episode.id }
            .toHashSet()
        val baseQueue = base?.queue
        val mergedQueue: List<String> = when {
            baseQueue == null -> if (localIds.isEmpty()) remoteIds else localIds + remoteIds.filter { it !in localIds }
            remoteIds == baseQueue -> localIds
            localSyncableIds == baseQueue -> {
                val result = remoteIds.toMutableList()
                localIds.forEachIndexed { index, id ->
                    if (id in pinnedIds && id !in result) result.add(index.coerceAtMost(result.size), id)
                }
                result
            }
            else -> {
                val removedRemotely = baseQueue.filter { it !in remoteIds }.toHashSet()
                val addedRemotely = remoteIds.filter { it !in baseQueue && it !in localIds }
                localIds.filter { it !in removedRemotely || it in pinnedIds } + addedRemotely
            }
        }
        if (mergedQueue != localIds) {
            val mergedSet = mergedQueue.toHashSet()
            localIds.filter { it !in mergedSet }.forEach { dao.removeFromQueue(it) }
            val localSet = localIds.toHashSet()
            mergedQueue.forEachIndexed { index, id ->
                if (id !in localSet) {
                    val qItem = remoteItemsById[id]
                    dao.insertQueueItem(
                        QueueItemEntity(
                            episodeId = id,
                            positionIndex = index,
                            // Solo al restaurar una cola vacía se respeta el episodio activo remoto
                            isCurrentlyPlaying = localIds.isEmpty() && qItem?.isCurrentlyPlaying == true,
                            addedAt = qItem?.addedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    )
                }
            }
            dao.reorderQueue(mergedQueue)
        }

        // 7. MARCADORES (fusión a tres bandas; una nota editada en un solo lado gana)
        val baseBookmarks = base?.bookmarks
        val remoteBookmarkFingerprints = HashMap<String, String>()
        if (remoteIsV3) {
            val localBookmarks = dao.getAllBookmarksList().associateBy { it.id }
            val remoteBookmarkIds = HashSet<String>()
            for (rb in remote.bookmarks) {
                if (rb.id.isBlank()) continue
                remoteBookmarkIds.add(rb.id)
                remoteBookmarkFingerprints[rb.id] = rb.fingerprint()
                val local = localBookmarks[rb.id]
                if (local == null) {
                    // Borrado aquí desde la última sincronización: no se restaura
                    if (baseBookmarks != null && rb.id in baseBookmarks) continue
                    val localEp = resolveEpisode(rb.episodeId, rb.audioUrl)
                    if (localEp == null) {
                        outcome.bookmarks.add(rb)
                        continue
                    }
                    dao.insertBookmark(rb.toEntity(localEp))
                } else if (local.fingerprint() != rb.fingerprint() && baseBookmarks?.get(rb.id) == local.fingerprint()) {
                    dao.insertBookmark(local.copy(positionMs = rb.positionMs, note = rb.note))
                }
            }
            if (baseBookmarks != null) {
                for (local in localBookmarks.values) {
                    if (local.id !in remoteBookmarkIds && local.id in baseBookmarks) {
                        dao.deleteBookmark(local.id)
                    }
                }
            }
        }

        outcome.seenRemote = SyncBaseSnapshot(
            account = base?.account.orEmpty(),
            queue = remoteIds,
            favorites = remoteFavorites.toList(),
            bookmarks = if (remoteIsV3) remoteBookmarkFingerprints else base?.bookmarks.orEmpty(),
            podcastSettings = if (remoteIsV3) seenSettings else base?.podcastSettings.orEmpty()
        )
        return outcome
    }

    private fun NextcloudBookmark.toEntity(localEp: EpisodeEntity) = BookmarkEntity(
        id = id,
        episodeId = localEp.id,
        podcastId = localEp.podcastId,
        podcastTitle = podcastTitle,
        episodeTitle = episodeTitle.ifBlank { localEp.title },
        positionMs = positionMs,
        note = note,
        createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis()
    )

    /**
     * Estadísticas como contadores por dispositivo: el total de un podcast es la base heredada más
     * lo que ha escuchado cada dispositivo, de modo que escuchar en el móvil y en la tableta suma
     * en lugar de quedarse con el mayor de los dos.
     */
    private suspend fun mergePlaybackStats(remote: NextcloudSyncData, deviceId: String) {
        val dao = database.podcastDao()
        val local = dao.getPodcastStatsList().associateBy { it.podcastId }
        val contributions = dao.getAllSyncStatsContributions()
        val baseline = contributions
            .filter { it.deviceId == SyncStatsContributionEntity.BASELINE_DEVICE_ID }
            .associateByTo(HashMap<String, SyncStatsContributionEntity>()) { it.podcastId }
        val others = contributions
            .filter { it.deviceId != SyncStatsContributionEntity.BASELINE_DEVICE_ID && it.deviceId != deviceId }
            .associateByTo(HashMap<Pair<String, String>, SyncStatsContributionEntity>()) { it.deviceId to it.podcastId }
        val dirty = LinkedHashMap<Pair<String, String>, SyncStatsContributionEntity>()

        fun othersFor(podcastId: String) = others.values.filter { it.podcastId == podcastId }

        // Lo escuchado en este dispositivo se deduce antes de tocar nada
        val ownTime = HashMap<String, Long>()
        val ownCompleted = HashMap<String, Int>()
        for ((podcastId, stat) in local) {
            val rest = othersFor(podcastId)
            ownTime[podcastId] = (stat.totalTimeListenedMs - (baseline[podcastId]?.totalTimeListenedMs ?: 0L) -
                rest.sumOf { it.totalTimeListenedMs }).coerceAtLeast(0L)
            ownCompleted[podcastId] = (stat.episodesCompletedCount - (baseline[podcastId]?.episodesCompletedCount ?: 0) -
                rest.sumOf { it.episodesCompletedCount }).coerceAtLeast(0)
        }

        fun mergeMax(current: SyncStatsContributionEntity?, incoming: SyncStatsContributionEntity): SyncStatsContributionEntity =
            if (current == null) incoming else current.copy(
                podcastTitle = current.podcastTitle.ifBlank { incoming.podcastTitle },
                totalTimeListenedMs = maxOf(current.totalTimeListenedMs, incoming.totalTimeListenedMs),
                episodesCompletedCount = maxOf(current.episodesCompletedCount, incoming.episodesCompletedCount),
                lastListenedAt = maxOf(current.lastListenedAt, incoming.lastListenedAt)
            )

        if (remote.version >= 3) {
            for (r in remote.deviceStats) {
                if (r.deviceId.isBlank() || r.podcastId.isBlank()) continue
                if (r.deviceId == deviceId) {
                    // Copia propia más avanzada en remoto (p. ej. tras restaurar una copia de seguridad antigua)
                    ownTime[r.podcastId] = maxOf(ownTime[r.podcastId] ?: 0L, r.totalTimeListenedMs)
                    ownCompleted[r.podcastId] = maxOf(ownCompleted[r.podcastId] ?: 0, r.episodesCompletedCount)
                    continue
                }
                val key = r.deviceId to r.podcastId
                val incoming = SyncStatsContributionEntity(
                    r.deviceId, r.podcastId, r.podcastTitle, r.totalTimeListenedMs, r.episodesCompletedCount, r.lastListenedAt
                )
                val merged = mergeMax(others[key], incoming)
                if (merged != others[key]) {
                    others[key] = merged
                    dirty[key] = merged
                }
            }
            for (b in remote.statsBaselines) {
                if (b.podcastId.isBlank()) continue
                val incoming = SyncStatsContributionEntity(
                    SyncStatsContributionEntity.BASELINE_DEVICE_ID, b.podcastId, b.podcastTitle,
                    b.totalTimeListenedMs, b.episodesCompletedCount, b.lastListenedAt
                )
                val merged = mergeMax(baseline[b.podcastId], incoming)
                if (merged != baseline[b.podcastId]) {
                    baseline[b.podcastId] = merged
                    dirty[merged.deviceId to merged.podcastId] = merged
                }
            }
        }
        val legacyTotals = if (remote.version < 3) remote.playbackStats.associateBy { it.podcastId } else emptyMap()

        val podcastIds = LinkedHashSet<String>().apply {
            addAll(local.keys); addAll(baseline.keys); addAll(others.values.map { it.podcastId }); addAll(legacyTotals.keys)
            addAll(ownTime.keys)
        }
        for (podcastId in podcastIds) {
            if (podcastId.isBlank()) continue
            val rest = othersFor(podcastId)
            var newTime = (baseline[podcastId]?.totalTimeListenedMs ?: 0L) + (ownTime[podcastId] ?: 0L) + rest.sumOf { it.totalTimeListenedMs }
            var newCompleted = (baseline[podcastId]?.episodesCompletedCount ?: 0) + (ownCompleted[podcastId] ?: 0) + rest.sumOf { it.episodesCompletedCount }
            var lastListenedAt = maxOf(baseline[podcastId]?.lastListenedAt ?: 0L, rest.maxOfOrNull { it.lastListenedAt } ?: 0L)
            var title = local[podcastId]?.podcastTitle?.takeIf { it.isNotBlank() }
                ?: rest.firstOrNull { it.podcastTitle.isNotBlank() }?.podcastTitle
                ?: baseline[podcastId]?.podcastTitle?.takeIf { it.isNotBlank() }

            // Un archivo antiguo solo trae totales: se fusionan por máximo, como hacía la versión anterior
            legacyTotals[podcastId]?.let { legacy ->
                if (legacy.totalTimeListenedMs > newTime || legacy.episodesCompletedCount > newCompleted) {
                    val current = baseline[podcastId] ?: SyncStatsContributionEntity(
                        SyncStatsContributionEntity.BASELINE_DEVICE_ID, podcastId, legacy.podcastTitle
                    )
                    val raised = current.copy(
                        totalTimeListenedMs = current.totalTimeListenedMs + (legacy.totalTimeListenedMs - newTime).coerceAtLeast(0L),
                        episodesCompletedCount = current.episodesCompletedCount + (legacy.episodesCompletedCount - newCompleted).coerceAtLeast(0),
                        lastListenedAt = maxOf(current.lastListenedAt, legacy.lastListenedAt)
                    )
                    baseline[podcastId] = raised
                    dirty[raised.deviceId to podcastId] = raised
                    newTime = maxOf(newTime, legacy.totalTimeListenedMs)
                    newCompleted = maxOf(newCompleted, legacy.episodesCompletedCount)
                }
                lastListenedAt = maxOf(lastListenedAt, legacy.lastListenedAt)
                if (title == null && legacy.podcastTitle.isNotBlank()) title = legacy.podcastTitle
            }

            val current = local[podcastId]
            val deltaTime = newTime - (current?.totalTimeListenedMs ?: 0L)
            val deltaCompleted = newCompleted - (current?.episodesCompletedCount ?: 0)
            if (deltaTime != 0L || deltaCompleted != 0 || (current != null && lastListenedAt > current.lastListenedAt) ||
                (current == null && newTime > 0L)
            ) {
                // Incremento atómico para no pisar el tiempo que se registre mientras se sincroniza
                dao.addToPlaybackStats(
                    podcastId = podcastId,
                    podcastTitle = title ?: podcastId,
                    deltaTimeMs = deltaTime,
                    deltaCompleted = deltaCompleted,
                    lastListenedAt = lastListenedAt
                )
            }
        }
        if (dirty.isNotEmpty()) dao.upsertSyncStatsContributions(dirty.values.toList())
    }

    private suspend fun ensureQueueEpisodeExists(qItem: NextcloudQueueItem): EpisodeEntity? {
        val dao = database.podcastDao()
        val existing = resolveEpisode(qItem.episodeId, qItem.audioUrl)
        if (existing != null) return existing
        if (qItem.episodeId.isBlank()) return null

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

    // ==========================================
    // RECOPILACIÓN LOCAL → REMOTO
    // ==========================================

    private suspend fun collectLocalSyncData(
        deviceId: String,
        account: String,
        outcome: SyncMergeOutcome
    ): Pair<NextcloudSyncData, SyncBaseSnapshot> {
        val dao = database.podcastDao()

        // 1. Suscripciones con lápidas y ajustes por podcast (las carpetas locales no viajan)
        val syncPodcasts = dao.getSyncablePodcasts().filter { !it.isLocalFolder && isRemoteUrl(it.feedUrl) }
        val subscriptions = syncPodcasts.map {
            NextcloudSubscription(
                feedUrl = it.feedUrl,
                title = it.title,
                subscribedAt = it.subscribedAt,
                unsubscribedAt = it.unsubscribedAt,
                isSubscribed = it.isSubscribed,
                settings = it.toSyncSettings()
            )
        }
        val localFeeds = subscriptions.mapTo(HashSet()) { it.feedUrl }
        val allSubscriptions = subscriptions + outcome.subscriptions.filter { it.feedUrl !in localFeeds }.distinctBy { it.feedUrl }

        // 2. Puntos de escucha y episodios reproducidos (con positionUpdatedAt)
        val playedOrProgress = dao.getPlayedOrInProgressEpisodes().filter { isSyncableEpisode(it) }
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
        val progressIds = episodesProgress.mapTo(HashSet()) { it.episodeId }
        val allProgress = episodesProgress + outcome.progress.filter { it.episodeId !in progressIds }.distinctBy { it.episodeId }

        // 3. Historial: cada sesión de escucha con su identificador estable
        val historyClearedAt = dao.getSyncState(PodcastDao.SYNC_KEY_HISTORY_CLEARED_AT)?.toLongOrNull() ?: 0L
        val localHistory = dao.getHistorySessionsForSync(limit = MAX_HISTORY_SESSIONS)
            .filter { isRemoteUrl(it.audioUrl) }
            .map {
                NextcloudHistoryItem(
                    episodeId = it.episodeId,
                    podcastId = it.podcastId,
                    audioUrl = it.audioUrl,
                    listenedAt = it.listenedAt,
                    durationListenedMs = it.durationListenedMs,
                    completed = it.completed,
                    syncId = it.syncId
                )
            }
        val historySyncIds = localHistory.mapTo(HashSet()) { it.syncId }
        val history = (localHistory + outcome.history.filter { it.syncId !in historySyncIds })
            .filter { it.listenedAt > historyClearedAt }
            .distinctBy { it.syncId }
            .sortedByDescending { it.listenedAt }
            .take(MAX_HISTORY_SESSIONS)

        // 4. Estadísticas: total (para versiones antiguas), aporte de cada dispositivo y base heredada
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
        val contributions = dao.getAllSyncStatsContributions()
        val baselineRows = contributions.filter { it.deviceId == SyncStatsContributionEntity.BASELINE_DEVICE_ID }
        val otherDeviceRows = contributions.filter {
            it.deviceId != SyncStatsContributionEntity.BASELINE_DEVICE_ID && it.deviceId != deviceId
        }
        val baselineById = baselineRows.associateBy { it.podcastId }
        val ownRows = statsList.mapNotNull { stat ->
            val rest = otherDeviceRows.filter { it.podcastId == stat.podcastId }
            val ownTime = stat.totalTimeListenedMs - (baselineById[stat.podcastId]?.totalTimeListenedMs ?: 0L) -
                rest.sumOf { it.totalTimeListenedMs }
            val ownCompleted = stat.episodesCompletedCount - (baselineById[stat.podcastId]?.episodesCompletedCount ?: 0) -
                rest.sumOf { it.episodesCompletedCount }
            if (ownTime <= 0L && ownCompleted <= 0) null else NextcloudDeviceStats(
                deviceId = deviceId,
                podcastId = stat.podcastId,
                podcastTitle = stat.podcastTitle,
                totalTimeListenedMs = ownTime.coerceAtLeast(0L),
                episodesCompletedCount = ownCompleted.coerceAtLeast(0),
                lastListenedAt = stat.lastListenedAt
            )
        }
        val deviceStats = ownRows + otherDeviceRows.map {
            NextcloudDeviceStats(it.deviceId, it.podcastId, it.podcastTitle, it.totalTimeListenedMs, it.episodesCompletedCount, it.lastListenedAt)
        }
        val statsBaselines = baselineRows.map {
            NextcloudPlaybackStats(it.podcastId, it.podcastTitle, it.totalTimeListenedMs, it.episodesCompletedCount, it.lastListenedAt)
        }

        // 5. Favoritos
        val favoriteEpisodes = dao.getFavoriteEpisodesList().filter { isSyncableEpisode(it) }
        val favorites = favoriteEpisodes.map {
            NextcloudFavoriteItem(episodeId = it.id, audioUrl = it.audioUrl, isFavorite = true)
        }
        val favoriteIds = favorites.mapTo(HashSet()) { it.episodeId }
        val allFavorites = favorites + outcome.favorites.filter { it.episodeId !in favoriteIds }.distinctBy { it.episodeId }

        // 6. Cola de reproducción completa
        val queueList = dao.getQueueList().filter { isSyncableEpisode(it.episode) }
        val queue = queueList.mapIndexed { index, it ->
            NextcloudQueueItem(
                episodeId = it.episode.id,
                audioUrl = it.episode.audioUrl,
                podcastId = it.episode.podcastId,
                podcastTitle = it.podcastTitle,
                podcastArtworkUrl = it.podcastArtworkUrl,
                episodeTitle = it.episode.title,
                episodeDuration = it.episode.duration,
                positionIndex = index,
                isCurrentlyPlaying = it.isCurrentlyPlaying,
                addedAt = it.addedAt
            )
        }

        // 7. Marcadores
        val localBookmarks = dao.getAllBookmarksList()
        val bookmarkEpisodes = HashMap<String, EpisodeEntity?>()
        val bookmarks = localBookmarks.mapNotNull { bm ->
            val ep = bookmarkEpisodes.getOrPut(bm.episodeId) { dao.getEpisodeById(bm.episodeId) }
            if (ep == null || !isSyncableEpisode(ep)) return@mapNotNull null
            NextcloudBookmark(
                id = bm.id,
                episodeId = bm.episodeId,
                audioUrl = ep.audioUrl,
                podcastId = bm.podcastId,
                podcastTitle = bm.podcastTitle,
                episodeTitle = bm.episodeTitle,
                positionMs = bm.positionMs,
                note = bm.note,
                createdAt = bm.createdAt
            )
        }
        val bookmarkIds = bookmarks.mapTo(HashSet()) { it.id }
        val allBookmarks = bookmarks + outcome.bookmarks.filter { it.id !in bookmarkIds }.distinctBy { it.id }

        val data = NextcloudSyncData(
            version = NEXTCLOUD_SYNC_FORMAT_VERSION,
            app = "MisPodcast",
            lastSyncTimestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            subscriptions = allSubscriptions,
            queue = queue,
            favorites = allFavorites,
            episodesProgress = allProgress,
            playbackStats = playbackStats,
            history = history,
            historyClearedAt = historyClearedAt,
            deviceStats = deviceStats,
            statsBaselines = statsBaselines,
            bookmarks = allBookmarks
        )
        val snapshot = SyncBaseSnapshot(
            account = account,
            queue = queue.map { it.episodeId },
            favorites = favorites.map { it.episodeId },
            bookmarks = bookmarks.associate { it.id to it.fingerprint() },
            podcastSettings = subscriptions.filter { it.isSubscribed }.associate { it.feedUrl to it.settings!! }
        )
        return data to snapshot
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
