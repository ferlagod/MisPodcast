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

package com.ferlagod.mispodcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.model.QueueItemEntityWithEpisode
import com.ferlagod.mispodcast.data.local.model.QueueSortOption
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel especializado en la administración de la cola de reproducción secuencial.
 *
 * Provee métodos para agregar episodios, reordenar elementos por arrastre, aplicar criterios de ordenación
 * automática (duración, tamaño, fecha) y vaciar la cola preservando el episodio actualmente en escucha.
 */
class QueueViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PodcastRepository = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.podcastRepository
        ?: PodcastRepository(PodcastDatabase.getInstance(application).podcastDao())
) : AndroidViewModel(application) {

    val queue: StateFlow<List<QueueItemEntityWithEpisode>> = repository.getQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueEpisodes: StateFlow<List<EpisodeEntity>> = repository.getQueueEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEpisodeToQueue(episodeId: String) {
        viewModelScope.launch {
            repository.addEpisodeToQueue(episodeId)
        }
    }

    fun addEpisodeToQueueTop(episodeId: String) {
        viewModelScope.launch {
            repository.addEpisodeToQueueTop(episodeId)
        }
    }

    fun removeEpisodeFromQueue(episodeId: String) {
        viewModelScope.launch {
            repository.removeEpisodeFromQueue(episodeId)
        }
    }

    fun markEpisodeAsPlayedAndRemoveFromQueue(episodeId: String) {
        viewModelScope.launch {
            repository.markEpisodeAsPlayedAndRemoveFromQueue(episodeId)
        }
    }

    fun setCurrentPlaybackInQueue(episodeId: String) {
        viewModelScope.launch {
            repository.setCurrentPlaybackInQueue(episodeId)
        }
    }

    fun reorderQueue(orderedEpisodeIds: List<String>) {
        viewModelScope.launch {
            repository.reorderQueue(orderedEpisodeIds)
        }
    }

    fun moveSubsequentQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = queue.value
        if (currentQueue.size <= 2) return
        val currentActive = currentQueue.first()
        val subsequent = currentQueue.drop(1).toMutableList()

        if (fromIndex in subsequent.indices && toIndex in subsequent.indices && fromIndex != toIndex) {
            val item = subsequent.removeAt(fromIndex)
            subsequent.add(toIndex, item)
            val newOrderedIds = listOf(currentActive.episode.id) + subsequent.map { it.episode.id }
            viewModelScope.launch {
                repository.reorderQueue(newOrderedIds)
            }
        }
    }

    fun sortQueue(sortOption: QueueSortOption) {
        val currentQueue = queue.value
        if (currentQueue.size <= 2) return

        val currentActive = currentQueue.first()
        val subsequent = currentQueue.drop(1)

        val sortedSubsequent = when (sortOption) {
            QueueSortOption.DURATION_ASC -> subsequent.sortedBy { it.episode.duration }
            QueueSortOption.DURATION_DESC -> subsequent.sortedByDescending { it.episode.duration }
            QueueSortOption.SIZE_ASC -> subsequent.sortedBy { getEpisodeSizeBytes(it) }
            QueueSortOption.SIZE_DESC -> subsequent.sortedByDescending { getEpisodeSizeBytes(it) }
            QueueSortOption.NAME_ASC -> subsequent.sortedBy { it.episode.title.lowercase() }
            QueueSortOption.NAME_DESC -> subsequent.sortedByDescending { it.episode.title.lowercase() }
            QueueSortOption.DATE_DESC -> subsequent.sortedByDescending { it.episode.pubDate }
            QueueSortOption.DATE_ASC -> subsequent.sortedBy { it.episode.pubDate }
        }

        val newOrderedIds = listOf(currentActive.episode.id) + sortedSubsequent.map { it.episode.id }
        viewModelScope.launch {
            repository.reorderQueue(newOrderedIds)
        }
    }

    private fun getEpisodeSizeBytes(item: QueueItemEntityWithEpisode): Long {
        val localPath = item.episode.localFilePath
        if (!localPath.isNullOrBlank()) {
            try {
                val file = java.io.File(localPath)
                if (file.exists() && file.isFile) {
                    return file.length()
                }
            } catch (_: Exception) {}
        }
        return item.episode.duration * 16L
    }

    fun clearQueue() {
        viewModelScope.launch {
            val q = queue.value
            val hasPlaying = q.any { it.isCurrentlyPlaying }
            if (hasPlaying) {
                repository.clearSubsequentQueue()
            } else {
                repository.clearQueue()
            }
        }
    }
}
