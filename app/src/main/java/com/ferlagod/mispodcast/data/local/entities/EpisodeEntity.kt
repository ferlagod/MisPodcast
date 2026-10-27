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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room que modela un episodio perteneciente a un podcast.
 *
 * Mantiene la información multimedia (URL de audio remota o ruta en almacenamiento local si fue descargado),
 * duración, estado de reproducción (tiempo exacto escuchado en milisegundos y si ya se ha completado),
 * favoritos y etiquetas enriquecidas de Podcasting 2.0 (capítulos JSON, transcripciones y personas participantes).
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["pubDate"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isPlayed"]),
        Index(value = ["isDismissedFromNewReleases"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey
    val id: String, // GUID o hash único del enclosure audioUrl
    val podcastId: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String,
    val pubDate: Long,
    val duration: Long, // Duración en milisegundos
    val currentPositionMs: Long = 0L, // Para recordar siempre el punto exacto de escucha
    val positionUpdatedAt: Long = 0L, // Marca temporal de la última actualización de posición o reproducción
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    // Campos Podcasting 2.0
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val fundingUrl: String? = null,
    val valueForValueInfo: String? = null,
    val persons: String? = null, // Serializado como JSON String
    val isDismissedFromNewReleases: Boolean = false
)
