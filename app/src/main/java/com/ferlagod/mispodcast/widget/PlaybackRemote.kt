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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ferlagod.mispodcast.playback.PodcastPlaybackService

/**
 * Envía órdenes de reproducción al servicio desde el widget y el Quick Settings Tile.
 *
 * Se conecta con un [MediaController] en lugar de arrancar el servicio con
 * `startForegroundService`: con una orden de pausa o salto el servicio no llamaba a
 * `startForeground()` a tiempo y Android lo cerraba con `ForegroundServiceDidNotStartInTimeException`.
 * Con play y sin episodio cargado, Media3 invoca la reanudación (`onPlaybackResumption`).
 */
object PlaybackRemote {

    private const val TAG = "PlaybackRemote"
    private const val RELEASE_DELAY_MS = 1_500L

    fun togglePlayPause(context: Context) = send(context) { controller ->
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekBack(context: Context) = send(context) { it.seekBack() }

    fun seekForward(context: Context) = send(context) { it.seekForward() }

    private fun send(context: Context, command: (MediaController) -> Unit) {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PodcastPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                command(controller)
                // Margen para que la orden llegue a la sesión antes de desconectar
                Handler(Looper.getMainLooper()).postDelayed({ controller.release() }, RELEASE_DELAY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar la orden al reproductor: ${e.message}")
                MediaController.releaseFuture(future)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }
}
