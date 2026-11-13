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

import com.ferlagod.mispodcast.util.asUiText
import com.ferlagod.mispodcast.util.ShareHelper
import com.ferlagod.mispodcast.util.rethrowIfCancellation
import android.content.Context
import android.net.Uri
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ferlagod.mispodcast.ui.navigation.sharedPodcastArtwork
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ferlagod.mispodcast.ui.components.AnimatedSubscribeButton
import com.ferlagod.mispodcast.ui.components.EqualizerIndicator
import com.ferlagod.mispodcast.ui.components.FundingBottomSheet
import com.ferlagod.mispodcast.ui.components.EpisodeDetailSheet
import com.ferlagod.mispodcast.ui.components.EpisodeDownloadButton
import com.ferlagod.mispodcast.ui.components.PlayProgressButton
import com.ferlagod.mispodcast.ui.components.PodcastArtworkPlaceholder
import com.ferlagod.mispodcast.ui.components.PodcastSettingsBottomSheet
import com.ferlagod.mispodcast.ui.components.SwipeableEpisodeRow
import com.ferlagod.mispodcast.ui.theme.AccentGreen
import com.ferlagod.mispodcast.ui.theme.AccentRed
import com.ferlagod.mispodcast.ui.theme.IndigoSecondary
import com.ferlagod.mispodcast.ui.theme.RoyalBlueLight
import com.ferlagod.mispodcast.ui.theme.rememberArtworkDominantColor
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ferlagod.mispodcast.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    podcastId: String,
    feedUrl: String,
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Observar si el podcast ya existe en la base de datos Room
    // Flujos recordados: crearlos en cada recomposición volvería a suscribir las consultas de Room continuamente
    val podcastFlow = remember(podcastId, feedUrl) { podcastViewModel.getPodcastFlow(podcastId, feedUrl) }
    val roomPodcast by podcastFlow.collectAsState(initial = null)
    val effectivePodcastId = roomPodcast?.id ?: podcastId.ifBlank { feedUrl }
    val episodesFlow = remember(effectivePodcastId) { podcastViewModel.getEpisodesForPodcastFlow(effectivePodcastId) }
    val roomEpisodes by episodesFlow.collectAsState(initial = emptyList())
    val downloadStates by podcastViewModel.downloadStates.collectAsState()

    // 2. Estados para descarga y parseo de feed en tiempo real con RssFeedParser o rescaneo de carpeta local
    var isLoadingFeed by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var isSubscribing by remember { mutableStateOf(false) }

    val targetUrl = feedUrl.ifBlank { roomPodcast?.feedUrl.orEmpty() }
    val isLocal = roomPodcast?.isLocalFolder == true || targetUrl.startsWith("local://") || targetUrl.startsWith("local:") || targetUrl.startsWith("file:") || targetUrl.startsWith("antennapod_local:")
    val isFolderValid = remember(roomPodcast?.localFolderPath, roomPodcast?.isLocalFolder) {
        if (roomPodcast?.isLocalFolder == true) {
            com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.isFolderAccessible(context, roomPodcast?.localFolderPath)
        } else {
            true
        }
    }

    val currentPodcastId by rememberUpdatedState(roomPodcast?.id ?: effectivePodcastId)

    val reconnectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            coroutineScope.launch {
                isLoadingFeed = true
                feedError = null
                val result = podcastViewModel.reconnectLocalFolderPodcast(currentPodcastId, treeUri)
                isLoadingFeed = false
                result.onSuccess { count ->
                    Toast.makeText(context, resources.getString(R.string.folder_reconnected, count), Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, resources.getString(R.string.folder_reconnect_error, err.asUiText().asString(context)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Función para re-escanear archivos en carpeta local
    val triggerRescanLocalFolder: () -> Unit = {
        if (!isFolderValid) {
            reconnectFolderLauncher.launch(null)
        } else {
            coroutineScope.launch {
                isLoadingFeed = true
                feedError = null
                try {
                    val addedCount = podcastViewModel.rescanLocalFolderPodcast(currentPodcastId)
                    isLoadingFeed = false
                    val msg = if (addedCount > 0) resources.getString(R.string.folder_new_audio_found, addedCount) else resources.getString(R.string.folder_up_to_date)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    isLoadingFeed = false
                    Toast.makeText(context, resources.getString(R.string.folder_scan_error, e.asUiText().asString(context)), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Función para descargar y persistir el podcast si aún no está en Room o para refrescar RSS
    val triggerFetchFeed: () -> Unit = {
        if (isLocal) {
            triggerRescanLocalFolder()
        } else if (targetUrl.isNotBlank()) {
            coroutineScope.launch {
                isLoadingFeed = true
                feedError = null
                val result = podcastViewModel.loadPodcastFeed(targetUrl)
                result.onSuccess {
                    isLoadingFeed = false
                }.onFailure { error ->
                    isLoadingFeed = false
                    if (roomPodcast == null) {
                        feedError = error.asUiText(R.string.feed_download_failed).asString(context)
                    } else {
                        Toast.makeText(context, resources.getString(R.string.feed_update_error, error.asUiText().asString(context)), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Si no está en Room y tenemos una URL de feed RSS, cargarlo automáticamente
    LaunchedEffect(targetUrl) {
        // roomPodcast aún es null en la primera composición: se consulta Room directamente
        // para no volver a descargar (y sobrescribir) un podcast que ya está guardado
        if (roomPodcast == null && targetUrl.isNotBlank() && !isLocal &&
            !podcastViewModel.isPodcastStored(podcastId, targetUrl)
        ) {
            triggerFetchFeed()
        } else if (isLocal) {
            // Re-escaneo silencioso al abrir podcast local
            podcastViewModel.rescanLocalFolderPodcast(effectivePodcastId)
        }
    }

    val currentEpisodePlaying by playerViewModel.currentEpisode.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    var showPodcastSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showFundingSheet by rememberSaveable { mutableStateOf(false) }

    val sortedEpisodes = remember(roomEpisodes, roomPodcast?.episodeSortOrder) {
        if (roomPodcast?.episodeSortOrder == "OLDEST_FIRST") {
            roomEpisodes.sortedWith(compareBy<EpisodeEntity> { it.pubDate }.thenBy { it.id })
        } else {
            roomEpisodes.sortedWith(compareByDescending<EpisodeEntity> { it.pubDate }.thenByDescending { it.id })
        }
    }

    var episodeFilter by rememberSaveable { mutableStateOf(EpisodeFilter.ALL) }
    val filteredEpisodes = remember(sortedEpisodes, episodeFilter, downloadStates) {
        sortedEpisodes.filter { episodeFilter.matches(it, downloadStates[it.id]) }
    }

    var selectedEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEpisode = remember(selectedEpisodeId, sortedEpisodes) {
        sortedEpisodes.find { it.id == selectedEpisodeId }
    }

    val listState = rememberLazyListState()
    val scrollRatio by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
        }
    }

    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = scrollRatio > 0.55f,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180))
                    ) {
                        Text(
                            text = roomPodcast?.title ?: "Podcast",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .then(
                                if (scrollRatio < 0.55f) {
                                    Modifier.background(Color.Black.copy(alpha = 0.38f), CircleShape)
                                } else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = if (scrollRatio < 0.55f) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val iconBg = if (scrollRatio < 0.55f) Modifier.background(Color.Black.copy(alpha = 0.38f), CircleShape) else Modifier
                    val iconTint = if (scrollRatio < 0.55f) Color.White else MaterialTheme.colorScheme.onSurface

                    // Mientras la cabecera (con su botón «Ajustes») está a la vista, no se duplica aquí
                    if (roomPodcast != null && headerScrolledAway) {
                        IconButton(onClick = { showPodcastSettingsSheet = true }, modifier = iconBg) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.podcast_settings_title),
                                tint = iconTint
                            )
                        }
                    }
                    if (isLocal) {
                        IconButton(onClick = { reconnectFolderLauncher.launch(null) }, modifier = iconBg) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = stringResource(R.string.podcast_reconnect_folder),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = triggerRescanLocalFolder, modifier = iconBg) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.podcast_scan_folder),
                                tint = iconTint
                            )
                        }
                    } else if (targetUrl.isNotBlank()) {
                        IconButton(onClick = triggerFetchFeed, modifier = iconBg) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.podcast_update_feed),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = {
                            val title = roomPodcast?.title ?: resources.getString(R.string.this_podcast)
                            val text = ShareHelper.buildPodcastShareText(
                                context = context,
                                podcastTitle = title,
                                author = roomPodcast?.author,
                                websiteUrl = roomPodcast?.link,
                                feedUrl = roomPodcast?.feedUrl ?: targetUrl
                            )
                            ShareHelper.launchShare(context, text, subject = title, chooserTitle = resources.getString(R.string.action_share))
                        }, modifier = iconBg) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.action_share),
                                tint = iconTint
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = scrollRatio * 0.95f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when {
                isLoadingFeed && roomPodcast == null -> {
                    // Estado de carga inicial con RssFeedParser
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.loading_podcast),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.may_take_seconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                feedError != null && roomPodcast == null -> {
                    // Estado de error al cargar el feed
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.podcast_load_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = triggerFetchFeed,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                roomPodcast != null -> {
                    val podcast = roomPodcast!!

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTwoPane = maxWidth >= 720.dp

                        if (isTwoPane) {
                            // Layout de dos paneles para tablets y plegables desplegados
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Panel izquierdo: Ficha del podcast con scroll vertical independiente
                                Box(
                                    modifier = Modifier
                                        .width(360.dp)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(bottom = 140.dp)
                                ) {
                                    PodcastHeaderSection(
                                        podcast = podcast,
                                        isSubscribing = isSubscribing,
                                        isFolderValid = isFolderValid,
                                        onToggleSubscription = {
                                            isSubscribing = true
                                            podcastViewModel.toggleSubscription(podcast)
                                            isSubscribing = false
                                        },
                                        onReconnectFolder = {
                                            reconnectFolderLauncher.launch(null)
                                        },
                                        onOpenSettings = {
                                            showPodcastSettingsSheet = true
                                        },
                                        onOpenFunding = {
                                            showFundingSheet = true
                                        }
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Panel derecho: Lista independiente de episodios
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(bottom = 140.dp)
                                ) {
                                    episodesListContent(
                                        podcast = podcast,
                                        sortedEpisodes = filteredEpisodes,
                                        totalEpisodeCount = sortedEpisodes.size,
                                        episodeFilter = episodeFilter,
                                        onFilterChange = { episodeFilter = it },
                                        downloadStates = downloadStates,
                                        isPlaying = isPlaying,
                                        currentEpisodePlaying = currentEpisodePlaying,
                                        podcastViewModel = podcastViewModel,
                                        playerViewModel = playerViewModel,
                                        context = context,
                                        reconnectFolderLauncher = reconnectFolderLauncher,
                                        triggerRescanLocalFolder = triggerRescanLocalFolder,
                                        onSelectEpisode = { selectedEpisodeId = it },
                                        onOpenSettings = { showPodcastSettingsSheet = true }
                                    )
                                }
                            }
                        } else {
                            // Teléfonos móviles o pantallas compactas: layout vertical de panel único
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 140.dp)
                            ) {
                                // 1. Cabecera estilo Apple Podcasts
                                item {
                                    PodcastHeaderSection(
                                        podcast = podcast,
                                        isSubscribing = isSubscribing,
                                        isFolderValid = isFolderValid,
                                        onToggleSubscription = {
                                            isSubscribing = true
                                            podcastViewModel.toggleSubscription(podcast)
                                            isSubscribing = false
                                        },
                                        onReconnectFolder = {
                                            reconnectFolderLauncher.launch(null)
                                        },
                                        onOpenSettings = {
                                            showPodcastSettingsSheet = true
                                        },
                                        onOpenFunding = {
                                            showFundingSheet = true
                                        }
                                    )
                                }

                                // Separador decorativo
                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }

                                episodesListContent(
                                    podcast = podcast,
                                    sortedEpisodes = filteredEpisodes,
                                    totalEpisodeCount = sortedEpisodes.size,
                                    episodeFilter = episodeFilter,
                                    onFilterChange = { episodeFilter = it },
                                    downloadStates = downloadStates,
                                    isPlaying = isPlaying,
                                    currentEpisodePlaying = currentEpisodePlaying,
                                    podcastViewModel = podcastViewModel,
                                    playerViewModel = playerViewModel,
                                    context = context,
                                    reconnectFolderLauncher = reconnectFolderLauncher,
                                    triggerRescanLocalFolder = triggerRescanLocalFolder,
                                    onSelectEpisode = { selectedEpisodeId = it },
                                    onOpenSettings = { showPodcastSettingsSheet = true }
                                )
                            }
                        }
                    }
                }
            }

            // Ficha completa del episodio (ModalBottomSheet) al pulsar sobre cualquier episodio
            selectedEpisode?.let { ep ->
                val isEpPlaying = currentEpisodePlaying?.id == ep.id && isPlaying
                val epDownloadState = downloadStates[ep.id] ?: podcastViewModel.getEpisodeDownloadState(ep)

                val effectiveTitle = roomPodcast?.title.orEmpty()
                val effectiveArtwork = roomPodcast?.artworkUrl.orEmpty()

                EpisodeDetailSheet(
                    episode = ep,
                    podcastTitle = effectiveTitle,
                    podcastArtwork = effectiveArtwork,
                    isPlaying = isEpPlaying,
                    downloadState = epDownloadState,
                    onDismiss = { selectedEpisodeId = null },
                    onSeekToPosition = { pos ->
                        playerViewModel.playEpisodeFrom(ep, effectiveTitle, effectiveArtwork, pos)
                    },
                    onPlayClick = {
                        if (isEpPlaying) {
                            playerViewModel.togglePlayPause()
                        } else {
                            playerViewModel.playEpisode(
                                episode = ep,
                                podcastTitle = effectiveTitle,
                                podcastArtwork = effectiveArtwork
                            )
                        }
                    },
                    onAddToQueue = {
                        podcastViewModel.addEpisodeToQueue(ep.id)
                        Toast.makeText(context, resources.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
                    },
                    onToggleFavorite = {
                        podcastViewModel.toggleFavorite(ep.id, ep.isFavorite)
                    },
                    onDownloadClick = {
                        podcastViewModel.downloadEpisode(ep)
                        Toast.makeText(context, resources.getString(R.string.toast_downloading_episode), Toast.LENGTH_SHORT).show()
                    },
                    onTogglePlayed = {
                        podcastViewModel.toggleEpisodePlayed(ep.id, ep.isPlayed)
                        val msg = if (!ep.isPlayed) resources.getString(R.string.toast_marked_as_played) else resources.getString(R.string.toast_marked_as_unplayed)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onShareClick = {
                        ShareHelper.shareEpisode(
                            context = context,
                            episodeTitle = ep.title,
                            podcastTitle = effectiveTitle,
                            episodeLink = ep.link,
                            audioUrl = ep.audioUrl,
                            podcastLink = roomPodcast?.link
                        )
                    }
                )
            }

            val settingsPodcast = roomPodcast
            if (showPodcastSettingsSheet && settingsPodcast != null) {
                PodcastSettingsBottomSheet(
                    podcast = settingsPodcast,
                    onDismissRequest = { showPodcastSettingsSheet = false },
                    onSaveSettings = { speed, skipIntro, skipEnding, autoDownload, sortOrder, autoEnqueue, notify, autoDelete ->
                        podcastViewModel.updatePodcastSettings(
                            podcastId = settingsPodcast.id,
                            customSpeed = speed,
                            skipIntroSeconds = skipIntro,
                            skipEndingSeconds = skipEnding,
                            autoDownloadEnabled = autoDownload,
                            episodeSortOrder = sortOrder,
                            autoEnqueueRule = autoEnqueue,
                            newEpisodeNotifications = notify,
                            autoDeleteRule = autoDelete
                        )
                        Toast.makeText(context, resources.getString(R.string.podcast_settings_saved, settingsPodcast.title), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showFundingSheet && roomPodcast != null) {
                FundingBottomSheet(
                    podcastTitle = roomPodcast!!.title,
                    podcastArtwork = roomPodcast!!.artworkUrl,
                    fundingUrl = roomPodcast!!.fundingUrl,
                    fundingText = roomPodcast!!.fundingText,
                    valueForValueRaw = roomPodcast!!.valueForValueInfo,
                    onDismissRequest = { showFundingSheet = false }
                )
            }
        }
    }
}
