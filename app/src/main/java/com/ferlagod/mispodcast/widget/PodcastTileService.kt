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

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ferlagod.mispodcast.MainActivity
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.playback.PodcastPlaybackService

/**
 * Servicio de control de reproducción para el panel de Ajustes Rápidos (Quick Settings Tile).
 *
 * Permite reproducir o pausar el podcast en curso directamente desde la cortina de notificaciones
 * de Android, mostrando el título del capítulo y el estado activo/inactivo.
 */
@RequiresApi(Build.VERSION_CODES.N)
class PodcastTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileVisuals()
    }

    override fun onClick() {
        super.onClick()
        val state = PodcastWidgetManager.getState(this)

        if (state.episodeId.isBlank()) {
            // No hay episodio en memoria ni previo: abrir la app
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        // Alternar reproducción / pausa
        val intent = Intent(this, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_PLAY_PAUSE
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            startService(intent)
        }

        // Actualización optimista inmediata del Tile
        val tile = qsTile ?: return
        tile.state = if (state.isPlaying) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private fun updateTileVisuals() {
        val tile = qsTile ?: return
        val state = PodcastWidgetManager.getState(this)

        tile.state = if (state.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (state.isPlaying) {
                state.episodeTitle.ifBlank { getString(R.string.tile_pause) }
            } else {
                if (state.episodeTitle.isNotBlank()) state.episodeTitle else getString(R.string.tile_no_episode)
            }
        }

        tile.icon = Icon.createWithResource(
            this,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        tile.updateTile()
    }

    companion object {
        /**
         * Notifica al sistema para que refresque el Tile de Ajustes Rápidos.
         */
        fun updateTile(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context.applicationContext,
                        ComponentName(context.applicationContext, PodcastTileService::class.java)
                    )
                } catch (e: Exception) {
                    // Ignorar si el tile no ha sido añadido por el usuario
                }
            }
        }
    }
}
