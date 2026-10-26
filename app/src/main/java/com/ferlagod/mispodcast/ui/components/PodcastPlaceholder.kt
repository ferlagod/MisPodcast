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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Genera un degradado armónico y único basado en el hash del título del podcast.
 *
 * Evita la monotonía del mismo degradado azul-gris en carátulas vacías o mientras cargan,
 * otorgando identidad visual propia a cada programa.
 */
@Composable
fun rememberPodcastGradient(title: String): Brush {
    return remember(title) {
        val hash = abs(title.ifBlank { "Podcast" }.hashCode())
        // Paletas armónicas de dos tonos vibrantes y elegantes
        val palettes = listOf(
            listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)), // Indigo a Cian
            listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)), // Violeta a Rosa
            listOf(Color(0xFF059669), Color(0xFF10B981)), // Esmeralda a Menta
            listOf(Color(0xFFD97706), Color(0xFFF59E0B)), // Ámbar a Dorado
            listOf(Color(0xFFDC2626), Color(0xFFFB923C)), // Coral a Naranja
            listOf(Color(0xFF2563EB), Color(0xFF38BDF8)), // Azul Real a Celeste
            listOf(Color(0xFF7C3AED), Color(0xFF6366F1)), // Púrpura profundo a Índigo
            listOf(Color(0xFF0D9488), Color(0xFF14B8A6)), // Teal a Turquesa
            listOf(Color(0xFFBE185D), Color(0xFFF43F5E)), // Borgoña a Frambuesa
            listOf(Color(0xFF3B82F6), Color(0xFF818CF8)), // Azul Eléctrico a Lavanda
            listOf(Color(0xFF0284C7), Color(0xFF2DD4BF)), // Océano a Aguamarina
            listOf(Color(0xFFE11D48), Color(0xFFFDA4AF))  // Carmesí a Rosa suave
        )
        val selected = palettes[hash % palettes.size]
        Brush.linearGradient(
            colors = listOf(selected[0], selected[1])
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
                fontWeight = FontWeight.Black,
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
