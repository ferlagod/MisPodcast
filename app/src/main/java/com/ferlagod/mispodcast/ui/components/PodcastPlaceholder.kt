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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferlagod.mispodcast.ui.theme.EditorialPalette
import kotlin.math.abs

/**
 * Genera un degradado sutil y estable basado en el hash del título del podcast.
 *
 * Usa la paleta editorial de la app para que cada programa tenga identidad propia
 * sin romper la coherencia cromática del conjunto.
 */
@Composable
fun rememberPodcastGradient(title: String): Brush {
    return remember(title) {
        val hash = abs(title.ifBlank { "Podcast" }.hashCode())
        val base = EditorialPalette[hash % EditorialPalette.size]
        Brush.linearGradient(
            colors = listOf(lerp(base, Color.White, 0.14f), base)
        )
    }
}

/**
 * Contenedor de reemplazo de carátula con degradado algorítmico y letra inicial / icono.
 */
@Composable
fun PodcastArtworkPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Podcasts,
    iconSize: Dp = 24.dp,
    showInitial: Boolean = true
) {
    val gradient = rememberPodcastGradient(title = title)
    val initial = remember(title) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty() && trimmed.first().isLetter()) {
            trimmed.first().uppercase()
        } else {
            ""
        }
    }

    Box(
        modifier = modifier.background(gradient),
        contentAlignment = Alignment.Center
    ) {
        if (showInitial && initial.isNotEmpty() && iconSize >= 28.dp) {
            Text(
                text = initial,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = (iconSize.value * 0.95f).sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
