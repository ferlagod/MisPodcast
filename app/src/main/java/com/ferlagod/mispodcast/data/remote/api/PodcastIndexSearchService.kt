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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * Búsqueda en el directorio abierto Podcast Index (https://podcastindex.org).
 *
 * Complementa al catálogo de Apple con podcasts independientes y Podcasting 2.0. Solo se activa si
 * se han configurado credenciales (gratuitas) en la compilación; si no, [isConfigured] es false.
 */
class PodcastIndexSearchService(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val isConfigured: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    suspend fun searchByTerm(term: String, max: Int = 30): List<ApplePodcastDto> = withContext(Dispatchers.IO) {
        if (!isConfigured || term.isBlank()) return@withContext emptyList()
        val url = "https://api.podcastindex.org/api/1.0/search/byterm".toHttpUrl().newBuilder()
            .addQueryParameter("q", term.trim())
            .addQueryParameter("max", max.toString())
            .build()
        val authDate = (System.currentTimeMillis() / 1000L).toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", com.ferlagod.mispodcast.util.AudioTrackingSanitizer.GENERIC_PRIVACY_USER_AGENT)
            .header("X-Auth-Key", apiKey)
            .header("X-Auth-Date", authDate)
            .header("Authorization", sha1Hex(apiKey + apiSecret + authDate))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            json.decodeFromString<PodcastIndexSearchResponse>(body).feeds
                .filter { it.url.isNotBlank() && !it.dead }
                .map { it.toApplePodcastDto() }
        }
    }

    private fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

@Serializable
data class PodcastIndexSearchResponse(
    @SerialName("feeds") val feeds: List<PodcastIndexFeedDto> = emptyList()
)

@Serializable
data class PodcastIndexFeedDto(
    @SerialName("id") val id: Long = 0L,
    @SerialName("title") val title: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("image") val image: String = "",
    @SerialName("artwork") val artwork: String = "",
    @SerialName("link") val link: String = "",
    @SerialName("episodeCount") val episodeCount: Int = 0,
    @SerialName("categories") val categories: Map<String, String>? = null,
    @SerialName("dead") private val deadFlag: Int = 0
) {
    val dead: Boolean get() = deadFlag != 0

    fun toApplePodcastDto(): ApplePodcastDto {
        val art = artwork.ifBlank { image }.ifBlank { null }
        return ApplePodcastDto(
            collectionId = null,
            collectionName = title.ifBlank { null },
            artistName = author.ifBlank { null },
            feedUrl = url,
            artworkUrl600 = art,
            artworkUrl100 = art,
            primaryGenreName = categories?.values?.firstOrNull(),
            collectionViewUrl = link.ifBlank { null },
            trackCount = episodeCount.takeIf { it > 0 }
        )
    }
}
