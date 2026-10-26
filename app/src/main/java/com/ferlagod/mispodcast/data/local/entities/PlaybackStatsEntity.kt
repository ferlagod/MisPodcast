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

package com.ferlagod.mispodcast.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que acumula métricas agregadas de reproducción por cada podcast.
 *
 * Registra el tiempo total escuchado en milisegundos, el número de capítulos terminados
 * y la fecha de última interacción auditiva con el programa.
 */
@Entity(tableName = "playback_stats")
data class PlaybackStatsEntity(
    @PrimaryKey
    val podcastId: String,
    val podcastTitle: String,
    val totalTimeListenedMs: Long = 0L,
    val episodesCompletedCount: Int = 0,
    val lastListenedAt: Long = System.currentTimeMillis()
)
