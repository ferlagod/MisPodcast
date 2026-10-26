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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState

/**
 * Botón interactivo de descarga con animación de estado:
 * - NOT_DOWNLOADED: Icono de flecha de descarga.
 * - DOWNLOADING: CircularProgressIndicator con progreso o rotación.
 * - DOWNLOADED: Icono de check relleno con color primario oficial (#297A9D).
 */
@Composable
fun EpisodeDownloadButton(
    downloadState: EpisodeDownloadState,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blueColor = MaterialTheme.colorScheme.primary

    IconButton(
        onClick = onDownloadClick,
        modifier = modifier.size(36.dp),
        enabled = downloadState.status != DownloadStatus.DOWNLOADING
    ) {
        AnimatedContent(
            targetState = downloadState.status,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "download_animation"
        ) { status ->
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    if (downloadState.progress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadState.progress },
                            color = blueColor,
                            trackColor = blueColor.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            color = blueColor,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DownloadStatus.DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.downloaded),
                        tint = blueColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DownloadStatus.NOT_DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.download),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
