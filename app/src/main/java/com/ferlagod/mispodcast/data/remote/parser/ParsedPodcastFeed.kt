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

package com.ferlagod.mispodcast.data.remote.parser

/**
 * Modelos de datos intermedios que representan el resultado del parseo de un canal RSS y sus episodios,
 * incluyendo metadatos estándar y etiquetas de la iniciativa Podcasting 2.0.
 */
data class ParsedEpisode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val audioType: String,
    val audioLength: Long,
    val imageUrl: String,
    val pubDate: Long,
    val durationMs: Long,
    // Etiquetas de Podcasting 2.0
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val fundingUrl: String? = null,
    val valueForValueInfo: String? = null,
    val persons: List<PodcastPerson> = emptyList()
)

data class ParsedPodcastFeed(
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String,
    val link: String,
    val category: String,
    val fundingUrl: String? = null,
    val fundingText: String? = null,
    val valueForValueInfo: String? = null,
    val episodes: List<ParsedEpisode> = emptyList()
)
