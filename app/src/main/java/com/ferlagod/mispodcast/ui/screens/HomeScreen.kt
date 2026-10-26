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
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.model.EpisodeWithPodcast
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.ui.navigation.sharedPodcastArtwork
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

/**
 * Modelo de datos para las categorías con iconos y colores temáticos.
 */
data class CategoryChipData(
    val emoji: String,
    val name: String,
    val genreId: Int,
    val accentColor: Color
)

/**
 * Genera un pincel animado para efecto Shimmer / Skeleton de carga.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Pantalla de inicio con estética editorial, carruseles de 140dp,
 * hero de ancho completo con color dominante y saludo contextual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToAddPodcast: () -> Unit = {},
    onNavigateToCategory: (categoryName: String, genreId: Int) -> Unit = { _, _ -> },
    onPodcastClick: (podcastId: String, feedUrl: String) -> Unit = { _, _ -> }
) {
    val isRefreshing by podcastViewModel.isRefreshing.collectAsState()
    val newReleases by podcastViewModel.newReleases.collectAsState()
    val queue by podcastViewModel.queue.collectAsState()
    val history by podcastViewModel.history.collectAsState()
    val currentPlayingEpisode by playerViewModel.currentEpisode.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosMs by playerViewModel.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val recommendations by podcastViewModel.recommendations.collectAsState()
    val recommendationTitle by podcastViewModel.recommendationTitle.collectAsState()
    val recommendationSubtitle by podcastViewModel.recommendationSubtitle.collectAsState()
    val isLoadingRecommendations by podcastViewModel.isLoadingRecommendations.collectAsState()
    val recommendationError by podcastViewModel.recommendationError.collectAsState()
    val subscriptionLoading by podcastViewModel.subscriptionLoading.collectAsState()
    val subscriptionMessage by podcastViewModel.subscriptionMessage.collectAsState()
    val subscribedPodcasts by podcastViewModel.subscribedPodcasts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Saludo contextual según la hora del día
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 6..12 -> "Buenos días, Fer"
            in 13..20 -> "Buenas tardes, Fer"
            else -> "Buenas noches, Fer"
        }
    }

    // Resumen de tiempo y actividad pendiente
    val pendingMs = remember(queue, newReleases) {
        if (queue.isNotEmpty()) {
            queue.sumOf { it.episode.duration }
        } else {
            newReleases.take(5).sumOf { it.episode.duration }
        }
    }
    val pendingHours = pendingMs / (1000 * 60 * 60)
    val pendingMinutes = (pendingMs % (1000 * 60 * 60)) / (1000 * 60)
    val pendingTimeText = if (pendingHours > 0) "${pendingHours} h pendientes" else "${max(1L, pendingMinutes)} min pendientes"

    val activitySummary = remember(newReleases.size, queue.size, pendingTimeText) {
        if (newReleases.isNotEmpty()) {
            "${newReleases.size} episodios nuevos · $pendingTimeText"
        } else if (queue.isNotEmpty()) {
            "${queue.size} en cola · $pendingTimeText"
        } else {
            "Tu biblioteca al día · Explora podcasts"
        }
    }

    // Categorías con emoji y color propio
    val categoryChips = remember {
        listOf(
            CategoryChipData("✨", "Para ti", 0, Color(0xFF6366F1)),
            CategoryChipData("🔬", "Ciencia", 1533, Color(0xFF10B981)),
            CategoryChipData("😂", "Comedia", 1303, Color(0xFFEC4899)),
            CategoryChipData("📚", "Libros", 1301, Color(0xFFF97316)),
            CategoryChipData("💻", "Tecnología", 1318, Color(0xFF0EA5E9)),
            CategoryChipData("🏛️", "Historia", 1487, Color(0xFFF59E0B)),
            CategoryChipData("📰", "Noticias", 1489, Color(0xFFEF4444)),
            CategoryChipData("🎭", "Ficción", 1483, Color(0xFF8B5CF6)),
            CategoryChipData("🏃", "Salud", 1512, Color(0xFF14B8A6))
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { podcastViewModel.refreshAllPodcasts() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Cabecera editorial con saludo contextual
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MIS PODCAST",
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activitySummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = stringResource(R.string.about_logo_description),
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAddPodcast() },
                        placeholder = {
                            Text(
                                stringResource(R.string.search_or_paste_rss),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.tab_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        singleLine = true,
                        readOnly = true
                    )
                }
            }

            // 2. Chips de categoría con emoji y color propio
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categoryChips) { chip ->
                        val isSelected = chip.genreId == 0
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = if (isSelected) chip.accentColor else chip.accentColor.copy(alpha = 0.14f),
                            modifier = Modifier.clickable {
                                if (chip.genreId > 0) {
                                    onNavigateToCategory(chip.name, chip.genreId)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chip.emoji,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = chip.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else chip.accentColor
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. Hero "Continuar escuchando" a todo el ancho con portada de fondo y color dominante
            val firstQueueItem = queue.firstOrNull()
            val activeEpisode = currentPlayingEpisode ?: firstQueueItem?.episode ?: history.firstOrNull { !it.completed }?.episode
            val activePodcastTitle = if (currentPlayingEpisode != null) playerViewModel.podcastTitle.value else firstQueueItem?.podcastTitle ?: history.firstOrNull()?.podcastTitle ?: "Podcast"
            val activeArtwork = currentPlayingEpisode?.imageUrl?.ifBlank { playerViewModel.podcastArtworkUrl.value } ?: firstQueueItem?.podcastArtworkUrl ?: history.firstOrNull()?.podcastArtworkUrl.orEmpty()

            if (activeEpisode != null) {
                item {
                    val dominantHeroColor = rememberArtworkDominantColor(artworkUrl = activeArtwork.ifBlank { null })

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playerViewModel.setExpanded(true) },
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                // Portada de fondo desenfocada
                                if (activeArtwork.isNotBlank()) {
                                    AsyncImage(
                                        model = activeArtwork,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(32.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Overlay degradado dominante cinematográfico
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    dominantHeroColor.copy(alpha = 0.72f),
                                                    Color(0xD90A0D14),
                                                    Color(0xF80A0D14)
                                                )
                                            )
                                        )
                                )

                                // Contenido Hero
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Badge superior
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Black.copy(alpha = 0.45f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isPlaying && currentPlayingEpisode?.id == activeEpisode.id) AccentGreen else dominantHeroColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isPlaying && currentPlayingEpisode?.id == activeEpisode.id) "EN REPRODUCCIÓN" else stringResource(R.string.continue_listening).uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.8.sp
                                                )
                                            }
                                        }

                                        val remainingMs = max(0L, (if (durationMs > 0) durationMs else activeEpisode.duration) - (if (currentPlayingEpisode != null) currentPosMs else activeEpisode.currentPositionMs))
                                        val remainingMin = remainingMs / 60000
                                        Text(
                                            text = if (remainingMin > 0) "Quedan $remainingMin min" else "Finalizado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Fila central: portada nítida, títulos y Play/Pause
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (activeArtwork.isNotBlank()) {
                                            AsyncImage(
                                                model = activeArtwork,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .border(1.dp, Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.medium),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            PodcastArtworkPlaceholder(
                                                title = activePodcastTitle,
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .border(1.dp, Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.medium),
                                                iconSize = 36.dp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activePodcastTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.75f),
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = activeEpisode.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        IconButton(
                                            onClick = {
                                                if (currentPlayingEpisode?.id == activeEpisode.id) {
                                                    playerViewModel.togglePlayPause()
                                                } else {
                                                    playerViewModel.playEpisode(activeEpisode, activePodcastTitle, activeArtwork)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .background(dominantHeroColor, RoundedCornerShape(18.dp))
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying && currentPlayingEpisode?.id == activeEpisode.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = stringResource(if (isPlaying && currentPlayingEpisode?.id == activeEpisode.id) R.string.action_pause else R.string.action_play),
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    // Barra de progreso inferior
                                    val effectiveDur = if (durationMs > 0) durationMs else activeEpisode.duration
                                    val effectivePos = if (currentPlayingEpisode != null) currentPosMs else activeEpisode.currentPositionMs
                                    val ratio = if (effectiveDur > 0) (effectivePos.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f) else 0f

                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = dominantHeroColor,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Carrusel 1: "Tus podcasts" (portadas de 140dp)
            if (subscribedPodcasts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.section_your_podcasts),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${subscribedPodcasts.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(subscribedPodcasts, key = { it.id }) { podcast ->
                            SubscribedPodcastCard(
                                podcast = podcast,
                                onClick = { onPodcastClick(podcast.id, podcast.feedUrl) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // 5. Carrusel 2: "Recomendados" con portadas grandes (140dp) y Shimmer / Skeleton
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recommendationTitle.ifBlank { stringResource(R.string.section_recommended) },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (recommendationSubtitle.isNotBlank()) {
                            Text(
                                text = recommendationSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { podcastViewModel.loadRecommendations() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                subscriptionMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✨ $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (isLoadingRecommendations) {
                    // Shimmer / Skeleton horizontal de portadas de 140dp
                    val shimmerBrush = rememberShimmerBrush()
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(4) {
                            Column(modifier = Modifier.width(140.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                            }
                        }
                    }
                } else if (recommendations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (recommendationError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = recommendationError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        androidx.compose.material3.Button(
                            onClick = { podcastViewModel.loadRecommendations() },
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explorar podcasts destacados", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recommendations, key = { it.collectionId ?: it.feedUrl.orEmpty() }) { podcast ->
                            val feedUrl = podcast.feedUrl.orEmpty()
                            val podcastId = podcast.collectionId?.toString() ?: feedUrl
                            val isSubscribed = subscribedPodcasts.any { it.feedUrl == feedUrl }

                            RecommendationCard(
                                podcast = podcast,
                                isSubscribed = isSubscribed,
                                isSubscriptionLoading = subscriptionLoading,
                                onSubscribe = {
                                    if (feedUrl.isNotBlank()) {
                                        podcastViewModel.subscribeByFeedUrl(feedUrl)
                                    }
                                },
                                onClick = {
                                    if (feedUrl.isNotBlank()) {
                                        onPodcastClick(podcastId, feedUrl)
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 6. Carrusel 3: "Por categoría" (tarjetas de colección coloridas)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_by_category),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(categoryChips.filter { it.genreId > 0 }) { chip ->
                        CategoryCollectionCard(
                            chip = chip,
                            onClick = { onNavigateToCategory(chip.name, chip.genreId) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 7. Últimos publicados / Nuevos lanzamientos
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimos publicados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Añadir feed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToAddPodcast() }
                    )
                }
            }

            if (newReleases.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Filled.Headphones,
                        title = if (subscribedPodcasts.isEmpty()) stringResource(R.string.empty_subscriptions_title) else stringResource(R.string.empty_new_releases),
                        subtitle = if (subscribedPodcasts.isEmpty()) stringResource(R.string.empty_subscriptions_subtitle) else stringResource(R.string.empty_new_releases_subtitle),
                        actionLabel = if (subscribedPodcasts.isEmpty()) stringResource(R.string.action_explore_podcasts) else null,
                        onAction = if (subscribedPodcasts.isEmpty()) { { onNavigateToCategory("Para ti", 0) } } else null
                    )
                }
            } else {
                items(newReleases.take(6), key = { it.episode.id }) { item ->
                    val isPlayingThis = isPlaying && currentPlayingEpisode?.id == item.episode.id
                    val progressRatio = if (isPlayingThis && durationMs > 0) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else if (item.episode.duration > 0 && item.episode.currentPositionMs > 0) {
                        (item.episode.currentPositionMs.toFloat() / item.episode.duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    SwipeableEpisodeRow(
                        onSwipeToQueue = {
                            podcastViewModel.addEpisodeToQueue(item.episode.id)
                        },
                        onSwipeToComplete = {
                            podcastViewModel.toggleEpisodePlayed(item.episode.id, item.episode.isPlayed)
                        }
                    ) {
                        EpisodeRowCard(
                            item = item,
                            isPlaying = isPlayingThis,
                            progressRatio = progressRatio,
                            onPlay = {
                                playerViewModel.playEpisode(item.episode, item.podcastTitle, item.podcastArtworkUrl)
                            },
                            onAddToQueue = {
                                podcastViewModel.addEpisodeToQueue(item.episode.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

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
                    model = podcast.artworkUrl,
                    contentDescription = podcast.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedPodcastArtwork(podcast.id),
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
                        .fillMaxSize()
                        .sharedPodcastArtwork(podcastId),
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
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = podcast.collectionName ?: "Sin título",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = podcast.artistName ?: "Autor desconocido",
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
 * Tarjeta de categoría estilo editorial con gradiente y emoji grande.
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
                .background(
                    Brush.linearGradient(
                        listOf(
                            chip.accentColor,
                            chip.accentColor.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = chip.emoji,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Text(
                text = chip.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        text = item.podcastTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
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
                    text = "$dateFormatted • ${if (durationMins > 0) "$durationMins min" else "Audio"}",
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
