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

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    val coroutineScope = rememberCoroutineScope()

    // 1. Observar si el podcast ya existe en la base de datos Room
    val roomPodcast by podcastViewModel.getPodcastFlow(podcastId, feedUrl).collectAsState(initial = null)
    val effectivePodcastId = roomPodcast?.id ?: podcastId.ifBlank { feedUrl }
    val roomEpisodes by podcastViewModel.getEpisodesForPodcastFlow(effectivePodcastId).collectAsState(initial = emptyList())
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
                    Toast.makeText(context, "¡Carpeta reconectada con éxito! ($count audios sincronizados)", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Error reconectando carpeta: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
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
                    val msg = if (addedCount > 0) "¡Se han detectado $addedCount nuevos audios!" else "Carpeta al día (sin nuevos audios)"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    isLoadingFeed = false
                    Toast.makeText(context, "Error al escanear carpeta: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                        feedError = error.localizedMessage ?: "No se pudo descargar el feed del podcast"
                    } else {
                        Toast.makeText(context, "Error al actualizar feed: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Si no está en Room y tenemos una URL de feed RSS, cargarlo automáticamente
    LaunchedEffect(targetUrl) {
        if (roomPodcast == null && targetUrl.isNotBlank() && !isLocal) {
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
    val dominantScreenColor = rememberArtworkDominantColor(artworkUrl = roomPodcast?.artworkUrl?.ifBlank { null })

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

                    if (roomPodcast != null) {
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
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, roomPodcast?.title ?: "Podcast")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Escucha ${roomPodcast?.title ?: "este podcast"}: ${roomPodcast?.link ?: targetUrl}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
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
                            text = "Descargando y analizando feed...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Importando episodios y metadatos de Podcasting 2.0",
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
                            text = "No se pudo cargar el podcast",
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
                            Text("Reintentar")
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
                                        sortedEpisodes = sortedEpisodes,
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
                                    sortedEpisodes = sortedEpisodes,
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
                        Toast.makeText(context, context.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
                    },
                    onToggleFavorite = {
                        podcastViewModel.toggleFavorite(ep.id, ep.isFavorite)
                    },
                    onDownloadClick = {
                        podcastViewModel.downloadEpisode(ep)
                        Toast.makeText(context, context.getString(R.string.toast_downloading_episode), Toast.LENGTH_SHORT).show()
                    },
                    onTogglePlayed = {
                        podcastViewModel.toggleEpisodePlayed(ep.id, ep.isPlayed)
                        val msg = if (!ep.isPlayed) context.getString(R.string.toast_marked_as_played) else context.getString(R.string.toast_marked_as_unplayed)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onShareClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, ep.title)
                            putExtra(
                                Intent.EXTRA_TEXT,
                                context.getString(R.string.share_episode_format, ep.title, effectiveTitle, ep.audioUrl)
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_episode)))
                    }
                )
            }

            if (showPodcastSettingsSheet && roomPodcast != null) {
                PodcastSettingsBottomSheet(
                    podcast = roomPodcast!!,
                    onDismissRequest = { showPodcastSettingsSheet = false },
                    onSaveSettings = { speed, skipIntro, skipEnding, autoDownload, sortOrder, autoEnqueue ->
                        podcastViewModel.updatePodcastSettings(
                            podcastId = roomPodcast!!.id,
                            customSpeed = speed,
                            skipIntroSeconds = skipIntro,
                            skipEndingSeconds = skipEnding,
                            autoDownloadEnabled = autoDownload,
                            episodeSortOrder = sortOrder,
                            autoEnqueueRule = autoEnqueue
                        )
                        Toast.makeText(context, "Ajustes de '${roomPodcast!!.title}' guardados", Toast.LENGTH_SHORT).show()
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

/**
 * Renderiza la sección de cabecera de episodios y la lista de episodios de forma reutilizable
 * para el layout de un panel (teléfonos) y el panel derecho en dos paneles (tablets/plegables).
 */
private fun LazyListScope.episodesListContent(
    podcast: PodcastEntity,
    sortedEpisodes: List<EpisodeEntity>,
    downloadStates: Map<String, EpisodeDownloadState>,
    isPlaying: Boolean,
    currentEpisodePlaying: EpisodeEntity?,
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    context: Context,
    reconnectFolderLauncher: androidx.activity.result.ActivityResultLauncher<Uri?>,
    triggerRescanLocalFolder: () -> Unit,
    onSelectEpisode: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Título de sección de episodios
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Episodios",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val sortText = if (podcast.episodeSortOrder == "OLDEST_FIRST") "Orden cronológico (viejo→nuevo)" else "Más recientes primero"
                Text(
                    text = "${sortedEpisodes.size} disponibles • $sortText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.podcast_settings_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (sortedEpisodes.isEmpty()) {
        item {
            if (podcast.isLocalFolder) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Carpeta sin audios detectados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No se han encontrado archivos de audio compatibles (.mp3, .m4a, .wav, .flac, .ogg, .opus, .aac) en esta carpeta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { reconnectFolderLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cambiar carpeta")
                            }
                            Button(
                                onClick = triggerRescanLocalFolder,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-escanear")
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay episodios disponibles en este feed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        items(sortedEpisodes, key = { it.id }) { episode ->
            val isThisPlaying = currentEpisodePlaying?.id == episode.id && isPlaying
            val downloadState = downloadStates[episode.id] ?: podcastViewModel.getEpisodeDownloadState(episode)

            SwipeableEpisodeRow(
                onSwipeToQueue = {
                    podcastViewModel.addEpisodeToQueue(episode.id)
                    Toast.makeText(context, context.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
                },
                onSwipeToComplete = {
                    val willBePlayed = !episode.isPlayed
                    podcastViewModel.toggleEpisodePlayed(episode.id, episode.isPlayed)
                    Toast.makeText(context, context.getString(if (willBePlayed) R.string.toast_marked_played else R.string.toast_marked_unplayed), Toast.LENGTH_SHORT).show()
                }
            ) {
                EpisodeDetailItem(
                    episode = episode,
                    podcastTitle = podcast.title,
                    podcastArtwork = podcast.artworkUrl,
                    isPlaying = isThisPlaying,
                    downloadState = downloadState,
                    onEpisodeClick = {
                        onSelectEpisode(episode.id)
                    },
                    onPlayClick = {
                        if (isThisPlaying) {
                            playerViewModel.togglePlayPause()
                        } else {
                            playerViewModel.playEpisode(
                                episode = episode,
                                podcastTitle = podcast.title,
                                podcastArtwork = podcast.artworkUrl
                            )
                        }
                    },
                    onAddToQueue = {
                        podcastViewModel.addEpisodeToQueue(episode.id)
                        Toast.makeText(context, context.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
                    },
                    onToggleFavorite = {
                        podcastViewModel.toggleFavorite(episode.id, episode.isFavorite)
                    },
                    onDownloadClick = {
                        podcastViewModel.downloadEpisode(episode)
                        Toast.makeText(context, context.getString(R.string.toast_downloading_episode), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/**
 * Cabecera estilo Apple Podcasts: portada grande, autor, categoría, descripción extensible y botón azul.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodcastHeaderSection(
    podcast: PodcastEntity,
    isSubscribing: Boolean,
    isFolderValid: Boolean = true,
    onToggleSubscription: () -> Unit,
    onReconnectFolder: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenFunding: () -> Unit = {}
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val defaultNoDescription = stringResource(R.string.no_description_available)
    val cleanDescription = remember(podcast.description, defaultNoDescription) {
        if (podcast.description.isBlank()) {
            defaultNoDescription
        } else {
            try {
                Html.fromHtml(podcast.description, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } catch (_: Exception) {
                podcast.description.trim()
            }
        }
    }

    val dominantArtworkColor = rememberArtworkDominantColor(artworkUrl = podcast.artworkUrl.ifBlank { null })

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cabecera inmersiva a todo el ancho (full-bleed) con portada desenfocada y degradado dominante
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(295.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo con portada desenfocada a todo el ancho
            if (podcast.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = podcast.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // Degradado vertical multicapa con color dominante
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                dominantArtworkColor.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            // Portada nítida encima, grande y elevada, con transición compartida
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.32f)),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(175.dp)
                    .aspectRatio(1f)
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
                        iconSize = 64.dp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        Spacer(modifier = Modifier.height(18.dp))

        // Categoría o etiqueta de Carpeta Local
        val categoryLabel = when {
            podcast.category.isNotBlank() -> podcast.category.uppercase()
            podcast.isLocalFolder -> "CARPETA LOCAL"
            else -> ""
        }
        if (categoryLabel.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (podcast.isLocalFolder) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Título del podcast
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Autor / Editorial
        val authorLabel = if (podcast.isLocalFolder) {
            if (podcast.author.isNotBlank() && podcast.author != "Local Folder") podcast.author else "Archivos locales"
        } else {
            podcast.author.ifBlank { "Autor desconocido" }
        }
        Text(
            text = authorLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        // CABECERA / ALERTA DE RECONEXIÓN SI LA CARPETA LOCAL NO ES ACCESIBLE
        if (podcast.isLocalFolder && !isFolderValid) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Carpeta local desconectada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "La ruta anterior no es accesible en este dispositivo (migración, OPML o copia de AntennaPod). Selecciona la carpeta actual para volver a enlazar los archivos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onReconnectFolder,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reconectar carpeta local", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Fila de acciones principales
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón animado de suscripción con rebote elástico y respuesta háptica (+ ↔ ✓)
            AnimatedSubscribeButton(
                isSubscribed = podcast.isSubscribed,
                isLoading = isSubscribing,
                onClick = onToggleSubscription,
                dominantColor = dominantArtworkColor
            )

            // Si es carpeta local, botón complementario para reconectar
            if (podcast.isLocalFolder) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onReconnectFolder,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reconectar carpeta",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Botón de ajustes por podcast
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Botón de apoyo / Value4Value (si el podcast tiene financiación o datos Lightning)
            if (!podcast.fundingUrl.isNullOrBlank() || !podcast.valueForValueInfo.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                val isLightningOnly = podcast.fundingUrl.isNullOrBlank() && !podcast.valueForValueInfo.isNullOrBlank()
                val amberColor = Color(0xFFF7931A)
                OutlinedButton(
                    onClick = onOpenFunding,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isLightningOnly) amberColor else AccentRed
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isLightningOnly) amberColor.copy(alpha = 0.6f) else AccentRed.copy(alpha = 0.6f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (isLightningOnly) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lightning),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = amberColor
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_support_heart),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentRed
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.support_podcast_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Descripción extensible
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                .clickable { isDescriptionExpanded = !isDescriptionExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = cleanDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isDescriptionExpanded) "Menos" else "Más",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
}

/**
 * Fila individual para cada episodio con botones de reproducción, cola, favoritos y descarga.
 */
@Composable
private fun EpisodeDetailItem(
    episode: EpisodeEntity,
    podcastTitle: String,
    podcastArtwork: String,
    isPlaying: Boolean,
    downloadState: EpisodeDownloadState,
    onEpisodeClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val dateText = remember(episode.pubDate) {
        if (episode.pubDate > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(episode.pubDate))
        } else ""
    }

    val durationText = remember(episode.duration) {
        if (episode.duration > 0) {
            val totalSeconds = episode.duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes} min"
                else -> "${totalSeconds}s"
            }
        } else "Audio"
    }

    val fileSizeText = remember(episode.fileSizeBytes) {
        if (episode.fileSizeBytes > 0) {
            val mb = episode.fileSizeBytes.toDouble() / (1024 * 1024)
            String.format(Locale.getDefault(), "%.1f MB", mb)
        } else ""
    }

    val cleanSnippet = remember(episode.description) {
        if (episode.description.isBlank()) ""
        else {
            try {
                Html.fromHtml(episode.description, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } catch (_: Exception) {
                episode.description.trim()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable(onClick = onEpisodeClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Metadatos: Fecha, duración, tamaño y badge local
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dateText.isNotBlank()) {
                    Text(
                        text = dateText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                if (fileSizeText.isNotBlank()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!episode.localFilePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "LOCAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Título del episodio con indicador de ecualizador si está sonando
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPlaying) {
                    EqualizerIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        isPlaying = true,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Resumen de descripción
            if (cleanSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de acciones: Reproducir con anillo de progreso, Cola, Favorito, Descargar
            val playProgress = if (episode.duration > 0) {
                (episode.currentPositionMs.toFloat() / episode.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Botón de reproducción con anillo de progreso para episodios a medias
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPlayClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    PlayProgressButton(
                        isPlaying = isPlaying,
                        progress = playProgress,
                        onClick = onPlayClick,
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val remainingMins = ((1f - playProgress) * (episode.duration / 60000)).toInt()
                    Text(
                        text = when {
                            isPlaying -> stringResource(R.string.action_pause)
                            playProgress > 0.02f -> "Continuar (${remainingMins}m)"
                            else -> stringResource(R.string.action_play)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val haptic = LocalHapticFeedback.current

                    // Botón añadir a cola
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddToQueue()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.add_to_queue),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Botón favorito con respuesta háptica
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(if (episode.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                            tint = if (episode.isFavorite) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Botón de descarga interactivo con animación y progreso
                    EpisodeDownloadButton(
                        downloadState = downloadState,
                        onDownloadClick = onDownloadClick
                    )
                }
            }
        }
    }
}
