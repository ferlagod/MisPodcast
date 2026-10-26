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

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ferlagod.mispodcast.MainActivity
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.playback.PodcastPlaybackService

/**
 * Widget para la pantalla de inicio de Android implementado con Jetpack Glance.
 *
 * Muestra el episodio y podcast en curso o el último reproducido, junto con la barra
 * de progreso y botones táctiles interactivos de Play/Pausa, -10s y +30s.
 */
class PodcastPlaybackWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = PodcastWidgetManager.getState(context)

        provideContent {
            GlanceTheme {
                val hasEpisode = state.episodeTitle.isNotBlank()
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fila superior: Carátula e información textual
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icono / Carátula de la app
                            Box(
                                modifier = GlanceModifier
                                    .size(44.dp)
                                    .cornerRadius(12.dp)
                                    .background(GlanceTheme.colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_podcast),
                                    contentDescription = "Podcast",
                                    modifier = GlanceModifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(10.dp))

                            Column(
                                modifier = GlanceModifier.defaultWeight()
                            ) {
                                Text(
                                    text = if (hasEpisode) state.podcastTitle.ifBlank { "Mis Podcast" } else "Mis Podcast",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = if (hasEpisode) state.episodeTitle else "Toca para reanudar la reproducción",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Barra de progreso si hay duración conocida
                        if (hasEpisode && state.durationMs > 0L) {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            val progressFloat = (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = progressFloat,
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .cornerRadius(2.dp),
                                color = GlanceTheme.colors.primary,
                                backgroundColor = GlanceTheme.colors.surfaceVariant
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Fila inferior: Controles de reproducción interactivos
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón Rebobinar -10s
                            Box(
                                modifier = GlanceModifier
                                    .size(38.dp)
                                    .cornerRadius(19.dp)
                                    .background(GlanceTheme.colors.surfaceVariant)
                                    .clickable(actionRunCallback<SkipBackActionCallback>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_replay_10),
                                    contentDescription = "Retroceder 10 segundos",
                                    modifier = GlanceModifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(16.dp))

                            // Botón Play / Pausa principal
                            Box(
                                modifier = GlanceModifier
                                    .size(46.dp)
                                    .cornerRadius(23.dp)
                                    .background(GlanceTheme.colors.primary)
                                    .clickable(actionRunCallback<TogglePlayPauseActionCallback>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(
                                        if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                                    ),
                                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                                    modifier = GlanceModifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(16.dp))

                            // Botón Avanzar +30s
                            Box(
                                modifier = GlanceModifier
                                    .size(38.dp)
                                    .cornerRadius(19.dp)
                                    .background(GlanceTheme.colors.surfaceVariant)
                                    .clickable(actionRunCallback<SkipForwardActionCallback>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_forward_30),
                                    contentDescription = "Avanzar 30 segundos",
                                    modifier = GlanceModifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Acción de alternar reproducción / pausa desde el widget de Glance.
 */
class TogglePlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_PLAY_PAUSE
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            context.startService(intent)
        }
    }
}

/**
 * Acción de saltar hacia atrás (-10s) desde el widget de Glance.
 */
class SkipBackActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_SKIP_BACKWARD
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            context.startService(intent)
        }
    }
}

/**
 * Acción de saltar hacia adelante (+30s) desde el widget de Glance.
 */
class SkipForwardActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_SKIP_FORWARD
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            context.startService(intent)
        }
    }
}
