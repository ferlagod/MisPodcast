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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadState

/** Acciones sobre una descarga existente, proporcionadas una vez en la raíz de la interfaz. */
class DownloadActions(
    val cancel: (episodeId: String) -> Unit,
    val delete: (episodeId: String) -> Unit,
    /** `false` si la descarga no se puede eliminar ahora (p. ej. el episodio está en el reproductor). */
    val canDelete: (episodeId: String) -> Boolean = { true },
    /** Se llama en lugar de pedir confirmación cuando [canDelete] devuelve `false`. */
    val onDeleteBlocked: () -> Unit = {}
)

val LocalDownloadActions = staticCompositionLocalOf<DownloadActions?> { null }

/**
 * Acción al pulsar un control de descarga según su estado:
 * - Sin descargar: [onDownloadClick].
 * - En espera o descargando: cancela la descarga.
 * - Descargado: pide confirmación y elimina el archivo.
 *
 * Sin [episodeId] o sin [LocalDownloadActions] solo hace [onDownloadClick], como antes.
 */
@Composable
fun rememberDownloadClick(
    episodeId: String?,
    status: DownloadStatus,
    onDownloadClick: () -> Unit
): () -> Unit {
    val actions = LocalDownloadActions.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && episodeId != null && actions != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_download_title)) },
            text = { Text(stringResource(R.string.delete_download_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.delete(episodeId)
                }) { Text(stringResource(R.string.delete_download)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    return {
        when {
            episodeId == null || actions == null -> onDownloadClick()
            status == DownloadStatus.DOWNLOADED ->
                if (actions.canDelete(episodeId)) confirmDelete = true else actions.onDeleteBlocked()
            status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED -> actions.cancel(episodeId)
            else -> onDownloadClick()
        }
    }
}

/** Etiqueta del control de descarga para el estado indicado (p. ej. bajo el icono en la ficha). */
@Composable
fun downloadActionLabel(status: DownloadStatus, canManage: Boolean = LocalDownloadActions.current != null): String =
    stringResource(
        when {
            !canManage -> if (status == DownloadStatus.DOWNLOADED) R.string.downloaded else R.string.download
            status == DownloadStatus.DOWNLOADED -> R.string.delete_download
            status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED -> R.string.cancel_download
            else -> R.string.download
        }
    )

/**
 * Botón de descarga con animación de estado:
 * - Sin descargar: flecha de descarga.
 * - En espera: reloj de arena; descargando: anillo de progreso. Con un cuadrado de «parar» para cancelar.
 * - Descargado: check relleno; al pulsarlo se ofrece eliminar la descarga.
 *
 * @param episodeId necesario para poder cancelar o eliminar la descarga desde el botón.
 */
@Composable
fun EpisodeDownloadButton(
    downloadState: EpisodeDownloadState,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    episodeId: String? = null
) {
    val blueColor = MaterialTheme.colorScheme.primary
    val canManage = episodeId != null && LocalDownloadActions.current != null
    val onClick = rememberDownloadClick(episodeId, downloadState.status, onDownloadClick)
    val inProgress = downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.QUEUED

    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        // Sin acciones disponibles no hay nada que hacer mientras descarga
        enabled = canManage || !inProgress
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
                    Box(contentAlignment = Alignment.Center) {
                        if (downloadState.progress > 0f) {
                            CircularProgressIndicator(
                                progress = { downloadState.progress },
                                color = blueColor,
                                trackColor = blueColor.copy(alpha = 0.2f),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = blueColor,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (canManage) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.cancel_download),
                                tint = blueColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                DownloadStatus.QUEUED -> {
                    Icon(
                        imageVector = Icons.Filled.HourglassTop,
                        contentDescription = if (canManage) {
                            stringResource(R.string.download_waiting) + ". " + stringResource(R.string.cancel_download)
                        } else {
                            stringResource(R.string.download_waiting)
                        },
                        tint = blueColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DownloadStatus.DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = if (canManage) {
                            stringResource(R.string.downloaded) + ". " + stringResource(R.string.delete_download)
                        } else {
                            stringResource(R.string.downloaded)
                        },
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
