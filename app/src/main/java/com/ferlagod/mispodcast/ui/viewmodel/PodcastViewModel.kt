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

import com.ferlagod.mispodcast.alerts.AppAlerts
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.util.rethrowIfCancellation
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadManager
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueSortOption
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import com.ferlagod.mispodcast.sync.SyncWorker
import com.ferlagod.mispodcast.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.ferlagod.mispodcast.data.local.model.SubscriptionSortOption
import com.ferlagod.mispodcast.data.mapper.PodcastLanguageMapper
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * ViewModel principal para la gestión del catálogo de podcasts, suscripciones y exploración.
 *
 * Administra la obtención de tendencias de Apple Podcasts, el feed de novedades de podcasts suscritos,
 * el escaneo de carpetas locales SAF, favoritos, historial y solicitudes de descarga offline.
 */
class PodcastViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PodcastRepository = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.podcastRepository,
    private val preferencesManager: UserPreferencesManager = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.preferencesManager,
    private val syncManager: NextcloudSyncManager = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.nextcloudSyncManager,
    private val database: PodcastDatabase = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.database
) : AndroidViewModel(application) {

    private val downloadManager = EpisodeDownloadManager.getInstance(application, database.podcastDao())

    val currentLanguageCode: StateFlow<String> = preferencesManager.userSettingsFlow
        .map { it.languageCode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")

    val downloadStates: StateFlow<Map<String, EpisodeDownloadState>> = downloadManager.downloadStates

    fun downloadEpisode(episode: EpisodeEntity) {
        downloadManager.downloadEpisode(episode)
    }

    /** Vuelve a encolar las descargas que fallaron (acción «Reintentar» de un aviso). */
    fun retryDownloads(episodeIds: List<String>) {
        viewModelScope.launch {
            episodeIds.forEach { id ->
                database.podcastDao().getEpisodeById(id)?.let { downloadManager.downloadEpisode(it) }
            }
        }
    }

    fun getEpisodeDownloadState(episode: EpisodeEntity): EpisodeDownloadState {
        return downloadManager.getDownloadState(episode)
    }

    // ==========================================
    // ESTADOS REACTIVOS DESDE ROOM
    // ==========================================

    val subscriptionSortOption: StateFlow<SubscriptionSortOption> = preferencesManager.userSettingsFlow
        .map { it.subscriptionSortOption }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubscriptionSortOption.RECENTLY_SUBSCRIBED)

    val subscribedPodcasts: StateFlow<List<PodcastEntity>> = combine(
        repository.getSubscribedPodcasts(),
        subscriptionSortOption
    ) { podcasts, sortOption ->
        sortPodcasts(podcasts, sortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // ==========================================
    // RECOMENDACIONES DIRECTAS APPLE PODCASTS
    // ==========================================

    private val _recommendations = MutableStateFlow<List<ApplePodcastDto>>(emptyList())
    val recommendations: StateFlow<List<ApplePodcastDto>> = _recommendations.asStateFlow()

    // Vacíos hasta la primera carga: la UI muestra entonces su título por defecto traducido
    private val _recommendationTitle = MutableStateFlow("")
    val recommendationTitle: StateFlow<String> = _recommendationTitle.asStateFlow()

    private val _recommendationSubtitle = MutableStateFlow("")
    val recommendationSubtitle: StateFlow<String> = _recommendationSubtitle.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private val _recommendationError = MutableStateFlow<UiText?>(null)
    val recommendationError: StateFlow<UiText?> = _recommendationError.asStateFlow()

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
                _recommendationError.value = it.localizedMessage?.let(UiText::Raw) ?: UiText.Res(R.string.msg_recommendations_error)
            }
            _isLoadingRecommendations.value = false
        }
    }

    init {
        // Traer al abrir la app lo hecho en otros dispositivos (posición, cola, favoritos...)
        viewModelScope.launch {
            val settings = preferencesManager.userSettingsFlow.first()
            if (settings.nextcloudServerUrl.isNotBlank() && settings.nextcloudUsername.isNotBlank() &&
                settings.nextcloudAppPassword.isNotBlank()
            ) {
                SyncWorker.scheduleImmediateSync(application)
            }
        }

        // Observar reactivamente el cambio de idioma/país para recargar recomendaciones
        viewModelScope.launch {
            preferencesManager.userSettingsFlow
                .map { it.languageCode }
                .distinctUntilChanged()
                .collect { langCode ->
                    loadRecommendations(langCode)
                }
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
                result.onSuccess { summary ->
                    AppAlerts.onFeedRefreshFinished(summary.failedTitles, summary.attemptedFeeds)
                    syncWithNextcloud(debounceMs = 1500L)
                    onFinished?.invoke(summary.newEpisodes)
                }.onFailure {
                    onFinished?.invoke(0)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
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
    // SINCRONIZACIÓN NEXTCLOUD
    // ==========================================

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

    private val _searchError = MutableStateFlow<UiText?>(null)
    val searchError: StateFlow<UiText?> = _searchError.asStateFlow()

    private val _subscriptionLoading = MutableStateFlow(false)
    val subscriptionLoading: StateFlow<Boolean> = _subscriptionLoading.asStateFlow()

    private val _subscriptionMessage = MutableStateFlow<UiText?>(null)
    val subscriptionMessage: StateFlow<UiText?> = _subscriptionMessage.asStateFlow()

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
                _searchError.value = UiText.Res(R.string.msg_search_error, it.localizedMessage.orEmpty())
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
            _subscriptionMessage.value = UiText.Res(R.string.msg_downloading_feed)
            val result = repository.subscribeToFeed(feedUrl)
            result.onSuccess {
                _subscriptionMessage.value = UiText.Res(R.string.msg_subscribed_to, it.title)
                syncWithNextcloud()
                onFinished(true)
            }.onFailure {
                _subscriptionMessage.value = UiText.Res(R.string.msg_subscribe_error, it.localizedMessage.orEmpty())
                onFinished(false)
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

    // ==========================================
    // ACCIONES RÁPIDAS (DESLIZAR) CON DESHACER
    // ==========================================

    /** Acción rápida recién aplicada que la interfaz ofrece deshacer en un Snackbar. */
    data class UndoableAction(
        val id: Long,
        val message: UiText,
        internal val snapshot: com.ferlagod.mispodcast.data.local.model.EpisodeUndoSnapshot
    )

    private val _undoableAction = MutableStateFlow<UndoableAction?>(null)
    val undoableAction: StateFlow<UndoableAction?> = _undoableAction.asStateFlow()

    private fun performUndoable(episodeId: String, message: UiText, action: suspend () -> Unit) {
        viewModelScope.launch {
            val snapshot = repository.captureEpisodeUndoSnapshot(episodeId)
            action()
            syncWithNextcloud()
            if (snapshot != null) {
                _undoableAction.value = UndoableAction(System.nanoTime(), message, snapshot)
            }
        }
    }

    fun swipeAddToQueue(episodeId: String) =
        performUndoable(episodeId, UiText.Res(R.string.toast_added_to_queue)) {
            repository.addEpisodeToQueue(episodeId)
        }

    fun swipeTogglePlayed(episodeId: String, currentStatus: Boolean) =
        performUndoable(
            episodeId,
            UiText.Res(if (currentStatus) R.string.toast_marked_unplayed else R.string.toast_marked_played)
        ) {
            repository.markEpisodePlayed(episodeId, !currentStatus)
        }

    fun swipeRemoveFromQueue(episodeId: String) =
        performUndoable(episodeId, UiText.Res(R.string.snackbar_removed_from_queue)) {
            repository.removeEpisodeFromQueue(episodeId)
        }

    fun swipeMarkPlayedAndRemoveFromQueue(episodeId: String) =
        performUndoable(episodeId, UiText.Res(R.string.snackbar_marked_played_removed)) {
            repository.markEpisodeAsPlayedAndRemoveFromQueue(episodeId)
        }

    fun undoAction(actionId: Long) {
        val action = _undoableAction.value?.takeIf { it.id == actionId } ?: return
        _undoableAction.value = null
        viewModelScope.launch {
            repository.restoreEpisodeUndoSnapshot(action.snapshot)
            syncWithNextcloud()
        }
    }

    fun dismissUndoableAction(actionId: Long) {
        if (_undoableAction.value?.id == actionId) _undoableAction.value = null
    }

    fun clearAllNewReleases() {
        viewModelScope.launch {
            repository.clearAllNewReleases()
            syncWithNextcloud()
        }
    }

    /**
     * Separa el episodio activo del resto de la cola con el mismo criterio que la pantalla de cola:
     * el episodio en reproducción (aunque no esté arriba) o, si no hay ninguno, el primero.
     */
    private fun splitActiveFromQueue(
        currentQueue: List<QueueEpisodeItem>,
        activeEpisodeId: String?
    ): Pair<QueueEpisodeItem?, List<QueueEpisodeItem>> {
        val activeId = activeEpisodeId ?: currentQueue.firstOrNull()?.episode?.id
        val active = currentQueue.firstOrNull { it.episode.id == activeId }
        return active to currentQueue.filter { it.episode.id != activeId }
    }

    fun sortQueue(sortOption: QueueSortOption, activeEpisodeId: String? = null) {
        val currentQueue = queue.value
        if (currentQueue.size < 2) return

        // El episodio activo queda en cabeza y el resto se ordena según el criterio elegido
        val (currentActive, subsequent) = splitActiveFromQueue(currentQueue, activeEpisodeId)
        if (subsequent.size < 2) return

        viewModelScope.launch {
            val sortedSubsequent = when (sortOption) {
                QueueSortOption.DURATION_ASC -> subsequent.sortedBy { it.episode.duration }
                QueueSortOption.DURATION_DESC -> subsequent.sortedByDescending { it.episode.duration }
                QueueSortOption.SIZE_ASC, QueueSortOption.SIZE_DESC -> {
                    // Leer el tamaño de cada fichero una sola vez y fuera del hilo principal
                    val sizes = withContext(Dispatchers.IO) {
                        subsequent.associate { it.episode.id to getEpisodeSizeBytes(it) }
                    }
                    if (sortOption == QueueSortOption.SIZE_ASC) subsequent.sortedBy { sizes[it.episode.id] }
                    else subsequent.sortedByDescending { sizes[it.episode.id] }
                }
                QueueSortOption.NAME_ASC -> subsequent.sortedBy { it.episode.title.lowercase() }
                QueueSortOption.NAME_DESC -> subsequent.sortedByDescending { it.episode.title.lowercase() }
                QueueSortOption.DATE_DESC -> subsequent.sortedByDescending { it.episode.pubDate }
                QueueSortOption.DATE_ASC -> subsequent.sortedBy { it.episode.pubDate }
            }

            val newOrderedIds = listOfNotNull(currentActive?.episode?.id) + sortedSubsequent.map { it.episode.id }
            repository.reorderQueue(newOrderedIds)
            syncWithNextcloud()
        }
    }

    fun setSubscriptionSortOption(option: SubscriptionSortOption) {
        viewModelScope.launch {
            preferencesManager.setSubscriptionSortOption(option)
        }
    }

    private fun sortPodcasts(podcasts: List<PodcastEntity>, sortOption: SubscriptionSortOption): List<PodcastEntity> {
        return when (sortOption) {
            SubscriptionSortOption.RECENTLY_SUBSCRIBED -> podcasts.sortedWith(
                compareByDescending<PodcastEntity> { it.subscribedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SubscriptionSortOption.OLDEST_SUBSCRIBED -> podcasts.sortedWith(
                compareBy<PodcastEntity> { it.subscribedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SubscriptionSortOption.TITLE_ASC -> podcasts.sortedWith(
                compareBy<PodcastEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SubscriptionSortOption.TITLE_DESC -> podcasts.sortedWith(
                compareByDescending<PodcastEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SubscriptionSortOption.RECENTLY_UPDATED -> podcasts.sortedWith(
                compareByDescending<PodcastEntity> { it.lastUpdated }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SubscriptionSortOption.AUTHOR_ASC -> podcasts.sortedWith(
                compareBy<PodcastEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.author }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
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
            } catch (_: SecurityException) {
                // Sin acceso al fichero: se estima el tamaño por la duración
            }
        }
        return item.episode.duration * 16L
    }

    fun moveSubsequentQueueItem(fromIndex: Int, toIndex: Int, activeEpisodeId: String? = null) {
        val currentQueue = queue.value
        if (currentQueue.size < 2) return
        val (currentActive, others) = splitActiveFromQueue(currentQueue, activeEpisodeId)
        val subsequent = others.toMutableList()

        if (fromIndex in subsequent.indices && toIndex in subsequent.indices && fromIndex != toIndex) {
            val item = subsequent.removeAt(fromIndex)
            subsequent.add(toIndex, item)
            // El episodio activo conserva su posición original en la cola
            val reordered = subsequent.map { it.episode.id }.toMutableList()
            if (currentActive != null) {
                val activeIndex = currentQueue.indexOf(currentActive).coerceIn(0, reordered.size)
                reordered.add(activeIndex, currentActive.episode.id)
            }
            val newOrderedIds = reordered.toList()
            viewModelScope.launch {
                repository.reorderQueue(newOrderedIds)
                syncWithNextcloud()
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
            syncWithNextcloud()
        }
    }

    // ==========================================
    // DETALLE DE PODCAST Y GESTIÓN
    // ==========================================

    fun getPodcastFlow(podcastId: String, feedUrl: String): kotlinx.coroutines.flow.Flow<PodcastEntity?> =
        repository.getPodcastByIdOrFeedUrl(podcastId, feedUrl)

    fun getEpisodesForPodcastFlow(podcastId: String): kotlinx.coroutines.flow.Flow<List<EpisodeEntity>> =
        repository.getEpisodesForPodcast(podcastId)

    /** Indica si el podcast ya está guardado en Room (consulta directa, sin esperar al Flow). */
    suspend fun isPodcastStored(podcastId: String, feedUrl: String): Boolean =
        repository.getPodcastDirect(podcastId, feedUrl) != null

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
        autoEnqueueRule: String = "DISABLED",
        newEpisodeNotifications: Boolean = true,
        autoDeleteRule: String = "DEFAULT"
    ) {
        viewModelScope.launch {
            repository.updatePodcastSettings(
                newEpisodeNotifications = newEpisodeNotifications,
                autoDeleteRule = autoDeleteRule,
                podcastId = podcastId,
                customSpeed = customSpeed,
                skipIntroSeconds = skipIntroSeconds,
                skipEndingSeconds = skipEndingSeconds,
                autoDownloadEnabled = autoDownloadEnabled,
                episodeSortOrder = episodeSortOrder,
                autoEnqueueRule = autoEnqueueRule
            )
            syncWithNextcloud(debounceMs = 2500L)
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
            _subscriptionMessage.value = UiText.Res(R.string.msg_importing_folder)
            val result = repository.importLocalFolder(context, treeUri, title, artworkUri)
            result.onSuccess {
                _subscriptionMessage.value = UiText.Res(R.string.msg_folder_imported, it.title)
                _subscriptionLoading.value = false
                onSuccess(it)
                syncWithNextcloud()
            }.onFailure {
                _subscriptionMessage.value = UiText.Res(R.string.msg_folder_import_error, it.localizedMessage.orEmpty())
                _subscriptionLoading.value = false
                onError(it.localizedMessage.orEmpty())
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
