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

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4BA3C7), // Versión más luminosa de #297A9D para contraste óptimo en fondo oscuro
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF297A9D), // Color exacto del logo para tarjetas destacadas y botones
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFB4CAD5),
    onSecondary = Color(0xFF1E333C),
    background = BackgroundDark, // Gris oscuro elegante para modo oscuro estándar
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF4BA3C7),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF297A9D),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFB4CAD5),
    onSecondary = Color(0xFF1E333C),
    background = BackgroundAmoled, // Negro absoluto 0x000000 para máximo ahorro de batería
    onBackground = TextPrimaryDark,
    surface = SurfaceAmoled, // Superficie negra pura (0x000000) apagando píxeles OLED en tarjetas y paneles
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantAmoled,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF1C1C1E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF297A9D), // Azul oficial del logo
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1ECF6),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4C626B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2F7), // iOS System Grouped Background
    onBackground = TextPrimaryLight,
    surface = Color(0xFFFFFFFF),
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA)
)

/**
 * Tema principal composable de Mis Podcast.
 *
 * Aplica los esquemas de color Material Design 3 (claro, oscuro elegante y negro puro AMOLED),
 * estiliza las barras del sistema (barra de estado y navegación) y asigna la tipografía de la app.
 */
@Composable
fun MisPodcastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    dynamicColor: Boolean = false, // false por defecto para preservar la identidad del logo oficial #297A9D
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isAmoled -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(LocalContext.current).copy(
                    background = BackgroundAmoled,
                    surface = SurfaceAmoled,
                    surfaceVariant = SurfaceVariantAmoled
                )
            } else {
                AmoledColorScheme
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                val isDark = darkTheme || isAmoled
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}