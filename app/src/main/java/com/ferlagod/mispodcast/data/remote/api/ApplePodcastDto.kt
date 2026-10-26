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

package com.ferlagod.mispodcast.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelos DTO para deserializar las respuestas de búsqueda y tendencias de la API pública de Apple Podcasts.
 * Emplea kotlinx.serialization para evitar la introspección (reflection) en tiempo de ejecución
 * y optimizar la compatibilidad con ofuscación R8.
 */
@Serializable
data class AppleSearchResponse(
    @SerialName("resultCount")
    val resultCount: Int = 0,
    @SerialName("results")
    val results: List<ApplePodcastDto> = emptyList()
)

@Serializable
data class ApplePodcastDto(
    @SerialName("collectionId")
    val collectionId: Long? = null,
    @SerialName("collectionName")
    val collectionName: String? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("feedUrl")
    val feedUrl: String? = null,
    @SerialName("artworkUrl600")
    val artworkUrl600: String? = null,
    @SerialName("artworkUrl100")
    val artworkUrl100: String? = null,
    @SerialName("primaryGenreName")
    val primaryGenreName: String? = null,
    @SerialName("collectionViewUrl")
    val collectionViewUrl: String? = null,
    @SerialName("trackCount")
    val trackCount: Int? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null
)
