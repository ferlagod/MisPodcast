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

package com.ferlagod.mispodcast.ui.components

import android.os.Build
import android.text.Html
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.playback.PlaybackController
import com.ferlagod.mispodcast.ui.theme.TabularTimeStyle
import com.ferlagod.mispodcast.ui.theme.dynamicDarkColorSchemeFromSeed
import com.ferlagod.mispodcast.ui.theme.dynamicLightColorSchemeFromSeed
import com.ferlagod.mispodcast.ui.theme.isAppInDarkTheme
import com.ferlagod.mispodcast.ui.theme.readableContentColor
import com.ferlagod.mispodcast.ui.theme.rememberArtworkDominantColor
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Componente composable del mini-reproductor persistente.
 *
 * Muestra el episodio actualmente en reproducción encima de la barra de navegación,
 * incluyendo carátula, título del podcast y capítulo, botón de play/pause, saltos de audio
 * y acceso a la vista expandida a pantalla completa mediante [ModalBottomSheet].
 */
@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToQueue: () -> Unit = {}
) {
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    var lastEpisode by remember { mutableStateOf(currentEpisode) }
    if (currentEpisode != null) {
        lastEpisode = currentEpisode
    }
    val episode = currentEpisode ?: lastEpisode

    val podcastTitle by playerViewModel.podcastTitle.collectAsState()
    val podcastArtworkUrl by playerViewModel.podcastArtworkUrl.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosMs by playerViewModel.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val isExpanded by playerViewModel.isExpanded.collectAsState()
    val userSettings by playerViewModel.userSettings.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Se muestra si hay un episodio cargado o en proceso de animación de salida
    if (episode != null) {
        val progress = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        val artwork = episode.imageUrl.ifBlank { podcastArtworkUrl }
        val dominantArtworkColor = rememberArtworkDominantColor(artworkUrl = artwork.ifBlank { null })

        var offsetX by remember { mutableFloatStateOf(0f) }
        val animatedOffsetX by animateFloatAsState(
            targetValue = offsetX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "mini_player_swipe_x"
        )

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .testTag("mini_player")
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Deslizar hacia arriba para expandir
                            if (dragAmount.y < -12f && abs(dragAmount.y) > abs(dragAmount.x) * 1.2f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.setExpanded(true)
                            } else {
                                // Deslizar horizontal con amortiguación
                                offsetX = (offsetX + dragAmount.x * 0.7f).coerceIn(-180f, 180f)
                            }
                        },
                        onDragEnd = {
                            if (offsetX < -75f) {
                                // Deslizar hacia la izquierda -> Siguiente episodio
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.skipToNextEpisode()
                            } else if (offsetX > 75f) {
                                // Deslizar hacia la derecha -> Episodio anterior
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.skipToPreviousEpisode()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = {
                            offsetX = 0f
                        }
                    )
                }
                .clickable { playerViewModel.setExpanded(true) },
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, dominantArtworkColor.copy(alpha = 0.28f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                dominantArtworkColor.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Barra de progreso delgada arriba del todo
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        color = dominantArtworkColor,
                        trackColor = dominantArtworkColor.copy(alpha = 0.16f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Portada
                        if (artwork.isNotBlank()) {
                            AsyncImage(
                                model = artwork,
                                contentDescription = stringResource(R.string.player_cover_description),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            PodcastArtworkPlaceholder(
                                title = podcastTitle,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Textos
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = podcastTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Botón Play / Pause
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.togglePlayPause()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(dominantArtworkColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                                tint = dominantArtworkColor.readableContentColor(),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Botón Avanzar configurado
                        val forwardSec = userSettings.skipForwardSeconds
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerViewModel.seekForward()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = when (forwardSec) {
                                    5 -> Icons.Filled.Forward5
                                    10 -> Icons.Filled.Forward10
                                    30 -> Icons.Filled.Forward30
                                    else -> Icons.Filled.FastForward
                                },
                                contentDescription = stringResource(R.string.player_skip_forward_format, forwardSec),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isExpanded) {
        FullPlayerSheet(
            playerViewModel = playerViewModel,
            onDismiss = { playerViewModel.setExpanded(false) },
            onNavigateToQueue = onNavigateToQueue
        )
    }
}
