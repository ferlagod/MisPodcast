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

package com.ferlagod.mispodcast.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

object LocalFolderPodcastImporter {

    private const val TAG = "LocalFolderImporter"

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "wma"
    )

    /**
     * Escanea e importa una carpeta seleccionada mediante OpenDocumentTree como un Podcast en Room.
     */
    suspend fun importLocalFolder(
        context: Context,
        treeUri: Uri,
        customTitle: String? = null,
        customArtworkUri: String? = null,
        podcastDao: PodcastDao
    ): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        try {
            // 1. Conservar permisos persistentes de lectura del árbol de documentos
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo tomar permiso persistente: ${e.message}")
            }

            // 2. Obtener el DocumentFile raíz de la carpeta
            val rootFolder = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo acceder a la carpeta seleccionada."))

            val folderName = rootFolder.name ?: "Podcast Local"
            val title = customTitle?.trim()?.ifBlank { null } ?: folderName

            // 3. Resolver la portada del podcast
            val folderArtwork = findCoverImageInFolder(rootFolder)
            val artworkUrl = customArtworkUri?.trim()?.ifBlank { null } ?: folderArtwork.orEmpty()

            // 4. Generar ID determinista basado en el URI de la carpeta
            val podcastId = "local_" + treeUri.toString().hashCode().toString().replace("-", "0")
            val feedUrl = "local://$podcastId"

            val podcastEntity = PodcastEntity(
                id = podcastId,
                title = title,
                author = "Carpeta Local",
                description = "Audios importados desde la carpeta local: $folderName",
                feedUrl = feedUrl,
                artworkUrl = artworkUrl,
                isSubscribed = true,
                subscribedAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
                category = "Archivos Locales",
                link = treeUri.toString(),
                isLocalFolder = true,
                localFolderPath = treeUri.toString()
            )

            // 5. Escanear recursivamente todos los archivos de audio compatibles (permitir carpetas vacías)
            val audioFiles = scanAudioFilesRecursively(rootFolder)

            // 6. Extraer metadatos de cada archivo si existen audios
            val episodes = if (audioFiles.isNotEmpty()) {
                audioFiles.mapIndexed { index, docFile ->
                    extractEpisodeMetadata(
                        context = context,
                        podcastId = podcastId,
                        docFile = docFile,
                        fallbackArtwork = artworkUrl,
                        fallbackIndex = index + 1
                    )
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            } else {
                emptyList()
            }

            // 7. Insertar en Room (la carpeta se crea incluso si está vacía)
            podcastDao.insertPodcast(podcastEntity)
            if (episodes.isNotEmpty()) {
                podcastDao.insertEpisodes(episodes)
            }

            Log.d(TAG, "Carpeta importada exitosamente: $title con ${episodes.size} audios iniciales.")
            Result.success(podcastEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error importando carpeta local", e)
            Result.failure(e)
        }
    }

    /**
     * Comprueba si la ruta de una carpeta local sigue siendo accesible y legible en el dispositivo actual.
     */
    fun isFolderAccessible(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val doc = DocumentFile.fromTreeUri(context, uri)
                doc != null && doc.exists() && doc.canRead()
            } else {
                val cleanPath = path.removePrefix("file://")
                val file = java.io.File(cleanPath)
                file.exists() && file.canRead()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reconecta un podcast local a una nueva ubicación física en el dispositivo (migraciones, OPML, AntennaPod),
     * actualizando `localFolderPath` en Room y re-enlazando los episodios existentes y nuevos.
     */
    suspend fun reconnectLocalFolder(
        context: Context,
        podcastId: String,
        newTreeUri: Uri,
        podcastDao: PodcastDao
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 1. Conservar permisos persistentes de lectura
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(newTreeUri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo tomar permiso persistente: ${e.message}")
            }

            val podcast = podcastDao.getPodcastById(podcastId)
                ?: return@withContext Result.failure(IllegalArgumentException("Podcast no encontrado en la base de datos."))

            val rootFolder = DocumentFile.fromTreeUri(context, newTreeUri)
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo acceder a la carpeta seleccionada."))

            // Portada de la carpeta si existe
            val folderArtwork = findCoverImageInFolder(rootFolder)
            val updatedArtwork = if (podcast.artworkUrl.isBlank() || !podcast.artworkUrl.startsWith("http")) {
                folderArtwork ?: podcast.artworkUrl
            } else {
                podcast.artworkUrl
            }

            // 2. Actualizar ruta en PodcastEntity
            val updatedPodcast = podcast.copy(
                isLocalFolder = true,
                localFolderPath = newTreeUri.toString(),
                link = newTreeUri.toString(),
                artworkUrl = updatedArtwork
            )
            podcastDao.insertPodcast(updatedPodcast)

            // 3. Escanear audios en la nueva carpeta
            val audioFiles = scanAudioFilesRecursively(rootFolder)
            val existingEpisodes = podcastDao.getEpisodesForPodcastFlow(podcastId).firstOrNull() ?: emptyList()

            val audioFilesByName = audioFiles.associateBy { it.name?.lowercase().orEmpty() }

            val episodesToUpdateOrInsert = mutableListOf<EpisodeEntity>()
            var reconnectedCount = 0

            // A. Re-enlazar episodios existentes buscando coincidencia por nombre de archivo o título
            for (ep in existingEpisodes) {
                val oldFileName = Uri.parse(ep.audioUrl).lastPathSegment?.substringAfterLast('/')?.lowercase().orEmpty()
                val matchedFile = audioFilesByName[oldFileName]
                    ?: audioFiles.firstOrNull { doc ->
                        val docBase = doc.name?.substringBeforeLast('.')?.lowercase().orEmpty()
                        val epTitle = ep.title.lowercase()
                        docBase.isNotBlank() && (docBase == epTitle || epTitle.contains(docBase))
                    }

                if (matchedFile != null) {
                    val updatedEp = ep.copy(
                        audioUrl = matchedFile.uri.toString(),
                        localFilePath = matchedFile.uri.toString(),
                        isDownloaded = true,
                        fileSizeBytes = matchedFile.length()
                    )
                    episodesToUpdateOrInsert.add(updatedEp)
                    reconnectedCount++
                } else {
                    episodesToUpdateOrInsert.add(ep)
                }
            }

            // B. Añadir audios nuevos que no estaban registrados previamente
            val existingUris = episodesToUpdateOrInsert.map { it.audioUrl }.toSet()
            val newAudioFiles = audioFiles.filter { it.uri.toString() !in existingUris }
            val newEpisodes = newAudioFiles.mapIndexed { index, docFile ->
                extractEpisodeMetadata(
                    context = context,
                    podcastId = podcastId,
                    docFile = docFile,
                    fallbackArtwork = podcast.artworkUrl,
                    fallbackIndex = existingEpisodes.size + index + 1
                )
            }
            episodesToUpdateOrInsert.addAll(newEpisodes)

            // Guardar en Room
            if (episodesToUpdateOrInsert.isNotEmpty()) {
                podcastDao.insertOrReplaceEpisodes(episodesToUpdateOrInsert)
            }

            Log.d(TAG, "Reconexión completada: $reconnectedCount audios re-enlazados y ${newEpisodes.size} nuevos audios añadidos.")
            Result.success(episodesToUpdateOrInsert.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error reconectando carpeta local", e)
            Result.failure(e)
        }
    }

    /**
     * Re-escanea una carpeta local existente para añadir nuevos audios detectados.
     */
    suspend fun rescanLocalFolder(
        context: Context,
        podcastId: String,
        podcastDao: PodcastDao
    ): Int = withContext(Dispatchers.IO) {
        try {
            val podcast = podcastDao.getPodcastById(podcastId) ?: return@withContext 0
            if (!podcast.isLocalFolder || podcast.localFolderPath.isNullOrBlank()) return@withContext 0

            val path = podcast.localFolderPath
            if (!path.startsWith("content://")) return@withContext 0
            val treeUri = Uri.parse(path)
            val rootFolder = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (_: Exception) {
                null
            } ?: return@withContext 0

            if (!rootFolder.exists() || !rootFolder.canRead()) return@withContext 0

            // Actualizar portada si antes no tenía
            if (podcast.artworkUrl.isBlank() || !podcast.artworkUrl.startsWith("http")) {
                val folderCover = findCoverImageInFolder(rootFolder)
                if (!folderCover.isNullOrBlank() && folderCover != podcast.artworkUrl) {
                    podcastDao.insertPodcast(podcast.copy(artworkUrl = folderCover))
                }
            }

            val existingEpisodes = podcastDao.getEpisodesForPodcastFlow(podcastId).firstOrNull() ?: emptyList()
            val existingUris = existingEpisodes.map { it.audioUrl }.toSet()

            val audioFiles = scanAudioFilesRecursively(rootFolder)
            val newAudioFiles = audioFiles.filter { it.uri.toString() !in existingUris }

            if (newAudioFiles.isEmpty()) return@withContext 0

            val newEpisodes = newAudioFiles.mapIndexed { index, docFile ->
                extractEpisodeMetadata(
                    context = context,
                    podcastId = podcastId,
                    docFile = docFile,
                    fallbackArtwork = podcast.artworkUrl,
                    fallbackIndex = existingEpisodes.size + index + 1
                )
            }

            podcastDao.insertEpisodes(newEpisodes)
            Log.d(TAG, "Rescan completado para ${podcast.title}: ${newEpisodes.size} nuevos episodios añadidos.")
            newEpisodes.size
        } catch (e: Exception) {
            Log.e(TAG, "Error re-escaneando carpeta local", e)
            0
        }
    }

    /**
     * Escanea todas las carpetas locales suscritas en Room para mantenerlas al día.
     */
    suspend fun rescanAllLocalPodcasts(
        context: Context,
        podcastDao: PodcastDao
    ): Int = withContext(Dispatchers.IO) {
        val subscriptions = podcastDao.getSubscribedPodcastsFlow().firstOrNull() ?: emptyList()
        val localPodcasts = subscriptions.filter { it.isLocalFolder && !it.localFolderPath.isNullOrBlank() }
        var totalAdded = 0
        for (podcast in localPodcasts) {
            totalAdded += rescanLocalFolder(context, podcast.id, podcastDao)
        }
        totalAdded
    }

    private fun scanAudioFilesRecursively(folder: DocumentFile): List<DocumentFile> {
        val results = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(folder)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val files: Array<DocumentFile> = try {
                current.listFiles()
            } catch (_: Exception) {
                emptyArray<DocumentFile>()
            }

            for (file in files) {
                if (file.isDirectory) {
                    queue.add(file)
                } else if (file.isFile) {
                    val ext = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                    if (ext in SUPPORTED_AUDIO_EXTENSIONS) {
                        results.add(file)
                    }
                }
            }
        }
        return results
    }

    private fun findCoverImageInFolder(folder: DocumentFile): String? {
        val files: Array<DocumentFile> = try {
            folder.listFiles()
        } catch (_: Exception) {
            emptyArray<DocumentFile>()
        }

        // Buscar primero nombres comunes de portada
        val coverKeywords = listOf("cover", "folder", "front", "artwork", "album", "poster", "image", "images")
        val imageExtensions = listOf("jpg", "jpeg", "png", "webp")

        for (file in files) {
            if (file.isFile) {
                val name = file.name?.lowercase() ?: continue
                val base = name.substringBeforeLast('.', "")
                val ext = name.substringAfterLast('.', "")
                if (ext in imageExtensions && coverKeywords.any { base.contains(it) }) {
                    return file.uri.toString()
                }
            }
        }

        // Si no hay ninguna con palabra clave, usar la primera imagen encontrada
        for (file in files) {
            if (file.isFile) {
                val ext = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                if (ext in imageExtensions) {
                    return file.uri.toString()
                }
            }
        }

        return null
    }

    private fun extractEpisodeMetadata(
        context: Context,
        podcastId: String,
        docFile: DocumentFile,
        fallbackArtwork: String,
        fallbackIndex: Int
    ): EpisodeEntity {
        val rawName = docFile.name ?: "Pista $fallbackIndex"
        val baseName = rawName.substringBeforeLast('.')
        var audioTitle = baseName
        var durationMs = 0L
        val fileSizeBytes = docFile.length()

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, docFile.uri)
            val id3Title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!id3Title.isNullOrBlank()) {
                audioTitle = id3Title.trim()
            }
            val id3Duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = id3Duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer etiquetas ID3 de $rawName: ${e.message}")
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }

        val episodeId = "local_ep_${podcastId}_${docFile.uri.toString().hashCode().toString().replace("-", "0")}"
        val sizeMb = if (fileSizeBytes > 0) String.format("%.1f MB", fileSizeBytes.toDouble() / (1024 * 1024)) else ""
        val descText = if (sizeMb.isNotBlank()) "Archivo local • $sizeMb" else "Archivo de audio local"
        val lastMod = docFile.lastModified()
        val pubDate = if (lastMod > 0L) lastMod else System.currentTimeMillis()

        return EpisodeEntity(
            id = episodeId,
            podcastId = podcastId,
            title = audioTitle,
            description = descText,
            audioUrl = docFile.uri.toString(),
            imageUrl = fallbackArtwork,
            pubDate = pubDate,
            duration = durationMs,
            currentPositionMs = 0L,
            isPlayed = false,
            isFavorite = false,
            isDownloaded = true,
            localFilePath = docFile.uri.toString(),
            fileSizeBytes = fileSizeBytes
        )
    }
}
