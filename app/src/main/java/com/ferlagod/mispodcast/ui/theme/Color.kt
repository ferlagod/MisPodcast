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

import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores oficial de Mis Podcast basada en el azul océano (#297A9D)
 * y superficies diseñadas para pantallas OLED y modos claros inspirados en Apple Podcasts.
 */
// Color oficial del Logo de Mis Podcast (#297A9D)
val LogoOceanBlue = Color(0xFF297A9D) // Azul oficial del logo
val LogoOceanBlueLight = Color(0xFF4BA3C7) // Versión luminosa para contraste óptimo en Dark Mode
val LogoOceanBlueContainer = Color(0xFFD1ECF6)
val LogoOceanBlueOnContainer = Color(0xFF001F2A)

// Colores secundarios adaptados a la paleta del logo
val SecondaryLight = Color(0xFF4C626B)
val SecondaryDark = Color(0xFFB4CAD5)
val OnPrimaryDark = Color(0xFF003547)

// Aliases para compatibilidad con gradientes existentes
val RoyalBlueLight = LogoOceanBlue
val RoyalBlueDark = LogoOceanBlueLight
val IndigoSecondary = Color(0xFF4C626B)
val IndigoSecondaryDark = Color(0xFF5A7885)

// Fondos y Superficies estilo Apple
val BackgroundLight = Color(0xFFF2F2F7) // iOS System Grouped Background
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE5E5EA)

// Modo Oscuro estándar (Gris oscuro elegante)
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)    // iOS / Material Dark Surface
val SurfaceVariantDark = Color(0xFF2C2C2E)

// Modo AMOLED puro (Negro absoluto para ahorro de energía en paneles OLED)
val BackgroundAmoled = Color(0xFF000000) // Píxeles OLED apagados al 100%
val SurfaceAmoled = Color(0xFF000000)
val SurfaceVariantAmoled = Color(0xFF121214)

// Textos y Contenidos
val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF8E8E93)
val TextTertiaryLight = Color(0xFFC7C7CC)

val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark = Color(0xFF48484A)

// Acentos complementarios
val AccentGreen = Color(0xFF34C759)
val AccentOrange = Color(0xFFFF9500)
val AccentRed = Color(0xFFFF3B30)