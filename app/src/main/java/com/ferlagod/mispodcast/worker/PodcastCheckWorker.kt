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

package com.ferlagod.mispodcast.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ferlagod.mispodcast.data.download.EpisodeDownloadManager
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.mapper.toEpisodeEntity
import com.ferlagod.mispodcast.data.preferences.EpisodeCheckFrequency
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.remote.parser.FeedFetchResult
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PodcastCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PodcastCheckWorker"
        const val PERIODIC_WORK_NAME = "mis_podcast_periodic_check_new_episodes"
        const val IMMEDIATE_WORK_NAME = "mis_podcast_immediate_check_new_episodes"

        /**
         * Gestión dinámica del intervalo con WorkManager según la preferencia del usuario.
         * Si el usuario elige "Manualmente", cancela el Worker periódico.
         */
        fun schedulePeriodicCheck(context: Context, frequency: EpisodeCheckFrequency) {
            val workManager = WorkManager.getInstance(context)

            if (frequency == EpisodeCheckFrequency.MANUAL) {
                Log.d(TAG, "Frecuencia manual seleccionada. Cancelando WorkManager periódico.")
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val intervalHours = frequency.intervalHours.coerceAtLeast(1L)
            val periodicRequest = PeriodicWorkRequestBuilder<PodcastCheckWorker>(
                intervalHours,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            Log.d(TAG, "WorkManager periódico programado cada $intervalHours horas.")
        }

        /**
         * Ejecución inmediata en segundo plano para comprobación manual o al abrir la app.
         */
        fun scheduleImmediateCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateRequest = OneTimeWorkRequestBuilder<PodcastCheckWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
            Log.d(TAG, "Comprobación inmediata lanzada en segundo plano.")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val container = (applicationContext as? com.ferlagod.mispodcast.MisPodcastApplication)?.container
            val database = container?.database ?: PodcastDatabase.getInstance(applicationContext)
            val podcastDao = database.podcastDao()
            val prefManager = container?.preferencesManager ?: UserPreferencesManager.getInstance(applicationContext)
            val settings = prefManager.userSettingsFlow.first()

            val subscribedPodcasts = podcastDao.getSubscribedPodcasts()
            if (subscribedPodcasts.isEmpty()) {
                Log.d(TAG, "No hay podcasts suscritos para comprobar.")
                return@withContext Result.success()
            }

            // 1. Re-escanear carpetas locales si existen
            try {
                com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.rescanAllLocalPodcasts(
                    applicationContext,
                    podcastDao
                )
            } catch (folderEx: Exception) {
                Log.w(TAG, "Aviso al re-escanear carpetas locales: ${folderEx.message}")
            }

            val feedParser = container?.rssFeedParser ?: RssFeedParser()
            val totalNewEpisodesFound = AtomicInteger(0)
            val downloadManager = EpisodeDownloadManager.getInstance(applicationContext, podcastDao)
            val semaphore = Semaphore(6) // Concurrencia controlada para no saturar sockets ni CPU

            // 2. Comprobar feeds RSS remotos en paralelo con Semaphore(6)
            coroutineScope {
                subscribedPodcasts.map { podcast ->
                    async {
                        if (podcast.isLocalFolder || podcast.feedUrl.isBlank() ||
                            (!podcast.feedUrl.startsWith("http://", ignoreCase = true) && !podcast.feedUrl.startsWith("https://", ignoreCase = true))) {
                            return@async
                        }

                        semaphore.withPermit {
                            try {
                                val result = feedParser.fetchAndParseFeedConditional(
                                    feedUrl = podcast.feedUrl,
                                    etag = podcast.httpEtag,
                                    lastModified = podcast.httpLastModified
                                )

                                when (result) {
                                    is FeedFetchResult.NotModified -> {
                                        Log.d(TAG, "Feed sin cambios (304 Not Modified) para '${podcast.title}'")
                                        podcastDao.updatePodcastCacheHeaders(
                                            podcastId = podcast.id,
                                            etag = podcast.httpEtag,
                                            lastModified = podcast.httpLastModified,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                    }

                                    is FeedFetchResult.Success -> {
                                        val parsedFeed = result.feed
                                        val episodeEntities = parsedFeed.episodes.map { it.toEpisodeEntity(podcast.id) }

                                        // Inserción directa eficiente con IGNORE (evita cargar miles de episodios a memoria)
                                        val insertResults = podcastDao.insertEpisodesIgnore(episodeEntities)
                                        val newEpisodes = episodeEntities.filterIndexed { index, _ ->
                                            insertResults.getOrNull(index) != null && insertResults[index] != -1L
                                        }

                                        if (newEpisodes.isNotEmpty()) {
                                            totalNewEpisodesFound.addAndGet(newEpisodes.size)
                                            Log.i(TAG, "Detectados ${newEpisodes.size} nuevos episodios en '${podcast.title}'")

                                            // Enviar notificación local agrupada
                                            if (settings.newEpisodeNotifications) {
                                                NotificationHelper.notifyNewEpisodes(
                                                    context = applicationContext,
                                                    podcast = podcast,
                                                    newEpisodes = newEpisodes
                                                )
                                            }

                                            // Auto-descargar según preferencia específica del podcast o ajuste global (respetando restricciones de Wi-Fi y batería)
                                            val shouldAutoDownload = podcast.autoDownloadEnabled ?: settings.autoDownloadNewEpisodes
                                            if (shouldAutoDownload) {
                                                newEpisodes.take(2).forEach { episode ->
                                                    downloadManager.downloadEpisode(episode, ignoreNetworkConstraints = false)
                                                }
                                            }

                                            // Auto-encolar automáticamente en la lista de reproducción ("Up Next")
                                            val effectiveRule = if (podcast.autoEnqueueRule == "DEFAULT" || podcast.autoEnqueueRule.isBlank()) {
                                                settings.defaultAutoEnqueueRule
                                            } else {
                                                podcast.autoEnqueueRule
                                            }
                                            if (effectiveRule != "DISABLED") {
                                                newEpisodes.sortedBy { it.pubDate }.forEach { ep ->
                                                    if (effectiveRule == "TOP") {
                                                        podcastDao.addEpisodeToQueueTop(ep.id)
                                                    } else if (effectiveRule == "BOTTOM") {
                                                        podcastDao.addEpisodeToQueueEnd(ep.id)
                                                    }
                                                }
                                            }
                                        }

                                        // Actualizar metadatos, timestamp y cabeceras ETag / Last-Modified
                                        val updatedPodcast = podcast.copy(
                                            title = parsedFeed.title.ifBlank { podcast.title },
                                            author = parsedFeed.author.ifBlank { podcast.author },
                                            description = parsedFeed.description.ifBlank { podcast.description },
                                            artworkUrl = parsedFeed.artworkUrl.ifBlank { podcast.artworkUrl },
                                            httpEtag = result.etag ?: podcast.httpEtag,
                                            httpLastModified = result.lastModified ?: podcast.httpLastModified,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        podcastDao.insertPodcast(updatedPodcast)
                                    }
                                }
                            } catch (feedEx: Exception) {
                                Log.w(TAG, "Error al comprobar feed de '${podcast.title}': ${feedEx.message}")
                            }
                        }
                    }
                }.awaitAll()
            }

            Log.i(TAG, "Comprobación finalizada. Total nuevos capítulos descubiertos: ${totalNewEpisodesFound.get()}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en PodcastCheckWorker: ${e.message}", e)
            Result.success()
        }
    }
}
