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

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Controlador de comandos multimedia, configuración de saltos inteligentes y gestión de cola de reproducción.
 *
 * Administra:
 * - El cálculo e interpolación de saltos hacia adelante y atrás ([seekBack], [seekForward]).
 * - El ciclo de velocidades de audio disponibles (1.0x a 2.0x).
 * - La resolución de la cola completa para habilitar precarga y reproducción continua sin pausas (*gapless playback*).
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    private val database: PodcastDatabase,
    private val autoBrowseTree: AutoBrowseTree
) {

    @Volatile var skipBackMs: Long = 10_000L
    @Volatile var skipForwardMs: Long = 30_000L
    @Volatile var currentChapters: List<com.ferlagod.mispodcast.data.model.Chapter> = emptyList()

    private var playbackSpeedIndex = 0
    val availableSpeeds = PLAYBACK_SPEEDS

    companion object {
        // Velocidades finas disponibles de 0.5x a 3.0x en incrementos de 0.1x (26 valores)
        val PLAYBACK_SPEEDS = (5..30).map { it / 10f }

        // Presets rápidos para botones físicos, notificaciones del sistema y Android Auto
        val CYCLE_PRESETS = listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

        const val ACTION_CUSTOM_REPLAY_10 = "com.ferlagod.mispodcast.REPLAY_10"
        const val ACTION_CUSTOM_FORWARD_30 = "com.ferlagod.mispodcast.FORWARD_30"
        const val ACTION_CUSTOM_CYCLE_SPEED = "com.ferlagod.mispodcast.CYCLE_SPEED"
        const val ACTION_CUSTOM_NEXT_CHAPTER = "com.ferlagod.mispodcast.NEXT_CHAPTER"
        const val ACTION_CUSTOM_PREV_CHAPTER = "com.ferlagod.mispodcast.PREV_CHAPTER"

        private fun createCommand(action: String): SessionCommand {
            return try {
                val bundle = Bundle.EMPTY ?: Bundle()
                SessionCommand(action, bundle)
            } catch (_: Throwable) {
                try {
                    val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
                    val unsafe = unsafeField.get(null) as sun.misc.Unsafe
                    val instance = unsafe.allocateInstance(SessionCommand::class.java) as SessionCommand
                    val actionField = SessionCommand::class.java.getDeclaredField("customAction").apply { isAccessible = true }
                    actionField.set(instance, action)
                    val codeField = SessionCommand::class.java.getDeclaredField("commandCode").apply { isAccessible = true }
                    codeField.setInt(instance, SessionCommand.COMMAND_CODE_CUSTOM)
                    instance
                } catch (_: Throwable) {
                    SessionCommand(action, Bundle.EMPTY)
                }
            }
        }

        val COMMAND_REPLAY_10 by lazy { createCommand(ACTION_CUSTOM_REPLAY_10) }
        val COMMAND_FORWARD_30 by lazy { createCommand(ACTION_CUSTOM_FORWARD_30) }
        val COMMAND_CYCLE_SPEED by lazy { createCommand(ACTION_CUSTOM_CYCLE_SPEED) }
        val COMMAND_NEXT_CHAPTER by lazy { createCommand(ACTION_CUSTOM_NEXT_CHAPTER) }
        val COMMAND_PREV_CHAPTER by lazy { createCommand(ACTION_CUSTOM_PREV_CHAPTER) }

        fun formatSpeed(speed: Float): String {
            val rounded = kotlin.math.round(speed * 10f) / 10f
            return String.format(java.util.Locale.US, "%.1fx", rounded)
        }
    }

    /**
     * Envuelve a [ExoPlayer] en un [ForwardingPlayer] para que los botones de salto del sistema
     * y de Android Auto utilicen los intervalos configurados por el usuario y los saltos de capítulo.
     */
    fun createForwardingPlayer(player: ExoPlayer): Player = object : ForwardingPlayer(player) {
        override fun seekBack() {
            val target = max(0L, currentPosition - skipBackMs)
            seekTo(target)
        }

        override fun seekForward() {
            val maxDur = if (duration > 0) duration else Long.MAX_VALUE
            val target = min(maxDur, currentPosition + skipForwardMs)
            seekTo(target)
        }

        override fun seekToNext() {
            if (!seekToNextChapter(player)) {
                super.seekToNext()
            }
        }

        override fun seekToPrevious() {
            if (!seekToPreviousChapter(player)) {
                super.seekToPrevious()
            }
        }

        override fun getSeekBackIncrement(): Long = skipBackMs
        override fun getSeekForwardIncrement(): Long = skipForwardMs
    }

    /**
     * Actualiza la lista de capítulos actuales del episodio en reproducción.
     */
    fun setChapters(chapters: List<com.ferlagod.mispodcast.data.model.Chapter>) {
        currentChapters = chapters
    }

    /**
     * Avanza al inicio del siguiente capítulo si existe.
     * @return true si se realizó el salto a un capítulo posterior, false en caso contrario.
     */
    fun seekToNextChapter(player: Player): Boolean {
        val chapters = currentChapters
        if (chapters.isEmpty()) return false
        val currentPos = player.currentPosition
        val next = chapters.firstOrNull { it.startTimeMs > currentPos + 1000L }
        return if (next != null) {
            player.seekTo(next.startTimeMs)
            true
        } else {
            false
        }
    }

    /**
     * Retrocede al inicio del capítulo anterior o al inicio del capítulo actual si ya han
     * transcurrido más de 3 segundos desde su inicio.
     */
    fun seekToPreviousChapter(player: Player): Boolean {
        val chapters = currentChapters
        if (chapters.isEmpty()) return false
        val currentPos = player.currentPosition
        val currentChapter = getCurrentChapter(currentPos)
        if (currentChapter != null && currentPos - currentChapter.startTimeMs > 3000L) {
            player.seekTo(currentChapter.startTimeMs)
            return true
        }
        val prev = chapters.lastOrNull { it.startTimeMs < (currentChapter?.startTimeMs ?: currentPos) - 500L }
        return if (prev != null) {
            player.seekTo(prev.startTimeMs)
            true
        } else {
            if (currentChapter != null) {
                player.seekTo(currentChapter.startTimeMs)
                true
            } else false
        }
    }

    /**
     * Obtiene el capítulo activo correspondiente a la posición de reproducción indicada.
     */
    fun getCurrentChapter(currentPosMs: Long): com.ferlagod.mispodcast.data.model.Chapter? {
        val chapters = currentChapters
        if (chapters.isEmpty()) return null
        return chapters.lastOrNull { currentPosMs >= it.startTimeMs } ?: chapters.firstOrNull()
    }

    /**
     * Cambia de forma cíclica la velocidad del reproductor entre los presets rápidos más habituales.
     */
    fun cycleSpeed(player: Player): Float {
        val current = player.playbackParameters.speed
        val currentIndex = CYCLE_PRESETS.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextIndex = (if (currentIndex >= 0) currentIndex + 1 else 0) % CYCLE_PRESETS.size
        val newSpeed = CYCLE_PRESETS[nextIndex]
        player.playbackParameters = PlaybackParameters(newSpeed)
        setSpeedIndexFromFloat(newSpeed)
        return newSpeed
    }

    fun setSpeedIndexFromFloat(speed: Float) {
        val index = availableSpeeds.indexOfFirst { kotlin.math.abs(it - speed) < 0.05f }
        if (index >= 0) {
            playbackSpeedIndex = index
        }
    }

    /**
     * Genera los botones de comando personalizados para la notificación del sistema y Android Auto.
     */
    fun buildCustomCommandButtons(
        skipBackSeconds: Int = (skipBackMs / 1000).toInt(),
        skipForwardSeconds: Int = (skipForwardMs / 1000).toInt(),
        currentSpeed: Float = availableSpeeds.getOrElse(playbackSpeedIndex) { 1.0f },
        hasChapters: Boolean = currentChapters.isNotEmpty()
    ): List<CommandButton> {
        val buttons = mutableListOf<CommandButton>()

        val replayButton = CommandButton.Builder(skipBackIcon(skipBackSeconds))
            .setSessionCommand(COMMAND_REPLAY_10)
            .setDisplayName("Retroceder $skipBackSeconds s")
            .build()
        buttons.add(replayButton)

        val forwardButton = CommandButton.Builder(skipForwardIcon(skipForwardSeconds))
            .setSessionCommand(COMMAND_FORWARD_30)
            .setDisplayName("Avanzar $skipForwardSeconds s")
            .build()
        buttons.add(forwardButton)

        if (hasChapters) {
            val nextChapterButton = CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(COMMAND_NEXT_CHAPTER)
                .setDisplayName("Siguiente capítulo")
                .setCustomIconResId(R.drawable.ic_skip_next)
                .build()
            buttons.add(nextChapterButton)
        }

        val speedButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setSessionCommand(COMMAND_CYCLE_SPEED)
            .setDisplayName("Velocidad: ${formatSpeed(currentSpeed)}")
            .setCustomIconResId(R.drawable.ic_speed)
            .build()
        buttons.add(speedButton)

        return buttons
    }

    private fun skipBackIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun skipForwardIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }

    fun formatSpeed(speed: Float): String = Companion.formatSpeed(speed)

    /**
     * Resuelve la cola completa para [MediaSession.Callback.onSetMediaItems], asegurando orden y precarga gapless.
     */
    suspend fun resolveMediaItemsForSet(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): MediaSession.MediaItemsWithStartPosition = withContext(Dispatchers.IO) {
        val targetItem = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
        val searchQuery = targetItem?.requestMetadata?.searchQuery
            ?: targetItem?.requestMetadata?.extras?.getString(android.app.SearchManager.QUERY)
            ?: targetItem?.requestMetadata?.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_TITLE)
            ?: targetItem?.requestMetadata?.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_ALBUM)

        // Comprobar si se trata de una solicitud de búsqueda por voz (Asistente de Google / Android Auto / Wear OS)
        val voiceItem = if (!searchQuery.isNullOrBlank()) {
            autoBrowseTree.resolveMediaItemFromVoiceSearch(searchQuery, targetItem?.requestMetadata?.extras)
        } else null

        val effectiveItem = voiceItem ?: targetItem
        val targetEpisodeId = effectiveItem?.mediaId

        val queueList = database.podcastDao().getQueueList()
        val resolvedItems = mutableListOf<MediaItem>()
        var targetIndex = 0
        var initialSeekPos = startPositionMs

        if (!targetEpisodeId.isNullOrBlank()) {
            val indexInQueue = queueList.indexOfFirst { it.episode.id == targetEpisodeId }

            if (indexInQueue >= 0) {
                // Caso 1: El episodio ya pertenece a la cola del usuario
                for (qItem in queueList) {
                    resolvedItems.add(
                        autoBrowseTree.buildPlayableMediaItem(
                            episode = qItem.episode,
                            podcastTitle = qItem.podcastTitle,
                            podcastArtworkUrl = qItem.podcastArtworkUrl
                        )
                    )
                }
                targetIndex = indexInQueue
                val targetEpisode = queueList[indexInQueue].episode
                val podcast = database.podcastDao().getPodcastById(targetEpisode.podcastId)
                val introSkipMs = (podcast?.skipIntroSeconds ?: 0) * 1000L
                if (initialSeekPos == C.TIME_UNSET || initialSeekPos == 0L) {
                    if (targetEpisode.currentPositionMs > 0L &&
                        (targetEpisode.duration <= 0L || targetEpisode.currentPositionMs < targetEpisode.duration - 5000L)) {
                        initialSeekPos = targetEpisode.currentPositionMs
                    } else {
                        initialSeekPos = introSkipMs
                    }
                }
                if (introSkipMs > 0L && initialSeekPos < introSkipMs) {
                    initialSeekPos = introSkipMs
                }
                database.podcastDao().setCurrentPlaybackInQueue(targetEpisodeId)
            } else {
                // Caso 2: El episodio no está en la cola (reproducción desde catálogo, novedades, búsqueda, etc.)
                val episode = database.podcastDao().getEpisodeById(targetEpisodeId)
                if (episode != null) {
                    val podcast = database.podcastDao().getPodcastById(episode.podcastId)
                    val introSkipMs = (podcast?.skipIntroSeconds ?: 0) * 1000L
                    resolvedItems.add(
                        autoBrowseTree.buildPlayableMediaItem(
                            episode = episode,
                            podcastTitle = podcast?.title.orEmpty(),
                            podcastArtworkUrl = podcast?.artworkUrl.orEmpty()
                        )
                    )
                    if (initialSeekPos == C.TIME_UNSET || initialSeekPos == 0L) {
                        if (episode.currentPositionMs > 0L &&
                            (episode.duration <= 0L || episode.currentPositionMs < episode.duration - 5000L)) {
                            initialSeekPos = episode.currentPositionMs
                        } else {
                            initialSeekPos = introSkipMs
                        }
                    }
                    if (introSkipMs > 0L && initialSeekPos < introSkipMs) {
                        initialSeekPos = introSkipMs
                    }
                    for (qItem in queueList) {
                        if (qItem.episode.id != episode.id) {
                            resolvedItems.add(
                                autoBrowseTree.buildPlayableMediaItem(
                                    episode = qItem.episode,
                                    podcastTitle = qItem.podcastTitle,
                                    podcastArtworkUrl = qItem.podcastArtworkUrl
                                )
                            )
                        }
                    }
                    targetIndex = 0
                } else if (effectiveItem?.requestMetadata?.mediaUri != null || effectiveItem?.localConfiguration?.uri != null) {
                    resolvedItems.add(effectiveItem)
                    for (qItem in queueList) {
                        resolvedItems.add(
                            autoBrowseTree.buildPlayableMediaItem(
                                episode = qItem.episode,
                                podcastTitle = qItem.podcastTitle,
                                podcastArtworkUrl = qItem.podcastArtworkUrl
                            )
                        )
                    }
                    targetIndex = 0
                } else {
                    resolvedItems.addAll(mediaItems)
                    targetIndex = startIndex.coerceIn(0, max(0, resolvedItems.size - 1))
                }
            }
        } else {
            resolvedItems.addAll(mediaItems)
            targetIndex = startIndex.coerceIn(0, max(0, resolvedItems.size - 1))
        }

        MediaSession.MediaItemsWithStartPosition(
            resolvedItems,
            targetIndex,
            if (initialSeekPos == C.TIME_UNSET) 0L else initialSeekPos
        )
    }

    /**
     * Resuelve los elementos a agregar a la lista de reproducción actual.
     */
    suspend fun resolveMediaItemsForAdd(mediaItems: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        mediaItems.map { mediaItem ->
            val searchQuery = mediaItem.requestMetadata.searchQuery
                ?: mediaItem.requestMetadata.extras?.getString(android.app.SearchManager.QUERY)
                ?: mediaItem.requestMetadata.extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_TITLE)
            if (!searchQuery.isNullOrBlank()) {
                val resolved = autoBrowseTree.resolveMediaItemFromVoiceSearch(searchQuery, mediaItem.requestMetadata.extras)
                if (resolved != null) return@map resolved
            }

            val episodeId = mediaItem.mediaId
            val episode = database.podcastDao().getEpisodeById(episodeId)
            if (episode != null) {
                val podcast = database.podcastDao().getPodcastById(episode.podcastId)
                autoBrowseTree.buildPlayableMediaItem(
                    episode = episode,
                    podcastTitle = podcast?.title.orEmpty(),
                    podcastArtworkUrl = podcast?.artworkUrl.orEmpty()
                )
            } else {
                mediaItem
            }
        }
    }

    /**
     * Resuelve los elementos para reanudación de reproducción en frío.
     */
    suspend fun resolveMediaItemsForResumption(): MediaSession.MediaItemsWithStartPosition = withContext(Dispatchers.IO) {
        val queueList = database.podcastDao().getQueueList()
        if (queueList.isNotEmpty()) {
            val first = queueList.first()
            val playlist = queueList.map {
                autoBrowseTree.buildPlayableMediaItem(
                    episode = it.episode,
                    podcastTitle = it.podcastTitle,
                    podcastArtworkUrl = it.podcastArtworkUrl
                )
            }
            val resumePos = if (first.episode.currentPositionMs > 0L && (first.episode.duration <= 0L || first.episode.currentPositionMs < first.episode.duration - 5000L)) {
                first.episode.currentPositionMs
            } else {
                0L
            }
            MediaSession.MediaItemsWithStartPosition(playlist, 0, resumePos)
        } else {
            val played = database.podcastDao().getPlayedOrInProgressEpisodes()
            val candidate = played.firstOrNull { it.currentPositionMs > 0L && (it.duration <= 0L || it.currentPositionMs < it.duration - 5000L) }
            if (candidate != null) {
                val pod = database.podcastDao().getPodcastById(candidate.podcastId)
                val item = autoBrowseTree.buildPlayableMediaItem(
                    episode = candidate,
                    podcastTitle = pod?.title.orEmpty(),
                    podcastArtworkUrl = pod?.artworkUrl.orEmpty()
                )
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, candidate.currentPositionMs)
            } else {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            }
        }
    }
}
