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

package com.ferlagod.mispodcast.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.local.model.QueueEpisodeItem
import com.ferlagod.mispodcast.data.local.model.QueueSortOption
import com.ferlagod.mispodcast.ui.theme.AccentRed
import com.ferlagod.mispodcast.ui.theme.IndigoSecondary
import com.ferlagod.mispodcast.ui.theme.RoyalBlueLight
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.ferlagod.mispodcast.ui.components.EmptyStateView
import com.ferlagod.mispodcast.ui.components.EpisodeDetailSheet
import com.ferlagod.mispodcast.ui.components.EpisodeDownloadButton
import com.ferlagod.mispodcast.ui.components.EqualizerIndicator
import com.ferlagod.mispodcast.ui.components.PlayProgressButton
import com.ferlagod.mispodcast.ui.components.PodcastArtworkPlaceholder
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel

/**
 * Pantalla de administración de la cola de reproducción.
 *
 * Permite:
 * - Visualizar la lista de episodios pendientes y el capítulo en escucha activa.
 * - Reordenar elementos mediante arrastre táctil.
 * - Eliminar episodios mediante gesto de deslizamiento (Swipe-to-Dismiss).
 * - Ordenar automáticamente la cola según duración, tamaño, fecha o título.
 * - Vaciar la cola completa conservando el episodio en reproducción.
 */
@Composable
fun QueueScreen(
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel
) {
    val queueItems by podcastViewModel.queue.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosMs by playerViewModel.currentPositionMs.collectAsState()
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val downloadStates by podcastViewModel.downloadStates.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val undownloadedCount = remember(queueItems, downloadStates) {
        queueItems.count { item ->
            val st = downloadStates[item.episode.id] ?: podcastViewModel.getEpisodeDownloadState(item.episode)
            st.status != DownloadStatus.DOWNLOADED && !item.episode.isDownloaded
        }
    }

    var showSortMenu by remember { mutableStateOf(false) }

    val totalDurationMs = remember(queueItems) {
        queueItems.sumOf { it.episode.duration }
    }
    val totalHours = totalDurationMs / 3600000
    val totalMins = (totalDurationMs % 3600000) / 60000

    var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
    val selectedEpisodeItem = remember(selectedEpisodeId, queueItems) {
        queueItems.find { it.episode.id == selectedEpisodeId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
        // Encabezado principal y acciones
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.up_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Menú de Ordenación Automática
                        if (queueItems.size > 2) {
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.sort_by),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    Text(
                                        text = stringResource(R.string.sort_rest_of_queue),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    QueueSortOption.entries.forEach { sortOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(sortOption.titleRes),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            onClick = {
                                                showSortMenu = false
                                                podcastViewModel.sortQueue(sortOption)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (queueItems.isNotEmpty()) {
                            TextButton(onClick = { podcastViewModel.clearQueue() }) {
                                Icon(
                                    imageVector = Icons.Filled.ClearAll,
                                    contentDescription = stringResource(R.string.clear_queue),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text("${queueItems.size} ${stringResource(R.string.episodes)}") }
                    )
                    if (totalDurationMs > 0) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Aprox. ${if (totalHours > 0) "${totalHours}h " else ""}${totalMins}m") }
                        )
                    }
                    if (undownloadedCount > 0) {
                        SuggestionChip(
                            onClick = {
                                queueItems.forEach { item ->
                                    val st = downloadStates[item.episode.id] ?: podcastViewModel.getEpisodeDownloadState(item.episode)
                                    if (st.status != DownloadStatus.DOWNLOADED && !item.episode.isDownloaded) {
                                        podcastViewModel.downloadEpisode(item.episode)
                                    }
                                }
                                Toast.makeText(context, "Descargando $undownloadedCount episodios...", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Descargar todo ($undownloadedCount)") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = stringResource(R.string.download_all),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        }

        val isCurrentPlayingInQueue = currentEpisode != null && queueItems.any { it.episode.id == currentEpisode?.id }

        val activeQueueEpisode = when {
            isCurrentPlayingInQueue -> {
                queueItems.firstOrNull { it.episode.id == currentEpisode?.id } ?: queueItems.first()
            }
            currentEpisode != null -> {
                currentEpisode?.let { ep ->
                    QueueEpisodeItem(
                        queueId = 0L,
                        positionIndex = 0,
                        isCurrentlyPlaying = true,
                        addedAt = System.currentTimeMillis(),
                        podcastTitle = playerViewModel.podcastTitle.value,
                        podcastArtworkUrl = playerViewModel.podcastArtworkUrl.value,
                        episode = ep
                    )
                }
            }
            else -> queueItems.firstOrNull()
        }

        if (activeQueueEpisode == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Filled.Headphones,
                        title = stringResource(R.string.empty_queue_title),
                        subtitle = stringResource(R.string.empty_queue_subtitle)
                    )
                }
            }
        } else {
            // 1. REGLA INAMOVIBLE: El episodio actual (posición 0) SIEMPRE permanece fijo arriba
            val firstItem = activeQueueEpisode
            val isCurrentItemActive = currentEpisode?.id == firstItem.episode.id
            val isAudioPlayingNow = isPlaying && isCurrentItemActive

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEpisodeId = firstItem.episode.id },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), // Borde azul corporativo
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Cabecera de la tarjeta con badge azul corporativo y onda activa
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        AudioWaveIndicator(
                                            isPlaying = isAudioPlayingNow,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAudioPlayingNow) "REPRODUCIENDO AHORA" else "EN REPRODUCCIÓN (PAUSA)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "POSICIÓN 1",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val art = firstItem.episode.imageUrl.ifBlank { firstItem.podcastArtworkUrl }
                                if (art.isNotBlank()) {
                                    AsyncImage(
                                        model = art,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    PodcastArtworkPlaceholder(
                                        title = firstItem.podcastTitle.ifBlank { firstItem.episode.title },
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        iconSize = 36.dp
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = firstItem.podcastTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isAudioPlayingNow) {
                                            EqualizerIndicator(
                                                isPlaying = true,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(width = 16.dp, height = 12.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = firstItem.episode.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val currentFormatted = formatQueueDuration(if (isCurrentItemActive) currentPosMs else firstItem.episode.currentPositionMs)
                                    val totalFormatted = formatQueueDuration(firstItem.episode.duration)
                                    Text(
                                        text = "$currentFormatted / $totalFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val activeDownloadState = downloadStates[firstItem.episode.id] ?: podcastViewModel.getEpisodeDownloadState(firstItem.episode)
                                EpisodeDownloadButton(
                                    downloadState = activeDownloadState,
                                    onDownloadClick = {
                                        podcastViewModel.downloadEpisode(firstItem.episode)
                                        Toast.makeText(context, "Descargando episodio...", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isCurrentItemActive) {
                                            playerViewModel.togglePlayPause()
                                        } else {
                                            playerViewModel.playEpisode(firstItem.episode, firstItem.podcastTitle, firstItem.podcastArtworkUrl)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isAudioPlayingNow) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isAudioPlayingNow) "Pausa" else "Reproducir",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val progress = if (firstItem.episode.duration > 0) {
                                val current = if (isCurrentItemActive) currentPosMs else firstItem.episode.currentPositionMs
                                (current.toFloat() / firstItem.episode.duration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentFormatted = formatQueueDuration(if (isCurrentItemActive) currentPosMs else firstItem.episode.currentPositionMs)
                                val remaining = if (firstItem.episode.duration > 0) (firstItem.episode.duration - (if (isCurrentItemActive) currentPosMs else firstItem.episode.currentPositionMs)).coerceAtLeast(0) else 0L
                                Text(
                                    text = currentFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "-${formatQueueDuration(remaining)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2, 3 y 4. Elementos siguientes en la cola con Reordenación y Gestos Swipe
            val subsequentItems = when {
                isCurrentPlayingInQueue -> {
                    queueItems.filter { it.episode.id != activeQueueEpisode.episode.id }
                }
                currentEpisode != null -> {
                    queueItems.filter { it.episode.id != currentEpisode?.id }
                }
                else -> {
                    queueItems.drop(1)
                }
            }
            if (subsequentItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A CONTINUACIÓN EN LA COLA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Desliza para gestionar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                itemsIndexed(
                    items = subsequentItems,
                    key = { _, item -> item.episode.id }
                ) { index, item ->
                    Box(modifier = Modifier.animateItem()) {
                        // 4. SwipeToDismissBox (Material 3)
                        @Suppress("DEPRECATION")
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        // Deslizar izquierda: Eliminar de la cola
                                        podcastViewModel.removeEpisodeFromQueue(item.episode.id)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        // Deslizar derecha: Marcar como reproducido y eliminar de la cola
                                        podcastViewModel.markEpisodeAsPlayedAndRemoveFromQueue(item.episode.id)
                                        true
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
                                val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart

                                val backgroundColor = when {
                                    isStartToEnd -> Color(0xFF34C759) // Verde
                                    isEndToStart -> Color(0xFFFF3B30) // Rojo
                                    else -> Color.Transparent
                                }

                                val alignment = when {
                                    isStartToEnd -> Alignment.CenterStart
                                    isEndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 5.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(backgroundColor)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (isStartToEnd) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = stringResource(R.string.mark_as_played),
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.wrapped_stat_completed),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    } else if (isEndToStart) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.delete_download),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.remove_from_queue),
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 5.dp)
                                    .clickable { selectedEpisodeId = item.episode.id },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Portada del episodio/podcast con bordes redondeados (8dp)
                                    val episodeCoverUrl = item.episode.imageUrl.ifBlank { item.podcastArtworkUrl }
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (episodeCoverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = episodeCoverUrl,
                                                contentDescription = item.episode.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            PodcastArtworkPlaceholder(
                                                title = item.podcastTitle.ifBlank { item.episode.title },
                                                modifier = Modifier.fillMaxSize(),
                                                iconSize = 24.dp
                                            )
                                        }

                                        // Índice de posición en la cola superpuesto con elegancia
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.72f),
                                                    shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 8.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "${index + 2}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.episode.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val durationMin = item.episode.duration / 60000
                                        Text(
                                            text = "${item.podcastTitle} • ${durationMin} min",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val itemDownloadState = downloadStates[item.episode.id] ?: podcastViewModel.getEpisodeDownloadState(item.episode)
                                    EpisodeDownloadButton(
                                        downloadState = itemDownloadState,
                                        onDownloadClick = {
                                            podcastViewModel.downloadEpisode(item.episode)
                                            Toast.makeText(context, "Descargando episodio...", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Botón reproducir directo con anillo de progreso
                                    PlayProgressButton(
                                        isPlaying = false,
                                        progress = if (item.episode.duration > 0 && item.episode.currentPositionMs > 0) {
                                            (item.episode.currentPositionMs.toFloat() / item.episode.duration.toFloat()).coerceIn(0f, 1f)
                                        } else 0f,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playerViewModel.playEpisode(item.episode, item.podcastTitle, item.podcastArtworkUrl)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    )

                                    // Controles de movimiento rápido arriba / abajo
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    podcastViewModel.moveSubsequentQueueItem(index, index - 1)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowUp,
                                                contentDescription = stringResource(R.string.move_up),
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index < subsequentItems.lastIndex) {
                                                    podcastViewModel.moveSubsequentQueueItem(index, index + 1)
                                                }
                                            },
                                            enabled = index < subsequentItems.lastIndex,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.move_down),
                                                tint = if (index < subsequentItems.lastIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // 3. Icono de agarre DragHandle con detección de arrastre vertical
                                    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
                                    var isDraggingItem by remember { mutableStateOf(false) }

                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.drag_handle_description),
                                        tint = if (isDraggingItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(subsequentItems.size, index) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        isDraggingItem = true
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDragEnd = {
                                                        isDraggingItem = false
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDragCancel = {
                                                        isDraggingItem = false
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragAccumulatedY += dragAmount
                                                        val thresholdPx = 70.dp.toPx()
                                                        if (dragAccumulatedY > thresholdPx && index < subsequentItems.lastIndex) {
                                                            podcastViewModel.moveSubsequentQueueItem(index, index + 1)
                                                            dragAccumulatedY = 0f
                                                        } else if (dragAccumulatedY < -thresholdPx && index > 0) {
                                                            podcastViewModel.moveSubsequentQueueItem(index, index - 1)
                                                            dragAccumulatedY = 0f
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Ficha completa del episodio (ModalBottomSheet) al pulsar sobre cualquier episodio de la cola
    selectedEpisodeItem?.let { item ->
        val isItemPlaying = currentEpisode?.id == item.episode.id && isPlaying
        val epDownloadState = downloadStates[item.episode.id] ?: podcastViewModel.getEpisodeDownloadState(item.episode)

        EpisodeDetailSheet(
            episode = item.episode,
            podcastTitle = item.podcastTitle,
            podcastArtwork = item.podcastArtworkUrl,
            isPlaying = isItemPlaying,
            downloadState = epDownloadState,
            onDismiss = { selectedEpisodeId = null },
            onPlayClick = {
                if (isItemPlaying) {
                    playerViewModel.togglePlayPause()
                } else {
                    playerViewModel.playEpisode(item.episode, item.podcastTitle, item.podcastArtworkUrl)
                }
            },
            onAddToQueue = {
                podcastViewModel.addEpisodeToQueue(item.episode.id)
                Toast.makeText(context, context.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
            },
            onToggleFavorite = {
                podcastViewModel.toggleFavorite(item.episode.id, item.episode.isFavorite)
            },
            onDownloadClick = {
                podcastViewModel.downloadEpisode(item.episode)
                Toast.makeText(context, context.getString(R.string.toast_downloading_episode), Toast.LENGTH_SHORT).show()
            },
            onTogglePlayed = {
                podcastViewModel.toggleEpisodePlayed(item.episode.id, item.episode.isPlayed)
                val msg = if (!item.episode.isPlayed) context.getString(R.string.toast_marked_as_played) else context.getString(R.string.toast_marked_as_unplayed)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onShareClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, item.episode.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.share_episode_format, item.episode.title, item.podcastTitle, item.episode.audioUrl)
                    )
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_episode)))
            }
        )
    }
}
}

@Composable
private fun AudioWaveIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b4"
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = if (isPlaying) listOf(bar1, bar2, bar3, bar4) else listOf(0.4f, 0.75f, 0.35f, 0.6f)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 * h).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

private fun formatQueueDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
