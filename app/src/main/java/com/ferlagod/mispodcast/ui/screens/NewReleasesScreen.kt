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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.ui.components.EpisodeDownloadButton
import com.ferlagod.mispodcast.ui.theme.AccentOrange
import com.ferlagod.mispodcast.ui.theme.AccentRed
import com.ferlagod.mispodcast.ui.theme.IndigoSecondary
import com.ferlagod.mispodcast.ui.theme.RoyalBlueLight
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ferlagod.mispodcast.ui.components.EqualizerIndicator
import com.ferlagod.mispodcast.ui.components.SwipeableEpisodeRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de novedades: muestra cronológicamente los nuevos episodios publicados
 * por los podcasts seguidos con posterioridad a la fecha de suscripción del usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReleasesScreen(
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToAddPodcast: () -> Unit = {}
) {
    val newReleases by podcastViewModel.newReleases.collectAsState()
    val downloadStates by podcastViewModel.downloadStates.collectAsState()
    val isRefreshing by podcastViewModel.isRefreshing.collectAsState()
    val queue by podcastViewModel.queue.collectAsState()
    val currentPlayingEpisode by playerViewModel.currentEpisode.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { podcastViewModel.refreshAllPodcasts() },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
        // Cabecera superior con título y botones de acción en bloque para la bandeja de entrada
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.from_your_subscriptions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.nav_new_releases),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Capítulos recién publicados por tus creadores favoritos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (newReleases.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var showEnqueueMenu by remember { mutableStateOf(false) }
                        Box {
                            FilledTonalButton(
                                onClick = { showEnqueueMenu = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = stringResource(R.string.queue_all),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Encolar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            DropdownMenu(
                                expanded = showEnqueueMenu,
                                onDismissRequest = { showEnqueueMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Añadir todos a continuación (Up Next)") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                                    },
                                    onClick = {
                                        showEnqueueMenu = false
                                        podcastViewModel.enqueueAllNewReleases(toTop = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Añadir todos al final de la cola") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Queue, contentDescription = null)
                                    },
                                    onClick = {
                                        showEnqueueMenu = false
                                        podcastViewModel.enqueueAllNewReleases(toTop = false)
                                    }
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { podcastViewModel.clearAllNewReleases() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = stringResource(R.string.clear),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.clear),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (newReleases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Podcasts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_new_releases),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.empty_new_releases_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToAddPodcast,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Explorar podcasts")
                        }
                    }
                }
            }
        } else {
            items(newReleases, key = { it.episode.id }) { item ->
                val downloadState = downloadStates[item.episode.id] ?: podcastViewModel.getEpisodeDownloadState(item.episode)
                val inQueue = remember(queue, item.episode.id) {
                    queue.any { it.episode.id == item.episode.id }
                }
                val isPlayingThis = isPlaying && currentPlayingEpisode?.id == item.episode.id

                SwipeableEpisodeRow(
                    onSwipeToQueue = {
                        podcastViewModel.addEpisodeToQueue(item.episode.id)
                    },
                    onSwipeToComplete = {
                        podcastViewModel.toggleEpisodePlayed(item.episode.id, item.episode.isPlayed)
                    }
                ) {
                    NewReleaseCard(
                        item = item,
                        inQueue = inQueue,
                        isPlaying = isPlayingThis,
                        downloadState = downloadState,
                        onPlay = {
                            playerViewModel.playEpisode(item.episode, item.podcastTitle, item.podcastArtworkUrl)
                        },
                        onAddToQueue = {
                            podcastViewModel.addEpisodeToQueue(item.episode.id)
                        },
                        onAddToQueueTop = {
                            podcastViewModel.addEpisodeToQueueTop(item.episode.id)
                        },
                        onRemoveFromQueue = {
                            podcastViewModel.removeEpisodeFromQueue(item.episode.id)
                        },
                        onToggleFavorite = {
                            podcastViewModel.toggleFavorite(item.episode.id, item.episode.isFavorite)
                        },
                        onDownload = {
                            podcastViewModel.downloadEpisode(item.episode)
                        },
                        onMarkAsPlayed = {
                            podcastViewModel.markEpisodeAsPlayed(item.episode.id)
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun NewReleaseCard(
    item: EpisodeWithPodcast,
    inQueue: Boolean,
    isPlaying: Boolean = false,
    downloadState: EpisodeDownloadState,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToQueueTop: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onMarkAsPlayed: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dateText = remember(item.episode.pubDate) {
        val now = System.currentTimeMillis()
        val diffDays = (now - item.episode.pubDate) / (1000 * 60 * 60 * 24)
        when {
            diffDays == 0L -> "Hoy"
            diffDays == 1L -> "Ayer"
            diffDays < 7L -> "Hace $diffDays días"
            else -> java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(item.episode.pubDate))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val art = item.episode.imageUrl.ifBlank { item.podcastArtworkUrl }
                if (art.isNotBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(RoyalBlueLight, AccentOrange))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Podcasts, null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.podcastTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlaying) {
                            EqualizerIndicator(
                                isPlaying = true,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = 16.dp, height = 12.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$dateText • ${item.episode.duration / 60000} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (inQueue) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "En cola",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botón rápido para marcar episodio individual como reproducido
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMarkAsPlayed()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.mark_as_played),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(if (item.episode.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                            tint = if (item.episode.isFavorite) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.episode.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (item.episode.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                // Limpiar posibles etiquetas HTML del texto
                val cleanDesc = remember(item.episode.description) {
                    item.episode.description.replace(Regex("<[^>]*>"), "").trim()
                }
                Text(
                    text = cleanDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción directa: Reproducir, Siguiente (Up Next), + Cola, Descargar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlay()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reproducir", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                if (!inQueue) {
                    FilledTonalButton(
                        onClick = onAddToQueueTop,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.play_next), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Siguiente", maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = onAddToQueue,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Queue, contentDescription = stringResource(R.string.play_last), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Cola", maxLines = 1, softWrap = false)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRemoveFromQueue,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("En cola ✓", maxLines = 1, softWrap = false)
                    }
                }

                EpisodeDownloadButton(
                    downloadState = downloadState,
                    onDownloadClick = onDownload
                )
            }
        }
    }
}
