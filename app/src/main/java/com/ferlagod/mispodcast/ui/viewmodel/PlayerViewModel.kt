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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.playback.PodcastPlaybackService
import com.ferlagod.mispodcast.data.local.model.QueueItemEntityWithEpisode
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ferlagod.mispodcast.data.local.entities.BookmarkEntity
import com.ferlagod.mispodcast.data.model.Chapter
import com.ferlagod.mispodcast.data.model.TranscriptSegment
import com.ferlagod.mispodcast.data.repository.ChapterRepository
import com.ferlagod.mispodcast.data.repository.TranscriptRepository
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel encargado del control y monitoreo del reproductor de audio.
 *
 * Conecta la interfaz de usuario con [PodcastPlaybackService] mediante [MediaController],
 * exponiendo flujos reactivos ([StateFlow]) para la posición actual, duración, estado de reproducción,
 * velocidad de escucha y saltos inteligentes hacia adelante y atrás.
 */
class PlayerViewModel(
    application: Application,
    private val repository: PodcastRepository = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.podcastRepository
        ?: PodcastRepository(PodcastDatabase.getInstance(application).podcastDao()),
    private val syncManager: NextcloudSyncManager = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.nextcloudSyncManager
        ?: NextcloudSyncManager.getInstance(application),
    private val preferencesManager: com.ferlagod.mispodcast.data.preferences.UserPreferencesManager = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.preferencesManager
        ?: com.ferlagod.mispodcast.data.preferences.UserPreferencesManager.getInstance(application),
    private val sleepTimerManager: com.ferlagod.mispodcast.playback.SleepTimerManager = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.sleepTimerManager
        ?: com.ferlagod.mispodcast.playback.SleepTimerManager.getInstance(application),
    private val chapterRepository: ChapterRepository = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.chapterRepository
        ?: ChapterRepository(okhttp3.OkHttpClient()),
    private val transcriptRepository: TranscriptRepository = (application as? com.ferlagod.mispodcast.MisPodcastApplication)?.container?.transcriptRepository
        ?: TranscriptRepository(okhttp3.OkHttpClient())
) : AndroidViewModel(application) {

    val chapters: StateFlow<List<Chapter>> = chapterRepository.activeChapters
    val transcript: StateFlow<List<TranscriptSegment>> = transcriptRepository.activeTranscript
    val isTranscriptLoading: StateFlow<Boolean> = transcriptRepository.isLoading

    val sleepTimerState: StateFlow<com.ferlagod.mispodcast.playback.SleepTimerState> = sleepTimerManager.state

    val userSettings: StateFlow<com.ferlagod.mispodcast.data.preferences.UserSettings> = preferencesManager.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ferlagod.mispodcast.data.preferences.UserSettings())

    val queue: StateFlow<List<QueueEpisodeItem>> = repository.getQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueEpisodes: StateFlow<List<EpisodeEntity>> = repository.getQueueEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val currentEpisode: StateFlow<EpisodeEntity?> = _currentEpisode.asStateFlow()

    private val _podcastTitle = MutableStateFlow("Mis Podcast")
    val podcastTitle: StateFlow<String> = _podcastTitle.asStateFlow()

    private val _podcastArtworkUrl = MutableStateFlow("")
    val podcastArtworkUrl: StateFlow<String> = _podcastArtworkUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    val currentChapter: StateFlow<Chapter?> = combine(chapters, _currentPositionMs) { chList, pos ->
        if (chList.isEmpty()) null
        else chList.lastOrNull { pos >= it.startTimeMs } ?: chList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTranscriptSegment: StateFlow<TranscriptSegment?> = combine(transcript, _currentPositionMs) { list, pos ->
        list.find { pos in it.startTimeMs..it.endTimeMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<BookmarkEntity>> = _currentEpisode
        .flatMapLatest { ep ->
            if (ep == null) flowOf(emptyList())
            else repository.getBookmarksForEpisode(ep.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val availableSpeeds = PodcastPlaybackService.PLAYBACK_SPEEDS

    init {
        connectToService()
        startPositionPolling()
        restoreLastStateIfIdle()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PodcastPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
                val activeMediaId = mediaController?.currentMediaItem?.mediaId
                if (!activeMediaId.isNullOrBlank()) {
                    viewModelScope.launch {
                        repository.setCurrentPlaybackInQueue(activeMediaId)
                        val ep = repository.getEpisodeById(activeMediaId)
                        if (ep != null) {
                            _currentEpisode.value = ep
                            loadChaptersForEpisode(ep)
                            if (ep.currentPositionMs > 0 && (ep.duration <= 0 || ep.currentPositionMs < ep.duration - 5000) && mediaController?.currentPosition == 0L) {
                                mediaController?.seekTo(ep.currentPositionMs)
                            }
                        }
                    }
                } else {
                    restoreLastStateIfIdle()
                }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    private fun restoreLastStateIfIdle() {
        viewModelScope.launch {
            if (_currentEpisode.value == null) {
                val queueList = repository.getQueueList()
                if (queueList.isNotEmpty()) {
                    val first = queueList.first()
                    val freshEp = repository.getEpisodeById(first.episode.id) ?: first.episode
                    _currentEpisode.value = freshEp
                    loadChaptersForEpisode(freshEp)
                    _podcastTitle.value = first.podcastTitle
                    _podcastArtworkUrl.value = first.podcastArtworkUrl
                    _currentPositionMs.value = freshEp.currentPositionMs
                    _durationMs.value = freshEp.duration
                } else {
                    val played = repository.getPlayedOrInProgressEpisodes()
                    val candidate = played.firstOrNull { it.currentPositionMs > 0 && (it.duration <= 0 || it.currentPositionMs < it.duration - 5000) }
                    if (candidate != null) {
                        _currentEpisode.value = candidate
                        loadChaptersForEpisode(candidate)
                        _currentPositionMs.value = candidate.currentPositionMs
                        _durationMs.value = candidate.duration
                        val podcast = repository.getPodcastByIdSync(candidate.podcastId)
                        if (podcast != null) {
                            _podcastTitle.value = podcast.title
                            _podcastArtworkUrl.value = podcast.artworkUrl
                        }
                    }
                }
            }
        }
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _playbackSpeed.value = controller.playbackParameters.speed

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    val activeController = mediaController
                    val ep = _currentEpisode.value
                    if (activeController != null && ep != null) {
                        val currentPos = activeController.currentPosition
                        if (currentPos > 0L) {
                            viewModelScope.launch {
                                repository.updatePlaybackPosition(ep.id, currentPos)
                                syncManager.triggerSyncIfConfigured(debounceMs = 3000L)
                            }
                        }
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val episodeId = mediaItem.mediaId
                    viewModelScope.launch {
                        // Sincronizar en Room: colocar obligatoriamente en positionIndex = 0
                        repository.setCurrentPlaybackInQueue(episodeId)
                        val ep = repository.getEpisodeById(episodeId)
                        if (ep != null) {
                            _currentEpisode.value = ep
                            loadChaptersForEpisode(ep)
                        }
                    }
                    _podcastTitle.value = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Podcast"
                    _podcastArtworkUrl.value = mediaItem.mediaMetadata.artworkUri?.toString() ?: ""
                }
            }
        })
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    _currentPositionMs.value = controller.currentPosition
                    _durationMs.value = if (controller.duration > 0) controller.duration else 0L
                }
                delay(300)
            }
        }
    }

    fun playEpisode(
        episode: EpisodeEntity,
        podcastTitle: String,
        podcastArtwork: String
    ) {
        viewModelScope.launch {
            // 1. Consultar siempre en Room el valor fresco de currentPositionMs y datos del episodio
            val freshEpisode = repository.getEpisodeById(episode.id) ?: episode
            _currentEpisode.value = freshEpisode
            loadChaptersForEpisode(freshEpisode)
            _podcastTitle.value = podcastTitle
            _podcastArtworkUrl.value = podcastArtwork

            // Asegurar que el podcast y el episodio existen en Room para que la cola relacional nunca los omita
            if (freshEpisode.podcastId.isNotBlank()) {
                val existingPod = repository.getPodcastByIdSync(freshEpisode.podcastId)
                if (existingPod == null) {
                    repository.insertPodcast(
                        PodcastEntity(
                            id = freshEpisode.podcastId,
                            title = podcastTitle,
                            author = "",
                            description = "",
                            feedUrl = freshEpisode.podcastId,
                            artworkUrl = podcastArtwork,
                            isSubscribed = false
                        )
                    )
                }
            }
            repository.insertOrUpdateEpisode(freshEpisode)

            repository.setCurrentPlaybackInQueue(freshEpisode.id)

            val controller = mediaController ?: withContext(Dispatchers.IO) {
                try {
                    controllerFuture?.get()
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch

            val extras = Bundle().apply {
                putString("podcastId", freshEpisode.podcastId)
                putString("episodeId", freshEpisode.id)
            }

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(freshEpisode.title)
                .setArtist(podcastTitle)
                .setAlbumTitle(podcastTitle)
                .setArtworkUri(Uri.parse(if (freshEpisode.imageUrl.isNotBlank()) freshEpisode.imageUrl else podcastArtwork))
                .setExtras(extras)
                .build()

            val playbackUri = when {
                // 1. Priorizar audio descargado: isDownloaded == true, localFilePath no nulo y archivo físico existe
                freshEpisode.isDownloaded && !freshEpisode.localFilePath.isNullOrBlank() && java.io.File(freshEpisode.localFilePath).exists() -> {
                    Uri.fromFile(java.io.File(freshEpisode.localFilePath))
                }
                // 2. Archivo físico local presente en el dispositivo
                !freshEpisode.localFilePath.isNullOrBlank() && java.io.File(freshEpisode.localFilePath).exists() -> {
                    Uri.fromFile(java.io.File(freshEpisode.localFilePath))
                }
                // 3. Audio local de carpeta SAF (content://)
                !freshEpisode.localFilePath.isNullOrBlank() && freshEpisode.localFilePath.startsWith("content://") -> {
                    Uri.parse(freshEpisode.localFilePath)
                }
                freshEpisode.audioUrl.startsWith("content://") -> {
                    Uri.parse(freshEpisode.audioUrl)
                }
                // 4. Fallback a audioUrl de internet si no está descargado o el archivo físico no existe
                else -> {
                    val cleanUrl = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(freshEpisode.audioUrl)
                    Uri.parse(cleanUrl)
                }
            }

            val mediaItem = MediaItem.Builder()
                .setMediaId(freshEpisode.id)
                .setUri(playbackUri)
                .setMediaMetadata(mediaMetadata)
                .build()

            val resumePositionMs = if (freshEpisode.currentPositionMs > 0L && (freshEpisode.duration <= 0L || freshEpisode.currentPositionMs < freshEpisode.duration - 5000L)) {
                freshEpisode.currentPositionMs
            } else {
                0L
            }

            if (resumePositionMs > 0L) {
                _currentPositionMs.value = resumePositionMs
                if (freshEpisode.isPlayed) {
                    repository.updatePlaybackPosition(freshEpisode.id, resumePositionMs)
                }
                controller.setMediaItem(mediaItem, resumePositionMs)
            } else {
                controller.setMediaItem(mediaItem)
            }
            controller.prepare()
            if (resumePositionMs > 0L) {
                controller.seekTo(resumePositionMs)
            }
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.currentMediaItem == null) {
            val ep = _currentEpisode.value
            if (ep != null) {
                playEpisode(ep, _podcastTitle.value, _podcastArtworkUrl.value)
                return
            }
        }
        if (controller.isPlaying) {
            val pos = controller.currentPosition
            val ep = _currentEpisode.value
            if (ep != null && pos > 0L) {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updatePlaybackPosition(ep.id, pos)
                }
            }
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        val ep = _currentEpisode.value
        if (ep != null && positionMs >= 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlaybackPosition(ep.id, positionMs)
                syncManager.triggerSyncIfConfigured(debounceMs = 3000L)
            }
        }
    }

    fun seekForward(seconds: Int? = null) {
        val controller = mediaController ?: return
        val sec = seconds ?: userSettings.value.skipForwardSeconds
        val maxDuration = if (controller.duration > 0) controller.duration else Long.MAX_VALUE
        val newPos = min(maxDuration, controller.currentPosition + (sec * 1000L))
        controller.seekTo(newPos)
        _currentPositionMs.value = newPos
        val ep = _currentEpisode.value
        if (ep != null && newPos >= 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlaybackPosition(ep.id, newPos)
            }
        }
    }

    fun seekBack(seconds: Int? = null) {
        val controller = mediaController ?: return
        val sec = seconds ?: userSettings.value.skipBackwardSeconds
        val newPos = max(0L, controller.currentPosition - (sec * 1000L))
        controller.seekTo(newPos)
        _currentPositionMs.value = newPos
        val ep = _currentEpisode.value
        if (ep != null && newPos >= 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlaybackPosition(ep.id, newPos)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = (Math.round(speed * 10f) / 10f).coerceIn(0.5f, 3.0f)
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(clamped)
        _playbackSpeed.value = clamped
    }

    fun cyclePlaybackSpeed() {
        val current = _playbackSpeed.value
        val presets = listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)
        val currentIndex = presets.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextIndex = (if (currentIndex >= 0) currentIndex + 1 else 0) % presets.size
        setPlaybackSpeed(presets[nextIndex])
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    // ==========================================
    // TEMPORIZADOR DE APAGADO (SLEEP TIMER)
    // ==========================================

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerManager.startMinutesTimer(minutes)
    }

    fun startSleepTimerEndOfEpisode() {
        sleepTimerManager.startEndOfEpisodeTimer()
    }

    fun extendSleepTimer(additionalMinutes: Int = 15) {
        sleepTimerManager.extendTimer(additionalMinutes)
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancelTimer()
    }

    fun setSleepTimerShakeToExtend(enabled: Boolean) {
        sleepTimerManager.setShakeToExtend(enabled)
    }

    // ==========================================
    // CAPÍTULOS (PODCASTING 2.0 & ID3 CHAP)
    // ==========================================

    private fun loadChaptersForEpisode(episode: EpisodeEntity) {
        chapterRepository.setActiveEpisodeId(episode.id)
        viewModelScope.launch {
            chapterRepository.getChapters(episode)
        }
        loadTranscriptForEpisode(episode)
    }

    fun loadTranscriptForEpisode(episode: EpisodeEntity) {
        transcriptRepository.setActiveEpisodeId(episode.id)
        if (!episode.transcriptUrl.isNullOrBlank()) {
            viewModelScope.launch {
                transcriptRepository.getTranscript(episode)
            }
        }
    }

    fun seekToNextChapter() {
        val chList = chapters.value
        if (chList.isEmpty()) return
        val pos = currentPositionMs.value
        val next = chList.firstOrNull { it.startTimeMs > pos + 1000L }
        if (next != null) {
            seekTo(next.startTimeMs)
        }
    }

    fun seekToPreviousChapter() {
        val chList = chapters.value
        if (chList.isEmpty()) return
        val pos = currentPositionMs.value
        val curr = currentChapter.value
        if (curr != null && pos - curr.startTimeMs > 3000L) {
            seekTo(curr.startTimeMs)
            return
        }
        val prev = chList.lastOrNull { it.startTimeMs < (curr?.startTimeMs ?: pos) - 500L }
        if (prev != null) {
            seekTo(prev.startTimeMs)
        } else if (curr != null) {
            seekTo(curr.startTimeMs)
        }
    }

    fun seekToChapter(chapter: Chapter) {
        seekTo(chapter.startTimeMs)
    }

    /**
     * Salta al siguiente episodio disponible en la cola de reproducción.
     */
    fun skipToNextEpisode() {
        viewModelScope.launch {
            val queue = repository.getQueueList()
            val currentId = _currentEpisode.value?.id
            val currentIndex = queue.indexOfFirst { it.episode.id == currentId }
            if (currentIndex >= 0 && currentIndex < queue.size - 1) {
                val next = queue[currentIndex + 1]
                playEpisode(next.episode, next.podcastTitle, next.podcastArtworkUrl)
            } else if (currentIndex == -1 && queue.isNotEmpty()) {
                val first = queue.first()
                playEpisode(first.episode, first.podcastTitle, first.podcastArtworkUrl)
            }
        }
    }

    /**
     * Retrocede al episodio anterior en la cola de reproducción o reinicia el actual.
     */
    fun skipToPreviousEpisode() {
        viewModelScope.launch {
            val queue = repository.getQueueList()
            val currentId = _currentEpisode.value?.id
            val currentIndex = queue.indexOfFirst { it.episode.id == currentId }
            if (currentIndex > 0) {
                val prev = queue[currentIndex - 1]
                playEpisode(prev.episode, prev.podcastTitle, prev.podcastArtworkUrl)
            } else {
                seekTo(0L)
            }
        }
    }

    // ==========================================
    // MEJORA DE VOZ, AUDIO MONO Y SILENCIO
    // ==========================================

    fun setVoiceBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVoiceBoostEnabled(enabled) }
    }

    fun setVoiceBoostGainMb(gainMb: Int) {
        viewModelScope.launch { preferencesManager.setVoiceBoostGainMb(gainMb) }
    }

    fun setMonoAudioEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setMonoAudioEnabled(enabled) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSkipSilence(enabled) }
    }

    // ==========================================
    // MARCADORES Y CLIPS (BOOKMARKS)
    // ==========================================

    fun addBookmark(note: String = "") {
        val ep = currentEpisode.value ?: return
        val pos = currentPositionMs.value
        val formattedTime = formatBookmarkTime(pos)
        val defaultNote = if (note.isBlank()) "Momento en $formattedTime" else note.trim()

        viewModelScope.launch {
            val bookmark = BookmarkEntity(
                episodeId = ep.id,
                podcastId = ep.podcastId,
                podcastTitle = podcastTitle.value,
                episodeTitle = ep.title,
                positionMs = pos,
                note = defaultNote,
                createdAt = System.currentTimeMillis()
            )
            repository.addBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
        }
    }

    fun generateShareText(bookmark: BookmarkEntity): String {
        val timeFormatted = formatBookmarkTime(bookmark.positionMs)
        val ep = currentEpisode.value
        val epTitle = ep?.title ?: bookmark.episodeTitle
        val pTitle = podcastTitle.value.ifBlank { bookmark.podcastTitle }
        val audioUrl = ep?.audioUrl.orEmpty()
        val seconds = bookmark.positionMs / 1000L

        val shareLink = if (audioUrl.isNotBlank()) {
            if (audioUrl.contains("?")) "$audioUrl&t=$seconds" else "$audioUrl?t=$seconds"
        } else {
            ""
        }

        val notePart = if (bookmark.note.isNotBlank() && !bookmark.note.startsWith("Momento en")) " \"${bookmark.note}\"" else ""

        return buildString {
            append("🎧 Escucha")
            if (notePart.isNotBlank()) append(notePart)
            append(" en \"$epTitle\" ($pTitle) desde el minuto $timeFormatted:")
            if (shareLink.isNotBlank()) {
                append("\n$shareLink")
            }
        }
    }

    fun shareBookmark(context: Context, bookmark: BookmarkEntity) {
        val text = generateShareText(bookmark)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Momento en ${bookmark.episodeTitle}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir momento del podcast")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun formatBookmarkTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
