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

package com.ferlagod.mispodcast.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.ferlagod.mispodcast.R
import java.util.Locale

/**
 * Representa el resumen de un podcast individual dentro de la experiencia anual Wrapped.
 *
 * @param podcastId Identificador único del podcast.
 * @param title Título oficial del programa.
 * @param artworkUrl URL o URI local de la carátula.
 * @param timeListenedMs Tiempo acumulado de escucha en milisegundos durante el periodo.
 * @param episodesCompleted Cantidad de episodios completados al 100%.
 * @param percentageOfTotal Porcentaje que este podcast representa sobre el tiempo total escuchado (0.0 a 1.0).
 */
data class WrappedPodcastItem(
    val podcastId: String,
    val title: String,
    val artworkUrl: String,
    val timeListenedMs: Long,
    val episodesCompleted: Int,
    val percentageOfTotal: Float
) {
    val hours: Double get() = timeListenedMs.toDouble() / 3600000.0
    val formattedHours: String
        get() = if (hours >= 1.0) {
            String.format(Locale.getDefault(), "%.1f h", hours)
        } else {
            "${timeListenedMs / 60000} min"
        }
}

/**
 * Modelo completo que agrupa todas las métricas anuales del usuario estilo "Wrapped" / "Year in Review".
 *
 * @param year Año calendario de las estadísticas (ej: 2026) o 0 para histórico total acumulado.
 * @param totalTimeListenedMs Milisegundos totales escuchados en el periodo.
 * @param totalEpisodesCompleted Cantidad total de episodios completados íntegramente.
 * @param totalPodcastsTracked Número de programas distintos escuchados.
 * @param topPodcasts Lista ordenada de mayor a menor tiempo de los podcasts más escuchados (hasta 10).
 * @param archetype Perfil o arquetipo de oyente (título y descripción traducibles).
 */
data class WrappedData(
    val year: Int,
    val totalTimeListenedMs: Long,
    val totalEpisodesCompleted: Int,
    val totalPodcastsTracked: Int,
    val topPodcasts: List<WrappedPodcastItem>,
    val archetype: WrappedArchetype
) {
    val totalHours: Double get() = totalTimeListenedMs.toDouble() / 3600000.0
    val totalDays: Double get() = totalTimeListenedMs.toDouble() / (1000.0 * 60 * 60 * 24)

    val formattedTotalHours: String
        get() = String.format(Locale.getDefault(), "%.1f", totalHours)

    val topPodcast: WrappedPodcastItem?
        get() = topPodcasts.firstOrNull()

    /**
     * Genera un texto atractivo con formato, emojis y hashtags listo para compartir
     * en cualquier aplicación externa mediante [android.content.Intent.ACTION_SEND].
     */
    fun generateShareText(context: Context): String {
        val yearLabel = if (year > 0) "$year" else context.getString(R.string.wrapped_historic_label)
        val top1 = topPodcast
        val topFive = topPodcasts.take(5)

        return buildString {
            appendLine(context.getString(R.string.wrapped_share_header, yearLabel))
            appendLine()
            appendLine(context.getString(R.string.wrapped_share_total_time, formattedTotalHours, String.format(Locale.getDefault(), "%.1f", totalDays)))
            appendLine(context.getString(R.string.wrapped_share_completed, totalEpisodesCompleted))
            appendLine(context.getString(R.string.wrapped_share_shows, totalPodcastsTracked))
            appendLine(context.getString(R.string.wrapped_share_archetype, context.getString(archetype.titleRes)))
            appendLine("💡 ${context.getString(archetype.descriptionRes)}")
            appendLine()
            if (top1 != null) {
                appendLine(context.getString(R.string.wrapped_share_top1, top1.title, top1.formattedHours))
            }
            if (topFive.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.wrapped_share_top5))
                topFive.forEachIndexed { index, item ->
                    val medal = when (index) {
                        0 -> "🥇"
                        1 -> "🥈"
                        2 -> "🥉"
                        else -> "${index + 1}."
                    }
                    appendLine("$medal ${item.title} — ${item.formattedHours}")
                }
            }
            appendLine()
            appendLine(context.getString(R.string.wrapped_share_footer))
            appendLine("#MisPodcastWrapped #Wrapped${if (year > 0) "$year" else ""}")
        }
    }

    companion object {
        /**
         * Determina el arquetipo de oyente según volumen y dispersión de la escucha.
         */
        fun resolveArchetype(
            totalHours: Double,
            totalPodcasts: Int,
            episodesCompleted: Int,
            topPercentage: Float
        ): WrappedArchetype {
            return when {
                totalHours >= 100.0 -> WrappedArchetype.MARATHONER
                totalPodcasts >= 12 -> WrappedArchetype.EXPLORER
                topPercentage >= 0.5f && totalHours >= 10.0 -> WrappedArchetype.LOYAL_FAN
                episodesCompleted >= 60 -> WrappedArchetype.BINGER
                totalHours >= 30.0 -> WrappedArchetype.PASSIONATE
                else -> WrappedArchetype.CONNOISSEUR
            }
        }
    }
}

/**
 * Arquetipos de oyente del resumen Wrapped, con título y descripción traducibles.
 */
enum class WrappedArchetype(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    MARATHONER(R.string.archetype_marathoner, R.string.archetype_marathoner_desc),
    EXPLORER(R.string.archetype_explorer, R.string.archetype_explorer_desc),
    LOYAL_FAN(R.string.archetype_loyal_fan, R.string.archetype_loyal_fan_desc),
    BINGER(R.string.archetype_binger, R.string.archetype_binger_desc),
    PASSIONATE(R.string.archetype_passionate, R.string.archetype_passionate_desc),
    CONNOISSEUR(R.string.archetype_connoisseur, R.string.archetype_connoisseur_desc)
}
