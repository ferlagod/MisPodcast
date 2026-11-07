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

import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity

/**
 * Estado de un episodio capturado justo antes de una acción rápida (deslizar),
 * para poder deshacerla: reproducido/posición, su fila en la cola y su historial.
 */
data class EpisodeUndoSnapshot(
    val episodeId: String,
    val isPlayed: Boolean,
    val currentPositionMs: Long,
    /** Fila original en la cola, o null si no estaba en ella. */
    val queueItem: QueueItemEntity?,
    /** Entrada de historial más reciente antes de la acción, o null si no había. */
    val latestHistory: ListeningHistoryEntity?,
    /** Mayor id de historial del episodio antes de la acción (las entradas posteriores son nuevas). */
    val maxHistoryId: Long
)
