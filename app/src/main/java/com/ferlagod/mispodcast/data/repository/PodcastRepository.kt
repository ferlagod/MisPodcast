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

package com.ferlagod.mispodcast.data.repository

import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.local.model.GlobalStats
import com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.ferlagod.mispodcast.data.model.WrappedData
import com.ferlagod.mispodcast.data.model.WrappedPodcastItem
import com.ferlagod.mispodcast.data.mapper.PodcastLanguageMapper
import com.ferlagod.mispodcast.data.mapper.toEpisodeEntity
import com.ferlagod.mispodcast.data.mapper.toPodcastEntity
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Modelo de datos para agrupar bloques de recomendaciones en la pantalla de inicio.
 */
data class HomeRecommendationData(
    val title: String,
    val subtitle: String,
    val podcasts: List<ApplePodcastDto>
)

/**
 * Repositorio central de datos de Mis Podcast.
 *
 * Actúa como única fuente de verdad para la capa de presentación (ViewModels), coordinando:
 * - Operaciones locales de persistencia en SQLite mediante [PodcastDao].
 * - Búsqueda remota y obtención de tendencias desde el catálogo de Apple Podcasts.
 * - Descarga y parseo de feeds RSS/Atom con soporte para etiquetas de Podcasting 2.0.
 * - Gestión integral de suscripciones, novedades, cola de reproducción, historial y favoritos.
 */
class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val searchService: ApplePodcastSearchService = ApplePodcastSearchService.create(),
    private val feedParser: RssFeedParser = RssFeedParser(),
    private val preferencesManager: com.ferlagod.mispodcast.data.preferences.UserPreferencesManager? = null
) {

    // ==========================================
    // BUSCADOR APPLE PODCASTS
    // ==========================================

    suspend fun searchApplePodcasts(
        term: String,
        country: String = "ES",
        lang: String? = null
    ): Result<List<ApplePodcastDto>> = withContext(Dispatchers.IO) {
        try {
            val response = searchService.searchPodcasts(term = term, country = country, lang = lang)
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPodcastsByGenre(
        genreId: Int,
        term: String = "podcast",
        country: String = "ES",
        lang: String? = null
    ): Result<List<ApplePodcastDto>> = withContext(Dispatchers.IO) {
        try {
            val response = if (genreId > 0) {
                searchService.searchPodcastsByGenre(
                    genreId = genreId,
                    term = term,
                    limit = 50,
                    country = country,
                    lang = lang
                )
            } else {
                searchService.searchPodcasts(
                    term = term,
                    limit = 50,
                    country = country,
                    lang = lang
                )
            }
            Result.success(response.results.filter { !it.feedUrl.isNullOrBlank() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHomeRecommendations(languageCode: String = "auto"): Result<HomeRecommendationData> = withContext(Dispatchers.IO) {
        try {
            val country = PodcastLanguageMapper.getCountryCode(languageCode)
            val langParam = PodcastLanguageMapper.getLanguageParam(languageCode)
            val subscribed = podcastDao.getSubscribedPodcasts()
            val followedFeedUrls = subscribed.mapNotNull { it.feedUrl.lowercase().trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            val followedTitles = subscribed.map { it.title.lowercase().trim() }.toSet()

            if (subscribed.isNotEmpty()) {
                val candidate = subscribed.firstOrNull { it.category.isNotBlank() && it.category != "General" }
                    ?: subscribed.firstOrNull { it.author.isNotBlank() }
                    ?: subscribed.first()

                val searchTerm = when {
                    candidate.category.isNotBlank() && candidate.category != "General" -> candidate.category
                    candidate.author.isNotBlank() -> candidate.author
                    else -> candidate.title
                }

                val response = searchService.searchPodcasts(
                    term = searchTerm,
                    media = "podcast",
                    limit = 15,
                    country = country,
                    lang = langParam
                )

                val filtered = response.results.filter { dto ->
                    val feed = dto.feedUrl?.lowercase()?.trim().orEmpty()
                    val title = dto.collectionName?.lowercase()?.trim().orEmpty()
                    feed.isNotBlank() && feed !in followedFeedUrls && title !in followedTitles
                }

                if (filtered.isNotEmpty()) {
                    return@withContext Result.success(
                        HomeRecommendationData(
                            title = PodcastLanguageMapper.getRecommendedTitle(languageCode),
                            subtitle = "Basado en tu interés por '${candidate.title}'",
                            podcasts = filtered.take(15)
                        )
                    )
                }
            }

            // Si aún no sigue ninguno o no hubo resultados filtrados:
            val trendingTerm = PodcastLanguageMapper.getHomeTrendingTerm(languageCode)
            val popularResponse = searchService.searchPodcasts(
                term = trendingTerm,
                media = "podcast",
                limit = 15,
                country = country,
                lang = langParam
            )

            val filteredPopular = popularResponse.results.filter { dto ->
                val feed = dto.feedUrl?.lowercase()?.trim().orEmpty()
                val title = dto.collectionName?.lowercase()?.trim().orEmpty()
                feed.isNotBlank() && feed !in followedFeedUrls && title !in followedTitles
            }

            val finalResults = if (filteredPopular.isNotEmpty()) filteredPopular else popularResponse.results.filter { !it.feedUrl.isNullOrBlank() }

            Result.success(
                HomeRecommendationData(
                    title = PodcastLanguageMapper.getTrendingTitle(languageCode),
                    subtitle = PodcastLanguageMapper.getTrendingSubtitle(languageCode),
                    podcasts = finalResults.take(15)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // SUSCRIPCIÓN Y ACTUALIZACIÓN DE FEEDS RSS
    // ==========================================

    suspend fun subscribeToFeed(feedUrl: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        try {
            val parsedFeed = feedParser.fetchAndParseFeed(feedUrl)
            val existing = podcastDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl)
            val isNewSubscription = existing == null || !existing.isSubscribed
            val now = System.currentTimeMillis()
            val subscribedAt = if (existing != null && existing.isSubscribed && existing.subscribedAt > 0L) {
                existing.subscribedAt
            } else {
                now
            }

            val podcastEntity = parsedFeed.toPodcastEntity(isSubscribed = true, subscribedAt = subscribedAt).let { entity ->
                if (existing != null) {
                    entity.copy(
                        customSpeed = existing.customSpeed,
                        skipIntroSeconds = existing.skipIntroSeconds,
                        skipEndingSeconds = existing.skipEndingSeconds,
                        autoDownloadEnabled = existing.autoDownloadEnabled,
                        episodeSortOrder = existing.episodeSortOrder,
                        autoEnqueueRule = existing.autoEnqueueRule
                    )
                } else {
                    entity.copy(autoEnqueueRule = "DEFAULT")
                }
            }
            val episodeEntities = parsedFeed.episodes.map { 
                val entity = it.toEpisodeEntity(podcastEntity.id)
                if (isNewSubscription) {
                    entity.copy(isDismissedFromNewReleases = true)
                } else {
                    entity
                }
            }

            podcastDao.insertPodcast(podcastEntity)
            podcastDao.insertEpisodes(episodeEntities)

            Result.success(podcastEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshPodcastFeed(podcastId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val podcast = podcastDao.getPodcastById(podcastId)
                ?: return@withContext Result.failure(Exception("Podcast no encontrado en local"))
            val parsedFeed = feedParser.fetchAndParseFeed(podcast.feedUrl)
            val existingEpisodes = podcastDao.getEpisodesForPodcast(podcast.id)
            val existingIds = existingEpisodes.map { it.id }.toSet()
            val existingAudioUrls = existingEpisodes.map { it.audioUrl }.toSet()

            val newEpisodes = parsedFeed.episodes
                .filter { !existingIds.contains(it.id) && !existingAudioUrls.contains(it.audioUrl) }
                .map { it.toEpisodeEntity(podcast.id) }

            val updatedPodcast = parsedFeed.toPodcastEntity(
                isSubscribed = podcast.isSubscribed,
                subscribedAt = podcast.subscribedAt
            ).copy(
                id = podcast.id,
                isLocalFolder = podcast.isLocalFolder,
                localFolderPath = podcast.localFolderPath,
                lastUpdated = System.currentTimeMillis(),
                customSpeed = podcast.customSpeed,
                skipIntroSeconds = podcast.skipIntroSeconds,
                skipEndingSeconds = podcast.skipEndingSeconds,
                autoDownloadEnabled = podcast.autoDownloadEnabled,
                episodeSortOrder = podcast.episodeSortOrder,
                autoEnqueueRule = podcast.autoEnqueueRule
            )
            val episodeEntities = parsedFeed.episodes.map { it.toEpisodeEntity(podcast.id) }

            podcastDao.insertPodcast(updatedPodcast)
            podcastDao.insertEpisodes(episodeEntities)

            if (newEpisodes.isNotEmpty()) {
                val defaultGlobal = preferencesManager?.userSettingsFlow?.firstOrNull()?.defaultAutoEnqueueRule ?: "DISABLED"
                val effectiveRule = if (podcast.autoEnqueueRule == "DEFAULT" || podcast.autoEnqueueRule.isBlank()) {
                    defaultGlobal
                } else {
                    podcast.autoEnqueueRule
                }
                if (effectiveRule != "DISABLED") {
                    for (ep in newEpisodes.sortedBy { it.pubDate }) {
                        if (effectiveRule == "TOP") {
                            podcastDao.addEpisodeToQueueTop(ep.id)
                        } else if (effectiveRule == "BOTTOM") {
                            podcastDao.addEpisodeToQueueEnd(ep.id)
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePodcastSettings(
        podcastId: String,
        customSpeed: Float?,
        skipIntroSeconds: Int,
        skipEndingSeconds: Int,
        autoDownloadEnabled: Boolean?,
        episodeSortOrder: String,
        autoEnqueueRule: String = "DISABLED"
    ): Int = withContext(Dispatchers.IO) {
        podcastDao.updatePodcastSettings(
            podcastId = podcastId,
            customSpeed = customSpeed,
            skipIntroSeconds = skipIntroSeconds,
            skipEndingSeconds = skipEndingSeconds,
            autoDownloadEnabled = autoDownloadEnabled,
            episodeSortOrder = episodeSortOrder,
            autoEnqueueRule = autoEnqueueRule
        )
    }

    suspend fun refreshAllSubscribedPodcasts(
        context: android.content.Context? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var newEpisodesCount = 0
            val subscribed = podcastDao.getSubscribedPodcasts()

            // 1. Re-escanear carpetas locales si se dispone de contexto
            if (context != null) {
                try {
                    com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.rescanAllLocalPodcasts(
                        context,
                        podcastDao
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PodcastRepository", "Aviso al escanear carpetas locales: ${e.message}")
                }
            }

            // 2. Comprobar y actualizar cada feed RSS remoto
            for (podcast in subscribed) {
                if (podcast.isLocalFolder) continue
                val feedUrl = podcast.feedUrl.trim()
                if (feedUrl.isBlank() || (!feedUrl.startsWith("http://", ignoreCase = true) && !feedUrl.startsWith("https://", ignoreCase = true))) {
                    continue
                }

                try {
                    val parsedFeed = feedParser.fetchAndParseFeed(feedUrl)
                    val existingEpisodes = podcastDao.getEpisodesForPodcast(podcast.id)
                    val existingIds = existingEpisodes.map { it.id }.toSet()
                    val existingAudioUrls = existingEpisodes.map { it.audioUrl }.toSet()

                    val newEpisodes = parsedFeed.episodes
                        .filter { !existingIds.contains(it.id) && !existingAudioUrls.contains(it.audioUrl) }
                        .map { it.toEpisodeEntity(podcast.id) }

                    if (newEpisodes.isNotEmpty()) {
                        podcastDao.insertEpisodes(newEpisodes)
                        newEpisodesCount += newEpisodes.size

                        val defaultGlobal = preferencesManager?.userSettingsFlow?.firstOrNull()?.defaultAutoEnqueueRule ?: "DISABLED"
                        val effectiveRule = if (podcast.autoEnqueueRule == "DEFAULT" || podcast.autoEnqueueRule.isBlank()) {
                            defaultGlobal
                        } else {
                            podcast.autoEnqueueRule
                        }
                        if (effectiveRule != "DISABLED") {
                            for (ep in newEpisodes.sortedBy { it.pubDate }) {
                                if (effectiveRule == "TOP") {
                                    podcastDao.addEpisodeToQueueTop(ep.id)
                                } else if (effectiveRule == "BOTTOM") {
                                    podcastDao.addEpisodeToQueueEnd(ep.id)
                                }
                            }
                        }
                    }

                    val updatedPodcast = podcast.copy(
                        title = parsedFeed.title.ifBlank { podcast.title },
                        author = parsedFeed.author.ifBlank { podcast.author },
                        description = parsedFeed.description.ifBlank { podcast.description },
                        artworkUrl = parsedFeed.artworkUrl.ifBlank { podcast.artworkUrl },
                        lastUpdated = System.currentTimeMillis()
                    )
                    podcastDao.insertPodcast(updatedPodcast)
                } catch (e: Exception) {
                    android.util.Log.w("PodcastRepository", "Error al actualizar feed '${podcast.title}': ${e.message}")
                }
            }

            Result.success(newEpisodesCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsubscribeFromPodcast(podcastId: String) = withContext(Dispatchers.IO) {
        podcastDao.updateSubscriptionStatus(podcastId, false)
    }

    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>> = podcastDao.getSubscribedPodcastsFlow()

    companion object {
        fun buildCandidateKeys(podcastId: String?, feedUrl: String?): List<String> {
            val results = LinkedHashSet<String>()
            val inputs = listOfNotNull(podcastId?.trim(), feedUrl?.trim()).filter { it.isNotBlank() }
            for (key in inputs) {
                results.add(key)
                if (key.contains("%")) {
                    try { results.add(java.net.URLDecoder.decode(key, "UTF-8")) } catch (_: Exception) {}
                }
                try { results.add(android.net.Uri.encode(key)) } catch (_: Exception) {}
                val stripped = key
                    .removePrefix("antennapod_local:")
                    .removePrefix("local://")
                    .removePrefix("local:")
                if (stripped != key && stripped.isNotBlank()) {
                    results.add(stripped)
                    results.add("antennapod_local:$stripped")
                    results.add("local://$stripped")
                    results.add("local:$stripped")
                    if (stripped.contains("%")) {
                        try { results.add(java.net.URLDecoder.decode(stripped, "UTF-8")) } catch (_: Exception) {}
                    }
                }
            }
            return results.toList()
        }
    }

    fun getPodcastById(podcastId: String): Flow<PodcastEntity?> = podcastDao.getPodcastFlow(podcastId)

    fun getPodcastByIdOrFeedUrl(podcastId: String, feedUrl: String): Flow<PodcastEntity?> {
        val candidates = buildCandidateKeys(podcastId, feedUrl)
        return if (candidates.isEmpty()) {
            podcastDao.getPodcastByIdOrFeedUrlFlow(podcastId, feedUrl)
        } else {
            podcastDao.findPodcastByCandidatesFlow(candidates)
        }
    }

    suspend fun getPodcastDirect(podcastId: String, feedUrl: String): PodcastEntity? = withContext(Dispatchers.IO) {
        val candidates = buildCandidateKeys(podcastId, feedUrl)
        if (candidates.isEmpty()) {
            podcastDao.getPodcastByIdOrFeedUrl(podcastId, feedUrl)
        } else {
            podcastDao.findPodcastByCandidates(candidates)
        }
    }

    suspend fun fetchAndStorePodcast(feedUrl: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        try {
            val parsedFeed = feedParser.fetchAndParseFeed(feedUrl)
            val existing = podcastDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl)
            val isSubscribed = existing?.isSubscribed ?: false
            val maxEpisodePubDate = parsedFeed.episodes.maxOfOrNull { it.pubDate } ?: 0L
            val now = System.currentTimeMillis()
            val subscribedAt = if (existing != null && existing.subscribedAt > 0L) {
                existing.subscribedAt
            } else {
                kotlin.math.max(now, maxEpisodePubDate)
            }
            val podcastEntity = parsedFeed.toPodcastEntity(
                isSubscribed = isSubscribed,
                subscribedAt = subscribedAt
            )
            val episodeEntities = parsedFeed.episodes.map { it.toEpisodeEntity(podcastEntity.id) }

            podcastDao.insertPodcast(podcastEntity)
            podcastDao.insertEpisodes(episodeEntities)

            Result.success(podcastEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>> {
        val candidates = buildCandidateKeys(podcastId, null)
        return if (candidates.isEmpty()) {
            podcastDao.getEpisodesForPodcastFlow(podcastId)
        } else {
            podcastDao.getEpisodesForCandidatesFlow(candidates)
        }
    }

    suspend fun getEpisodeById(episodeId: String): EpisodeEntity? = withContext(Dispatchers.IO) {
        podcastDao.getEpisodeById(episodeId)
    }

    suspend fun getPodcastByIdSync(podcastId: String): PodcastEntity? = withContext(Dispatchers.IO) {
        podcastDao.getPodcastById(podcastId)
    }

    suspend fun insertPodcast(podcast: PodcastEntity) = withContext(Dispatchers.IO) {
        podcastDao.insertPodcast(podcast)
    }

    suspend fun insertOrUpdateEpisode(episode: EpisodeEntity) = withContext(Dispatchers.IO) {
        podcastDao.insertOrUpdateEpisode(episode)
    }

    suspend fun getQueueList(): List<QueueEpisodeItem> = withContext(Dispatchers.IO) {
        podcastDao.getQueueList()
    }

    suspend fun getPlayedOrInProgressEpisodes(): List<EpisodeEntity> = withContext(Dispatchers.IO) {
        podcastDao.getPlayedOrInProgressEpisodes()
    }

    // ==========================================
    // NOVEDADES Y FAVORITOS
    // ==========================================

    fun getNewReleases(limit: Int = 100): Flow<List<EpisodeWithPodcast>> =
        podcastDao.getNewReleasesFlow(limit)

    fun getFavorites(): Flow<List<EpisodeWithPodcast>> =
        podcastDao.getFavoriteEpisodesFlow()

    suspend fun toggleFavorite(episodeId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        podcastDao.updateFavoriteStatus(episodeId, isFavorite)
    }

    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        podcastDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun markEpisodePlayed(episodeId: String, isPlayed: Boolean) = withContext(Dispatchers.IO) {
        podcastDao.markEpisodeAsPlayed(episodeId, isPlayed)
    }

    suspend fun markEpisodeAsPlayed(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.markAsPlayedAndRemoveFromQueue(episodeId)
    }

    suspend fun clearAllNewReleases() = withContext(Dispatchers.IO) {
        podcastDao.clearAllNewReleasesDirect()
    }

    suspend fun clearNewReleases(episodeIds: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        podcastDao.clearAllNewReleasesDirect()
    }

    // ==========================================
    // COLA DE REPRODUCCIÓN
    // ==========================================

    fun getQueue(): Flow<List<QueueEpisodeItem>> = podcastDao.getQueueFlow()

    fun getQueueEpisodes(): Flow<List<EpisodeEntity>> = podcastDao.getQueueEpisodesFlow()

    fun getQueueCount(): Flow<Int> = podcastDao.getQueueCountFlow()

    suspend fun addEpisodeToQueue(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.addEpisodeToQueueEnd(episodeId)
    }

    suspend fun addEpisodeToQueueTop(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.addEpisodeToQueueTop(episodeId)
    }

    suspend fun setCurrentPlaybackInQueue(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.setCurrentPlaybackInQueue(episodeId)
    }

    suspend fun removeEpisodeFromQueue(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.removeFromQueue(episodeId)
    }

    suspend fun reorderQueue(orderedEpisodeIds: List<String>) = withContext(Dispatchers.IO) {
        podcastDao.reorderQueue(orderedEpisodeIds)
    }

    suspend fun markEpisodeAsPlayedAndRemoveFromQueue(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.markAsPlayedAndRemoveFromQueue(episodeId)
    }

    suspend fun clearQueue() = withContext(Dispatchers.IO) {
        podcastDao.clearQueue()
    }

    suspend fun clearSubsequentQueue() = withContext(Dispatchers.IO) {
        podcastDao.clearSubsequentQueue()
    }

    // ==========================================
    // HISTORIAL Y ESTADÍSTICAS
    // ==========================================

    fun getListeningHistory(limit: Int = 100): Flow<List<HistoryEpisodeItem>> =
        podcastDao.getListeningHistoryFlow(limit).map { items ->
            items.distinctBy { it.episode.id }
        }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        podcastDao.clearHistory()
    }

    fun getPodcastStats(): Flow<List<PlaybackStatsEntity>> = podcastDao.getPodcastStatsFlow()

    fun getGlobalStats(): Flow<GlobalStats> = podcastDao.getGlobalStatsFlow()

    /**
     * Calcula y retorna el resumen anual "Wrapped" para un año específico (o histórico acumulado si year = 0).
     */
    suspend fun getWrappedStats(year: Int = 2026): WrappedData = withContext(Dispatchers.IO) {
        val periodStats = if (year > 0) {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startMs = calendar.timeInMillis
            calendar.apply {
                set(java.util.Calendar.MONTH, java.util.Calendar.DECEMBER)
                set(java.util.Calendar.DAY_OF_MONTH, 31)
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            val endMs = calendar.timeInMillis
            val historyStats = podcastDao.getWrappedStatsForPeriod(startMs, endMs)
            if (historyStats.isNotEmpty()) {
                historyStats
            } else {
                podcastDao.getAllTimePlaybackStatsWithArtwork()
            }
        } else {
            podcastDao.getAllTimePlaybackStatsWithArtwork()
        }

        val totalTimeMs = periodStats.sumOf { it.timeListenedMs }
        val totalCompleted = periodStats.sumOf { it.completedCount }
        val totalPodcasts = periodStats.size

        val topPodcasts = periodStats.take(10).map { stat ->
            val ratio = if (totalTimeMs > 0) {
                (stat.timeListenedMs.toFloat() / totalTimeMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            WrappedPodcastItem(
                podcastId = stat.podcastId,
                title = stat.podcastTitle,
                artworkUrl = stat.podcastArtworkUrl,
                timeListenedMs = stat.timeListenedMs,
                episodesCompleted = stat.completedCount,
                percentageOfTotal = ratio
            )
        }

        val top1Percentage = topPodcasts.firstOrNull()?.percentageOfTotal ?: 0f
        val (archetype, desc) = WrappedData.resolveArchetype(
            totalHours = totalTimeMs.toDouble() / 3600000.0,
            totalPodcasts = totalPodcasts,
            episodesCompleted = totalCompleted,
            topPercentage = top1Percentage
        )

        WrappedData(
            year = year,
            totalTimeListenedMs = totalTimeMs,
            totalEpisodesCompleted = totalCompleted,
            totalPodcastsTracked = totalPodcasts,
            topPodcasts = topPodcasts,
            archetype = archetype,
            archetypeDescription = desc
        )
    }

    suspend fun recordListeningSession(
        podcastId: String,
        podcastTitle: String,
        episodeId: String,
        listenedMs: Long,
        isCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        podcastDao.recordListenedDuration(
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            episodeId = episodeId,
            additionalDurationMs = listenedMs,
            isCompleted = isCompleted
        )
    }

    suspend fun importLocalFolder(
        context: android.content.Context,
        treeUri: android.net.Uri,
        title: String?,
        artworkUri: String?
    ): Result<PodcastEntity> = com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.importLocalFolder(
        context = context,
        treeUri = treeUri,
        customTitle = title,
        customArtworkUri = artworkUri,
        podcastDao = podcastDao
    )

    suspend fun rescanLocalFolder(
        context: android.content.Context,
        podcastId: String
    ): Int = com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.rescanLocalFolder(
        context = context,
        podcastId = podcastId,
        podcastDao = podcastDao
    )

    suspend fun reconnectLocalFolder(
        context: android.content.Context,
        podcastId: String,
        newTreeUri: android.net.Uri
    ): Result<Int> = com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.reconnectLocalFolder(
        context = context,
        podcastId = podcastId,
        newTreeUri = newTreeUri,
        podcastDao = podcastDao
    )

    // ==========================================
    // MARCADORES Y CLIPS (BOOKMARKS)
    // ==========================================

    fun getBookmarksForEpisode(episodeId: String): Flow<List<com.ferlagod.mispodcast.data.local.entities.BookmarkEntity>> =
        podcastDao.getBookmarksForEpisode(episodeId)

    fun getAllBookmarks(): Flow<List<com.ferlagod.mispodcast.data.local.entities.BookmarkEntity>> =
        podcastDao.getAllBookmarks()

    suspend fun addBookmark(bookmark: com.ferlagod.mispodcast.data.local.entities.BookmarkEntity) = withContext(Dispatchers.IO) {
        podcastDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmarkId: String) = withContext(Dispatchers.IO) {
        podcastDao.deleteBookmark(bookmarkId)
    }

    suspend fun getBookmarksForEpisodeSync(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.getBookmarksForEpisodeSync(episodeId)
    }
}

