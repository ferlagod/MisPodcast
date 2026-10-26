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
 * Entidad Room que representa un podcast suscrito o carpeta local de audio.
 *
 * Almacena los metadatos esenciales del programa, su feed RSS de origen,
 * la carátula, datos de categorización y extensiones de Podcasting 2.0 (financiación, Value4Value),
 * así como los identificadores para carpetas locales montadas mediante SAF (Storage Access Framework).
 */
@Entity(
    tableName = "podcasts",
    indices = [
        Index(value = ["feedUrl"], unique = true)
    ]
)
data class PodcastEntity(
    @PrimaryKey
    val id: String, // Feed URL normalizada o iTunes collectionId
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String,
    val isSubscribed: Boolean = true,
    val subscribedAt: Long = System.currentTimeMillis(),
    val unsubscribedAt: Long = 0L, // Marca temporal para sincronización bidireccional de bajas (tombstone)
    val lastUpdated: Long = System.currentTimeMillis(),
    val category: String = "General",
    val link: String = "",
    val fundingUrl: String? = null,
    val fundingText: String? = null,
    val valueForValueInfo: String? = null,
    val isLocalFolder: Boolean = false,
    val localFolderPath: String? = null,
    val httpEtag: String? = null, // Cabecera HTTP ETag para peticiones condicionales (If-None-Match -> 304)
    val httpLastModified: String? = null, // Cabecera HTTP Last-Modified para peticiones condicionales (If-Modified-Since)
    // Ajustes específicos por podcast
    val customSpeed: Float? = null, // null hereda la velocidad global predeterminada
    val skipIntroSeconds: Int = 0, // Segundos a saltar al inicio del episodio (intro)
    val skipEndingSeconds: Int = 0, // Segundos a saltar al final del episodio (créditos / patrocinadores)
    val autoDownloadEnabled: Boolean? = null, // null hereda la configuración global, true fuerza descarga, false la desactiva
    val episodeSortOrder: String = "NEWEST_FIRST", // "NEWEST_FIRST" (recientes) o "OLDEST_FIRST" (cronológico para series narrativas)
    val autoEnqueueRule: String = "DISABLED" // "DISABLED", "TOP" (siguiente en reproducirse) o "BOTTOM" (al final de la cola)
)
