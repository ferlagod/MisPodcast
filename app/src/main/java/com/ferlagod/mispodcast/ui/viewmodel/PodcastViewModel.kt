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
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadManager
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.local.model.GlobalStats
import com.ferlagod.mispodcast.data.model.WrappedData
import com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueSortOption
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import com.ferlagod.mispodcast.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.ferlagod.mispodcast.data.mapper.PodcastLanguageMapper
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * ViewModel principal para la gestión del catálogo de podcasts, suscripciones y exploración.
 *
 * Administra la obtención de tendencias de Apple Podcasts, el feed de novedades de podcasts suscritos,
 * el escaneo de carpetas locales SAF, favoritos, historial y solicitudes de descarga offline.
 */
class PodcastViewModel(
    application: Application,
    private val repository: PodcastRepository = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.podcastRepository
        ?: PodcastRepository(PodcastDatabase.getInstance(application).podcastDao()),
    private val preferencesManager: UserPreferencesManager = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.preferencesManager
        ?: UserPreferencesManager.getInstance(application),
    private val syncManager: NextcloudSyncManager = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.nextcloudSyncManager
        ?: NextcloudSyncManager.getInstance(application),
    private val database: PodcastDatabase = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.database
        ?: PodcastDatabase.getInstance(application)
) : AndroidViewModel(application) {

    private val downloadManager = EpisodeDownloadManager.getInstance(application, database.podcastDao())

    val currentLanguageCode: StateFlow<String> = preferencesManager.userSettingsFlow
        .map { it.languageCode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")

    val downloadStates: StateFlow<Map<String, EpisodeDownloadState>> = downloadManager.downloadStates

    fun downloadEpisode(episode: EpisodeEntity) {
        downloadManager.downloadEpisode(episode)
    }

    fun getEpisodeDownloadState(episode: EpisodeEntity): EpisodeDownloadState {
        return downloadManager.getDownloadState(episode)
    }

    init {
        // Programar sincronización periódica en segundo plano
        SyncWorker.schedulePeriodicSync(application)

        // Observar reactivamente el cambio de idioma/país para recargar recomendaciones
        viewModelScope.launch {
            preferencesManager.userSettingsFlow
                .map { it.languageCode }
                .distinctUntilChanged()
                .collect { langCode ->
                    loadRecommendations(langCode)
                }
        }
        // Carga inicial del resumen anual Wrapped
        loadWrapped(2026)
    }

    // ==========================================
    // ESTADOS REACTIVOS DESDE ROOM
    // ==========================================

    val subscribedPodcasts: StateFlow<List<PodcastEntity>> = repository.getSubscribedPodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val newReleases: StateFlow<List<EpisodeWithPodcast>> = repository.getNewReleases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queue: StateFlow<List<QueueEpisodeItem>> = repository.getQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueEpisodes: StateFlow<List<EpisodeEntity>> = repository.getQueueEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<EpisodeWithPodcast>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEpisodeItem>> = repository.getListeningHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val podcastStats: StateFlow<List<PlaybackStatsEntity>> = repository.getPodcastStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalStats: StateFlow<GlobalStats> = repository.getGlobalStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalStats())

    // ==========================================
    // RESUMEN ANUAL "WRAPPED"
    // ==========================================

    private val _wrappedData = MutableStateFlow<WrappedData?>(null)
    val wrappedData: StateFlow<WrappedData?> = _wrappedData.asStateFlow()

    private val _selectedWrappedYear = MutableStateFlow(2026)
    val selectedWrappedYear: StateFlow<Int> = _selectedWrappedYear.asStateFlow()

    private val _isLoadingWrapped = MutableStateFlow(false)
    val isLoadingWrapped: StateFlow<Boolean> = _isLoadingWrapped.asStateFlow()

    fun loadWrapped(year: Int = _selectedWrappedYear.value) {
        _selectedWrappedYear.value = year
        viewModelScope.launch {
            _isLoadingWrapped.value = true
            _wrappedData.value = repository.getWrappedStats(year)
            _isLoadingWrapped.value = false
        }
    }

    // ==========================================
    // RECOMENDACIONES DIRECTAS APPLE PODCASTS
    // ==========================================

    private val _recommendations = MutableStateFlow<List<ApplePodcastDto>>(emptyList())
    val recommendations: StateFlow<List<ApplePodcastDto>> = _recommendations.asStateFlow()

    private val _recommendationTitle = MutableStateFlow("Tendencias en España")
    val recommendationTitle: StateFlow<String> = _recommendationTitle.asStateFlow()

    private val _recommendationSubtitle = MutableStateFlow("Los podcasts más populares del momento en Apple Podcasts")
    val recommendationSubtitle: StateFlow<String> = _recommendationSubtitle.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private val _recommendationError = MutableStateFlow<String?>(null)
    val recommendationError: StateFlow<String?> = _recommendationError.asStateFlow()

    fun loadRecommendations(languageCode: String? = null) {
        val targetLang = languageCode ?: currentLanguageCode.value
        viewModelScope.launch {
            _isLoadingRecommendations.value = true
            _recommendationError.value = null
            val result = repository.getHomeRecommendations(targetLang)
            result.onSuccess { data ->
                _recommendationTitle.value = data.title
                _recommendationSubtitle.value = data.subtitle
                _recommendations.value = data.podcasts
            }.onFailure {
                _recommendationError.value = it.localizedMessage ?: "Error al obtener recomendaciones de Apple Podcasts"
            }
            _isLoadingRecommendations.value = false
        }
    }

    // ==========================================
    // ACTUALIZACIÓN DE PODCASTS Y PULL-TO-REFRESH
    // ==========================================

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    fun refreshAllPodcasts(onFinished: ((newEpisodesCount: Int) -> Unit)? = null) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val app = getApplication<Application>()
                val result = repository.refreshAllSubscribedPodcasts(app)
                loadRecommendations()
                result.onSuccess { count ->
                    syncWithNextcloud(debounceMs = 1500L)
                    onFinished?.invoke(count)
                }.onFailure {
                    onFinished?.invoke(0)
                }
            } catch (e: Exception) {
                onFinished?.invoke(0)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    suspend fun refreshPodcast(podcastId: String): Result<Unit> {
        val result = repository.refreshPodcastFeed(podcastId)
        if (result.isSuccess) {
            syncWithNextcloud(debounceMs = 1500L)
        }
        return result
    }

    // ==========================================
    // ESTADOS DE SINCRONIZACIÓN NEXTCLOUD
    // ==========================================

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null

    fun syncWithNextcloud(debounceMs: Long = 1500L) {
        syncManager.triggerSyncIfConfigured(debounceMs)
    }

    // ==========================================
    // ESTADOS DE BÚSQUEDA Y SUSCRIPCIÓN
    // ==========================================

    private val _searchResults = MutableStateFlow<List<ApplePodcastDto>>(emptyList())
    val searchResults: StateFlow<List<ApplePodcastDto>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _subscriptionLoading = MutableStateFlow(false)
    val subscriptionLoading: StateFlow<Boolean> = _subscriptionLoading.asStateFlow()

    private val _subscriptionMessage = MutableStateFlow<String?>(null)
    val subscriptionMessage: StateFlow<String?> = _subscriptionMessage.asStateFlow()

    fun searchApplePodcasts(term: String) {
        if (term.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val lang = currentLanguageCode.value
            val country = PodcastLanguageMapper.getCountryCode(lang)
            val langParam = PodcastLanguageMapper.getLanguageParam(lang)
            val result = repository.searchApplePodcasts(term = term, country = country, lang = langParam)
            result.onSuccess {
                _searchResults.value = it
            }.onFailure {
                _searchError.value = "Error al buscar: ${it.localizedMessage}"
            }
            _isSearching.value = false
        }
    }

    suspend fun getPodcastsByGenre(genreId: Int, term: String = "podcast"): Result<List<ApplePodcastDto>> {
        val lang = currentLanguageCode.value
        val country = PodcastLanguageMapper.getCountryCode(lang)
        val langParam = PodcastLanguageMapper.getLanguageParam(lang)
        return repository.getPodcastsByGenre(genreId, term, country, langParam)
    }

    suspend fun searchAndGetFirst(term: String): ApplePodcastDto? {
        if (term.isBlank()) return null
        val lang = currentLanguageCode.value
        val country = PodcastLanguageMapper.getCountryCode(lang)
        val langParam = PodcastLanguageMapper.getLanguageParam(lang)
        return repository.searchApplePodcasts(term = term, country = country, lang = langParam).getOrNull()?.firstOrNull { !it.feedUrl.isNullOrBlank() }
    }

    suspend fun loadCategoryPodcasts(
        categoryName: String,
        genreId: Int,
        languageCode: String? = null
    ): List<ApplePodcastDto> = withContext(Dispatchers.IO) {
        val lang = languageCode ?: currentLanguageCode.value
        val country = PodcastLanguageMapper.getCountryCode(lang)
        val langParam = PodcastLanguageMapper.getLanguageParam(lang)
        val searchTerm = if (genreId > 0) {
            PodcastLanguageMapper.getCategoryTerm(genreId, lang)
        } else {
            categoryName.lowercase()
        }

        val result = repository.getPodcastsByGenre(
            genreId = genreId,
            term = searchTerm,
            country = country,
            lang = langParam
        )
        result.getOrElse {
            repository.searchApplePodcasts(term = searchTerm, country = country, lang = langParam).getOrDefault(emptyList())
        }.filter { !it.feedUrl.isNullOrBlank() }
    }

    fun subscribeByFeedUrl(feedUrl: String, onFinished: (Boolean) -> Unit = {}) {
        if (feedUrl.isBlank()) return

        viewModelScope.launch {
            _subscriptionLoading.value = true
            _subscriptionMessage.value = "Descargando feed RSS..."
            val result = repository.subscribeToFeed(feedUrl)
            result.onSuccess {
                _subscriptionMessage.value = "Suscrito con éxito a ${it.title}"
                syncWithNextcloud()
                onFinished(true)
            }.onFailure {
                _subscriptionMessage.value = "Error al suscribir: ${it.localizedMessage}"
                onFinished(false)
            }
            _subscriptionLoading.value = false
        }
    }

    fun searchAndSubscribe(searchTerm: String, onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        if (searchTerm.isBlank()) return

        viewModelScope.launch {
            _subscriptionLoading.value = true
            _subscriptionMessage.value = "Buscando '$searchTerm'..."
            val searchResult = repository.searchApplePodcasts(searchTerm)
            searchResult.onSuccess { results ->
                val firstWithFeed = results.firstOrNull { !it.feedUrl.isNullOrBlank() }
                if (firstWithFeed != null && !firstWithFeed.feedUrl.isNullOrBlank()) {
                    _subscriptionMessage.value = "Suscribiendo a ${firstWithFeed.collectionName ?: searchTerm}..."
                    val subResult = repository.subscribeToFeed(firstWithFeed.feedUrl)
                    subResult.onSuccess { podcast ->
                        _subscriptionMessage.value = "¡Suscrito a ${podcast.title}!"
                        syncWithNextcloud()
                        onFinished(true, podcast.title)
                    }.onFailure { err ->
                        _subscriptionMessage.value = "Error al suscribirse: ${err.localizedMessage}"
                        onFinished(false, err.localizedMessage ?: "Error al suscribirse")
                    }
                } else {
                    _subscriptionMessage.value = "No se encontró feed RSS para '$searchTerm'"
                    onFinished(false, "No se encontró feed RSS")
                }
            }.onFailure { err ->
                _subscriptionMessage.value = "Error al buscar: ${err.localizedMessage}"
                onFinished(false, err.localizedMessage ?: "Error al buscar")
            }
            _subscriptionLoading.value = false
        }
    }

    fun clearSubscriptionMessage() {
        _subscriptionMessage.value = null
    }

    // ==========================================
    // OPERACIONES DE COLA
    // ==========================================

    fun addEpisodeToQueue(episodeId: String) {
        viewModelScope.launch {
            repository.addEpisodeToQueue(episodeId)
            syncWithNextcloud()
        }
    }

    fun addEpisodeToQueueTop(episodeId: String) {
        viewModelScope.launch {
            repository.addEpisodeToQueueTop(episodeId)
            syncWithNextcloud()
        }
    }

    fun enqueueAllNewReleases(toTop: Boolean = false) {
        val releases = newReleases.value
        if (releases.isEmpty()) return
        viewModelScope.launch {
            if (toTop) {
                for (item in releases.reversed()) {
                    repository.addEpisodeToQueueTop(item.episode.id)
                }
            } else {
                for (item in releases) {
                    repository.addEpisodeToQueue(item.episode.id)
                }
            }
            syncWithNextcloud()
        }
    }

    fun removeEpisodeFromQueue(episodeId: String) {
        viewModelScope.launch {
            repository.removeEpisodeFromQueue(episodeId)
            syncWithNextcloud()
        }
    }

    fun markEpisodeAsPlayedAndRemoveFromQueue(episodeId: String) {
        viewModelScope.launch {
            repository.markEpisodeAsPlayedAndRemoveFromQueue(episodeId)
            syncWithNextcloud()
        }
    }

    fun markEpisodeAsPlayed(episodeId: String) {
        viewModelScope.launch {
            repository.markEpisodeAsPlayed(episodeId)
            syncWithNextcloud()
        }
    }

    fun toggleEpisodePlayed(episodeId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.markEpisodePlayed(episodeId, !currentStatus)
            syncWithNextcloud()
        }
    }

    fun clearAllNewReleases() {
        viewModelScope.launch {
            repository.clearAllNewReleases()
            syncWithNextcloud()
        }
    }

    fun sortQueue(sortOption: QueueSortOption) {
        val currentQueue = queue.value
        if (currentQueue.size <= 2) return

        // Regla inamovible: el primer elemento (episodio activo) permanece SIEMPRE en posición 0
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
            syncWithNextcloud()
        }
    }

    private fun getEpisodeSizeBytes(item: QueueEpisodeItem): Long {
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

    fun clearQueue() {
        viewModelScope.launch {
            val q = queue.value
            val hasPlaying = q.any { it.isCurrentlyPlaying }
            if (hasPlaying) {
                repository.clearSubsequentQueue()
            } else {
                repository.clearQueue()
            }
            syncWithNextcloud()
        }
    }

    fun toggleFavorite(episodeId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(episodeId, !currentStatus)
            syncWithNextcloud()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // ==========================================
    // DETALLE DE PODCAST Y GESTIÓN
    // ==========================================

    fun getPodcastFlow(podcastId: String, feedUrl: String): kotlinx.coroutines.flow.Flow<PodcastEntity?> =
        repository.getPodcastByIdOrFeedUrl(podcastId, feedUrl)

    fun getEpisodesForPodcastFlow(podcastId: String): kotlinx.coroutines.flow.Flow<List<EpisodeEntity>> =
        repository.getEpisodesForPodcast(podcastId)

    suspend fun loadPodcastFeed(feedUrl: String): Result<PodcastEntity> {
        return repository.fetchAndStorePodcast(feedUrl)
    }

    fun unsubscribeFromPodcast(podcastId: String) {
        viewModelScope.launch {
            repository.unsubscribeFromPodcast(podcastId)
            syncWithNextcloud()
        }
    }

    fun toggleSubscription(podcast: PodcastEntity) {
        viewModelScope.launch {
            if (podcast.isSubscribed) {
                repository.unsubscribeFromPodcast(podcast.id)
            } else {
                if (podcast.isLocalFolder) {
                    val updated = podcast.copy(isSubscribed = true, subscribedAt = System.currentTimeMillis())
                    database.podcastDao().insertPodcast(updated)
                } else {
                    repository.subscribeToFeed(podcast.feedUrl)
                }
            }
            syncWithNextcloud()
        }
    }

    fun updatePodcastSettings(
        podcastId: String,
        customSpeed: Float?,
        skipIntroSeconds: Int,
        skipEndingSeconds: Int,
        autoDownloadEnabled: Boolean?,
        episodeSortOrder: String,
        autoEnqueueRule: String = "DISABLED"
    ) {
        viewModelScope.launch {
            repository.updatePodcastSettings(
                podcastId = podcastId,
                customSpeed = customSpeed,
                skipIntroSeconds = skipIntroSeconds,
                skipEndingSeconds = skipEndingSeconds,
                autoDownloadEnabled = autoDownloadEnabled,
                episodeSortOrder = episodeSortOrder,
                autoEnqueueRule = autoEnqueueRule
            )
        }
    }

    fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String? = null) {
        viewModelScope.launch {
            database.podcastDao().updateDownloadStatus(episodeId, isDownloaded, localPath)
        }
    }

    fun importLocalFolder(
        context: android.content.Context,
        treeUri: android.net.Uri,
        title: String? = null,
        artworkUri: String? = null,
        onSuccess: (PodcastEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _subscriptionLoading.value = true
            _subscriptionMessage.value = "Importando audios de carpeta local..."
            val result = repository.importLocalFolder(context, treeUri, title, artworkUri)
            result.onSuccess {
                _subscriptionMessage.value = "Carpeta importada con éxito: ${it.title}"
                _subscriptionLoading.value = false
                onSuccess(it)
                syncWithNextcloud()
            }.onFailure {
                _subscriptionMessage.value = "Error importando carpeta: ${it.localizedMessage}"
                _subscriptionLoading.value = false
                onError(it.localizedMessage ?: "Error al importar carpeta local")
            }
        }
    }

    suspend fun rescanLocalFolderPodcast(podcastId: String): Int {
        val app = getApplication<Application>()
        return repository.rescanLocalFolder(app, podcastId)
    }

    suspend fun reconnectLocalFolderPodcast(
        podcastId: String,
        newTreeUri: Uri
    ): Result<Int> {
        val app = getApplication<Application>()
        val result = repository.reconnectLocalFolder(app, podcastId, newTreeUri)
        if (result.isSuccess) {
            syncWithNextcloud(debounceMs = 2500L)
        }
        return result
    }
}
