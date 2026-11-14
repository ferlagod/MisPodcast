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

package com.ferlagod.mispodcast.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Tokens de movimiento compartidos por todas las transiciones de navegación (Material 3). */
internal object NavMotion {
    /** Curva «emphasized decelerate»: arranca con decisión y se posa suavemente. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Curva «emphasized»: aproximación de la curva estándar enfatizada para movimientos completos. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Curva «emphasized accelerate» para lo que sale de pantalla. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val DURATION = 380
    const val FADE_IN = 260
    const val FADE_IN_DELAY = 70
    const val FADE_OUT = 120
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val ArtworkBoundsTransform = BoundsTransform { _, _ ->
    tween(NavMotion.DURATION, easing = NavMotion.Emphasized)
}

/**
 * Modificador composable que asocia la portada de un podcast a una transición compartida fluida
 * entre pantallas (Inicio/Suscripciones -> Detalle del podcast).
 *
 * Debe aplicarse al contenedor completo de la portada (el que la recorta y le da fondo/borde) para
 * que viaje entera; [shape] recorta la portada mientras se dibuja en la capa superpuesta, evitando
 * que vuele con esquinas rectas.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPodcastArtwork(podcastId: String, shape: Shape): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || navScope == null || podcastId.isBlank()) return this

    return with(sharedScope) {
        this@sharedPodcastArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key = "podcast_artwork_$podcastId"),
            animatedVisibilityScope = navScope,
            boundsTransform = ArtworkBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/**
 * Petición de Coil para portadas que participan en la transición compartida. Usa como marcador
 * cualquier versión de la portada ya presente en la caché de memoria (se resuelve de forma
 * síncrona), así la portada nunca vuela vacía ni parpadea en el primer frame de la transición
 * aunque cada pantalla la pida a un tamaño distinto.
 */
@Composable
fun rememberSharedArtworkRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .placeholderMemoryCacheKey(MemoryCache.Key(url))
            .build()
    }
}
