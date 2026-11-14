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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest

/** Tokens de movimiento compartidos por todas las transiciones de navegación (Material 3). */
internal object NavMotion {
    /** Curva «emphasized decelerate»: arranca con decisión y se posa suavemente. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val DURATION = 300
    const val DURATION_BACK = 250
    const val FADE_IN = 210
    const val FADE_IN_DELAY = 60
    const val FADE_OUT = 90
}

/**
 * Petición de Coil para portadas que aparecen en varias pantallas. Usa como marcador cualquier
 * versión de la portada ya presente en la caché de memoria (se resuelve de forma síncrona), así la
 * portada se ve desde el primer frame aunque cada pantalla la pida a un tamaño distinto.
 */
@Composable
fun rememberArtworkRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .placeholderMemoryCacheKey(MemoryCache.Key(url))
            .build()
    }
}
