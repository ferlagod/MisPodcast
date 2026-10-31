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

package com.ferlagod.mispodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ferlagod.mispodcast.data.local.entities.BookmarkEntity
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeOwner
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.local.model.GlobalStats
import com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueItemEntityWithEpisode
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    // ==========================================
    // PODCASTS
    // ==========================================

    @Upsert
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Upsert
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Query("SELECT * FROM podcasts WHERE id = :podcastId LIMIT 1")
    suspend fun getPodcastById(podcastId: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id = :podcastId LIMIT 1")
    fun getPodcastFlow(podcastId: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId OR feedUrl = :feedUrl LIMIT 1")
    fun getPodcastByIdOrFeedUrlFlow(podcastId: String, feedUrl: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId OR feedUrl = :feedUrl LIMIT 1")
    suspend fun getPodcastByIdOrFeedUrl(podcastId: String, feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id IN (:candidates) OR feedUrl IN (:candidates) OR localFolderPath IN (:candidates) LIMIT 1")
    suspend fun findPodcastByCandidates(candidates: List<String>): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id IN (:candidates) OR feedUrl IN (:candidates) OR localFolderPath IN (:candidates) LIMIT 1")
    fun findPodcastByCandidatesFlow(candidates: List<String>): Flow<PodcastEntity?>

    @Query("SELECT * FROM episodes WHERE podcastId IN (:candidates) ORDER BY pubDate DESC")
    fun getEpisodesForCandidatesFlow(candidates: List<String>): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM podcasts WHERE isLocalFolder = 1")
    suspend fun getAllLocalFolderPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE isLocalFolder = 1")
    fun getAllLocalFolderPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY subscribedAt DESC")
    fun getSubscribedPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY subscribedAt DESC")
    suspend fun getSubscribedPodcasts(): List<PodcastEntity>

    @Query("""
        UPDATE podcasts 
        SET isSubscribed = :isSubscribed, 
            subscribedAt = CASE 
                WHEN :isSubscribed = 1 AND isSubscribed = 0 THEN :now 
                ELSE subscribedAt 
            END,
            unsubscribedAt = CASE
                WHEN :isSubscribed = 0 THEN :now
                ELSE unsubscribedAt
            END
        WHERE id = :podcastId
    """)
    suspend fun updateSubscriptionStatus(
        podcastId: String, 
        isSubscribed: Boolean, 
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("""
        UPDATE podcasts 
        SET isSubscribed = :isSubscribed, 
            subscribedAt = :subscribedAt,
            unsubscribedAt = :unsubscribedAt
        WHERE id = :podcastId
    """)
    suspend fun updateSubscriptionWithTimestamps(
        podcastId: String,
        isSubscribed: Boolean,
        subscribedAt: Long,
        unsubscribedAt: Long
    ): Int

    /**
     * Obtiene podcasts tanto suscritos como desuscritos con lápida temporal para sincronización con Nextcloud.
     */
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 OR unsubscribedAt > 0")
    suspend fun getSyncablePodcasts(): List<PodcastEntity>

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: String): Int

    @Query("UPDATE podcasts SET httpEtag = :etag, httpLastModified = :lastModified, lastUpdated = :lastUpdated WHERE id = :podcastId")
    suspend fun updatePodcastCacheHeaders(
        podcastId: String,
        etag: String?,
        lastModified: String?,
        lastUpdated: Long = System.currentTimeMillis()
    ): Int

    @Query("""
        UPDATE podcasts 
        SET customSpeed = :customSpeed,
            skipIntroSeconds = :skipIntroSeconds,
            skipEndingSeconds = :skipEndingSeconds,
            autoDownloadEnabled = :autoDownloadEnabled,
            episodeSortOrder = :episodeSortOrder,
            autoEnqueueRule = :autoEnqueueRule,
            newEpisodeNotifications = :newEpisodeNotifications,
            autoDeleteRule = :autoDeleteRule
        WHERE id = :podcastId
    """)
    suspend fun updatePodcastSettings(
        podcastId: String,
        customSpeed: Float?,
        skipIntroSeconds: Int,
        skipEndingSeconds: Int,
        autoDownloadEnabled: Boolean?,
        episodeSortOrder: String,
        autoEnqueueRule: String = "DISABLED",
        newEpisodeNotifications: Boolean = true,
        autoDeleteRule: String = "DEFAULT"
    ): Int

    @Query("UPDATE podcasts SET autoEnqueueRule = :rule WHERE id = :podcastId")
    suspend fun updatePodcastAutoEnqueueRule(podcastId: String, rule: String): Int

    // ==========================================
    // EPISODIOS
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    /**
     * Inserta episodios ignorando duplicados por clave primaria o índice único.
     * Devuelve el ID de fila generado (> 0) para episodios nuevos, o -1L para los ya existentes.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodesIgnore(episodes: List<EpisodeEntity>): List<Long>

    @Upsert
    suspend fun insertOrReplaceEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT id, podcastId FROM episodes WHERE id IN (:ids)")
    suspend fun getEpisodeOwners(ids: List<String>): List<EpisodeOwner>

    @Query("SELECT id FROM episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodeIdsForPodcast(podcastId: String): List<String>

    @Query("SELECT audioUrl FROM episodes WHERE podcastId = :podcastId")
    suspend fun getAudioUrlsForPodcast(podcastId: String): List<String>

    /**
     * Actualiza los metadatos editoriales de un episodio ya guardado con los del feed, sin tocar
     * nunca el estado del usuario (posición, escuchado, favorito, descarga) ni la fecha de publicación.
     * Solo escribe si algo cambió, para no disparar invalidaciones de Room innecesarias.
     */
    @Query("""
        UPDATE episodes
        SET title = :title,
            description = :description,
            audioUrl = :audioUrl,
            imageUrl = :imageUrl,
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END,
            chaptersUrl = COALESCE(:chaptersUrl, chaptersUrl),
            transcriptUrl = COALESCE(:transcriptUrl, transcriptUrl),
            soundbites = COALESCE(:soundbites, soundbites)
        WHERE id = :id AND podcastId = :podcastId AND (
            title != :title OR description != :description OR audioUrl != :audioUrl OR imageUrl != :imageUrl
            OR (:duration > 0 AND duration != :duration)
            OR (:chaptersUrl IS NOT NULL AND IFNULL(chaptersUrl, '') != :chaptersUrl)
            OR (:transcriptUrl IS NOT NULL AND IFNULL(transcriptUrl, '') != :transcriptUrl)
            OR (:soundbites IS NOT NULL AND IFNULL(soundbites, '') != :soundbites)
        )
    """)
    suspend fun updateEpisodeFeedMetadata(
        id: String,
        podcastId: String,
        title: String,
        description: String,
        audioUrl: String,
        imageUrl: String,
        duration: Long,
        chaptersUrl: String?,
        transcriptUrl: String?,
        soundbites: String?
    ): Int

    /**
     * Si el guid de un episodio ya pertenece a otro podcast (feeds que usan guids como "1", "2"...),
     * le asigna un ID determinista con espacio de nombres para no descartarlo en silencio.
     */
    suspend fun resolveEpisodeIdCollisions(episodes: List<EpisodeEntity>): List<EpisodeEntity> {
        if (episodes.isEmpty()) return episodes
        val owners = HashMap<String, String>()
        episodes.map { it.id }.distinct().chunked(500).forEach { chunk ->
            getEpisodeOwners(chunk).forEach { owners[it.id] = it.podcastId }
        }
        return episodes.map { ep ->
            val owner = owners[ep.id]
            if (owner != null && owner != ep.podcastId) ep.copy(id = "${ep.podcastId}::${ep.id}") else ep
        }
    }

    /**
     * Guarda los episodios de un feed: inserta los nuevos y actualiza los metadatos de los existentes.
     * Omite duplicados del mismo audio guardados con otro ID (p. ej. importados de AntennaPod).
     *
     * @return los episodios realmente insertados como nuevos.
     */
    @Transaction
    suspend fun upsertFeedEpisodes(podcastId: String, parsed: List<EpisodeEntity>): List<EpisodeEntity> {
        if (parsed.isEmpty()) return emptyList()
        val clean = com.ferlagod.mispodcast.util.AudioTrackingSanitizer::cleanAudioUrl
        val existingIds = getEpisodeIdsForPodcast(podcastId).toHashSet()
        val existingUrls = getAudioUrlsForPodcast(podcastId).mapTo(HashSet()) { clean(it) }
        val candidates = resolveEpisodeIdCollisions(parsed)
            .filter { it.id in existingIds || clean(it.audioUrl) !in existingUrls }

        val results = insertEpisodesIgnore(candidates)
        val inserted = candidates.filterIndexed { index, _ -> results.getOrNull(index)?.let { it != -1L } == true }
        val insertedIds = inserted.mapTo(HashSet()) { it.id }
        candidates.filter { it.id !in insertedIds }.forEach { ep ->
            updateEpisodeFeedMetadata(
                id = ep.id,
                podcastId = ep.podcastId,
                title = ep.title,
                description = ep.description,
                audioUrl = ep.audioUrl,
                imageUrl = ep.imageUrl,
                duration = ep.duration,
                chaptersUrl = ep.chaptersUrl,
                transcriptUrl = ep.transcriptUrl,
                soundbites = ep.soundbites
            )
        }
        return inserted
    }

    @Upsert
    suspend fun insertOrUpdateEpisode(episode: EpisodeEntity)

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl LIMIT 1")
    suspend fun getEpisodeByAudioUrl(audioUrl: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    fun getEpisodeFlow(episodeId: String): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun getEpisodesForPodcastFlow(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    suspend fun getEpisodesForPodcast(podcastId: String): List<EpisodeEntity>

    // Actualizar progreso de escucha en tiempo real con marca temporal
    @Query("""
        UPDATE episodes 
        SET currentPositionMs = :positionMs, 
            isPlayed = CASE WHEN duration > 0 AND :positionMs >= duration - 3000 THEN 1 ELSE 0 END,
            positionUpdatedAt = :now
        WHERE id = :episodeId
    """)
    suspend fun updatePlaybackPosition(
        episodeId: String, 
        positionMs: Long, 
        now: Long = System.currentTimeMillis()
    ): Int

    // Marcar como reproducido / no reproducido con marca temporal
    @Query("""
        UPDATE episodes 
        SET isPlayed = :isPlayed, 
            currentPositionMs = CASE WHEN :isPlayed = 1 THEN duration ELSE 0 END,
            positionUpdatedAt = :now
        WHERE id = :episodeId
    """)
    suspend fun markEpisodeAsPlayed(
        episodeId: String, 
        isPlayed: Boolean, 
        now: Long = System.currentTimeMillis()
    ): Int

    // Actualizar estado completo de reproducción con marca temporal
    @Query("""
        UPDATE episodes 
        SET isPlayed = :isPlayed, 
            currentPositionMs = :positionMs,
            positionUpdatedAt = :now
        WHERE id = :episodeId
    """)
    suspend fun updateEpisodePlaybackState(
        episodeId: String, 
        isPlayed: Boolean, 
        positionMs: Long, 
        now: Long = System.currentTimeMillis()
    ): Int

    // Obtener episodios con progreso de reproducción o escuchados
    @Query("SELECT * FROM episodes WHERE isPlayed = 1 OR currentPositionMs > 0")
    suspend fun getPlayedOrInProgressEpisodes(): List<EpisodeEntity>

    // Marcar como favorito
    @Query("UPDATE episodes SET isFavorite = :isFavorite WHERE id = :episodeId")
    suspend fun updateFavoriteStatus(episodeId: String, isFavorite: Boolean): Int

    // Actualizar estado de descarga
    @Query("""
        UPDATE episodes 
        SET isDownloaded = :isDownloaded, 
            localFilePath = :localPath,
            fileSizeBytes = CASE WHEN :fileSizeBytes > 0 THEN :fileSizeBytes ELSE fileSizeBytes END
        WHERE id = :episodeId
    """)
    suspend fun updateDownloadStatus(
        episodeId: String, 
        isDownloaded: Boolean, 
        localPath: String?,
        fileSizeBytes: Long = 0L
    ): Int

    // ==========================================
    // NOVEDADES
    // ==========================================

    @Query("""
        SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl 
        FROM episodes e 
        INNER JOIN podcasts p ON e.podcastId = p.id 
        WHERE p.isSubscribed = 1 
          AND e.isPlayed = 0 
          AND e.isDismissedFromNewReleases = 0
          AND (
              p.isLocalFolder = 1 
              OR p.subscribedAt <= 0 
              OR e.pubDate >= p.subscribedAt
          )
        ORDER BY e.pubDate DESC 
        LIMIT :limit
    """)
    fun getNewReleasesFlow(limit: Int = 100): Flow<List<EpisodeWithPodcast>>

    @Query("""
        SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl 
        FROM episodes e 
        INNER JOIN podcasts p ON e.podcastId = p.id 
        WHERE p.isSubscribed = 1 
          AND e.isPlayed = 0 
          AND e.isDismissedFromNewReleases = 0
          AND (
              p.isLocalFolder = 1 
              OR p.subscribedAt <= 0 
              OR e.pubDate >= p.subscribedAt
          )
        ORDER BY e.pubDate DESC 
        LIMIT :limit
    """)
    suspend fun getNewReleasesList(limit: Int = 100): List<EpisodeWithPodcast>

    @Query("""
        SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl 
        FROM episodes e 
        INNER JOIN podcasts p ON e.podcastId = p.id 
        WHERE e.isDownloaded = 1
        ORDER BY e.pubDate DESC
    """)
    suspend fun getDownloadedEpisodesList(): List<EpisodeWithPodcast>

    @Query("""
        UPDATE episodes 
        SET isDismissedFromNewReleases = 1,
            isPlayed = CASE 
                WHEN id IN (SELECT episodeId FROM queue_items) THEN isPlayed 
                ELSE 1 
            END,
            currentPositionMs = CASE 
                WHEN id IN (SELECT episodeId FROM queue_items) THEN currentPositionMs 
                ELSE duration 
            END 
        WHERE id IN (
            SELECT e.id 
            FROM episodes e 
            INNER JOIN podcasts p ON e.podcastId = p.id 
            WHERE p.isSubscribed = 1 
              AND e.isPlayed = 0 
              AND e.isDismissedFromNewReleases = 0
              AND (
                  p.isLocalFolder = 1 
                  OR p.subscribedAt <= 0 
                  OR e.pubDate >= p.subscribedAt
              )
        )
    """)
    /**
     * Limpia la pestaña Novedades:
     * - Todos los episodios en Novedades quedan marcados como descartados de dicha pestaña (isDismissedFromNewReleases = 1).
     * - Los episodios que el usuario tiene en la cola de reproducción permanecen intactos (no se borran de la cola,
     *   no se marcan como reproducidos y conservan su posición actual).
     * - Los episodios que NO están en la cola se marcan como reproducidos con posición igual a su duración.
     */
    suspend fun markAllNewReleasesAsPlayedDirect(): Int

    @Transaction
    suspend fun clearAllNewReleasesDirect() {
        markAllNewReleasesAsPlayedDirect()
    }

    @Transaction
    suspend fun markAllNewReleasesAsPlayed(episodeIds: List<String>) {
        clearAllNewReleasesDirect()
    }

    // ==========================================
    // FAVORITOS
    // ==========================================

    @Query("""
        SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl 
        FROM episodes e 
        INNER JOIN podcasts p ON e.podcastId = p.id 
        WHERE e.isFavorite = 1 
        ORDER BY e.pubDate DESC
    """)
    fun getFavoriteEpisodesFlow(): Flow<List<EpisodeWithPodcast>>

    // ==========================================
    // COLA DE REPRODUCCIÓN (El actual SIEMPRE en posición 0)
    // ==========================================

    @Query("""
        SELECT q.id AS queueId, q.positionIndex, q.isCurrentlyPlaying, q.addedAt,
               COALESCE(p.title, '') AS podcastTitle, 
               COALESCE(p.artworkUrl, '') AS podcastArtworkUrl, 
               e.*
        FROM queue_items q
        INNER JOIN episodes e ON q.episodeId = e.id
        LEFT JOIN podcasts p ON e.podcastId = p.id
        ORDER BY q.positionIndex ASC
    """)
    fun getQueueFlow(): Flow<List<QueueItemEntityWithEpisode>>

    @Query("""
        SELECT q.id AS queueId, q.positionIndex, q.isCurrentlyPlaying, q.addedAt,
               COALESCE(p.title, '') AS podcastTitle, 
               COALESCE(p.artworkUrl, '') AS podcastArtworkUrl, 
               e.*
        FROM queue_items q
        INNER JOIN episodes e ON q.episodeId = e.id
        LEFT JOIN podcasts p ON e.podcastId = p.id
        ORDER BY q.positionIndex ASC
    """)
    suspend fun getQueueList(): List<QueueItemEntityWithEpisode>

    @Query("""
        SELECT e.*
        FROM queue_items q
        INNER JOIN episodes e ON q.episodeId = e.id
        ORDER BY q.positionIndex ASC
    """)
    fun getQueueEpisodesFlow(): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: QueueItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(queueItems: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items WHERE episodeId = :episodeId")
    suspend fun removeFromQueue(episodeId: String): Int

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue(): Int

    @Query("SELECT COUNT(*) FROM queue_items")
    fun getQueueCountFlow(): Flow<Int>

    @Query("SELECT MAX(positionIndex) FROM queue_items")
    suspend fun getMaxQueuePosition(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM queue_items WHERE episodeId = :episodeId)")
    suspend fun isEpisodeInQueue(episodeId: String): Boolean

    @Query("SELECT episodeId FROM queue_items WHERE episodeId != :excludeEpisodeId ORDER BY positionIndex ASC")
    suspend fun getQueueEpisodeIdsExcept(excludeEpisodeId: String): List<String>

    @Query("SELECT episodeId FROM queue_items ORDER BY positionIndex ASC")
    suspend fun getOrderedQueueEpisodeIds(): List<String>

    @Query("SELECT * FROM queue_items ORDER BY positionIndex ASC")
    suspend fun getQueueItemsSync(): List<QueueItemEntity>

    @Query("UPDATE queue_items SET isCurrentlyPlaying = 0")
    suspend fun clearCurrentlyPlayingFlags(): Int

    @Query("UPDATE queue_items SET isCurrentlyPlaying = 1 WHERE episodeId = :episodeId")
    suspend fun setEpisodeAsCurrentlyPlaying(episodeId: String): Int

    @Query("UPDATE queue_items SET positionIndex = :newPosition, isCurrentlyPlaying = :isPlaying WHERE episodeId = :episodeId")
    suspend fun updateQueueItemPositionAndPlaying(episodeId: String, newPosition: Int, isPlaying: Boolean): Int

    @Query("UPDATE queue_items SET positionIndex = positionIndex + 1, isCurrentlyPlaying = 0")
    suspend fun shiftAllQueueIndices(): Int

    @Query("DELETE FROM queue_items WHERE isCurrentlyPlaying = 0")
    suspend fun clearSubsequentQueue(): Int

    @Query("UPDATE queue_items SET positionIndex = :newPosition WHERE episodeId = :episodeId")
    suspend fun updateQueuePosition(episodeId: String, newPosition: Int): Int

    @Transaction
    suspend fun reindexQueue() {
        val ids = getOrderedQueueEpisodeIds()
        ids.forEachIndexed { index, id ->
            updateQueuePosition(id, index)
        }
    }

    /**
     * Garantía del episodio actual en la posición 0:
     * 1. Elimine ese episodeId de cualquier posición anterior de la tabla queue_items.
     * 2. Desplace todos los demás índices positionIndex + 1.
     * 3. Inserte el episodio actual con positionIndex = 0.
     */
    @Transaction
    suspend fun setCurrentPlaybackInQueue(episodeId: String) {
        val inQueue = isEpisodeInQueue(episodeId)
        if (inQueue) {
            // El episodio ya pertenece a la cola del usuario: no alteramos los índices de posición,
            // únicamente actualizamos las banderas de reproducción activa (isCurrentlyPlaying).
            clearCurrentlyPlayingFlags()
            setEpisodeAsCurrentlyPlaying(episodeId)
        } else {
            // No estaba en la cola: lo insertamos al inicio con positionIndex = 0 como activo
            removeFromQueue(episodeId)
            shiftAllQueueIndices()
            insertQueueItem(
                QueueItemEntity(
                    episodeId = episodeId,
                    positionIndex = 0,
                    isCurrentlyPlaying = true,
                    addedAt = System.currentTimeMillis()
                )
            )
            reindexQueue()
            setEpisodeAsCurrentlyPlaying(episodeId)
        }
    }

    @Transaction
    suspend fun addEpisodeToQueueTop(episodeId: String) {
        if (!isEpisodeInQueue(episodeId)) {
            val queue = getQueueItemsSync()
            val currentlyPlaying = queue.firstOrNull { it.isCurrentlyPlaying }
            val insertIndex = if (currentlyPlaying != null && queue.isNotEmpty()) 1 else 0

            for (item in queue) {
                if (item.positionIndex >= insertIndex) {
                    updateQueuePosition(item.episodeId, item.positionIndex + 1)
                }
            }
            insertQueueItem(
                QueueItemEntity(
                    episodeId = episodeId,
                    positionIndex = insertIndex,
                    isCurrentlyPlaying = false,
                    addedAt = System.currentTimeMillis()
                )
            )
            reindexQueue()
        }
    }

    @Transaction
    suspend fun addEpisodeToQueueEnd(episodeId: String) {
        if (!isEpisodeInQueue(episodeId)) {
            val maxPos = getMaxQueuePosition()
            val nextPos = if (maxPos == null) 0 else maxPos + 1
            insertQueueItem(
                QueueItemEntity(
                    episodeId = episodeId,
                    positionIndex = nextPos,
                    isCurrentlyPlaying = false,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @Transaction
    suspend fun reorderQueue(orderedEpisodeIds: List<String>) {
        // Solo cambia el orden: la marca de reproducción activa la gestiona setCurrentPlaybackInQueue
        orderedEpisodeIds.forEachIndexed { index, episodeId ->
            updateQueuePosition(episodeId, index)
        }
    }

    @Transaction
    suspend fun markAsPlayedAndRemoveFromQueue(episodeId: String) {
        markEpisodeAsPlayed(episodeId, true)
        removeFromQueue(episodeId)
        reindexQueue()
        val episode = getEpisodeById(episodeId)
        if (episode != null) {
            val latestHistory = getLatestListeningHistoryEntry(episodeId)
            val now = System.currentTimeMillis()
            if (latestHistory != null && (now - latestHistory.listenedAt) < 30 * 60 * 1000L) {
                updateListeningHistory(
                    latestHistory.copy(
                        listenedAt = now,
                        completed = true
                    )
                )
            } else {
                insertListeningHistory(
                    ListeningHistoryEntity(
                        episodeId = episodeId,
                        podcastId = episode.podcastId,
                        listenedAt = now,
                        durationListenedMs = episode.duration,
                        completed = true
                    )
                )
            }
        }
    }

    // ==========================================
    // HISTORIAL DE ESCUCHA
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningHistory(history: ListeningHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningHistoryList(items: List<ListeningHistoryEntity>)

    @Update
    suspend fun updateListeningHistory(history: ListeningHistoryEntity)

    @Query("SELECT * FROM listening_history WHERE episodeId = :episodeId ORDER BY listenedAt DESC, id DESC LIMIT 1")
    suspend fun getLatestListeningHistoryEntry(episodeId: String): ListeningHistoryEntity?

    @Query("""
        SELECT h.id AS historyId,
               latest.maxListenedAt AS listenedAt,
               h.durationListenedMs,
               (CASE WHEN MAX(h.completed) = 1 OR e.isPlayed = 1 THEN 1 ELSE 0 END) AS completed,
               p.title AS podcastTitle,
               p.artworkUrl AS podcastArtworkUrl,
               e.*
        FROM listening_history h
        INNER JOIN episodes e ON h.episodeId = e.id
        INNER JOIN podcasts p ON e.podcastId = p.id
        INNER JOIN (
            SELECT episodeId, MAX(listenedAt) AS maxListenedAt
            FROM listening_history
            GROUP BY episodeId
        ) latest ON h.episodeId = latest.episodeId AND h.listenedAt = latest.maxListenedAt
        GROUP BY e.id
        ORDER BY listenedAt DESC
        LIMIT :limit
    """)
    fun getListeningHistoryFlow(limit: Int = 100): Flow<List<HistoryEpisodeItem>>

    @Query("""
        SELECT h.id AS historyId,
               latest.maxListenedAt AS listenedAt,
               h.durationListenedMs,
               (CASE WHEN MAX(h.completed) = 1 OR e.isPlayed = 1 THEN 1 ELSE 0 END) AS completed,
               p.title AS podcastTitle,
               p.artworkUrl AS podcastArtworkUrl,
               e.*
        FROM listening_history h
        INNER JOIN episodes e ON h.episodeId = e.id
        INNER JOIN podcasts p ON e.podcastId = p.id
        INNER JOIN (
            SELECT episodeId, MAX(listenedAt) AS maxListenedAt
            FROM listening_history
            GROUP BY episodeId
        ) latest ON h.episodeId = latest.episodeId AND h.listenedAt = latest.maxListenedAt
        GROUP BY e.id
        ORDER BY listenedAt DESC
        LIMIT :limit
    """)
    suspend fun getListeningHistoryList(limit: Int = 50): List<HistoryEpisodeItem>

    @Query("""
        SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl 
        FROM episodes e 
        INNER JOIN podcasts p ON e.podcastId = p.id 
        WHERE e.title LIKE '%' || :query || '%' OR p.title LIKE '%' || :query || '%' OR p.author LIKE '%' || :query || '%'
        ORDER BY e.pubDate DESC 
        LIMIT :limit
    """)
    suspend fun searchLocalEpisodes(query: String, limit: Int = 30): List<EpisodeWithPodcast>

    @Query("""
        SELECT * FROM podcasts 
        WHERE isSubscribed = 1 AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
        LIMIT :limit
    """)
    suspend fun searchLocalPodcasts(query: String, limit: Int = 20): List<PodcastEntity>

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM listening_history WHERE episodeId = :episodeId)")
    suspend fun hasHistoryForEpisode(episodeId: String): Boolean

    /**
     * Obtiene la marca de tiempo más reciente en que se reprodujo un episodio específico.
     */
    @Query("SELECT MAX(listenedAt) FROM listening_history WHERE episodeId = :episodeId")
    suspend fun getLatestListenedAtForEpisode(episodeId: String): Long?

    // ==========================================
    // ESTADÍSTICAS DE REPRODUCCIÓN
    // ==========================================

    @Query("SELECT * FROM playback_stats ORDER BY totalTimeListenedMs DESC")
    fun getPodcastStatsFlow(): Flow<List<PlaybackStatsEntity>>

    @Query("SELECT * FROM playback_stats ORDER BY totalTimeListenedMs DESC")
    suspend fun getPodcastStatsList(): List<PlaybackStatsEntity>

    @Query("""
        SELECT p.id AS podcastId, p.title AS podcastTitle, COALESCE(p.artworkUrl, '') AS podcastArtworkUrl,
               COALESCE(SUM(h.durationListenedMs), 0) AS timeListenedMs,
               COALESCE(SUM(CASE WHEN h.completed = 1 THEN 1 ELSE 0 END), 0) AS completedCount
        FROM listening_history h
        INNER JOIN episodes e ON h.episodeId = e.id
        INNER JOIN podcasts p ON e.podcastId = p.id
        WHERE h.listenedAt >= :startTimestamp AND h.listenedAt <= :endTimestamp
        GROUP BY p.id
        ORDER BY timeListenedMs DESC
    """)
    suspend fun getWrappedStatsForPeriod(startTimestamp: Long, endTimestamp: Long): List<PodcastPeriodStat>

    @Query("""
        SELECT s.podcastId, s.podcastTitle, COALESCE(p.artworkUrl, '') AS podcastArtworkUrl,
               s.totalTimeListenedMs AS timeListenedMs, s.episodesCompletedCount AS completedCount
        FROM playback_stats s
        LEFT JOIN podcasts p ON s.podcastId = p.id
        ORDER BY s.totalTimeListenedMs DESC
    """)
    suspend fun getAllTimePlaybackStatsWithArtwork(): List<PodcastPeriodStat>

    @Query("""
        SELECT COALESCE(SUM(totalTimeListenedMs), 0) AS totalTimeListenedMs,
               COALESCE(SUM(episodesCompletedCount), 0) AS totalEpisodesCompleted,
               COUNT(podcastId) AS totalPodcastsTracked
        FROM playback_stats
    """)
    fun getGlobalStatsFlow(): Flow<GlobalStats>

    @Upsert
    suspend fun upsertPlaybackStats(stats: PlaybackStatsEntity)

    @Upsert
    suspend fun upsertPlaybackStatsList(stats: List<PlaybackStatsEntity>)

    @Query("SELECT * FROM playback_stats WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getStatsForPodcast(podcastId: String): PlaybackStatsEntity?

    @Transaction
    suspend fun recordListenedDuration(
        podcastId: String,
        podcastTitle: String,
        episodeId: String,
        additionalDurationMs: Long,
        isCompleted: Boolean
    ) {
        // 1. Guardar o actualizar entrada de historial para la sesión
        val latestHistory = getLatestListeningHistoryEntry(episodeId)
        val now = System.currentTimeMillis()
        if (latestHistory != null && (now - latestHistory.listenedAt) < 30 * 60 * 1000L) {
            updateListeningHistory(
                latestHistory.copy(
                    listenedAt = now,
                    durationListenedMs = latestHistory.durationListenedMs + additionalDurationMs,
                    completed = isCompleted || latestHistory.completed
                )
            )
        } else {
            insertListeningHistory(
                ListeningHistoryEntity(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    listenedAt = now,
                    durationListenedMs = additionalDurationMs,
                    completed = isCompleted
                )
            )
        }

        // 2. Acumular estadísticas del podcast
        val existing = getStatsForPodcast(podcastId)
        val newTotalTime = (existing?.totalTimeListenedMs ?: 0L) + additionalDurationMs
        val newCompletedCount = (existing?.episodesCompletedCount ?: 0) + (if (isCompleted) 1 else 0)
        upsertPlaybackStats(
            PlaybackStatsEntity(
                podcastId = podcastId,
                podcastTitle = podcastTitle,
                totalTimeListenedMs = newTotalTime,
                episodesCompletedCount = newCompletedCount,
                lastListenedAt = now
            )
        )
    }

    // ==========================================
    // MARCADORES Y CLIPS (BOOKMARKS)
    // ==========================================

    @Query("SELECT * FROM bookmarks WHERE episodeId = :episodeId ORDER BY positionMs ASC")
    fun getBookmarksForEpisode(episodeId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE episodeId = :episodeId ORDER BY positionMs ASC")
    suspend fun getBookmarksForEpisodeSync(episodeId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: String): Int
}

/**
 * DTO para representar el tiempo y reproducciones de un podcast dentro de un periodo (ej: Wrapped anual).
 */
data class PodcastPeriodStat(
    val podcastId: String,
    val podcastTitle: String,
    val podcastArtworkUrl: String,
    val timeListenedMs: Long,
    val completedCount: Int
)
