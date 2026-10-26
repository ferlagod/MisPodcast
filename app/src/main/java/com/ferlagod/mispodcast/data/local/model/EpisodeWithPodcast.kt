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

package com.ferlagod.mispodcast.data.local.model

import androidx.room.Embedded
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity

/**
 * Modelo de relación Room que une los datos de un [EpisodeEntity] con la información
 * básica del podcast al que pertenece (título, carátula y URL del feed).
 */
data class EpisodeWithPodcast(
    @Embedded
    val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastArtworkUrl: String,
    val podcastFeedUrl: String
)

data class GlobalStats(
    val totalTimeListenedMs: Long = 0L,
    val totalEpisodesCompleted: Int = 0,
    val totalPodcastsTracked: Int = 0
)

data class HistoryEpisodeItem(
    val historyId: Long,
    val listenedAt: Long,
    val durationListenedMs: Long,
    val completed: Boolean,
    val podcastTitle: String,
    val podcastArtworkUrl: String,
    @Embedded
    val episode: EpisodeEntity
)
