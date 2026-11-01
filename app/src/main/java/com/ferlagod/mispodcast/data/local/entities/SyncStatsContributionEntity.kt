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

/**
 * Contador de escucha que aporta cada dispositivo a las estadísticas de un podcast.
 *
 * Permite sumar el tiempo escuchado en varios dispositivos sin contarlo dos veces: cada uno
 * publica solo lo suyo y el total es la suma. La fila con [BASELINE_DEVICE_ID] guarda el
 * acumulado previo a este sistema, que se fusiona tomando el máximo.
 */
@Entity(
    tableName = "sync_stats_contributions",
    primaryKeys = ["deviceId", "podcastId"]
)
data class SyncStatsContributionEntity(
    val deviceId: String,
    val podcastId: String,
    val podcastTitle: String = "",
    val totalTimeListenedMs: Long = 0L,
    val episodesCompletedCount: Int = 0,
    val lastListenedAt: Long = 0L
) {
    companion object {
        const val BASELINE_DEVICE_ID = "__baseline__"
    }
}
