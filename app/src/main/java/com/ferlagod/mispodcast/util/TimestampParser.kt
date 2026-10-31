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

/**
 * Detecta marcas de tiempo ("12:34", "1:02:03") en las notas de un episodio para hacerlas pulsables.
 */
object TimestampParser {

    data class TimestampMatch(val start: Int, val end: Int, val positionMs: Long)

    // Sin dígitos ni ":" pegados a los lados para no confundir fechas, horas de emisión largas o IDs
    private val TIMESTAMP_REGEX = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

    fun findTimestamps(text: String, maxDurationMs: Long = 0L): List<TimestampMatch> {
        return TIMESTAMP_REGEX.findAll(text).mapNotNull { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            if (seconds >= 60 || (match.groupValues[1].isNotEmpty() && minutes >= 60)) return@mapNotNull null
            val positionMs = ((hours * 3600) + (minutes * 60) + seconds) * 1000L
            // Si conocemos la duración, descartar marcas imposibles (p. ej. "23:59" en un episodio de 10 min)
            if (maxDurationMs > 0L && positionMs > maxDurationMs) return@mapNotNull null
            TimestampMatch(match.range.first, match.range.last + 1, positionMs)
        }.toList()
    }

    fun format(positionMs: Long): String {
        val total = (positionMs / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }
}
