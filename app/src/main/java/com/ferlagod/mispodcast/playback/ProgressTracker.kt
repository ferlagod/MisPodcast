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

import android.content.Context
import androidx.media3.common.Player
import com.ferlagod.mispodcast.data.download.DownloadCleaner
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gestor del seguimiento y persistencia del progreso de reproducción en tiempo real.
 *
 * Se encarga de:
 * - Guardar periódicamente la posición de escucha en SQLite (Room) cada 5 segundos.
 * - Registrar las estadísticas acumuladas de tiempo escuchado por podcast.
 * - Desencadenar la sincronización continua y atómica con servidores Nextcloud WebDAV.
 * - Ejecutar las operaciones de guardado crítico dentro de un ámbito [applicationScope]
 *   asíncrono con [SupervisorJob], garantizando su finalización sin ANRs al detenerse o destruirse el servicio.
 */
class ProgressTracker(
    private val context: Context,
    private val database: PodcastDatabase,
    private val syncManager: NextcloudSyncManager,
    private val scope: CoroutineScope
) {

    private var progressTrackingJob: Job? = null
    private var lastContinuousSyncTimeMs: Long = 0L
    private var lastRecordedPositionMs: Long = 0L

    companion object {
        /**
         * Ámbito de corrutinas a nivel de aplicación para operaciones de persistencia crítica
         * que deben asegurarse aun si la vista o servicio se destruye inmediatamente.
         */
        val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    /**
     * Inicia el temporizador de guardado periódico de progreso cada 5 segundos mientras el reproductor está activo.
     */
    fun startPeriodicSaver(
        player: Player,
        currentEpisodeSupplier: () -> String?,
        currentPodcastSupplier: () -> String?,
        currentPodcastTitleSupplier: () -> String?
    ) {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            var counter = 0
            while (isActive) {
                delay(1000)
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    val podcastId = currentPodcastSupplier()
                    val episodeId = currentEpisodeSupplier()

                    // Comprobación de salto de créditos / patrocinadores al final del episodio
                    if (podcastId != null && episodeId != null && duration > 0L) {
                        val podcast = database.podcastDao().getPodcastById(podcastId)
                        val skipEndingMs = (podcast?.skipEndingSeconds ?: 0) * 1000L
                        if (skipEndingMs > 0 && currentPos >= (duration - skipEndingMs)) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                player.seekTo(duration)
                            }
                            continue
                        }
                    }

                    counter++
                    if (counter >= 5) {
                        counter = 0
                        saveCurrentPlaybackPosition(
                            player = player,
                            episodeId = episodeId,
                            podcastId = podcastId,
                            podcastTitle = currentPodcastTitleSupplier() ?: "Podcast",
                            isCompleted = false
                        )
                        val now = System.currentTimeMillis()
                        if (now - lastContinuousSyncTimeMs > 5 * 60 * 1000L) {
                            lastContinuousSyncTimeMs = now
                            syncManager.triggerSyncIfConfigured(debounceMs = 2000L)
                        }
                    }
                }
            }
        }
    }

    /**
     * Guarda la posición actual de reproducción en la base de datos local y sincroniza si corresponde.
     */
    fun saveCurrentPlaybackPosition(
        player: Player,
        episodeId: String?,
        podcastId: String?,
        podcastTitle: String = "Podcast",
        isCompleted: Boolean = false
    ) {
        val targetEpisodeId = episodeId ?: return
        val currentPos = player.currentPosition
        val duration = player.duration

        if (currentPos <= 0L && !isCompleted) {
            return
        }

        var deltaToRecord = 0L
        if (podcastId != null && currentPos > lastRecordedPositionMs) {
            val delta = currentPos - lastRecordedPositionMs
            if (delta > 1000) {
                deltaToRecord = delta
                lastRecordedPositionMs = currentPos
            }
        }

        val saveAction: suspend () -> Unit = {
            database.podcastDao().updatePlaybackPosition(targetEpisodeId, currentPos)

            if (isCompleted || (duration > 0 && currentPos >= duration - 2000)) {
                database.podcastDao().markEpisodeAsPlayed(targetEpisodeId, true)
            }

            if (podcastId != null && deltaToRecord > 0) {
                database.podcastDao().recordListenedDuration(
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    episodeId = targetEpisodeId,
                    additionalDurationMs = deltaToRecord,
                    isCompleted = isCompleted
                )
            }
        }

        applicationScope.launch {
            saveAction()
        }

        if (isCompleted) {
            syncManager.triggerSyncIfConfigured(debounceMs = 1500L)
        } else if (!player.isPlaying) {
            syncManager.triggerSyncIfConfigured(debounceMs = 3000L)
        }
    }

    /**
     * Marca un episodio como completado y lo retira de la cola atómicamente,
     * invocando la limpieza automática de archivos descargados ("Borrar tras escuchar").
     */
    fun handleEpisodeCompleted(episodeId: String) {
        applicationScope.launch {
            database.podcastDao().markAsPlayedAndRemoveFromQueue(episodeId)
            DownloadCleaner.onEpisodeCompleted(context, database.podcastDao(), episodeId)
            syncManager.triggerSyncIfConfigured(debounceMs = 1500L)
        }
    }

    /**
     * Reinicia el tracking para un nuevo episodio.
     */
    fun resetRecordedPosition() {
        lastRecordedPositionMs = 0L
    }

    /**
     * Detiene el guardado periódico y cancela el trabajo en ejecución.
     */
    fun stop() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }
}
