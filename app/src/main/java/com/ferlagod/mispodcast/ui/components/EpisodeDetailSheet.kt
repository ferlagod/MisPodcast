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

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.ui.theme.AccentGreen
import com.ferlagod.mispodcast.ui.theme.AccentRed
import com.ferlagod.mispodcast.ui.theme.LogoOceanBlue
import com.ferlagod.mispodcast.ui.theme.LogoOceanBlueLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ficha completa del episodio mostrada como ModalBottomSheet.
 * Proporciona portada, metadatos enriquecidos, controles de reproducción,
 * gestión de cola, descarga, favoritos, estado de reproducción y notas completas.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeDetailSheet(
    episode: EpisodeEntity,
    podcastTitle: String,
    podcastArtwork: String,
    isPlaying: Boolean,
    downloadState: EpisodeDownloadState,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onTogglePlayed: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var showFundingSheet by remember { mutableStateOf(false) }

    val artworkUrl = remember(episode.imageUrl, podcastArtwork) {
        episode.imageUrl.ifBlank { podcastArtwork }
    }

    val formattedDate = remember(episode.pubDate) {
        if (episode.pubDate > 0) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            sdf.format(Date(episode.pubDate))
        } else ""
    }

    val formattedDuration = remember(episode.duration) {
        if (episode.duration > 0) {
            val totalSeconds = episode.duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes} min"
                else -> "${totalSeconds}s"
            }
        } else "Audio"
    }

    val formattedFileSize = remember(episode.fileSizeBytes) {
        if (episode.fileSizeBytes > 0) {
            val mb = episode.fileSizeBytes.toDouble() / (1024 * 1024)
            String.format(Locale.getDefault(), "%.1f MB", mb)
        } else ""
    }

    val noNotesPlaceholder = stringResource(R.string.no_episode_notes)
    val cleanDescription = remember(episode.description, noNotesPlaceholder) {
        if (episode.description.isBlank()) {
            noNotesPlaceholder
        } else {
            try {
                Html.fromHtml(episode.description, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } catch (_: Exception) {
                episode.description.trim()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Barra superior con cabecera y botón de cerrar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.episode_detail_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sección Principal: Portada + Títulos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (artworkUrl.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.size(92.dp)
                    ) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.size(92.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(LogoOceanBlue, LogoOceanBlueLight))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Podcasts,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (podcastTitle.isNotBlank()) {
                        Text(
                            text = podcastTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Fila de insignias / chips de metadatos
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (formattedDate.isNotBlank()) {
                    MetadataChip(text = formattedDate)
                }
                MetadataChip(text = formattedDuration, isHighlighted = true)

                if (formattedFileSize.isNotBlank()) {
                    MetadataChip(text = formattedFileSize)
                }

                if (!episode.localFilePath.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.badge_local),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (episode.isPlayed) {
                    Surface(
                        color = AccentGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.badge_played),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AccentGreen
                            )
                        }
                    }
                }
            }

            // Botón Principal de Reproducción
            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) stringResource(R.string.pause_episode) else stringResource(R.string.play_episode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fila de acciones secundarias
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cola
                ActionIconButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = stringResource(R.string.add_to_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onAddToQueue
                )

                // Favorito
                ActionIconButton(
                    icon = if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (episode.isFavorite) stringResource(R.string.favorite) else stringResource(R.string.mark_favorite),
                    tint = if (episode.isFavorite) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onToggleFavorite
                )

                // Descarga
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onDownloadClick)
                ) {
                    EpisodeDownloadButton(
                        downloadState = downloadState,
                        onDownloadClick = onDownloadClick
                    )
                    Text(
                        text = stringResource(R.string.download),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Estado de Escuchado
                if (onTogglePlayed != null) {
                    ActionIconButton(
                        icon = if (episode.isPlayed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        label = if (episode.isPlayed) stringResource(R.string.mark_as_played) else stringResource(R.string.mark_as_unplayed),
                        tint = if (episode.isPlayed) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onTogglePlayed
                    )
                }

                // Compartir
                if (onShareClick != null) {
                    ActionIconButton(
                        icon = Icons.Filled.Share,
                        label = stringResource(R.string.share_episode),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onShareClick
                    )
                }
            }

            // Separador
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Sección: Notas del Episodio / Descripción Completa
            Text(
                text = stringResource(R.string.episode_notes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            SelectionContainer {
                Text(
                    text = cleanDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }

            // Metadatos Podcasting 2.0 (si existen enlaces externos como transcripciones o financiación)
            if (!episode.fundingUrl.isNullOrBlank() || !episode.valueForValueInfo.isNullOrBlank() || !episode.transcriptUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = stringResource(R.string.additional_resources),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!episode.fundingUrl.isNullOrBlank() || !episode.valueForValueInfo.isNullOrBlank()) {
                        val isLightningOnly = episode.fundingUrl.isNullOrBlank() && !episode.valueForValueInfo.isNullOrBlank()
                        OutlinedButton(
                            onClick = { showFundingSheet = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLightningOnly) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lightning),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFF7931A)
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
                            Text(stringResource(R.string.support_show), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (!episode.transcriptUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(episode.transcriptUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.transcript), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        if (showFundingSheet) {
            FundingBottomSheet(
                podcastTitle = podcastTitle.ifBlank { episode.title },
                podcastArtwork = episode.imageUrl,
                fundingUrl = episode.fundingUrl,
                valueForValueRaw = episode.valueForValueInfo,
                onDismissRequest = { showFundingSheet = false }
            )
        }
    }
}

@Composable
private fun MetadataChip(
    text: String,
    isHighlighted: Boolean = false
) {
    Surface(
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
