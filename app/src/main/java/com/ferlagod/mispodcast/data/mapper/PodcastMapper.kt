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

package com.ferlagod.mispodcast.data.mapper

import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastDto
import com.ferlagod.mispodcast.data.remote.parser.ParsedEpisode
import com.ferlagod.mispodcast.data.remote.parser.ParsedPodcastFeed
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Funciones de extensión de mapeo para transformar modelos de feeds RSS y respuestas de API
 * en entidades persistibles de Room ([PodcastEntity] y [EpisodeEntity]).
 */
fun ParsedPodcastFeed.toPodcastEntity(
    isSubscribed: Boolean = true,
    subscribedAt: Long = System.currentTimeMillis()
): PodcastEntity {
    return PodcastEntity(
        id = feedUrl,
        title = title,
        author = author,
        description = description,
        feedUrl = feedUrl,
        artworkUrl = artworkUrl,
        isSubscribed = isSubscribed,
        subscribedAt = subscribedAt,
        lastUpdated = System.currentTimeMillis(),
        category = category,
        link = link,
        fundingUrl = fundingUrl,
        fundingText = fundingText,
        valueForValueInfo = valueForValueInfo
    )
}

fun ParsedEpisode.toEpisodeEntity(podcastId: String): EpisodeEntity {
    val personsJson = if (persons.isNotEmpty()) {
        try {
            Json.encodeToString(persons)
        } catch (_: Exception) {
            null
        }
    } else null
    return EpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        pubDate = pubDate,
        duration = durationMs,
        currentPositionMs = 0L,
        isPlayed = false,
        isFavorite = false,
        isDownloaded = false,
        localFilePath = null,
        fileSizeBytes = audioLength,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        fundingUrl = fundingUrl,
        valueForValueInfo = valueForValueInfo,
        persons = personsJson
    )
}

fun ApplePodcastDto.toPodcastEntity(): PodcastEntity? {
    val url = feedUrl ?: return null
    return PodcastEntity(
        id = url,
        title = collectionName ?: "Podcast sin título",
        author = artistName ?: "Autor desconocido",
        description = "",
        feedUrl = url,
        artworkUrl = artworkUrl600 ?: artworkUrl100 ?: "",
        isSubscribed = false,
        subscribedAt = System.currentTimeMillis(),
        lastUpdated = System.currentTimeMillis(),
        category = primaryGenreName ?: "General",
        link = collectionViewUrl ?: ""
    )
}
