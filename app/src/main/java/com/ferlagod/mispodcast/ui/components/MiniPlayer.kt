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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Podcasts
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
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.playback.PlaybackController
import com.ferlagod.mispodcast.ui.theme.IndigoSecondary
import com.ferlagod.mispodcast.ui.theme.TabularTimeStyle
import com.ferlagod.mispodcast.ui.theme.dynamicDarkColorSchemeFromSeed
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
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(dominantArtworkColor, IndigoSecondary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Podcasts,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                                tint = Color.White,
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

/**
 * Vista de reproductor expandido inmersivo (Fase 2).
 *
 * Características:
 * - Fondo con portada desenfocada (`Modifier.blur(60.dp)`) y degradado oscuro de alto contraste.
 * - Portada expansiva (~85% de ancho) con halo suave y escala animada que se encoge ligeramente al pausar.
 * - Deslizamiento horizontal de la portada para avanzar o retroceder en la cola.
 * - Barra de progreso ondulada estilo Material 3 Expressive ([ExpressiveWavySlider]) con marcas de capítulos.
 * - Botón play/pause con metamorfosis geométrica (círculo ↔ squircle redondeado) y vibración háptica.
 * - Fila inferior con acceso a Velocidad, Temporizador, Marcadores, Transcripción y Cola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onNavigateToQueue: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val podcastTitle by playerViewModel.podcastTitle.collectAsState()
    val podcastArtworkUrl by playerViewModel.podcastArtworkUrl.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosMs by playerViewModel.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val userSettings by playerViewModel.userSettings.collectAsState()
    val sleepTimerState by playerViewModel.sleepTimerState.collectAsState()
    val chapters by playerViewModel.chapters.collectAsState()
    val currentChapter by playerViewModel.currentChapter.collectAsState()
    val transcript by playerViewModel.transcript.collectAsState()
    val isTranscriptLoading by playerViewModel.isTranscriptLoading.collectAsState()
    val bookmarks by playerViewModel.bookmarks.collectAsState()
    val queueEpisodes by playerViewModel.queueEpisodes.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showEpisodeNotes by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showTranscriptSheet by remember { mutableStateOf(false) }
    var showAudioEffectsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val artworkUrl = currentEpisode?.imageUrl?.ifBlank { podcastArtworkUrl } ?: podcastArtworkUrl
    val dominantArtworkColor = rememberArtworkDominantColor(artworkUrl = artworkUrl.ifBlank { null })
    val currentColorScheme = MaterialTheme.colorScheme
    val dynamicColorScheme = remember(dominantArtworkColor, currentColorScheme) {
        dynamicDarkColorSchemeFromSeed(dominantArtworkColor, currentColorScheme)
    }

    // Escala animada de la portada: 1.0f cuando reproduce, 0.88f cuando está pausado (estilo Apple Podcasts)
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.88f,
        animationSpec = tween(durationMillis = 350),
        label = "artworkScaleAnim"
    )

    // Metamorfosis de forma del botón de Play/Pause (Círculo 32dp ↔ Squircle 18dp)
    val playButtonCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 20.dp else 36.dp,
        animationSpec = tween(durationMillis = 300),
        label = "playButtonCornerRadius"
    )

    // Desplazamiento horizontal para arrastrar carátula (cambio de episodio en cola)
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    MaterialTheme(colorScheme = dynamicColorScheme) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.Black,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 1. FONDO: Portada desenfocada (blur 60dp en API 31+) con degradado oscuro inmersivo
                if (artworkUrl.isNotBlank()) {
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(60.dp)
                    } else {
                        Modifier
                    }
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                            .scale(1.25f),
                        contentScale = ContentScale.Crop
                    )
                }

                // Velo de degradado oscuro multicapa para máximo contraste y legibilidad
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Black.copy(alpha = 0.96f),
                                    Color.Black
                                )
                            )
                        )
                )

                // 2. CONTENIDO PRINCIPAL SCROLLABLE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 4.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // BARRA SUPERIOR: Solo botón de cerrar (⌄) y menú de opciones (⋮)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.player_close_description),
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Indicador de "En reproducción"
                        Text(
                            text = podcastTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            textAlign = TextAlign.Center
                        )

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Efectos de audio (Voz / Silencio)") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = dominantArtworkColor)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showAudioEffectsSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showEpisodeNotes) "Ocultar notas del episodio" else "Ver notas del episodio") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Subtitles, contentDescription = null)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showEpisodeNotes = !showEpisodeNotes
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // PORTADA GRANDE (~85% de ancho) con escala animada al pausar y gesto de deslizamiento
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .scale(artworkScale)
                            .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    swipeOffsetX = (swipeOffsetX + delta).coerceIn(-180f, 180f)
                                },
                                onDragStopped = {
                                    if (swipeOffsetX > 90f) {
                                        // Gesto hacia la derecha: reanudar / repetir
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        playerViewModel.seekBack()
                                    } else if (swipeOffsetX < -90f) {
                                        // Gesto hacia la izquierda: siguiente episodio si existe en la cola
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val nextInQueue = queueEpisodes.firstOrNull()
                                        if (nextInQueue != null) {
                                            playerViewModel.playEpisode(
                                                nextInQueue,
                                                podcastTitle,
                                                nextInQueue.imageUrl.ifBlank { podcastArtworkUrl }
                                            )
                                        } else {
                                            playerViewModel.seekForward()
                                        }
                                    }
                                    swipeOffsetX = 0f
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Halo suave con el color dominante
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .shadow(
                                    elevation = 28.dp,
                                    shape = RoundedCornerShape(26.dp),
                                    ambientColor = dominantArtworkColor,
                                    spotColor = dominantArtworkColor
                                )
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = stringResource(R.string.player_cover_description),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(dominantArtworkColor, IndigoSecondary))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Podcasts,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // TÍTULO DEL EPISODIO Y PODCAST
                    Text(
                        text = currentEpisode?.title ?: "Episodio",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = podcastTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = dominantArtworkColor,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    // CHIP DE CAPÍTULO INTERACTIVO CON SALTOS (si tiene capítulos)
                    if (chapters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = dominantArtworkColor.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChaptersSheet = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playerViewModel.seekToPreviousChapter()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = stringResource(R.string.player_prev_chapter_description),
                                        tint = dominantArtworkColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val currentIdx = currentChapter?.let { chapters.indexOf(it) } ?: -1
                                    val chapterIndexText = if (currentIdx >= 0) "Capítulo ${currentIdx + 1} de ${chapters.size}" else "${chapters.size} capítulos"
                                    Text(
                                        text = currentChapter?.title ?: "Capítulo",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "$chapterIndexText • Toca para lista",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playerViewModel.seekToNextChapter()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = stringResource(R.string.player_next_chapter_description),
                                        tint = dominantArtworkColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. SLIDER ONDULADO MATERIAL 3 EXPRESSIVE CON MARCAS DE CAPÍTULOS
                    val maxSlider = if (durationMs > 0) durationMs.toFloat() else 1f
                    val currentPosFloat = currentPosMs.toFloat().coerceIn(0f, maxSlider)

                    ExpressiveWavySlider(
                        value = currentPosFloat,
                        onValueChange = { playerViewModel.seekTo(it.toLong()) },
                        valueRange = 0f..maxSlider,
                        isPlaying = isPlaying,
                        chapters = chapters,
                        totalDurationMs = durationMs,
                        waveColor = dominantArtworkColor,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tiempos (actual y restante con cifras tabulares tnum)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosMs),
                            style = TabularTimeStyle.copy(color = Color.White.copy(alpha = 0.75f))
                        )
                        Text(
                            text = "-" + formatTime(max(0L, durationMs - currentPosMs)),
                            style = TabularTimeStyle.copy(color = Color.White.copy(alpha = 0.75f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. CONTROLES DE TRANSPORTE PRINCIPALES: Retroceso, Play/Pause Squircle y Avance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val backSec = userSettings.skipBackwardSeconds
                        val forwardSec = userSettings.skipForwardSeconds

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerViewModel.seekBack()
                            },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = when (backSec) {
                                    5 -> Icons.Filled.Replay5
                                    10 -> Icons.Filled.Replay10
                                    30 -> Icons.Filled.Replay30
                                    else -> Icons.Filled.FastRewind
                                },
                                contentDescription = stringResource(R.string.player_skip_backward_format, backSec),
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }

                        // Botón central de Play / Pause con Squircle animado y respuesta háptica
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerViewModel.togglePlayPause()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(playButtonCornerRadius))
                                .background(dominantArtworkColor)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerViewModel.seekForward()
                            },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = when (forwardSec) {
                                    5 -> Icons.Filled.Forward5
                                    10 -> Icons.Filled.Forward10
                                    30 -> Icons.Filled.Forward30
                                    else -> Icons.Filled.FastForward
                                },
                                contentDescription = stringResource(R.string.player_skip_forward_format, forwardSec),
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 6. FILA INFERIOR DE 5 ICONOS ESENCIALES (Velocidad, Temporizador, Clips, Transcripción, Cola)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Selector de velocidad
                            IconButton(onClick = { showPlaybackSpeedSheet = true }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = stringResource(R.string.player_speed_description),
                                        tint = if (playbackSpeed != 1.0f) dominantArtworkColor else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = PlaybackController.formatSpeed(playbackSpeed),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (playbackSpeed != 1.0f) dominantArtworkColor else Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // 2. Temporizador de apagado
                            IconButton(onClick = { showSleepTimerSheet = true }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Bedtime,
                                        contentDescription = stringResource(R.string.player_sleep_timer_description),
                                        tint = if (sleepTimerState.isActive) dominantArtworkColor else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (sleepTimerState.isActive) sleepTimerState.formattedRemaining else "Apagar",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (sleepTimerState.isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sleepTimerState.isActive) dominantArtworkColor else Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // 3. Marcadores / Clips
                            IconButton(onClick = { showBookmarksSheet = true }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = stringResource(R.string.player_bookmarks_description),
                                        tint = if (bookmarks.isNotEmpty()) dominantArtworkColor else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (bookmarks.isNotEmpty()) "${bookmarks.size}" else "Clips",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (bookmarks.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                        color = if (bookmarks.isNotEmpty()) dominantArtworkColor else Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // 4. Transcripción / Subtítulos
                            val hasTranscript = !currentEpisode?.transcriptUrl.isNullOrBlank() || transcript.isNotEmpty()
                            IconButton(
                                onClick = { if (hasTranscript) showTranscriptSheet = true },
                                enabled = hasTranscript
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Subtitles,
                                        contentDescription = stringResource(R.string.player_transcript_description),
                                        tint = if (hasTranscript) dominantArtworkColor else Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Texto",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (hasTranscript) FontWeight.Bold else FontWeight.Normal,
                                        color = if (hasTranscript) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            // 5. Cola de reproducción
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onNavigateToQueue()
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = "Cola",
                                        tint = if (queueEpisodes.isNotEmpty()) dominantArtworkColor else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (queueEpisodes.isNotEmpty()) "${queueEpisodes.size}" else "Cola",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (queueEpisodes.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                        color = if (queueEpisodes.isNotEmpty()) dominantArtworkColor else Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }

                    // Ficha descriptiva opcional desplegable
                    val rawDescription = currentEpisode?.description.orEmpty()
                    if (showEpisodeNotes && rawDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val cleanDescription = remember(rawDescription) {
                            try {
                                Html.fromHtml(rawDescription, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                            } catch (_: Exception) {
                                rawDescription.trim()
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Notas del episodio",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = dominantArtworkColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cleanDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // SUB-HOJAS DESPLEGABLES
    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            playerViewModel = playerViewModel,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showPlaybackSpeedSheet) {
        PlaybackSpeedBottomSheet(
            currentSpeed = playbackSpeed,
            onSpeedSelected = { playerViewModel.setPlaybackSpeed(it) },
            onDismissRequest = { showPlaybackSpeedSheet = false }
        )
    }

    if (showChaptersSheet) {
        ChaptersBottomSheet(
            chapters = chapters,
            currentChapter = currentChapter,
            currentPositionMs = currentPosMs,
            onChapterSelected = { playerViewModel.seekToChapter(it) },
            onNextChapter = { playerViewModel.seekToNextChapter() },
            onPrevChapter = { playerViewModel.seekToPreviousChapter() },
            onDismissRequest = { showChaptersSheet = false }
        )
    }

    if (showTranscriptSheet) {
        TranscriptBottomSheet(
            transcript = transcript,
            currentPositionMs = currentPosMs,
            isLoading = isTranscriptLoading,
            onSeekTo = { playerViewModel.seekTo(it) },
            onDismissRequest = { showTranscriptSheet = false }
        )
    }

    if (showAudioEffectsSheet) {
        AudioEffectsBottomSheet(
            playerViewModel = playerViewModel,
            onDismissRequest = { showAudioEffectsSheet = false }
        )
    }

    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            playerViewModel = playerViewModel,
            onDismissRequest = { showBookmarksSheet = false }
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
