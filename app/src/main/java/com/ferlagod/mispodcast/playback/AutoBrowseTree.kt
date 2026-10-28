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

package com.ferlagod.mispodcast.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.util.AudioTrackingSanitizer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Árbol de navegación y catálogo multimedia para clientes MediaBrowser y Android Auto.
 *
 * Gestiona la estructura de carpetas jerárquicas (Cola, Mis Podcasts, Novedades, Descargas, Historial),
 * la resolución de metadatos de reproducción enriquecidos y la búsqueda unificada local y remota.
 */
@OptIn(UnstableApi::class)
class AutoBrowseTree(
    private val database: PodcastDatabase,
    private val searchService: ApplePodcastSearchService,
    private val rssParser: RssFeedParser
) {

    // Mapa temporal para almacenar feedUrls encontrados en búsquedas remotas
    private val searchFeedUrlCache = ConcurrentHashMap<String, String>()

    companion object {
        const val ROOT_ID = "root"
        const val MEDIA_ID_QUEUE = "auto_queue"
        const val MEDIA_ID_PODCASTS = "auto_podcasts"
        const val MEDIA_ID_NEW_RELEASES = "auto_new_releases"
        const val MEDIA_ID_DOWNLOADS = "auto_downloads"
        const val MEDIA_ID_HISTORY = "auto_history"
        const val PREFIX_PODCAST = "podcast_"
        const val PREFIX_SEARCH_PODCAST = "search_pod_"
    }

    /**
     * Construye un folder navegable para Android Auto.
     */
    fun createBrowsableFolder(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /**
     * Resuelve la URI real de reproducción para un episodio (archivo local o streaming sanitizado).
     */
    fun resolvePlaybackUri(episode: EpisodeEntity): Uri {
        return when {
            episode.isDownloaded && !episode.localFilePath.isNullOrBlank() && File(episode.localFilePath).exists() -> {
                Uri.fromFile(File(episode.localFilePath))
            }
            !episode.localFilePath.isNullOrBlank() && File(episode.localFilePath).exists() -> {
                Uri.fromFile(File(episode.localFilePath))
            }
            !episode.localFilePath.isNullOrBlank() && episode.localFilePath.startsWith("content://") -> {
                Uri.parse(episode.localFilePath)
            }
            episode.audioUrl.startsWith("content://") -> {
                Uri.parse(episode.audioUrl)
            }
            else -> {
                val cleanUrl = AudioTrackingSanitizer.cleanAudioUrl(episode.audioUrl)
                Uri.parse(cleanUrl)
            }
        }
    }

    /**
     * Construye un [MediaItem] reproducible con metadatos completos y soporte para porcentaje de progreso en Auto.
     */
    fun buildPlayableMediaItem(
        episode: EpisodeEntity,
        podcastTitle: String,
        podcastArtworkUrl: String
    ): MediaItem {
        val artworkUri = when {
            episode.imageUrl.isNotBlank() -> Uri.parse(episode.imageUrl)
            podcastArtworkUrl.isNotBlank() -> Uri.parse(podcastArtworkUrl)
            else -> null
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcastTitle.ifBlank { "Podcast" })
            .setAlbumTitle(podcastTitle.ifBlank { "Podcast" })
            .setArtworkUri(artworkUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)

        runCatching {
            val extras = Bundle().apply {
                putString("podcastId", episode.podcastId)
                putString("episodeId", episode.id)
                putLong("durationMs", episode.duration)

                // Soporte de indicadores de progreso nativos en Android Auto
                val status = when {
                    episode.isPlayed -> 2 // FULLY_PLAYED
                    episode.currentPositionMs > 0 && episode.duration > 0 -> 1 // PARTIALLY_PLAYED
                    else -> 0 // NOT_PLAYED
                }
                putInt("androidx.media.MediaItem.extra.COMPLETION_STATUS", status)
                if (episode.duration > 0) {
                    val percentage = (episode.currentPositionMs.toDouble() / episode.duration.toDouble()).coerceIn(0.0, 1.0)
                    putDouble("androidx.media.MediaItem.extra.COMPLETION_PERCENTAGE", percentage)
                }
            }
            metadataBuilder.setExtras(extras)
        }

        val mediaMetadata = metadataBuilder.build()

        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(resolvePlaybackUri(episode))
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    /**
     * Retorna las pestañas principales del menú raíz de Android Auto (máximo 4 según lineamientos de Google).
     */
    fun getRootTabs(): ImmutableList<MediaItem> {
        return ImmutableList.of(
            createBrowsableFolder(MEDIA_ID_QUEUE, "Cola de reproducción"),
            createBrowsableFolder(MEDIA_ID_PODCASTS, "Mis Podcasts"),
            createBrowsableFolder(MEDIA_ID_NEW_RELEASES, "Nuevos episodios"),
            createBrowsableFolder(MEDIA_ID_DOWNLOADS, "Descargas")
        )
    }

    /**
     * Obtiene la lista de elementos para una carpeta navegable.
     */
    suspend fun getChildrenForFolder(parentId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        when {
            parentId == MEDIA_ID_QUEUE -> {
                val queueList = database.podcastDao().getQueueList()
                for (qItem in queueList) {
                    items.add(
                        buildPlayableMediaItem(
                            episode = qItem.episode,
                            podcastTitle = qItem.podcastTitle,
                            podcastArtworkUrl = qItem.podcastArtworkUrl
                        )
                    )
                }
            }

            parentId == MEDIA_ID_PODCASTS -> {
                val subscribed = database.podcastDao().getSubscribedPodcasts()
                for (pod in subscribed) {
                    items.add(
                        MediaItem.Builder()
                            .setMediaId("$PREFIX_PODCAST${pod.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(pod.title)
                                    .setArtist(pod.author)
                                    .setAlbumTitle(pod.title)
                                    .setArtworkUri(if (pod.artworkUrl.isNotBlank()) Uri.parse(pod.artworkUrl) else null)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                                    .build()
                            )
                            .build()
                    )
                }
                items.add(createBrowsableFolder(MEDIA_ID_HISTORY, "Historial de reproducción"))
            }

            parentId.startsWith(PREFIX_PODCAST) -> {
                val podId = parentId.removePrefix(PREFIX_PODCAST)
                val podcast = database.podcastDao().getPodcastById(podId)
                val episodes = database.podcastDao().getEpisodesForPodcast(podId)
                for (ep in episodes) {
                    items.add(
                        buildPlayableMediaItem(
                            episode = ep,
                            podcastTitle = podcast?.title.orEmpty(),
                            podcastArtworkUrl = podcast?.artworkUrl.orEmpty()
                        )
                    )
                }
            }

            parentId.startsWith(PREFIX_SEARCH_PODCAST) -> {
                val feedUrl = searchFeedUrlCache[parentId]
                if (!feedUrl.isNullOrBlank()) {
                    try {
                        val parsed = rssParser.fetchAndParseFeed(feedUrl)
                        for (ep in parsed.episodes.take(30)) {
                            val mediaMetadata = MediaMetadata.Builder()
                                .setTitle(ep.title)
                                .setArtist(parsed.title)
                                .setAlbumTitle(parsed.title)
                                .setArtworkUri(Uri.parse(ep.imageUrl.ifBlank { parsed.artworkUrl }))
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                .build()

                            items.add(
                                MediaItem.Builder()
                                    .setMediaId(ep.audioUrl)
                                    .setUri(Uri.parse(ep.audioUrl))
                                    .setMediaMetadata(mediaMetadata)
                                    .build()
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            parentId == MEDIA_ID_NEW_RELEASES -> {
                val releases = database.podcastDao().getNewReleasesList(50)
                for (rel in releases) {
                    items.add(
                        buildPlayableMediaItem(
                            episode = rel.episode,
                            podcastTitle = rel.podcastTitle,
                            podcastArtworkUrl = rel.podcastArtworkUrl
                        )
                    )
                }
            }

            parentId == MEDIA_ID_DOWNLOADS -> {
                val downloads = database.podcastDao().getDownloadedEpisodesList()
                for (down in downloads) {
                    items.add(
                        buildPlayableMediaItem(
                            episode = down.episode,
                            podcastTitle = down.podcastTitle,
                            podcastArtworkUrl = down.podcastArtworkUrl
                        )
                    )
                }
            }

            parentId == MEDIA_ID_HISTORY -> {
                val historyList = database.podcastDao().getListeningHistoryList(30).distinctBy { it.episode.id }
                for (hist in historyList) {
                    items.add(
                        buildPlayableMediaItem(
                            episode = hist.episode,
                            podcastTitle = hist.podcastTitle,
                            podcastArtworkUrl = hist.podcastArtworkUrl
                        )
                    )
                }
            }
        }

        items
    }

    /**
     * Resuelve un elemento individual solicitado por su mediaId.
     */
    suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        when {
            mediaId == ROOT_ID -> {
                MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Mis Podcasts")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
            }
            mediaId == MEDIA_ID_QUEUE -> {
                MediaItem.Builder()
                    .setMediaId(MEDIA_ID_QUEUE)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Cola de reproducción")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
            }
            mediaId.startsWith(PREFIX_PODCAST) -> {
                val podId = mediaId.removePrefix(PREFIX_PODCAST)
                val pod = database.podcastDao().getPodcastById(podId)
                if (pod != null) {
                    MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(pod.title)
                                .setArtist(pod.author)
                                .setArtworkUri(if (pod.artworkUrl.isNotBlank()) Uri.parse(pod.artworkUrl) else null)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                } else null
            }
            else -> {
                val ep = database.podcastDao().getEpisodeById(mediaId)
                if (ep != null) {
                    val pod = database.podcastDao().getPodcastById(ep.podcastId)
                    buildPlayableMediaItem(
                        episode = ep,
                        podcastTitle = pod?.title.orEmpty(),
                        podcastArtworkUrl = pod?.artworkUrl.orEmpty()
                    )
                } else null
            }
        }
    }

    /**
     * Realiza una búsqueda unificada de podcasts y episodios locales y remotos.
     */
    suspend fun searchMediaItems(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext results

        // 1. Buscar en podcasts locales suscritos
        val localPodcasts = database.podcastDao().searchLocalPodcasts(trimmed)
        for (pod in localPodcasts) {
            results.add(
                MediaItem.Builder()
                    .setMediaId("$PREFIX_PODCAST${pod.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(pod.title)
                            .setArtist(pod.author)
                            .setAlbumTitle(pod.title)
                            .setArtworkUri(if (pod.artworkUrl.isNotBlank()) Uri.parse(pod.artworkUrl) else null)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                            .build()
                    )
                    .build()
            )
        }

        // 2. Buscar en episodios locales
        val localEpisodes = database.podcastDao().searchLocalEpisodes(trimmed)
        for (item in localEpisodes) {
            results.add(
                buildPlayableMediaItem(
                    episode = item.episode,
                    podcastTitle = item.podcastTitle,
                    podcastArtworkUrl = item.podcastArtworkUrl
                )
            )
        }

        // 3. Buscar remotamente en Apple Podcasts si hay espacio
        if (results.size < 15) {
            try {
                val appleResponse = searchService.searchPodcasts(
                    term = trimmed,
                    media = "podcast",
                    limit = 10,
                    country = "ES",
                    lang = "es_es"
                )
                for (appleDto in appleResponse.results) {
                    val feedUrl = appleDto.feedUrl
                    if (!feedUrl.isNullOrBlank() && localPodcasts.none { it.feedUrl == feedUrl }) {
                        val searchMediaId = "$PREFIX_SEARCH_PODCAST${appleDto.collectionId ?: feedUrl.hashCode()}"
                        searchFeedUrlCache[searchMediaId] = feedUrl

                        results.add(
                            MediaItem.Builder()
                                .setMediaId(searchMediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(appleDto.collectionName ?: "Podcast")
                                        .setArtist(appleDto.artistName ?: "")
                                        .setAlbumTitle(appleDto.collectionName ?: "")
                                        .setArtworkUri(Uri.parse(appleDto.artworkUrl600 ?: appleDto.artworkUrl100 ?: ""))
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        results
    }

    /**
     * Resuelve un [MediaItem] directamente ejecutable a partir de una orden de voz del Asistente de Google,
     * Android Auto o Wear OS (ej: "Ok Google, pon Nadie Sabe Nada en Mis Podcast").
     *
     * Analiza la consulta hablada eliminando expresiones de activación ("pon", "reproduce", "en Mis Podcast"),
     * busca en los podcasts y episodios locales (priorizando no escuchados o en progreso) y recurre a búsqueda
     * remota si el usuario aún no está suscrito.
     *
     * @param query Texto o término de búsqueda proporcionado por el Asistente.
     * @param extras Metadatos adicionales o Bundle de MediaStore / SearchManager.
     * @return [MediaItem] reproducible configurado con URI real y metadatos, o null si no se encontraron coincidencias.
     */
    suspend fun resolveMediaItemFromVoiceSearch(query: String?, extras: Bundle? = null): MediaItem? = withContext(Dispatchers.IO) {
        val raw = when {
            !query.isNullOrBlank() -> query
            extras != null -> {
                extras.getString("query")
                    ?: extras.getString(android.provider.MediaStore.EXTRA_MEDIA_TITLE)
                    ?: extras.getString(android.provider.MediaStore.EXTRA_MEDIA_ALBUM)
                    ?: extras.getString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST)
                    ?: extras.getString(android.app.SearchManager.QUERY)
            }
            else -> null
        }

        val cleaned = sanitizeVoiceQuery(raw.orEmpty())

        if (cleaned.isBlank()) {
            val queue = database.podcastDao().getQueueList()
            if (queue.isNotEmpty()) {
                val qItem = queue.first()
                return@withContext buildPlayableMediaItem(
                    episode = qItem.episode,
                    podcastTitle = qItem.podcastTitle,
                    podcastArtworkUrl = qItem.podcastArtworkUrl
                )
            }
            val releases = database.podcastDao().getNewReleasesList(1)
            if (releases.isNotEmpty()) {
                val rel = releases.first()
                return@withContext buildPlayableMediaItem(
                    episode = rel.episode,
                    podcastTitle = rel.podcastTitle,
                    podcastArtworkUrl = rel.podcastArtworkUrl
                )
            }
            return@withContext null
        }

        // 1. Intento por podcast local suscrito
        val localPodcasts = database.podcastDao().searchLocalPodcasts(cleaned)
        val matchedPodcast = localPodcasts.firstOrNull()
        if (matchedPodcast != null) {
            val episodes = database.podcastDao().getEpisodesForPodcast(matchedPodcast.id)
            if (episodes.isNotEmpty()) {
                val inProgress = episodes.firstOrNull { it.currentPositionMs > 0 && !it.isPlayed }
                val unplayed = episodes.firstOrNull { !it.isPlayed }
                val targetEpisode = inProgress ?: unplayed ?: episodes.first()
                return@withContext buildPlayableMediaItem(
                    episode = targetEpisode,
                    podcastTitle = matchedPodcast.title,
                    podcastArtworkUrl = matchedPodcast.artworkUrl
                )
            }
        }

        // 2. Intento por episodio local
        val localEpisodes = database.podcastDao().searchLocalEpisodes(cleaned)
        if (localEpisodes.isNotEmpty()) {
            val bestEp = localEpisodes.first()
            return@withContext buildPlayableMediaItem(
                episode = bestEp.episode,
                podcastTitle = bestEp.podcastTitle,
                podcastArtworkUrl = bestEp.podcastArtworkUrl
            )
        }

        // 3. Intento por búsqueda remota en Apple Podcasts si no está en local
        try {
            val appleResponse = searchService.searchPodcasts(
                term = cleaned,
                media = "podcast",
                limit = 5,
                country = "ES",
                lang = "es_es"
            )
            val remoteResult = appleResponse.results.firstOrNull { !it.feedUrl.isNullOrBlank() }
            if (remoteResult != null && !remoteResult.feedUrl.isNullOrBlank()) {
                val parsed = rssParser.fetchAndParseFeed(remoteResult.feedUrl)
                val firstEpisode = parsed.episodes.firstOrNull()
                if (firstEpisode != null && firstEpisode.audioUrl.isNotBlank()) {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(firstEpisode.title)
                        .setArtist(parsed.title)
                        .setAlbumTitle(parsed.title)
                        .setArtworkUri(Uri.parse(firstEpisode.imageUrl.ifBlank { parsed.artworkUrl }))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                        .build()

                    return@withContext MediaItem.Builder()
                        .setMediaId(firstEpisode.audioUrl)
                        .setUri(Uri.parse(AudioTrackingSanitizer.cleanAudioUrl(firstEpisode.audioUrl)))
                        .setMediaMetadata(mediaMetadata)
                        .build()
                }
            }
        } catch (_: Exception) {
        }

        null
    }

    /**
     * Limpia la consulta de voz de prefijos ("pon", "reproduce") y sufijos ("en Mis Podcast").
     */
    fun sanitizeVoiceQuery(raw: String): String {
        var q = raw.trim()
        val hotwords = listOf(
            "ok google,", "ok google", "hey google,", "hey google", "oye google,", "oye google"
        )
        for (hw in hotwords) {
            if (q.startsWith(hw, ignoreCase = true)) {
                q = q.substring(hw.length).trim().removePrefix(",").trim()
                break
            }
        }
        val prefixes = listOf(
            "pon ", "reproduce ", "reproducir ", "escuchar ", "play ", "listen to ", "quiero escuchar "
        )
        for (prefix in prefixes) {
            if (q.startsWith(prefix, ignoreCase = true)) {
                q = q.substring(prefix.length).trim()
                break
            }
        }
        val suffixes = listOf(
            " en mis podcast", " en mis podcasts", " con mis podcast", " con mis podcasts",
            " on mis podcast", " on mis podcasts", " in mis podcast", " por favor"
        )
        for (suffix in suffixes) {
            if (q.endsWith(suffix, ignoreCase = true)) {
                q = q.substring(0, q.length - suffix.length).trim()
                break
            }
        }
        return q
    }
}
