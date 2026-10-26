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

package com.ferlagod.mispodcast.widget

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Modelo de datos inmutable que representa el estado del reproductor para el widget y el tile.
 */
data class PodcastWidgetState(
    val episodeId: String = "",
    val podcastId: String = "",
    val podcastTitle: String = "",
    val episodeTitle: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L
)

/**
 * Gestor singleton para persistir y sincronizar el estado del reproductor entre
 * [PodcastPlaybackService], el widget de Jetpack Glance y el Quick Settings Tile.
 */
object PodcastWidgetManager {

    private const val PREFS_NAME = "mis_podcast_widget_state"
    private const val KEY_EPISODE_ID = "episode_id"
    private const val KEY_PODCAST_ID = "podcast_id"
    private const val KEY_PODCAST_TITLE = "podcast_title"
    private const val KEY_EPISODE_TITLE = "episode_title"
    private const val KEY_ARTWORK_URL = "artwork_url"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_POSITION_MS = "position_ms"

    private val _currentState = MutableStateFlow(PodcastWidgetState())
    val currentState: StateFlow<PodcastWidgetState> = _currentState.asStateFlow()

    private val widgetScope = CoroutineScope(Dispatchers.IO)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Obtiene el estado actual, restaurándolo desde SharedPreferences si es necesario.
     */
    fun getState(context: Context): PodcastWidgetState {
        val memory = _currentState.value
        if (memory.episodeId.isNotBlank()) {
            return memory
        }
        val prefs = getPrefs(context)
        val state = PodcastWidgetState(
            episodeId = prefs.getString(KEY_EPISODE_ID, "").orEmpty(),
            podcastId = prefs.getString(KEY_PODCAST_ID, "").orEmpty(),
            podcastTitle = prefs.getString(KEY_PODCAST_TITLE, "").orEmpty(),
            episodeTitle = prefs.getString(KEY_EPISODE_TITLE, "").orEmpty(),
            artworkUrl = prefs.getString(KEY_ARTWORK_URL, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
            currentPositionMs = prefs.getLong(KEY_POSITION_MS, 0L)
        )
        _currentState.value = state
        return state
    }

    /**
     * Actualiza el estado, lo persiste y solicita el refresco del Widget y del Tile.
     */
    fun updateState(context: Context, state: PodcastWidgetState) {
        _currentState.value = state
        getPrefs(context).edit()
            .putString(KEY_EPISODE_ID, state.episodeId)
            .putString(KEY_PODCAST_ID, state.podcastId)
            .putString(KEY_PODCAST_TITLE, state.podcastTitle)
            .putString(KEY_EPISODE_TITLE, state.episodeTitle)
            .putString(KEY_ARTWORK_URL, state.artworkUrl)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putLong(KEY_DURATION_MS, state.durationMs)
            .putLong(KEY_POSITION_MS, state.currentPositionMs)
            .apply()

        // Notificar actualización al Widget de Glance en background
        widgetScope.launch {
            try {
                PodcastPlaybackWidget().updateAll(context)
            } catch (e: Exception) {
                // Glance puede no tener receptores activos aún
            }
        }

        // Notificar actualización al Quick Settings Tile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, PodcastTileService::class.java)
                )
            } catch (e: Exception) {
                // Ignorar si el componente aún no ha sido activado por el sistema
            }
        }
    }
}
