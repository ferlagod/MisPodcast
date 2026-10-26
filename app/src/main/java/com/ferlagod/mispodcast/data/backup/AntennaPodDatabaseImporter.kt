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

package com.ferlagod.mispodcast.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Importador especializado en bases de datos exportadas desde AntennaPod (.db o descomprimidas de .zip).
 * Extrae y migra al esquema de MisPodcast:
 * 1. Podcasts seguidos / suscripciones (tabla Feeds).
 * 2. Episodios y su estado de reproducción (tabla FeedItems + FeedMedia):
 *    - Episodios completados (read = 1 o playback_completion_date > 0).
 *    - Posición exacta de reproducción para episodios a medio escuchar (position).
 *    - Favoritos (tabla Favorites).
 * 3. Cola de reproducción con su orden (tabla Queue).
 * 4. Historial de reproducción (listening_history).
 * 5. Estadísticas de escucha globales y por podcast (playback_stats).
 */
object AntennaPodDatabaseImporter {

    private data class PodcastStatsAccumulator(
        val podcastTitle: String,
        var totalTimeMs: Long = 0L,
        var completedCount: Int = 0,
        var lastListenedAt: Long = 0L
    )

    /**
     * Comprueba si el archivo SQLite contiene las tablas características de AntennaPod.
     */
    fun isAntennaPodDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            hasTable(db, "Feeds") && hasTable(db, "FeedItems")
        } catch (e: Exception) {
            false
        } finally {
            try { db?.close() } catch (ignored: Exception) {}
        }
    }

    private fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        return try {
            db.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=? COLLATE NOCASE",
                arrayOf(tableName)
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTableColumns(db: SQLiteDatabase, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx != -1) {
                        columns.add(cursor.getString(nameIdx).lowercase())
                    }
                }
            }
        } catch (e: Exception) {
            // Ignorar y devolver las columnas encontradas
        }
        return columns
    }

    /**
     * Importa toda la información de la base de datos SQLite de AntennaPod a MisPodcast.
     */
    suspend fun importFromSqliteFile(
        context: Context,
        dbFile: File,
        database: PodcastDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        var sqliteDb: SQLiteDatabase? = null
        try {
            sqliteDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)

            if (!hasTable(sqliteDb, "Feeds") || !hasTable(sqliteDb, "FeedItems")) {
                return@withContext Result.failure(
                    IllegalArgumentException("El archivo no contiene las tablas de suscripciones de AntennaPod (Feeds, FeedItems)")
                )
            }

            // 1. Extraer Podcasts (Feeds)
            val feedIdToPodcastMap = mutableMapOf<Long, PodcastEntity>()
            val podcastsToInsert = mutableListOf<PodcastEntity>()

            sqliteDb.rawQuery("SELECT * FROM Feeds", null).use { cursor ->
                val idIdx = cursor.getColumnIndex("id")
                val downloadUrlIdx = cursor.getColumnIndex("download_url")
                val fileUrlIdx = cursor.getColumnIndex("file_url")
                val titleIdx = cursor.getColumnIndex("title")
                val customTitleIdx = cursor.getColumnIndex("custom_title")
                val authorIdx = cursor.getColumnIndex("author")
                val descIdx = cursor.getColumnIndex("description")
                val imageIdx = cursor.getColumnIndex("image_url")
                val linkIdx = cursor.getColumnIndex("link")
                val stateIdx = cursor.getColumnIndex("state")

                while (cursor.moveToNext()) {
                    val apFeedId = if (idIdx != -1) cursor.getLong(idIdx) else continue
                    val downloadUrl = if (downloadUrlIdx != -1) cursor.getString(downloadUrlIdx) else null
                    val fileUrl = if (fileUrlIdx != -1) cursor.getString(fileUrlIdx) else null
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) else null
                    val customTitle = if (customTitleIdx != -1) cursor.getString(customTitleIdx) else null
                    val author = if (authorIdx != -1) cursor.getString(authorIdx) else null
                    val desc = if (descIdx != -1) cursor.getString(descIdx) else null
                    val imageUrl = if (imageIdx != -1) cursor.getString(imageIdx) else null
                    val link = if (linkIdx != -1) cursor.getString(linkIdx) else null
                    val state = if (stateIdx != -1 && !cursor.isNull(stateIdx)) cursor.getInt(stateIdx) else 0

                    val finalFeedUrl = downloadUrl?.takeIf { it.isNotBlank() }
                        ?: fileUrl?.takeIf { it.isNotBlank() }
                        ?: "antennapod_feed_$apFeedId"

                    val finalTitle = customTitle?.takeIf { it.isNotBlank() }
                        ?: title?.takeIf { it.isNotBlank() }
                        ?: "Podcast"

                    // En AntennaPod: state = 0 (STATE_SUBSCRIBED), state = 1 (desuscrito)
                    val isSubscribed = (state == 0)

                    val hasHttpUrl = (downloadUrl?.startsWith("http://", ignoreCase = true) == true ||
                            downloadUrl?.startsWith("https://", ignoreCase = true) == true ||
                            finalFeedUrl.startsWith("http://", ignoreCase = true) ||
                            finalFeedUrl.startsWith("https://", ignoreCase = true))

                    val isLocalFeed = !hasHttpUrl && (
                            finalFeedUrl.startsWith("antennapod_local:") ||
                            finalFeedUrl.startsWith("local:") ||
                            finalFeedUrl.startsWith("local://") ||
                            finalFeedUrl.startsWith("content:") ||
                            finalFeedUrl.startsWith("file:") ||
                            (downloadUrl != null && (
                                downloadUrl.startsWith("antennapod_local:") ||
                                downloadUrl.startsWith("local:") ||
                                downloadUrl.startsWith("local://") ||
                                downloadUrl.startsWith("content:") ||
                                downloadUrl.startsWith("file:")
                            )) ||
                            (fileUrl != null && !fileUrl.contains("/de.danoeh.antennapod/files/cache/") && fileUrl.startsWith("/"))
                    )

                    val rawPath = downloadUrl ?: fileUrl ?: finalFeedUrl
                    val cleanLocalPath = rawPath
                        .removePrefix("antennapod_local:")
                        .removePrefix("local://")
                        .removePrefix("local:")

                    val localFolderPath = if (isLocalFeed) {
                        cleanLocalPath
                    } else {
                        null
                    }

                    val podcastEntity = PodcastEntity(
                        id = finalFeedUrl,
                        title = finalTitle,
                        author = author?.takeIf { it.isNotBlank() } ?: (if (isLocalFeed) "Carpeta Local" else "Autor desconocido"),
                        description = desc.orEmpty(),
                        feedUrl = finalFeedUrl,
                        artworkUrl = imageUrl.orEmpty(),
                        isSubscribed = isSubscribed,
                        subscribedAt = System.currentTimeMillis(),
                        lastUpdated = System.currentTimeMillis(),
                        category = if (isLocalFeed) "Archivos Locales" else "General",
                        link = link.orEmpty(),
                        isLocalFolder = isLocalFeed,
                        localFolderPath = localFolderPath
                    )

                    feedIdToPodcastMap[apFeedId] = podcastEntity
                    podcastsToInsert.add(podcastEntity)
                }
            }

            // 2. Extraer Favoritos si existe la tabla Favorites
            val favoriteItemIds = mutableSetOf<Long>()
            if (hasTable(sqliteDb, "Favorites")) {
                try {
                    sqliteDb.rawQuery("SELECT feeditem FROM Favorites", null).use { cursor ->
                        val itemIdx = cursor.getColumnIndex("feeditem")
                        if (itemIdx != -1) {
                            while (cursor.moveToNext()) {
                                if (!cursor.isNull(itemIdx)) {
                                    favoriteItemIds.add(cursor.getLong(itemIdx))
                                }
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // 3. Extraer Cola de Reproducción si existe la tabla Queue
            val queueFeedItemIds = mutableListOf<Long>()
            if (hasTable(sqliteDb, "Queue")) {
                try {
                    sqliteDb.rawQuery("SELECT feeditem FROM Queue ORDER BY id ASC", null).use { cursor ->
                        val itemIdx = cursor.getColumnIndex("feeditem")
                        if (itemIdx != -1) {
                            while (cursor.moveToNext()) {
                                if (!cursor.isNull(itemIdx)) {
                                    queueFeedItemIds.add(cursor.getLong(itemIdx))
                                }
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // 4. Preparar consulta flexible para FeedItems y FeedMedia según columnas existentes
            val itemCols = getTableColumns(sqliteDb, "FeedItems")
            val hasFeedMedia = hasTable(sqliteDb, "FeedMedia")
            val mediaCols = if (hasFeedMedia) getTableColumns(sqliteDb, "FeedMedia") else emptySet()

            val titleExpr = if ("title" in itemCols) "i.title" else "''"
            val pubDateExpr = if ("pubdate" in itemCols) "i.pubDate" else "0"
            val readExpr = if ("read" in itemCols) "i.read" else "0"
            val linkExpr = if ("link" in itemCols) "i.link" else "''"
            val descExpr = if ("description" in itemCols) "i.description" else "''"
            val guidExpr = if ("item_identifier" in itemCols) "i.item_identifier" else "''"
            val imageExpr = if ("image_url" in itemCols) "i.image_url" else "''"

            val durationExpr = if ("duration" in mediaCols) "m.duration" else "0"
            val positionExpr = if ("position" in mediaCols) "m.position" else "0"
            val urlExpr = if ("download_url" in mediaCols) "m.download_url" else "''"
            val sizeExpr = if ("filesize" in mediaCols) "m.filesize" else "0"
            val completionExpr = if ("playback_completion_date" in mediaCols) "m.playback_completion_date" else "0"
            val playedExpr = if ("played_duration" in mediaCols) "m.played_duration" else "0"
            val lastPlayedExpr = if ("last_played_time" in mediaCols) "m.last_played_time" else "0"

            val querySql = """
                SELECT 
                    i.id AS i_id,
                    i.feed AS i_feed,
                    $titleExpr AS i_title,
                    $pubDateExpr AS i_pubDate,
                    $readExpr AS i_read,
                    $linkExpr AS i_link,
                    $descExpr AS i_desc,
                    $guidExpr AS i_guid,
                    $imageExpr AS i_image,
                    $durationExpr AS m_duration,
                    $positionExpr AS m_position,
                    $urlExpr AS m_url,
                    $sizeExpr AS m_size,
                    $completionExpr AS m_completion,
                    $playedExpr AS m_played,
                    $lastPlayedExpr AS m_last_played
                FROM FeedItems i
                ${if (hasFeedMedia) "LEFT JOIN FeedMedia m ON (m.feeditem = i.id OR i.media = m.id)" else ""}
            """.trimIndent()

            val episodesToInsert = mutableListOf<EpisodeEntity>()
            val historyToInsert = mutableListOf<ListeningHistoryEntity>()
            val apItemIdToEpisodeId = mutableMapOf<Long, String>()
            val statsMap = mutableMapOf<String, PodcastStatsAccumulator>()

            sqliteDb.rawQuery(querySql, null).use { cursor ->
                val idIdx = cursor.getColumnIndex("i_id")
                val feedIdx = cursor.getColumnIndex("i_feed")
                val epTitleIdx = cursor.getColumnIndex("i_title")
                val pubDateIdx = cursor.getColumnIndex("i_pubDate")
                val readIdx = cursor.getColumnIndex("i_read")
                val linkIdx = cursor.getColumnIndex("i_link")
                val descIdx = cursor.getColumnIndex("i_desc")
                val guidIdx = cursor.getColumnIndex("i_guid")
                val imageIdx = cursor.getColumnIndex("i_image")
                val durIdx = cursor.getColumnIndex("m_duration")
                val posIdx = cursor.getColumnIndex("m_position")
                val urlIdx = cursor.getColumnIndex("m_url")
                val sizeIdx = cursor.getColumnIndex("m_size")
                val compIdx = cursor.getColumnIndex("m_completion")
                val playedDurIdx = cursor.getColumnIndex("m_played")
                val lastPlayedIdx = cursor.getColumnIndex("m_last_played")

                while (cursor.moveToNext()) {
                    val apItemId = if (idIdx != -1) cursor.getLong(idIdx) else continue
                    val apFeedId = if (feedIdx != -1) cursor.getLong(feedIdx) else continue
                    val podcast = feedIdToPodcastMap[apFeedId] ?: continue

                    val epTitle = if (epTitleIdx != -1) cursor.getString(epTitleIdx) else null
                    val rawPubDate = if (pubDateIdx != -1) cursor.getLong(pubDateIdx) else 0L
                    val readStatus = if (readIdx != -1) cursor.getInt(readIdx) else 0
                    val guid = if (guidIdx != -1) cursor.getString(guidIdx)?.trim().orEmpty() else ""
                    val audioUrl = if (urlIdx != -1) cursor.getString(urlIdx)?.trim().orEmpty() else ""
                    val epImage = if (imageIdx != -1) cursor.getString(imageIdx) else null
                    val epDesc = if (descIdx != -1) cursor.getString(descIdx) else null

                    val duration = if (durIdx != -1) cursor.getLong(durIdx).coerceAtLeast(0L) else 0L
                    val position = if (posIdx != -1) cursor.getLong(posIdx).coerceAtLeast(0L) else 0L
                    val fileSize = if (sizeIdx != -1) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
                    val completionDate = if (compIdx != -1) cursor.getLong(compIdx).coerceAtLeast(0L) else 0L
                    val playedDuration = if (playedDurIdx != -1) cursor.getLong(playedDurIdx).coerceAtLeast(0L) else 0L
                    val lastPlayedTime = if (lastPlayedIdx != -1) cursor.getLong(lastPlayedIdx).coerceAtLeast(0L) else 0L

                    // ID coherente con el parser RSS de MisPodcast
                    val episodeId = when {
                        guid.isNotBlank() -> guid
                        audioUrl.isNotBlank() -> "${podcast.id}_${audioUrl.hashCode()}"
                        else -> "antennapod_item_$apItemId"
                    }
                    apItemIdToEpisodeId[apItemId] = episodeId

                    // Timestamp pubDate normalizado (segundos vs milisegundos)
                    val pubDate = when {
                        rawPubDate in 1L until 100_000_000_000L -> rawPubDate * 1000L
                        rawPubDate > 0L -> rawPubDate
                        else -> System.currentTimeMillis()
                    }

                    // Determinar si fue reproducido completamente
                    val isPlayed = (readStatus == 1) ||
                            (completionDate > 0L) ||
                            (duration > 0L && position >= (duration - 5000L))

                    // Posición exacta para continuar escucha
                    val currentPositionMs = when {
                        isPlayed && position <= 0L -> duration
                        else -> position
                    }

                    val isFavorite = favoriteItemIds.contains(apItemId)

                    val episodeEntity = EpisodeEntity(
                        id = episodeId,
                        podcastId = podcast.id,
                        title = epTitle?.takeIf { it.isNotBlank() } ?: "Episodio sin título",
                        description = epDesc.orEmpty(),
                        audioUrl = audioUrl,
                        imageUrl = epImage?.takeIf { it.isNotBlank() } ?: podcast.artworkUrl,
                        pubDate = pubDate,
                        duration = duration,
                        currentPositionMs = currentPositionMs,
                        isPlayed = isPlayed,
                        isFavorite = isFavorite,
                        isDownloaded = false,
                        localFilePath = null,
                        fileSizeBytes = fileSize
                    )
                    episodesToInsert.add(episodeEntity)

                    // 5. Historial y Estadísticas de reproducción
                    if (isPlayed || playedDuration > 0L || completionDate > 0L || lastPlayedTime > 0L || position > 0L) {
                        val listenedAt = when {
                            completionDate > 0L -> completionDate
                            lastPlayedTime > 0L -> lastPlayedTime
                            pubDate > 0L -> pubDate
                            else -> System.currentTimeMillis()
                        }

                        val effectivePlayedTime = when {
                            playedDuration > 0L -> playedDuration
                            duration > 0L && isPlayed -> duration
                            position > 0L -> position
                            else -> duration
                        }

                        historyToInsert.add(
                            ListeningHistoryEntity(
                                episodeId = episodeId,
                                podcastId = podcast.id,
                                listenedAt = listenedAt,
                                durationListenedMs = effectivePlayedTime,
                                completed = isPlayed
                            )
                        )

                        // Acumular estadísticas para el podcast
                        val statAcc = statsMap.getOrPut(podcast.id) {
                            PodcastStatsAccumulator(podcastTitle = podcast.title)
                        }
                        statAcc.totalTimeMs += effectivePlayedTime
                        if (isPlayed) {
                            statAcc.completedCount += 1
                        }
                        if (listenedAt > statAcc.lastListenedAt) {
                            statAcc.lastListenedAt = listenedAt
                        }
                    }
                }
            }

            // 6. Preparar cola de reproducción
            val queueToInsert = mutableListOf<QueueItemEntity>()
            queueFeedItemIds.forEachIndexed { index, apItemId ->
                val epId = apItemIdToEpisodeId[apItemId]
                if (epId != null) {
                    queueToInsert.add(
                        QueueItemEntity(
                            episodeId = epId,
                            positionIndex = index,
                            isCurrentlyPlaying = (index == 0),
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            // 7. Preparar lista de estadísticas por podcast
            val statsToInsert = statsMap.map { (pId, acc) ->
                PlaybackStatsEntity(
                    podcastId = pId,
                    podcastTitle = acc.podcastTitle,
                    totalTimeListenedMs = acc.totalTimeMs,
                    episodesCompletedCount = acc.completedCount,
                    lastListenedAt = if (acc.lastListenedAt > 0L) acc.lastListenedAt else System.currentTimeMillis()
                )
            }

            // 8. Inserción atómica en la base de datos Room
            val dao = database.podcastDao()
            database.withTransaction {
                // Inserción de podcasts
                podcastsToInsert.chunked(100).forEach { chunk ->
                    dao.insertPodcasts(chunk)
                }

                // Inserción de episodios (REPLACE asegura que episodios ya existentes actualicen su estado jugado)
                episodesToInsert.chunked(250).forEach { chunk ->
                    dao.insertOrReplaceEpisodes(chunk)
                }

                // Inserción de historial
                if (historyToInsert.isNotEmpty()) {
                    historyToInsert.chunked(250).forEach { chunk ->
                        dao.insertListeningHistoryList(chunk)
                    }
                }

                // Inserción de estadísticas
                if (statsToInsert.isNotEmpty()) {
                    dao.upsertPlaybackStatsList(statsToInsert)
                }

                // Inserción de cola de reproducción si AntennaPod tenía episodios en cola
                if (queueToInsert.isNotEmpty()) {
                    dao.clearQueue()
                    dao.insertQueueItems(queueToInsert)
                }
            }

            val playedCount = episodesToInsert.count { it.isPlayed }
            val totalHours = statsToInsert.sumOf { it.totalTimeListenedMs } / (1000 * 60 * 60)

            val summary = "¡Base de datos de AntennaPod importada con éxito!\n" +
                    "• ${podcastsToInsert.size} podcasts suscritos\n" +
                    "• ${episodesToInsert.size} episodios ($playedCount escuchados)\n" +
                    "• $totalHours h de estadísticas y ${historyToInsert.size} registros de historial\n" +
                    (if (queueToInsert.isNotEmpty()) "• ${queueToInsert.size} episodios añadidos a la cola" else "")

            com.ferlagod.mispodcast.sync.NextcloudSyncManager.getInstance(context).triggerSyncIfConfigured(debounceMs = 3000L)

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sqliteDb?.close() } catch (ignored: Exception) {}
        }
    }
}
