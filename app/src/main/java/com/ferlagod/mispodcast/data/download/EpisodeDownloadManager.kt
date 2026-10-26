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

package com.ferlagod.mispodcast.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.worker.EpisodeDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Estados posibles del ciclo de descarga de un episodio.
 */
enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

/**
 * Estado observable de la descarga de un episodio.
 *
 * @property status fase actual de la descarga.
 * @property progress progreso entre 0f y 1f (solo significativo durante [DownloadStatus.DOWNLOADING]).
 */
data class EpisodeDownloadState(
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val progress: Float = 0f
)

/**
 * Gestor singleton de descargas de episodios para escucha sin conexión respaldado por WorkManager.
 *
 * Delega la ejecución y reanudación a [EpisodeDownloadWorker], garantizando que las descargas
 * sobrevivan al cierre de la aplicación o a la terminación del proceso. Expone el progreso en
 * tiempo real mediante [downloadStates] y respeta restricciones configuradas (solo Wi-Fi y solo cargando).
 */
class EpisodeDownloadManager private constructor(
    private val context: Context,
    private val podcastDao: PodcastDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preferencesManager = UserPreferencesManager.getInstance(context)

    private val _downloadStates = MutableStateFlow<Map<String, EpisodeDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, EpisodeDownloadState>> = _downloadStates.asStateFlow()

    init {
        scope.launch {
            DownloadCleaner.cleanupOrphanTempFiles(context)
        }
    }

    /**
     * Consulta el estado de descarga de un episodio específico.
     */
    fun getDownloadState(episode: EpisodeEntity): EpisodeDownloadState {
        val inMemory = _downloadStates.value[episode.id]
        if (inMemory != null) {
            return inMemory
        }
        return if (episode.isDownloaded && !episode.localFilePath.isNullOrBlank() && File(episode.localFilePath).exists()) {
            EpisodeDownloadState(status = DownloadStatus.DOWNLOADED, progress = 1f)
        } else {
            EpisodeDownloadState(status = DownloadStatus.NOT_DOWNLOADED, progress = 0f)
        }
    }

    /**
     * Actualiza el progreso y estado en memoria observable desde la interfaz gráfica.
     */
    fun reportProgress(episodeId: String, status: DownloadStatus, progress: Float) {
        _downloadStates.update { current ->
            current + (episodeId to EpisodeDownloadState(status, progress))
        }
    }

    /**
     * Cancela la descarga en curso de un episodio a través de WorkManager.
     */
    fun cancelDownload(episodeId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$episodeId")
        reportProgress(episodeId, DownloadStatus.NOT_DOWNLOADED, 0f)
    }

    /**
     * Encola la descarga de un episodio mediante [EpisodeDownloadWorker] aplicando las restricciones del usuario.
     */
    fun downloadEpisode(episode: EpisodeEntity, ignoreNetworkConstraints: Boolean = false) {
        if (episode.audioUrl.isBlank()) {
            Log.e("EpisodeDownloadManager", "Cannot download episode without audioUrl")
            return
        }

        val currentState = getDownloadState(episode)
        if (currentState.status == DownloadStatus.DOWNLOADING || currentState.status == DownloadStatus.DOWNLOADED) {
            return
        }

        reportProgress(episode.id, DownloadStatus.DOWNLOADING, 0f)

        scope.launch {
            try {
                // 1. Asegurar que el podcast y el episodio existen en Room sin violar claves foráneas
                if (episode.podcastId.isNotBlank()) {
                    val existingPodcast = podcastDao.getPodcastById(episode.podcastId)
                    if (existingPodcast == null) {
                        podcastDao.insertPodcast(
                            PodcastEntity(
                                id = episode.podcastId,
                                title = "Podcast",
                                author = "",
                                description = "",
                                feedUrl = episode.podcastId,
                                artworkUrl = episode.imageUrl,
                                isSubscribed = false
                            )
                        )
                    }
                }
                podcastDao.insertOrUpdateEpisode(episode)

                // 2. Comprobar preferencias de red y energía
                val settings = preferencesManager.userSettingsFlow.first()
                val constraintsBuilder = Constraints.Builder()

                if (!ignoreNetworkConstraints) {
                    if (settings.downloadOnlyOnWifi) {
                        constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
                    } else {
                        constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
                    }

                    if (settings.downloadOnlyWhenCharging) {
                        constraintsBuilder.setRequiresCharging(true)
                    }
                } else {
                    constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
                }

                // 3. Crear WorkRequest para WorkManager con persistencia y reintentos exponenciales
                val inputData = workDataOf(
                    EpisodeDownloadWorker.KEY_EPISODE_ID to episode.id,
                    EpisodeDownloadWorker.KEY_AUDIO_URL to episode.audioUrl,
                    EpisodeDownloadWorker.KEY_EPISODE_TITLE to episode.title,
                    EpisodeDownloadWorker.KEY_PODCAST_ID to episode.podcastId,
                    EpisodeDownloadWorker.KEY_IMAGE_URL to episode.imageUrl,
                    EpisodeDownloadWorker.KEY_DURATION to episode.duration,
                    EpisodeDownloadWorker.KEY_PUB_DATE to episode.pubDate
                )

                val workRequest = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
                    .setConstraints(constraintsBuilder.build())
                    .setInputData(inputData)
                    .addTag(EpisodeDownloadWorker.TAG_DOWNLOAD)
                    .addTag("ep_${episode.id}")
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork("download_${episode.id}", ExistingWorkPolicy.KEEP, workRequest)

            } catch (e: Exception) {
                Log.e("EpisodeDownloadManager", "Error al encolar descarga para ${episode.title}", e)
                reportProgress(episode.id, DownloadStatus.NOT_DOWNLOADED, 0f)
            }
        }
    }

    /**
     * Notifica que la descarga de un episodio se ha eliminado fuera de este gestor
     * (por ejemplo, por la limpieza automática), para que la interfaz deje de mostrarlo como descargado.
     */
    fun markAsRemoved(episodeId: String) {
        reportProgress(episodeId, DownloadStatus.NOT_DOWNLOADED, 0f)
    }

    companion object {
        @Volatile
        private var INSTANCE: EpisodeDownloadManager? = null

        fun sanitizeFileName(rawName: String): String {
            return rawName
                .replace(Regex("[^a-zA-Z0-9.-]"), "_")
                .take(50)
        }

        fun getInstance(context: Context, podcastDao: PodcastDao): EpisodeDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EpisodeDownloadManager(
                    context.applicationContext,
                    podcastDao
                ).also { INSTANCE = it }
            }
        }
    }
}
