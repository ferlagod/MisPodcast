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

import com.ferlagod.mispodcast.util.rethrowIfCancellation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ferlagod.mispodcast.MainActivity
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano (Foreground Service) basado en AndroidX Media3 ([MediaLibraryService]).
 *
 * Arquitectura modular compuesta por:
 * - [PlaybackController]: Gestión de saltos inteligentes (seek), velocidades y botones de acción.
 * - [AutoBrowseTree]: Catálogo y jerarquía navegable para Android Auto y MediaBrowser.
 * - [ProgressTracker]: Monitoreo periódico del progreso, persistencia en Room y sincronización con Nextcloud.
 */
@OptIn(UnstableApi::class)
class PodcastPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    // Dependencias inyectadas desde AppContainer
    private lateinit var database: PodcastDatabase
    private lateinit var syncManager: NextcloudSyncManager
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var searchService: ApplePodcastSearchService
    private lateinit var rssParser: RssFeedParser

    // Módulos especializados
    private lateinit var autoBrowseTree: AutoBrowseTree
    private lateinit var playbackController: PlaybackController
    private lateinit var progressTracker: ProgressTracker
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var chapterRepository: com.ferlagod.mispodcast.data.repository.ChapterRepository

    private val monoAudioProcessor = MonoAudioProcessor()
    private val audioEffectsManager = AudioEffectsManager()

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var currentPlayingEpisodeId: String? = null
    private var currentPodcastId: String? = null
    private var currentPodcastTitle: String? = null
    private var appliedDefaultSpeed: Float? = null
    private var currentPodcastHasCustomSpeed = false

    companion object {
        private const val TAG = "PodcastPlaybackService"

        const val CHANNEL_ID = "mis_podcast_playback_channel"
        const val NOTIFICATION_ID = 1001

        val PLAYBACK_SPEEDS = PlaybackController.PLAYBACK_SPEEDS
        val COMMAND_REPLAY_10 = PlaybackController.COMMAND_REPLAY_10
        val COMMAND_FORWARD_30 = PlaybackController.COMMAND_FORWARD_30
        val COMMAND_CYCLE_SPEED = PlaybackController.COMMAND_CYCLE_SPEED
        val COMMAND_NEXT_CHAPTER = PlaybackController.COMMAND_NEXT_CHAPTER
        val COMMAND_PREV_CHAPTER = PlaybackController.COMMAND_PREV_CHAPTER

        const val ACTION_PLAY_PAUSE = "com.ferlagod.mispodcast.action.PLAY_PAUSE"
        const val ACTION_SKIP_FORWARD = "com.ferlagod.mispodcast.action.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.ferlagod.mispodcast.action.SKIP_BACKWARD"

        const val ROOT_ID = AutoBrowseTree.ROOT_ID
        const val MEDIA_ID_QUEUE = AutoBrowseTree.MEDIA_ID_QUEUE
        const val MEDIA_ID_PODCASTS = AutoBrowseTree.MEDIA_ID_PODCASTS
        const val MEDIA_ID_NEW_RELEASES = AutoBrowseTree.MEDIA_ID_NEW_RELEASES
        const val MEDIA_ID_DOWNLOADS = AutoBrowseTree.MEDIA_ID_DOWNLOADS
        const val MEDIA_ID_HISTORY = AutoBrowseTree.MEDIA_ID_HISTORY
        const val PREFIX_PODCAST = AutoBrowseTree.PREFIX_PODCAST
        const val PREFIX_SEARCH_PODCAST = AutoBrowseTree.PREFIX_SEARCH_PODCAST
    }

    override fun onCreate() {
        super.onCreate()
        val container = (applicationContext as com.ferlagod.mispodcast.MisPodcastApplication).container
        database = container.database
        syncManager = container.nextcloudSyncManager
        preferencesManager = container.preferencesManager
        searchService = container.applePodcastSearchService
        rssParser = container.rssFeedParser

        autoBrowseTree = AutoBrowseTree(database, searchService, rssParser, ::getString)
        playbackController = PlaybackController(database, autoBrowseTree)
        progressTracker = ProgressTracker(applicationContext, database, syncManager, serviceScope)
        sleepTimerManager = container.sleepTimerManager
        chapterRepository = container.chapterRepository

        createNotificationChannel()
        initializePlayer()
        // El temporizador expira en un hilo de fondo: ExoPlayer solo admite llamadas desde el hilo principal
        sleepTimerManager.registerPauseAction {
            serviceScope.launch(Dispatchers.Main) { player.pause() }
        }
        sleepTimerManager.registerVolumeAction { volume ->
            serviceScope.launch(Dispatchers.Main) { player.volume = volume }
        }
        sleepTimerManager.setPlaybackActive(player.isPlaying)
        initializeMediaLibrarySession()
        observeUserSettings()
        progressTracker.startPeriodicSaver(
            player = player,
            currentEpisodeSupplier = { currentPlayingEpisodeId },
            currentPodcastSupplier = { currentPodcastId },
            currentPodcastTitleSupplier = { currentPodcastTitle }
        )
        observeDatabaseChangesForAuto()
    }

    private data class ServiceSettings(
        val skipSilence: Boolean,
        val defaultSpeed: Float,
        val skipBackSeconds: Int,
        val skipForwardSeconds: Int,
        val voiceBoostEnabled: Boolean,
        val voiceBoostGainMb: Int,
        val monoAudioEnabled: Boolean
    )

    private fun observeUserSettings() {
        serviceScope.launch {
            preferencesManager.userSettingsFlow
                .map { s ->
                    ServiceSettings(
                        skipSilence = s.skipSilence,
                        defaultSpeed = s.defaultPlaybackSpeed,
                        skipBackSeconds = s.skipBackwardSeconds,
                        skipForwardSeconds = s.skipForwardSeconds,
                        voiceBoostEnabled = s.voiceBoostEnabled,
                        voiceBoostGainMb = s.voiceBoostGainMb,
                        monoAudioEnabled = s.monoAudioEnabled
                    )
                }
                .distinctUntilChanged()
                .collect { s ->
                    player.skipSilenceEnabled = s.skipSilence
                    monoAudioProcessor.isEnabled = s.monoAudioEnabled
                    audioEffectsManager.setVoiceBoostEnabled(s.voiceBoostEnabled)
                    audioEffectsManager.setTargetGainMb(s.voiceBoostGainMb)
                    playbackController.skipBackMs = s.skipBackSeconds * 1000L
                    playbackController.skipForwardMs = s.skipForwardSeconds * 1000L

                    // Aplicar la velocidad global al arrancar y cada vez que el usuario la cambie,
                    // salvo que el podcast en curso tenga su propia velocidad personalizada
                    if (s.defaultSpeed > 0f && s.defaultSpeed != appliedDefaultSpeed) {
                        appliedDefaultSpeed = s.defaultSpeed
                        if (!currentPodcastHasCustomSpeed) {
                            player.playbackParameters = PlaybackParameters(s.defaultSpeed)
                            playbackController.setSpeedIndexFromFloat(s.defaultSpeed)
                        }
                    }

                    mediaLibrarySession.setCustomLayout(
                        playbackController.buildCustomCommandButtons(
                            skipBackSeconds = s.skipBackSeconds,
                            skipForwardSeconds = s.skipForwardSeconds,
                            currentSpeed = player.playbackParameters.speed
                        )
                    )
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_playback_desc)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(monoAudioProcessor))
                    .build()
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MisPodcast/1.0 (Android; ExoPlayer)")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        audioEffectsManager.attachAudioSession(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectsManager.attachAudioSession(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                sleepTimerManager.setPlaybackActive(isPlaying)
                if (!isPlaying) {
                    progressTracker.saveCurrentPlaybackPosition(
                        player = player,
                        episodeId = currentPlayingEpisodeId,
                        podcastId = currentPodcastId,
                        podcastTitle = currentPodcastTitle ?: "Podcast"
                    )
                }
                syncWidgetState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                mediaLibrarySession.setCustomLayout(
                    playbackController.buildCustomCommandButtons(
                        currentSpeed = playbackParameters.speed
                    )
                )
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val id3Chapters = chapterRepository.parseId3Metadata(metadata)
                if (id3Chapters.isNotEmpty()) {
                    val epId = currentPlayingEpisodeId
                    if (!epId.isNullOrBlank()) {
                        chapterRepository.setChapters(epId, id3Chapters)
                        playbackController.setChapters(id3Chapters)
                        mediaLibrarySession.setCustomLayout(
                            playbackController.buildCustomCommandButtons(
                                currentSpeed = player.playbackParameters.speed
                            )
                        )
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousEpisodeId = currentPlayingEpisodeId
                val previousPodcastId = currentPodcastId

                if (mediaItem != null) {
                    val extras = mediaItem.mediaMetadata.extras
                    val newEpisodeId = extras?.getString("episodeId") ?: mediaItem.mediaId
                    val newPodcastId = extras?.getString("podcastId")
                    val newPodcastTitle = mediaItem.mediaMetadata.artist?.toString()
                        ?: mediaItem.mediaMetadata.albumTitle?.toString()

                    val finishedNaturally = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        !previousEpisodeId.isNullOrBlank() && previousEpisodeId != newEpisodeId
                    if (!previousEpisodeId.isNullOrBlank() && previousEpisodeId != newEpisodeId) {
                        progressTracker.flushPendingListenedTime(
                            episodeId = previousEpisodeId,
                            podcastId = previousPodcastId,
                            podcastTitle = currentPodcastTitle ?: "Podcast",
                            isCompleted = finishedNaturally
                        )
                    }

                    currentPlayingEpisodeId = newEpisodeId
                    currentPodcastId = newPodcastId
                    currentPodcastTitle = newPodcastTitle
                    progressTracker.resetRecordedPosition()
                    chapterRepository.setActiveEpisodeId(newEpisodeId)
                    syncWidgetState()

                    if (finishedNaturally) {
                        progressTracker.handleEpisodeCompleted(previousEpisodeId!!)
                        if (sleepTimerManager.state.value.isActive && sleepTimerManager.state.value.mode is SleepTimerMode.EndOfEpisode) {
                            // Pausar sin cortar el resto: el siguiente episodio debe quedar listo
                            // (capítulos, velocidad y posición reanudada) para cuando se pulse play
                            sleepTimerManager.onEpisodeEnded()
                            player.pause()
                        }
                    }

                    serviceScope.launch(Dispatchers.IO) {
                        database.podcastDao().setCurrentPlaybackInQueue(newEpisodeId)

                        // Cargar capítulos (Podcasting 2.0 o ID3) para el episodio activo
                        val ep = database.podcastDao().getEpisodeById(newEpisodeId)
                        val chapters = if (ep != null) chapterRepository.getChapters(ep) else emptyList()

                        // Cargar podcast para aplicar velocidad personalizada y salto de intro
                        val podcast = if (!newPodcastId.isNullOrBlank()) {
                            database.podcastDao().getPodcastById(newPodcastId)
                        } else {
                            if (ep != null) database.podcastDao().getPodcastById(ep.podcastId) else null
                        }

                        withContext(Dispatchers.Main) {
                            // Si el usuario ya cambió de episodio mientras se consultaba Room, no aplicar nada
                            if (player.currentMediaItem?.mediaId != mediaItem.mediaId) return@withContext

                            playbackController.setChapters(chapters)

                            // Los episodios siguientes de la lista de ExoPlayer arrancan en 0: reanudar
                            // desde la posición guardada al avanzar automáticamente o con "siguiente"
                            val autoAdvanced = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                            if (autoAdvanced && ep != null && ep.currentPositionMs > 0L &&
                                (ep.duration <= 0L || ep.currentPositionMs < ep.duration - 5000L) &&
                                player.currentPosition < 2000L
                            ) {
                                player.seekTo(ep.currentPositionMs)
                            }

                            currentPodcastHasCustomSpeed = podcast?.customSpeed != null && podcast.customSpeed > 0f
                            if (podcast?.customSpeed != null && podcast.customSpeed > 0f) {
                                player.playbackParameters = PlaybackParameters(podcast.customSpeed)
                                playbackController.setSpeedIndexFromFloat(podcast.customSpeed)
                            } else if (appliedDefaultSpeed != null && appliedDefaultSpeed!! > 0f) {
                                player.playbackParameters = PlaybackParameters(appliedDefaultSpeed!!)
                                playbackController.setSpeedIndexFromFloat(appliedDefaultSpeed!!)
                            }

                            val introSkipMs = (podcast?.skipIntroSeconds ?: 0) * 1000L
                            if (introSkipMs > 0 && player.currentPosition < introSkipMs) {
                                player.seekTo(introSkipMs)
                            }

                            mediaLibrarySession.setCustomLayout(
                                playbackController.buildCustomCommandButtons(
                                    currentSpeed = player.playbackParameters.speed
                                )
                            )
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val finishedEpisodeId = currentPlayingEpisodeId
                    if (!finishedEpisodeId.isNullOrBlank()) {
                        progressTracker.saveCurrentPlaybackPosition(
                            player = player,
                            episodeId = finishedEpisodeId,
                            podcastId = currentPodcastId,
                            podcastTitle = currentPodcastTitle ?: "Podcast",
                            isCompleted = true
                        )
                        progressTracker.handleEpisodeCompleted(finishedEpisodeId)
                    }

                    // Si el temporizador de apagado estaba configurado para el fin del episodio, pausar y no avanzar cola
                    if (sleepTimerManager.state.value.isActive && sleepTimerManager.state.value.mode is SleepTimerMode.EndOfEpisode) {
                        sleepTimerManager.onEpisodeEnded()
                        return
                    }

                    // Avance de cola secuencial ordenado
                    serviceScope.launch(Dispatchers.IO) {
                        val queueList = database.podcastDao().getQueueList()
                        if (queueList.isNotEmpty() && !finishedEpisodeId.isNullOrBlank()) {
                            val finishedIndex = queueList.indexOfFirst { it.episode.id == finishedEpisodeId }
                            val nextItem = if (finishedIndex >= 0 && finishedIndex + 1 < queueList.size) {
                                queueList[finishedIndex + 1]
                            } else {
                                queueList.firstOrNull { it.episode.id != finishedEpisodeId }
                            }

                            if (nextItem != null) {
                                withContext(Dispatchers.Main) {
                                    val nextMediaItem = autoBrowseTree.buildPlayableMediaItem(
                                        episode = nextItem.episode,
                                        podcastTitle = nextItem.podcastTitle,
                                        podcastArtworkUrl = nextItem.podcastArtworkUrl
                                    )
                                    player.setMediaItem(nextMediaItem)
                                    val resumePos = if (nextItem.episode.currentPositionMs > 0 &&
                                        (nextItem.episode.duration <= 0 || nextItem.episode.currentPositionMs < nextItem.episode.duration - 5000)) {
                                        nextItem.episode.currentPositionMs
                                    } else {
                                        0L
                                    }
                                    player.seekTo(resumePos)
                                    player.prepare()
                                    player.play()
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun initializeMediaLibrarySession() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionPlayer = playbackController.createForwardingPlayer(player)

        mediaLibrarySession = MediaLibrarySession.Builder(this, sessionPlayer, MediaLibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setCustomLayout(playbackController.buildCustomCommandButtons())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )
    }

    private fun observeDatabaseChangesForAuto() {
        serviceScope.launch {
            database.podcastDao().getQueueFlow()
                .distinctUntilChanged()
                .collect { queue ->
                    mediaLibrarySession.notifyChildrenChanged(MEDIA_ID_QUEUE, 0, null)
                    syncUpcomingPlaylist(queue)
                }
        }
    }

    /**
     * Mantiene los elementos posteriores al actual en la lista de ExoPlayer alineados con la cola
     * de Room, para que reordenar, añadir o quitar episodios se respete en el avance automático.
     */
    private fun syncUpcomingPlaylist(queue: List<com.ferlagod.mispodcast.data.local.model.QueueItemEntityWithEpisode>) {
        if (player.mediaItemCount == 0) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        // Mismo orden que muestra la pantalla de cola: el resto de la cola, sin el episodio activo
        val desired = queue.filter { it.episode.id != currentId }

        val currentIndex = player.currentMediaItemIndex
        val upcomingIds = (currentIndex + 1 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (upcomingIds == desired.map { it.episode.id }) return

        if (player.mediaItemCount > currentIndex + 1) {
            player.removeMediaItems(currentIndex + 1, player.mediaItemCount)
        }
        player.addMediaItems(
            desired.map {
                autoBrowseTree.buildPlayableMediaItem(
                    episode = it.episode,
                    podcastTitle = it.podcastTitle,
                    podcastArtworkUrl = it.podcastArtworkUrl
                )
            }
        )
    }

    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Desde Media3 1.6+, super.onConnect() devuelve un resultado "no implementado" con
            // comandos VACÍOS. Si partimos de él, Android Auto no recibe los comandos de biblioteca
            // (GET_LIBRARY_ROOT, GET_CHILDREN...) y su onGetRoot() devuelve null: la interfaz no carga.
            // Se conceden explícitamente todos los comandos de sesión, biblioteca y reproductor.
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(COMMAND_REPLAY_10)
                .add(COMMAND_FORWARD_30)
                .add(COMMAND_CYCLE_SPEED)
                .add(COMMAND_NEXT_CHAPTER)
                .add(COMMAND_PREV_CHAPTER)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(playbackController.buildCustomCommandButtons())
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = (params?.extras ?: Bundle()).apply {
                putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Mis Podcasts")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

            val libraryParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, libraryParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofItemList(autoBrowseTree.getRootTabs(), params))
            }

            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = autoBrowseTree.getChildrenForFolder(parentId)
                    val fromIndex = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
                    val toIndex = (fromIndex.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
                    future.set(LibraryResult.ofItemList(items.subList(fromIndex, toIndex), params))
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val item = autoBrowseTree.getItem(mediaId)
                    if (item != null) {
                        future.set(LibraryResult.ofItem(item, null))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val count = autoBrowseTree.searchMediaItems(query).size
                    withContext(Dispatchers.Main) {
                        session.notifySearchResultChanged(controller, query, count, params)
                    }
                    future.set(LibraryResult.ofVoid())
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val results = autoBrowseTree.searchMediaItems(query)
                    val fromIndex = page * pageSize
                    val toIndex = kotlin.math.min(fromIndex + pageSize, results.size)
                    val pagedResults = if (fromIndex < results.size) {
                        results.subList(fromIndex, toIndex)
                    } else {
                        emptyList()
                    }
                    future.set(LibraryResult.ofItemList(pagedResults, params))
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val result = playbackController.resolveMediaItemsForSet(mediaItems, startIndex, startPositionMs)
                    future.set(result)
                } catch (_: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    future.set(playbackController.resolveMediaItemsForAdd(mediaItems).toMutableList())
                } catch (_: Exception) {
                    future.set(mediaItems)
                }
            }
            return future
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val result = playbackController.resolveMediaItemsForResumption()
                    future.set(result)
                } catch (_: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackController.ACTION_CUSTOM_REPLAY_10 -> {
                    val target = kotlin.math.max(0L, player.currentPosition - playbackController.skipBackMs)
                    player.seekTo(target)
                    progressTracker.saveCurrentPlaybackPosition(
                        player = player,
                        episodeId = currentPlayingEpisodeId,
                        podcastId = currentPodcastId,
                        podcastTitle = currentPodcastTitle ?: "Podcast"
                    )
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackController.ACTION_CUSTOM_FORWARD_30 -> {
                    val maxDur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                    val target = kotlin.math.min(maxDur, player.currentPosition + playbackController.skipForwardMs)
                    player.seekTo(target)
                    progressTracker.saveCurrentPlaybackPosition(
                        player = player,
                        episodeId = currentPlayingEpisodeId,
                        podcastId = currentPodcastId,
                        podcastTitle = currentPodcastTitle ?: "Podcast"
                    )
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackController.ACTION_CUSTOM_CYCLE_SPEED -> {
                    val newSpeed = playbackController.cycleSpeed(player)
                    session.setCustomLayout(playbackController.buildCustomCommandButtons(currentSpeed = newSpeed))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackController.ACTION_CUSTOM_NEXT_CHAPTER -> {
                    playbackController.seekToNextChapter(player)
                    progressTracker.saveCurrentPlaybackPosition(
                        player = player,
                        episodeId = currentPlayingEpisodeId,
                        podcastId = currentPodcastId,
                        podcastTitle = currentPodcastTitle ?: "Podcast"
                    )
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackController.ACTION_CUSTOM_PREV_CHAPTER -> {
                    playbackController.seekToPreviousChapter(player)
                    progressTracker.saveCurrentPlaybackPosition(
                        player = player,
                        episodeId = currentPlayingEpisodeId,
                        podcastId = currentPodcastId,
                        podcastTitle = currentPodcastTitle ?: "Podcast"
                    )
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        progressTracker.saveCurrentPlaybackPosition(
            player = player,
            episodeId = currentPlayingEpisodeId,
            podcastId = currentPodcastId,
            podcastTitle = currentPodcastTitle ?: "Podcast"
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (player.mediaItemCount > 0) {
                            player.play()
                        } else {
                            val state = com.ferlagod.mispodcast.widget.PodcastWidgetManager.getState(this)
                            if (state.episodeId.isNotBlank()) {
                                serviceScope.launch(Dispatchers.Main) {
                                    val ep = withContext(Dispatchers.IO) {
                                        database.podcastDao().getEpisodeById(state.episodeId)
                                    }
                                    if (ep != null) {
                                        val mediaItem = autoBrowseTree.buildPlayableMediaItem(ep, state.podcastTitle, state.artworkUrl)
                                        player.setMediaItem(mediaItem)
                                        player.prepare()
                                        if (state.currentPositionMs > 0L) {
                                            player.seekTo(state.currentPositionMs)
                                        }
                                        player.play()
                                    }
                                }
                            }
                        }
                    }
                    syncWidgetState()
                }
                ACTION_SKIP_FORWARD -> {
                    val maxDur = if (player.duration > 0) player.duration else Long.MAX_VALUE
                    val target = kotlin.math.min(maxDur, player.currentPosition + playbackController.skipForwardMs)
                    player.seekTo(target)
                    syncWidgetState()
                }
                ACTION_SKIP_BACKWARD -> {
                    val target = kotlin.math.max(0L, player.currentPosition - playbackController.skipBackMs)
                    player.seekTo(target)
                    syncWidgetState()
                }
                "android.media.action.MEDIA_PLAY_FROM_SEARCH" -> {
                    val query = intent.getStringExtra(android.app.SearchManager.QUERY)
                        ?: intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE)
                    serviceScope.launch(Dispatchers.IO) {
                        val mediaItem = autoBrowseTree.resolveMediaItemFromVoiceSearch(query, intent.extras)
                        if (mediaItem != null) {
                            withContext(Dispatchers.Main) {
                                player.setMediaItem(mediaItem)
                                player.prepare()
                                player.play()
                                syncWidgetState()
                            }
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun syncWidgetState() {
        val epId = currentPlayingEpisodeId.orEmpty()
        val mediaItem = player.currentMediaItem
        val episodeTitle = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val podcastTitle = currentPodcastTitle ?: mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString().orEmpty()
        val duration = if (player.duration > 0) player.duration else 0L
        val position = player.currentPosition

        com.ferlagod.mispodcast.widget.PodcastWidgetManager.updateState(
            this,
            com.ferlagod.mispodcast.widget.PodcastWidgetState(
                episodeId = epId,
                podcastId = currentPodcastId.orEmpty(),
                podcastTitle = podcastTitle,
                episodeTitle = episodeTitle,
                artworkUrl = artworkUri,
                isPlaying = player.isPlaying,
                durationMs = duration,
                currentPositionMs = position
            )
        )
    }

    override fun onDestroy() {
        com.ferlagod.mispodcast.widget.PodcastWidgetManager.updateState(
            this,
            com.ferlagod.mispodcast.widget.PodcastWidgetManager.getState(this).copy(isPlaying = false)
        )
        progressTracker.saveCurrentPlaybackPosition(
            player = player,
            episodeId = currentPlayingEpisodeId,
            podcastId = currentPodcastId,
            podcastTitle = currentPodcastTitle ?: "Podcast"
        )
        progressTracker.stop()
        serviceScope.cancel()
        audioEffectsManager.release()
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }
}
