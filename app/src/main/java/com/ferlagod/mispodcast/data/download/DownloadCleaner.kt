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
import android.os.Environment
import android.util.Log
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aplica las preferencias de almacenamiento del usuario sobre los episodios descargados:
 *
 * - **Borrar tras escuchar** (`deleteAfterPlayed`): elimina el archivo de un episodio en cuanto termina.
 * - **Límite de episodios descargados** (`cachedEpisodesLimit`): si se supera, borra primero los
 *   episodios ya escuchados y después los más antiguos que no estén en la cola.
 *
 * Reglas de seguridad:
 * - Solo se borran archivos que la propia app descargó en su carpeta privada. Nunca se tocan
 *   carpetas locales del usuario (SAF / `content://`).
 * - Nunca se borran episodios marcados como favoritos, ni el episodio protegido (el que se está
 *   reproduciendo o el que se acaba de descargar).
 * - Los episodios pendientes en la cola solo se borran si ya están escuchados.
 */
object DownloadCleaner {

    private const val TAG = "DownloadCleaner"

    /** Valor del ajuste a partir del cual el límite se considera "Ilimitado". */
    const val UNLIMITED_THRESHOLD = 100

    /**
     * Se invoca cuando un episodio termina de reproducirse.
     * Si el usuario activó "Borrar tras escuchar", elimina su descarga.
     */
    suspend fun onEpisodeCompleted(context: Context, dao: PodcastDao, episodeId: String) =
        withContext(Dispatchers.IO) {
            try {
                val settings = UserPreferencesManager.getInstance(context).userSettingsFlow.first()
                if (!settings.deleteAfterPlayed) return@withContext
                val episode = dao.getEpisodeById(episodeId) ?: return@withContext
                if (episode.isFavorite) return@withContext
                deleteDownloadedFile(context, dao, episode)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo aplicar 'borrar tras escuchar' a $episodeId: ${e.message}")
            }
        }

    /**
     * Garantiza que el número de episodios descargados no supera el límite configurado.
     *
     * @param protectedEpisodeId episodio que nunca debe borrarse (p. ej. el recién descargado
     * o el que está sonando).
     */
    suspend fun enforceCacheLimit(context: Context, dao: PodcastDao, protectedEpisodeId: String? = null) =
        withContext(Dispatchers.IO) {
            try {
                val limit = UserPreferencesManager.getInstance(context).userSettingsFlow.first().cachedEpisodesLimit
                if (limit >= UNLIMITED_THRESHOLD) return@withContext

                val downloaded = dao.getDownloadedEpisodesList()
                    .map { it.episode }
                    .filter { isAppManagedFile(context, it.localFilePath) }
                val excess = downloaded.size - limit
                if (excess <= 0) return@withContext

                val queueIds = dao.getOrderedQueueEpisodeIds().toSet()
                val candidates = downloaded
                    .filter { !it.isFavorite && it.id != protectedEpisodeId }
                    .filter { it.isPlayed || it.id !in queueIds }
                    // Primero los escuchados, después los más antiguos.
                    .sortedWith(compareByDescending<EpisodeEntity> { it.isPlayed }.thenBy { it.pubDate })
                    .take(excess)

                candidates.forEach { deleteDownloadedFile(context, dao, it) }
                Log.i(TAG, "Límite de $limit descargas: eliminados ${candidates.size} de $excess sobrantes")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo aplicar el límite de descargas: ${e.message}")
            }
        }

    private suspend fun deleteDownloadedFile(context: Context, dao: PodcastDao, episode: EpisodeEntity) {
        val path = episode.localFilePath
        if (!episode.isDownloaded || !isAppManagedFile(context, path)) return
        val file = File(path!!)
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "No se pudo borrar ${file.absolutePath}")
            return
        }
        dao.updateDownloadStatus(episodeId = episode.id, isDownloaded = false, localPath = null)
        EpisodeDownloadManager.getInstance(context, dao).markAsRemoved(episode.id)
        Log.i(TAG, "Descarga eliminada: ${episode.title}")
    }

    /**
     * Limpia archivos temporales (.tmp) huérfanos dejados por descargas canceladas o fallidas.
     */
    suspend fun cleanupOrphanTempFiles(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) =
        withContext(Dispatchers.IO) {
            try {
                val podcastsDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
                if (podcastsDir != null && podcastsDir.exists()) {
                    val now = System.currentTimeMillis()
                    val tmpFiles = podcastsDir.listFiles { file -> file.isFile && file.name.endsWith(".tmp") } ?: emptyArray()
                    var cleaned = 0
                    for (file in tmpFiles) {
                        if (now - file.lastModified() > maxAgeMs) {
                            if (file.delete()) {
                                cleaned++
                            }
                        }
                    }
                    if (cleaned > 0) {
                        Log.i(TAG, "Limpieza de archivos .tmp huérfanos: eliminados $cleaned archivo(s)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error limpiando temporales huérfanos: ${e.message}")
            }
        }

    /** Devuelve `true` solo si la ruta pertenece a la carpeta privada de descargas de la app. */
    private fun isAppManagedFile(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank() || path.startsWith("content://")) return false
        val roots = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS),
            context.filesDir
        ).map { it.canonicalPath }
        val canonical = try { File(path).canonicalPath } catch (_: Exception) { return false }
        return roots.any { canonical.startsWith(it + File.separator) }
    }
}
