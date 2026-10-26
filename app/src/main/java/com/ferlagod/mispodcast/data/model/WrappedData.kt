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
 * @param archetype Título del perfil o arquetipo de oyente ("Maratoniano del Audio", "Explorador Ecléctico", etc.).
 * @param archetypeDescription Explicación temática divertida del arquetipo.
 */
data class WrappedData(
    val year: Int,
    val totalTimeListenedMs: Long,
    val totalEpisodesCompleted: Int,
    val totalPodcastsTracked: Int,
    val topPodcasts: List<WrappedPodcastItem>,
    val archetype: String,
    val archetypeDescription: String
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
    fun generateShareText(): String {
        val yearLabel = if (year > 0) "$year" else "Histórico"
        val top1 = topPodcast
        val topFive = topPodcasts.take(5)

        return buildString {
            appendLine("🎉 ¡Mi Wrapped $yearLabel en Mis Podcast! 🎧")
            appendLine()
            appendLine("⏱️ Tiempo total escuchado: $formattedTotalHours horas (${String.format(Locale.getDefault(), "%.1f", totalDays)} días continuos)")
            appendLine("📚 Episodios completados: $totalEpisodesCompleted")
            appendLine("🎙️ Programas disfrutados: $totalPodcastsTracked")
            appendLine("🌟 Arquetipo de oyente: \"$archetype\"")
            appendLine("💡 $archetypeDescription")
            appendLine()
            if (top1 != null) {
                appendLine("🏆 Mi podcast #1: ${top1.title} (${top1.formattedHours})")
            }
            if (topFive.isNotEmpty()) {
                appendLine()
                appendLine("Top 5 podcasts:")
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
            appendLine("Escuchado con Mis Podcast • Gestor libre de podcasts para Android")
            appendLine("#MisPodcastWrapped #Wrapped$yearLabel")
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
        ): Pair<String, String> {
            return when {
                totalHours >= 100.0 -> {
                    "El Maratoniano del Audio" to "El audio es la banda sonora permanente de tu vida. Vives con auriculares puestos."
                }
                totalPodcasts >= 12 -> {
                    "El Explorador Ecléctico" to "Saltas de programa en programa descubriendo nuevas voces, géneros e ideas sin descanso."
                }
                topPercentage >= 0.5f && totalHours >= 10.0 -> {
                    "El Fan Incondicional" to "Fidelidad absoluta a tu programa de cabecera. No perdonas ni un solo minuto emitido."
                }
                episodesCompleted >= 60 -> {
                    "El Devorador de Capítulos" to "Episodio que entra en tu cola, episodio que terminas de principio a fin. Disciplina pura."
                }
                totalHours >= 30.0 -> {
                    "El Oyente Apasionado" to "Los podcasts son tu momento sagrado del día: en desplazamientos, tareas o descanso."
                }
                else -> {
                    "El Sibarita del Podcast" to "Calidad por encima de cantidad. Eliges con mimo las conversaciones que merecen tu tiempo."
                }
            }
        }
    }
}
