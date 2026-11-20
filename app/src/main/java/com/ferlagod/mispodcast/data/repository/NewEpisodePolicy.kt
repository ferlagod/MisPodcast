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

package com.ferlagod.mispodcast.data.repository

import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity

/**
 * Reglas comunes para decidir qué episodios recién insertados son novedades (notificar,
 * descargar y encolar), compartidas por la comprobación en segundo plano y la actualización manual.
 */
object NewEpisodePolicy {

    const val MIN_WINDOW_MS = 48L * 3600L * 1000L
    const val MAX_WINDOW_MS = 14L * 24L * 3600L * 1000L

    /** Número máximo de novedades por podcast que se descargan automáticamente en cada comprobación. */
    const val MAX_AUTO_DOWNLOADS_PER_PODCAST = 2

    /**
     * Novedades genuinas entre [newlyInserted] (episodios que no existían en la base de datos).
     *
     * @param podcast el podcast tal y como estaba ANTES de esta comprobación (su `lastUpdated` previo).
     *
     * - La primera sincronización (`lastUpdated <= 0`) nunca produce novedades: es el archivo histórico.
     * - No se exige `pubDate > lastUpdated`: muchos feeds publican el episodio minutos u horas después
     *   de la fecha que declaran, y se perdería si la comprobación anterior cayó entre ambas.
     * - Se descartan los anteriores a la suscripción y los más antiguos que la ventana (48 h, ampliada
     *   hasta 14 días si hacía más que no se comprobaba), para no tratar como nuevo un episodio
     *   antiguo que el autor ha vuelto a subir.
     */
    fun selectGenuinelyNew(
        podcast: PodcastEntity,
        newlyInserted: List<EpisodeEntity>,
        now: Long = System.currentTimeMillis()
    ): List<EpisodeEntity> {
        if (podcast.lastUpdated <= 0L || newlyInserted.isEmpty()) return emptyList()
        val window = (now - podcast.lastUpdated).coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)
        val oldestAllowedDate = now - window
        return newlyInserted.filter { episode ->
            episode.pubDate > (podcast.subscribedAt - 3600_000L) &&
                episode.pubDate >= oldestAllowedDate
        }
    }

    /** Regla de encolado efectiva: la del podcast o, si es «DEFAULT», la global. */
    fun effectiveEnqueueRule(podcast: PodcastEntity, defaultRule: String): String =
        if (podcast.autoEnqueueRule == "DEFAULT" || podcast.autoEnqueueRule.isBlank()) defaultRule
        else podcast.autoEnqueueRule

    /** Encola [episodes] en orden de publicación según [rule] («TOP», «BOTTOM» o «DISABLED»). */
    suspend fun enqueue(dao: PodcastDao, episodes: List<EpisodeEntity>, rule: String) {
        if (rule != "TOP" && rule != "BOTTOM") return
        episodes.sortedBy { it.pubDate }.forEach { ep ->
            if (rule == "TOP") dao.addEpisodeToQueueTop(ep.id) else dao.addEpisodeToQueueEnd(ep.id)
        }
    }
}
