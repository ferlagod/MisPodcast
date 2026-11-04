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

import com.ferlagod.mispodcast.util.rethrowIfCancellation
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

// Modelos serializables del fichero de sincronización de Nextcloud y estado de fusión.

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
