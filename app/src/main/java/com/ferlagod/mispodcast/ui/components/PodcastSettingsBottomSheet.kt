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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity

/**
 * Diálogo modal inferior interactivo (ModalBottomSheet) para configurar ajustes específicos
 * por podcast:
 * 1. Velocidad de reproducción propia (ej. 1.0x para audiolibros, 1.5x o 1.6x para noticias).
 * 2. Saltar introducción (X segundos al inicio).
 * 3. Saltar créditos/patrocinadores (X segundos al final).
 * 4. Política de descarga automática específica para este programa.
 * 5. Orden de episodios: más recientes primero (noticias) vs orden cronológico (series narrativas).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PodcastSettingsBottomSheet(
    podcast: PodcastEntity,
    onDismissRequest: () -> Unit,
    onSaveSettings: (
        customSpeed: Float?,
        skipIntroSeconds: Int,
        skipEndingSeconds: Int,
        autoDownloadEnabled: Boolean?,
        episodeSortOrder: String,
        autoEnqueueRule: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentSpeed by remember { mutableStateOf(podcast.customSpeed) }
    var currentSkipIntro by remember { mutableIntStateOf(podcast.skipIntroSeconds) }
    var currentSkipEnding by remember { mutableIntStateOf(podcast.skipEndingSeconds) }
    var currentAutoDownload by remember { mutableStateOf(podcast.autoDownloadEnabled) }
    var currentSortOrder by remember { mutableStateOf(podcast.episodeSortOrder) }
    var currentAutoEnqueue by remember { mutableStateOf(podcast.autoEnqueueRule) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cabecera del BottomSheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ajustes del podcast",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SECCIÓN 1: VELOCIDAD DE REPRODUCCIÓN
            SettingsSection(
                icon = Icons.Default.Speed,
                title = "Velocidad de reproducción propia",
                description = "Velocidad fija para este podcast (los audiolibros a 1.0x, las noticias a 1.6x). Si se elige global, heredará el ajuste general."
            ) {
                val speedOptions = listOf(
                    null to "Global",
                    0.8f to "0.8x",
                    1.0f to "1.0x",
                    1.2f to "1.2x",
                    1.4f to "1.4x",
                    1.5f to "1.5x",
                    1.6f to "1.6x",
                    1.8f to "1.8x",
                    2.0f to "2.0x",
                    2.5f to "2.5x",
                    3.0f to "3.0x"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedOptions.forEach { (speedVal, label) ->
                        val isSelected = if (speedVal == null) currentSpeed == null else (currentSpeed != null && kotlin.math.abs(currentSpeed!! - speedVal) < 0.05f)
                        ChoiceChip(
                            label = label,
                            isSelected = isSelected,
                            onClick = { currentSpeed = speedVal }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 2: SALTO DE INTRODUCCIÓN
            SettingsSection(
                icon = Icons.Default.FastForward,
                title = "Saltar introducción (intro)",
                description = "Segundos a omitir automáticamente al inicio de cada episodio."
            ) {
                val introOptions = listOf(
                    0 to "Desactivado",
                    15 to "15 s",
                    30 to "30 s",
                    45 to "45 s",
                    60 to "60 s",
                    90 to "90 s",
                    120 to "2 min"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    introOptions.forEach { (seconds, label) ->
                        ChoiceChip(
                            label = label,
                            isSelected = currentSkipIntro == seconds,
                            onClick = { currentSkipIntro = seconds }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 3: SALTO DE CRÉDITOS / FINAL
            SettingsSection(
                icon = Icons.Default.FastRewind,
                title = "Saltar créditos y final",
                description = "Segundos a omitir al final del episodio (créditos y despedidas repetitivas)."
            ) {
                val endingOptions = listOf(
                    0 to "Desactivado",
                    15 to "15 s",
                    30 to "30 s",
                    45 to "45 s",
                    60 to "60 s",
                    90 to "90 s"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    endingOptions.forEach { (seconds, label) ->
                        ChoiceChip(
                            label = label,
                            isSelected = currentSkipEnding == seconds,
                            onClick = { currentSkipEnding = seconds }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 4: DESCARGA AUTOMÁTICA
            SettingsSection(
                icon = Icons.Default.Download,
                title = "Descarga automática",
                description = "Controla si se descargan automáticamente los episodios nuevos de este programa."
            ) {
                val downloadOptions = listOf(
                    null to "Ajuste global",
                    true to "Siempre descargar",
                    false to "Nunca descargar"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    downloadOptions.forEach { (opt, label) ->
                        ChoiceChip(
                            label = label,
                            isSelected = currentAutoDownload == opt,
                            onClick = { currentAutoDownload = opt }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 5: ORDEN DE EPISODIOS
            SettingsSection(
                icon = Icons.AutoMirrored.Filled.Sort,
                title = "Orden de episodios",
                description = "Elige el orden adecuado según la naturaleza del podcast."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChoiceCard(
                        title = "Más recientes primero",
                        subtitle = "Nuevo → Viejo (noticias, actualidad)",
                        isSelected = currentSortOrder == "NEWEST_FIRST",
                        icon = Icons.Default.ArrowDownward,
                        modifier = Modifier.weight(1f),
                        onClick = { currentSortOrder = "NEWEST_FIRST" }
                    )

                    ChoiceCard(
                        title = "Cronológico",
                        subtitle = "Viejo → Nuevo (series, audiolibros)",
                        isSelected = currentSortOrder == "OLDEST_FIRST",
                        icon = Icons.Default.ArrowUpward,
                        modifier = Modifier.weight(1f),
                        onClick = { currentSortOrder = "OLDEST_FIRST" }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECCIÓN 6: BANDEJA DE ENTRADA / UP NEXT INTELIGENTE
            SettingsSection(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Añadir a la cola automáticamente (\"Up Next\")",
                description = "¿Dónde encolar los nuevos episodios publicados de este podcast?"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceChip(
                            label = "Por defecto global",
                            isSelected = currentAutoEnqueue == "DEFAULT" || currentAutoEnqueue.isBlank(),
                            onClick = { currentAutoEnqueue = "DEFAULT" }
                        )
                        ChoiceChip(
                            label = "No añadir",
                            isSelected = currentAutoEnqueue == "DISABLED",
                            onClick = { currentAutoEnqueue = "DISABLED" }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceChip(
                            label = "Al principio (siguiente)",
                            isSelected = currentAutoEnqueue == "TOP",
                            onClick = { currentAutoEnqueue = "TOP" }
                        )
                        ChoiceChip(
                            label = "Al final de la cola",
                            isSelected = currentAutoEnqueue == "BOTTOM",
                            onClick = { currentAutoEnqueue = "BOTTOM" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Botones de acción inferiores: Cancelar y Guardar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            currentSpeed,
                            currentSkipIntro,
                            currentSkipEnding,
                            currentAutoDownload,
                            currentSortOrder,
                            currentAutoEnqueue
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar ajustes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
