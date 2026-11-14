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

import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.ui.navigation.rememberArtworkRequest
import com.ferlagod.mispodcast.ui.theme.AccentGreen
import com.ferlagod.mispodcast.ui.theme.IndigoSecondary
import com.ferlagod.mispodcast.ui.theme.RoyalBlueLight
import com.ferlagod.mispodcast.ui.theme.rememberArtworkDominantColor
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ferlagod.mispodcast.ui.components.EmptyStateView
import com.ferlagod.mispodcast.ui.components.EqualizerIndicator
import com.ferlagod.mispodcast.ui.components.PlayProgressButton
import com.ferlagod.mispodcast.ui.components.PodcastArtworkPlaceholder
import com.ferlagod.mispodcast.ui.components.SwipeableEpisodeRow
import androidx.compose.material.icons.filled.Headphones
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import com.ferlagod.mispodcast.ui.theme.EditorialAmber
import com.ferlagod.mispodcast.ui.theme.EditorialMoss
import com.ferlagod.mispodcast.ui.theme.EditorialOcean
import com.ferlagod.mispodcast.ui.theme.EditorialOchre
import com.ferlagod.mispodcast.ui.theme.EditorialPlum
import com.ferlagod.mispodcast.ui.theme.EditorialRose
import com.ferlagod.mispodcast.ui.theme.EditorialSlate
import com.ferlagod.mispodcast.ui.theme.EditorialTeal
import com.ferlagod.mispodcast.ui.theme.readableContentColor

// Tarjetas reutilizables de la pantalla de inicio.

/**
 * Tarjeta cuadrada de 140dp para "Tus podcasts".
 */
@Composable
fun SubscribedPodcastCard(
    podcast: PodcastEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (podcast.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = rememberArtworkRequest(podcast.artworkUrl),
                    contentDescription = podcast.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PodcastArtworkPlaceholder(
                    title = podcast.title,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 44.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = podcast.author.ifBlank { "Podcast" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tarjeta de 140dp para el carrusel de recomendaciones de Apple Podcasts
 * con botón flotante de suscripción rápida y categoría.
 */
@Composable
fun RecommendationCard(
    podcast: ApplePodcastDto,
    isSubscribed: Boolean,
    isSubscriptionLoading: Boolean,
    onSubscribe: () -> Unit,
    onClick: () -> Unit
) {
    val artwork = podcast.artworkUrl600 ?: podcast.artworkUrl100

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        val podcastId = podcast.collectionId?.toString() ?: podcast.feedUrl.orEmpty()
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = podcast.collectionName,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PodcastArtworkPlaceholder(
                    title = podcast.collectionName.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 44.dp
                )
            }

            // Botón flotante de suscripción en la esquina inferior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                if (isSubscribed) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = !isSubscriptionLoading) { onSubscribe() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.subscribe),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = podcast.collectionName ?: stringResource(R.string.untitled),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = podcast.artistName ?: stringResource(R.string.unknown_author),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!podcast.primaryGenreName.isNullOrBlank()) {
            Text(
                text = podcast.primaryGenreName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tarjeta de categoría estilo editorial: color sólido con icono decorativo recortado.
 */
@Composable
fun CategoryCollectionCard(
    chip: CategoryChipData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(90.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chip.accentColor)
        ) {
            Icon(
                imageVector = chip.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
            )

            Text(
                text = stringResource(chip.nameRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Fila para cada episodio en la lista cronológica de últimos publicados.
 */
@Composable
fun EpisodeRowCard(
    item: EpisodeWithPodcast,
    isPlaying: Boolean = false,
    progressRatio: Float = 0f,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dateFormatted = remember(item.episode.pubDate) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(item.episode.pubDate))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val art = item.episode.imageUrl.ifBlank { item.podcastArtworkUrl }
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            } else {
                PodcastArtworkPlaceholder(
                    title = item.podcastTitle,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium),
                    iconSize = 28.dp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                Text(
                    text = item.episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val durationMins = item.episode.duration / 60000
                Text(
                    text = if (durationMins > 0) "$dateFormatted · $durationMins min" else dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            PlayProgressButton(
                isPlaying = isPlaying,
                progress = progressRatio,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlay()
                },
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
