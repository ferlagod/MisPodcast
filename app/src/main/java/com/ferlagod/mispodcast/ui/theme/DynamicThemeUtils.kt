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

package com.ferlagod.mispodcast.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache en memoria para colores dominantes extraídos por URL de carátula
 * para evitar reprocesar imágenes ya analizadas.
 */
private val paletteColorCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

/**
 * Extrae de forma reactiva y asíncrona el color dominante/vibrante de una URL de carátula
 * utilizando Coil y AndroidX Palette.
 */
@Composable
fun rememberArtworkDominantColor(
    artworkUrl: String?,
    defaultColor: Color = MaterialThemeTokens.DefaultBrandColor
): Color {
    val context = LocalContext.current
    var dominantColor by remember(artworkUrl) {
        mutableStateOf(
            if (!artworkUrl.isNullOrBlank()) {
                paletteColorCache[artworkUrl] ?: defaultColor
            } else {
                defaultColor
            }
        )
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            dominantColor = defaultColor
            return@LaunchedEffect
        }

        // Si ya está en caché, no necesitamos descargar
        paletteColorCache[artworkUrl]?.let {
            dominantColor = it
            return@LaunchedEffect
        }

        val extracted = extractDominantColorFromUrl(context, artworkUrl, defaultColor)
        paletteColorCache[artworkUrl] = extracted
        dominantColor = extracted
    }

    // Transición suave entre cambios de episodio / podcast
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 450),
        label = "dominantArtworkColorAnimation"
    )

    return animatedColor
}

/**
 * Descarga y analiza la imagen a baja resolución con Coil + Palette para extraer
 * el color vibrante o dominante óptimo para UI.
 */
suspend fun extractDominantColorFromUrl(
    context: Context,
    imageUrl: String,
    fallbackColor: Color
): Color = withContext(Dispatchers.IO) {
    try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(160, 160) // Miniatura suficiente y muy rápida para Palette
            .allowHardware(false) // Necesario para crear un Bitmap de software accesible por Palette
            .build()

        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = result.drawable.toBitmap(width = 160, height = 160, config = Bitmap.Config.ARGB_8888)
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()

            // Priorizamos swatches vibrantes con suficiente saturación
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.mutedSwatch

            swatch?.rgb?.let { Color(it) } ?: fallbackColor
        } else {
            fallbackColor
        }
    } catch (_: Exception) {
        fallbackColor
    }
}

/**
 * Tokens de referencia para la paleta base del reproductor y podcast.
 */
object MaterialThemeTokens {
    val DefaultBrandColor = Color(0xFF297A9D) // Azul oficial de la aplicación
}

/**
 * Genera un esquema [ColorScheme] Material 3 dinámico en torno a un color semilla [seedColor],
 * ajustando contraste, brillo y superficies para que el reproductor o cabecera adopten
 * la identidad estética de la carátula sin perder legibilidad.
 */
@Stable
fun dynamicDarkColorSchemeFromSeed(
    seedColor: Color,
    baseDarkScheme: ColorScheme
): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedColor.toArgb(), hsv)

    // Ajustar luminosidad y saturación para garantizar buen contraste sobre fondo oscuro
    hsv[1] = hsv[1].coerceIn(0.45f, 0.85f) // Saturación equilibrada
    hsv[2] = hsv[2].coerceIn(0.65f, 0.95f) // Brillo alto para el primary en dark mode
    val adjustedPrimary = Color(android.graphics.Color.HSVToColor(hsv))

    // Color de contenedor primario (un poco más oscuro y saturado)
    hsv[2] = 0.35f
    val primaryContainer = Color(android.graphics.Color.HSVToColor(hsv))

    // Fondo enriquecido sutilmente con el tono del podcast (tinte muy oscuro)
    hsv[1] = (hsv[1] * 0.25f).coerceAtMost(0.20f)
    hsv[2] = 0.08f
    val dynamicBackground = Color(android.graphics.Color.HSVToColor(hsv))

    hsv[2] = 0.12f
    val dynamicSurface = Color(android.graphics.Color.HSVToColor(hsv))

    return baseDarkScheme.copy(
        primary = adjustedPrimary,
        onPrimary = Color(0xFF001F2A),
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color(0xFFD1ECF6),
        background = dynamicBackground,
        surface = dynamicSurface,
        surfaceVariant = dynamicSurface.copy(alpha = 0.85f)
    )
}

/**
 * Genera un esquema [ColorScheme] en modo claro alrededor de un color semilla.
 */
@Stable
fun dynamicLightColorSchemeFromSeed(
    seedColor: Color,
    baseLightScheme: ColorScheme
): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedColor.toArgb(), hsv)

    // Ajuste de legibilidad para primary en modo claro (más oscuro)
    hsv[1] = hsv[1].coerceIn(0.5f, 0.9f)
    hsv[2] = hsv[2].coerceIn(0.35f, 0.65f)
    val adjustedPrimary = Color(android.graphics.Color.HSVToColor(hsv))

    // Container pastel suave
    hsv[1] = 0.20f
    hsv[2] = 0.95f
    val primaryContainer = Color(android.graphics.Color.HSVToColor(hsv))

    return baseLightScheme.copy(
        primary = adjustedPrimary,
        primaryContainer = primaryContainer,
        onPrimary = Color.White
    )
}
