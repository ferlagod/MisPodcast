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
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para gestionar la cola de reproducción secuencial persistente.
 *
 * Permite preservar el orden exacto de escucha fijado por el usuario o mediante
 * ordenación inteligente, registrando el episodio asociado, la posición en la cola
 * y si es el elemento actualmente en reproducción activa.
 */
@Entity(
    tableName = "queue_items",
    indices = [
        Index(value = ["episodeId"], unique = true),
        Index(value = ["positionIndex"])
    ]
)
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val episodeId: String,
    val positionIndex: Int, // Posición ordenada en la cola
    val isCurrentlyPlaying: Boolean = false, // True para el episodio actualmente en reproducción
    val addedAt: Long = System.currentTimeMillis()
)
