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

package com.ferlagod.mispodcast.util

import java.util.Locale

/**
 * Utilidades de accesibilidad para lectores de pantalla (TalkBack) en componentes de la interfaz de usuario.
 *
 * Provee formateadores semánticos para que controles como [androidx.compose.material3.Slider]
 * no anuncien porcentajes numéricos genéricos sino descripciones legibles y naturales
 * ("12 minutos de 58", "1 h 15 min de 2 h 30 min", etc.).
 */
object AccessibilityUtils {

    /**
     * Calcula la descripción de estado semántica (`stateDescription`) para el control deslizante
     * de reproducción a partir de la posición actual y la duración en milisegundos.
     *
     * @param currentPosMs Posición actual en milisegundos.
     * @param durationMs Duración total en milisegundos.
     * @param minutesFormat Formato para episodios menores a 1 hora (ej: "%1$d minutos de %2$d").
     * @param hoursFormat Formato para episodios de 1 hora o más cuando la posición actual ya superó 1 hora (ej: "%1$d h %2$d min de %3$d h %4$d min").
     * @param hoursStartFormat Formato para episodios de 1 hora o más cuando la posición actual aún está en la primera hora (ej: "%1$d min de %2$d h %3$d min").
     * @return Cadena formateada para que TalkBack la verbalice con naturalidad.
     */
    fun formatProgressTalkBack(
        currentPosMs: Long,
        durationMs: Long,
        minutesFormat: String = "%1\$d minutos de %2\$d",
        hoursFormat: String = "%1\$d h %2\$d min de %3\$d h %4\$d min",
        hoursStartFormat: String = "%1\$d min de %2\$d h %3\$d min"
    ): String {
        if (durationMs <= 0L) return ""

        val clampedPos = currentPosMs.coerceIn(0L, durationMs)

        return if (durationMs < 3600_000L) {
            val curMin = (clampedPos / 60_000L).coerceAtLeast(0L)
            val totMin = (durationMs / 60_000L).coerceAtLeast(1L)
            String.format(Locale.ROOT, minutesFormat, curMin, totMin)
        } else {
            val totH = durationMs / 3600_000L
            val totM = (durationMs % 3600_000L) / 60_000L
            val curH = clampedPos / 3600_000L
            val curM = (clampedPos % 3600_000L) / 60_000L

            if (curH > 0L) {
                String.format(Locale.ROOT, hoursFormat, curH, curM, totH, totM)
            } else {
                String.format(Locale.ROOT, hoursStartFormat, curM, totH, totM)
            }
        }
    }
}
