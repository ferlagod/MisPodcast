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

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.io.StringReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.ferlagod.mispodcast.data.model.ValueForValueConfig
import com.ferlagod.mispodcast.data.model.ValueRecipient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Resultado de una descarga condicional de feed RSS/Atom.
 */
sealed class FeedFetchResult {
    /**
     * El feed fue descargado y parseado con éxito (HTTP 200 OK).
     */
    data class Success(
        val feed: ParsedPodcastFeed,
        val etag: String?,
        val lastModified: String?
    ) : FeedFetchResult()

    /**
     * El servidor confirmó que el feed no ha cambiado (HTTP 304 Not Modified).
     * Ahorra el 100% de transferencia de datos y ciclos de CPU.
     */
    data object NotModified : FeedFetchResult()
}

class RssFeedParser(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Descarga y parsea un feed RSS utilizando cabeceras condicionales HTTP (If-None-Match e If-Modified-Since).
     *
     * Si el servidor devuelve HTTP 304 (Not Modified), retorna [FeedFetchResult.NotModified] sin descargar
     * el cuerpo XML ni consumir CPU en parseo.
     */
    suspend fun fetchAndParseFeedConditional(
        feedUrl: String,
        etag: String? = null,
        lastModified: String? = null
    ): FeedFetchResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "MisPodcast/1.0 (Android; Podcasting 2.0 Compliant)")

        if (!etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304) {
                return@withContext FeedFetchResult.NotModified
            }
            if (!response.isSuccessful) {
                throw Exception("Error HTTP al descargar feed: ${response.code}")
            }
            val newEtag = response.header("ETag")
            val newLastModified = response.header("Last-Modified")
            val bodyStream = response.body?.byteStream()
                ?: throw Exception("El cuerpo del feed RSS está vacío")
            val parsed = parseFeed(bodyStream, feedUrl)
            FeedFetchResult.Success(
                feed = parsed,
                etag = newEtag,
                lastModified = newLastModified
            )
        }
    }

    /**
     * Descarga y parsea un feed RSS desde una URL dada de forma incondicional.
     */
    suspend fun fetchAndParseFeed(feedUrl: String): ParsedPodcastFeed = withContext(Dispatchers.IO) {
        when (val result = fetchAndParseFeedConditional(feedUrl)) {
            is FeedFetchResult.Success -> result.feed
            is FeedFetchResult.NotModified -> throw Exception("Respuesta 304 Not Modified inesperada en consulta directa")
        }
    }

    /**
     * Parsea el contenido XML desde un InputStream.
     */
    fun parseFeed(inputStream: InputStream, feedUrl: String): ParsedPodcastFeed {
        val parser: XmlPullParser = try {
            Xml.newPullParser()
        } catch (_: Throwable) {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var inChannel = false
        var inItem = false

        // Metadatos de canal (Podcast)
        var channelTitle = ""
        var channelAuthor = ""
        var channelDescription = ""
        var channelLink = ""
        var channelArtworkUrl = ""
        var channelCategory = "General"
        var channelFundingUrl: String? = null
        var channelFundingText: String? = null
        var channelValueForValue: String? = null

        val episodes = mutableListOf<ParsedEpisode>()

        // Metadatos temporales de episodio
        var itemTitle = ""
        var itemDescription = ""
        var itemAudioUrl = ""
        var itemAudioType = "audio/mpeg"
        var itemAudioLength = 0L
        var itemGuid = ""
        var itemPubDate = 0L
        var itemDurationMs = 0L
        var itemImageUrl = ""
        var itemLink = ""
        var itemChaptersUrl: String? = null
        var itemTranscriptUrl: String? = null
        var itemFundingUrl: String? = null
        var itemValueForValue: String? = null
        val itemPersons = mutableListOf<PodcastPerson>()
        val itemSoundbites = mutableListOf<PodcastSoundbite>()
        var itemAltAudioUrl = ""
        var itemAltAudioType = ""
        var inAlternateEnclosure = false
        var alternateEnclosureType = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName == "channel" -> inChannel = true
                        tagName == "item" || tagName == "entry" -> {
                            inItem = true
                            // Reiniciar variables para el nuevo episodio
                            itemTitle = ""
                            itemDescription = ""
                            itemAudioUrl = ""
                            itemAudioType = "audio/mpeg"
                            itemAudioLength = 0L
                            itemGuid = ""
                            itemPubDate = 0L
                            itemDurationMs = 0L
                            itemImageUrl = ""
                            itemLink = ""
                            itemChaptersUrl = null
                            itemTranscriptUrl = null
                            itemFundingUrl = null
                            itemValueForValue = null
                            itemPersons.clear()
                            itemSoundbites.clear()
                            itemAltAudioUrl = ""
                            itemAltAudioType = ""
                            inAlternateEnclosure = false
                        }

                        inItem -> {
                            // Parseo de elementos del episodio
                            when (tagName) {
                                "title" -> itemTitle = parser.nextTextSafe()
                                "description", "itunes:summary" -> {
                                    if (itemDescription.isBlank()) {
                                        itemDescription = parser.nextTextSafe()
                                    }
                                }
                                "content:encoded" -> {
                                    val content = parser.nextTextSafe()
                                    if (content.isNotBlank()) itemDescription = content
                                }
                                "enclosure" -> {
                                    val url = parser.getAttributeValueSafe("url")
                                    val type = parser.getAttributeValueSafe("type")
                                    val length = parser.getAttributeValueSafe("length")
                                    if (url.isNotBlank()) itemAudioUrl = url
                                    if (type.isNotBlank()) itemAudioType = type
                                    itemAudioLength = length.toLongOrNull() ?: 0L
                                }
                                "guid", "id" -> itemGuid = parser.nextTextSafe()
                                "link" -> {
                                    // RSS: <link>url</link> · Atom: <link rel="alternate" href="url"/>
                                    val href = parser.getAttributeValueSafe("href")
                                    val rel = parser.getAttributeValueSafe("rel")
                                    if (href.isNotBlank()) {
                                        if (itemLink.isBlank() && (rel.isBlank() || rel.equals("alternate", ignoreCase = true))) {
                                            itemLink = href.trim()
                                        }
                                    } else if (itemLink.isBlank()) {
                                        itemLink = parser.nextTextSafe().trim()
                                    }
                                }
                                // RSS <pubDate> y Atom <published> mandan; <updated> solo si no hay otra fecha,
                                // para que editar un episodio antiguo no lo haga pasar por nuevo
                                "pubdate", "published" -> {
                                    val parsed = FeedDateParser.parseToMillis(parser.nextTextSafe())
                                    if (parsed > 0) itemPubDate = parsed
                                }
                                "updated" -> {
                                    val parsed = FeedDateParser.parseToMillis(parser.nextTextSafe())
                                    if (parsed > 0 && itemPubDate <= 0) itemPubDate = parsed
                                }
                                "itunes:duration" -> {
                                    itemDurationMs = parseDurationToMillis(parser.nextTextSafe())
                                }
                                "itunes:image" -> {
                                    val href = parser.getAttributeValueSafe("href")
                                    if (href.isNotBlank()) itemImageUrl = href
                                }

                                // ==========================================
                                // ETIQUETAS PODCASTING 2.0 (EPISODIO)
                                // ==========================================
                                "podcast:chapters" -> {
                                    val url = parser.getAttributeValueSafe("url")
                                    if (url.isNotBlank()) itemChaptersUrl = url
                                }
                                "podcast:transcript" -> {
                                    val url = parser.getAttributeValueSafe("url")
                                    if (url.isNotBlank() && itemTranscriptUrl == null) {
                                        itemTranscriptUrl = url
                                    }
                                }
                                "podcast:funding" -> {
                                    itemFundingUrl = parser.getAttributeValueSafe("url")
                                }
                                "podcast:value" -> {
                                    val type = parser.getAttributeValueSafe("type").ifBlank { "lightning" }
                                    val method = parser.getAttributeValueSafe("method").ifBlank { "keysend" }
                                    val suggested = parser.getAttributeValueSafe("suggested").ifBlank { null }
                                    val recipients = parseValueRecipients(parser)
                                    val v4v = ValueForValueConfig(
                                        type = type,
                                        method = method,
                                        suggested = suggested,
                                        recipients = recipients
                                    )
                                    itemValueForValue = if (recipients.isEmpty() && suggested == null) {
                                        "type=$type;method=$method"
                                    } else {
                                        try {
                                            Json.encodeToString(v4v)
                                        } catch (_: Exception) {
                                            "type=$type;method=$method"
                                        }
                                    }
                                }
                                "podcast:soundbite" -> {
                                    val start = parser.getAttributeValueSafe("starttime").ifBlank { parser.getAttributeValueSafe("startTime") }
                                    val dur = parser.getAttributeValueSafe("duration")
                                    val title = parser.nextTextSafe().trim()
                                    val startMs = start.toDoubleOrNull()?.let { (it * 1000).toLong() }
                                    val durMs = dur.toDoubleOrNull()?.let { (it * 1000).toLong() }
                                    if (startMs != null && startMs >= 0L && durMs != null && durMs > 0L) {
                                        itemSoundbites.add(PodcastSoundbite(startMs, durMs, title.ifBlank { null }))
                                    }
                                }
                                // Alternativa cuando el episodio no trae <enclosure> clásico
                                "podcast:alternateenclosure" -> {
                                    inAlternateEnclosure = true
                                    alternateEnclosureType = parser.getAttributeValueSafe("type")
                                }
                                "podcast:source" -> {
                                    val uri = parser.getAttributeValueSafe("uri")
                                    val isAudio = alternateEnclosureType.isBlank() || alternateEnclosureType.startsWith("audio", ignoreCase = true)
                                    if (inAlternateEnclosure && isAudio && itemAltAudioUrl.isBlank() &&
                                        (uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true))
                                    ) {
                                        itemAltAudioUrl = uri
                                        itemAltAudioType = alternateEnclosureType
                                    }
                                }
                                "podcast:person" -> {
                                    val role = parser.getAttributeValueSafe("role")
                                    val group = parser.getAttributeValueSafe("group")
                                    val img = parser.getAttributeValueSafe("img")
                                    val href = parser.getAttributeValueSafe("href")
                                    val name = parser.nextTextSafe()
                                    if (name.isNotBlank()) {
                                        itemPersons.add(
                                            PodcastPerson(
                                                name = name.trim(),
                                                role = role.ifBlank { null },
                                                group = group.ifBlank { null },
                                                img = img.ifBlank { null },
                                                href = href.ifBlank { null }
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        inChannel -> {
                            // Parseo de elementos del canal (Podcast)
                            when (tagName) {
                                "title" -> if (channelTitle.isBlank()) channelTitle = parser.nextTextSafe()
                                "itunes:author", "author", "managingeditor" -> {
                                    if (channelAuthor.isBlank()) channelAuthor = parser.nextTextSafe()
                                }
                                "description", "itunes:summary" -> {
                                    if (channelDescription.isBlank()) channelDescription = parser.nextTextSafe()
                                }
                                "link" -> if (channelLink.isBlank()) channelLink = parser.nextTextSafe()
                                "itunes:image" -> {
                                    val href = parser.getAttributeValueSafe("href")
                                    if (href.isNotBlank() && channelArtworkUrl.isBlank()) {
                                        channelArtworkUrl = href
                                    }
                                }
                                "itunes:category" -> {
                                    val catText = parser.getAttributeValueSafe("text")
                                    if (catText.isNotBlank()) channelCategory = catText
                                }

                                // ==========================================
                                // ETIQUETAS PODCASTING 2.0 (CANAL)
                                // ==========================================
                                "podcast:funding" -> {
                                    channelFundingUrl = parser.getAttributeValueSafe("url")
                                    channelFundingText = parser.nextTextSafe()
                                }
                                "podcast:value" -> {
                                    val type = parser.getAttributeValueSafe("type").ifBlank { "lightning" }
                                    val method = parser.getAttributeValueSafe("method").ifBlank { "keysend" }
                                    val suggested = parser.getAttributeValueSafe("suggested").ifBlank { null }
                                    val recipients = parseValueRecipients(parser)
                                    val v4v = ValueForValueConfig(
                                        type = type,
                                        method = method,
                                        suggested = suggested,
                                        recipients = recipients
                                    )
                                    channelValueForValue = if (recipients.isEmpty() && suggested == null) {
                                        "type=$type;method=$method"
                                    } else {
                                        try {
                                            Json.encodeToString(v4v)
                                        } catch (_: Exception) {
                                            "type=$type;method=$method"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when {
                        tagName == "podcast:alternateenclosure" -> inAlternateEnclosure = false
                        tagName == "item" || tagName == "entry" -> {
                            inItem = false
                            if (itemAudioUrl.isBlank() && itemAltAudioUrl.isNotBlank()) {
                                itemAudioUrl = itemAltAudioUrl
                                if (itemAltAudioType.isNotBlank()) itemAudioType = itemAltAudioType
                            }
                            if (itemAudioUrl.isNotBlank()) {
                                // ID estable sobre la URL limpia (compatible con los ya guardados); la URL
                                // original se conserva y se limpia al reproducir/descargar si el ajuste lo pide
                                val finalId = itemGuid.ifBlank {
                                    "${feedUrl}_${com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(itemAudioUrl).hashCode()}"
                                }
                                val finalImage = itemImageUrl.ifBlank { channelArtworkUrl }

                                episodes.add(
                                    ParsedEpisode(
                                        id = finalId,
                                        title = itemTitle.ifBlank { "Episodio sin título" },
                                        description = itemDescription,
                                        audioUrl = itemAudioUrl,
                                        audioType = itemAudioType,
                                        audioLength = itemAudioLength,
                                        imageUrl = finalImage,
                                        pubDate = if (itemPubDate > 0) itemPubDate else System.currentTimeMillis(),
                                        durationMs = itemDurationMs,
                                        chaptersUrl = itemChaptersUrl,
                                        transcriptUrl = itemTranscriptUrl,
                                        fundingUrl = itemFundingUrl,
                                        valueForValueInfo = itemValueForValue,
                                        persons = itemPersons.toList(),
                                        soundbites = itemSoundbites.toList(),
                                        link = itemLink.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                                    )
                                )
                            }
                        }
                        tagName == "channel" -> inChannel = false
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedPodcastFeed(
            title = channelTitle.ifBlank { "Podcast" },
            author = channelAuthor.ifBlank { "Desconocido" },
            description = channelDescription,
            feedUrl = feedUrl,
            artworkUrl = channelArtworkUrl,
            link = channelLink,
            category = channelCategory,
            fundingUrl = channelFundingUrl,
            fundingText = channelFundingText,
            valueForValueInfo = channelValueForValue,
            episodes = episodes
        )
    }

    private fun XmlPullParser.nextTextSafe(): String {
        return try {
            nextText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun XmlPullParser.getAttributeValueSafe(name: String): String {
        return (getAttributeValue(null, name) ?: getAttributeValue("", name) ?: "").trim()
    }

    /**
     * Parsea las etiquetas hijas <podcast:valueRecipient> dentro de un bloque <podcast:value>.
     */
    private fun parseValueRecipients(parser: XmlPullParser): List<ValueRecipient> {
        val recipients = mutableListOf<ValueRecipient>()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val subTag = parser.name?.lowercase(Locale.ROOT) ?: ""
                    if (subTag == "podcast:valuerecipient" || subTag == "valuerecipient") {
                        val name = parser.getAttributeValueSafe("name").ifBlank { null }
                        val type = parser.getAttributeValueSafe("type").ifBlank { "node" }
                        val address = parser.getAttributeValueSafe("address")
                        val split = parser.getAttributeValueSafe("split").toIntOrNull() ?: 100
                        val fee = parser.getAttributeValueSafe("fee").equals("true", ignoreCase = true)
                        val customKey = parser.getAttributeValueSafe("customKey").ifBlank { null }
                        val customValue = parser.getAttributeValueSafe("customValue").ifBlank { null }
                        if (address.isNotBlank()) {
                            recipients.add(
                                ValueRecipient(
                                    name = name,
                                    type = type,
                                    address = address,
                                    split = split,
                                    fee = fee,
                                    customKey = customKey,
                                    customValue = customValue
                                )
                            )
                        }
                    }
                    depth++
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return recipients
    }

    /**
     * Parsea formatos de duración como "HH:MM:SS", "MM:SS" o número entero de segundos a milisegundos.
     */
    private fun parseDurationToMillis(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L

        val parts = durationStr.trim().split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLongOrNull() ?: 0L
                    val minutes = parts[1].toLongOrNull() ?: 0L
                    val seconds = parts[2].toDoubleOrNull()?.toLong() ?: 0L
                    ((hours * 3600) + (minutes * 60) + seconds) * 1000L
                }
                2 -> {
                    val minutes = parts[0].toLongOrNull() ?: 0L
                    val seconds = parts[1].toDoubleOrNull()?.toLong() ?: 0L
                    ((minutes * 60) + seconds) * 1000L
                }
                1 -> {
                    val seconds = durationStr.toDoubleOrNull()?.toLong() ?: 0L
                    seconds * 1000L
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
