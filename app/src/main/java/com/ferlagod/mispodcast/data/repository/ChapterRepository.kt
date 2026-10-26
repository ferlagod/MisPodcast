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

package com.ferlagod.mispodcast.data.repository

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.UrlLinkFrame
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio unificado para la obtención, parseo y sincronización de capítulos de podcasts.
 *
 * Soporta dos fuentes de datos complementarias:
 * 1. **Podcasting 2.0 Chapters (JSON)**: Especificación abierta que define marcas temporales,
 *    imágenes por capítulo y enlaces web complementarios desde la URL del tag `<podcast:chapters>`.
 * 2. **Metadatos ID3 CHAP (MP3)**: Capítulos embebidos directamente en el stream de audio o archivo local,
 *    extraídos a través de [ChapterFrame] de AndroidX Media3 o leídos directamente del encabezado ID3.
 */
class ChapterRepository(
    private val okHttpClient: OkHttpClient
) {

    // Caché en memoria hilo-segura por ID de episodio
    private val chaptersCache = ConcurrentHashMap<String, List<Chapter>>()

    // Flujo del episodio activo actualmente en reproducción
    private val _activeChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val activeChapters: StateFlow<List<Chapter>> = _activeChapters.asStateFlow()

    private var activeEpisodeId: String? = null

    /**
     * Establece el episodio activo y actualiza [activeChapters] con los datos en caché si existen.
     */
    fun setActiveEpisodeId(episodeId: String?) {
        activeEpisodeId = episodeId
        _activeChapters.value = if (episodeId != null) chaptersCache[episodeId].orEmpty() else emptyList()
    }

    /**
     * Devuelve los capítulos en memoria asociados a un episodio.
     */
    fun getCachedChapters(episodeId: String): List<Chapter> {
        return chaptersCache[episodeId].orEmpty()
    }

    /**
     * Asocia manualmente o actualiza los capítulos de un episodio en la memoria caché.
     */
    fun setChapters(episodeId: String, chapters: List<Chapter>) {
        val sorted = sortAndFillEndTimes(chapters)
        chaptersCache[episodeId] = sorted
        if (activeEpisodeId == episodeId) {
            _activeChapters.value = sorted
        }
    }

    /**
     * Obtiene los capítulos de un episodio, descargando el JSON de Podcasting 2.0 si está disponible
     * o recurriendo a los metadatos ID3 del archivo de audio descargado.
     */
    suspend fun getChapters(episode: EpisodeEntity): List<Chapter> = withContext(Dispatchers.IO) {
        // 1. Verificar si ya están en memoria
        val cached = chaptersCache[episode.id]
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }

        // 2. Intentar descargar Podcasting 2.0 JSON si chaptersUrl está presente
        val chaptersUrl = episode.chaptersUrl
        if (!chaptersUrl.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url(chaptersUrl)
                    .header("Accept", "application/json+chapters, application/json;q=0.9, */*;q=0.8")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parsePodcasting20Json(body)
                            if (parsed.isNotEmpty()) {
                                setChapters(episode.id, parsed)
                                return@withContext parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback silencioso a ID3 local
            }
        }

        // 3. Si hay archivo local MP3 descargado, intentar leer los capítulos ID3 (CHAP)
        val localPath = episode.localFilePath
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists() && file.canRead()) {
                val id3Chapters = extractId3ChaptersFromFile(file)
                if (id3Chapters.isNotEmpty()) {
                    setChapters(episode.id, id3Chapters)
                    return@withContext id3Chapters
                }
            }
        }

        emptyList()
    }

    companion object {
        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * Parsea un documento JSON conforme a la especificación Podcasting 2.0 Chapters.
     */
    fun parsePodcasting20Json(jsonStr: String): List<Chapter> {
        val list = mutableListOf<Chapter>()
        try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val chaptersArray = root["chapters"]?.jsonArray ?: return emptyList()

            for (item in chaptersArray) {
                val obj = item.jsonObject
                val startElement = obj["startTime"] ?: continue
                val rawStart = startElement.jsonPrimitive.content
                val startTimeMs = parseTimeToMs(rawStart) ?: continue

                val endElement = obj["endTime"]
                val rawEnd = endElement?.jsonPrimitive?.contentOrNull
                val endTimeMs = if (rawEnd != null) parseTimeToMs(rawEnd) else null

                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                    ?: "Capítulo ${list.size + 1}"
                val img = obj["img"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }

                list.add(
                    Chapter(
                        title = title,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        imageUrl = img,
                        linkUrl = url
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return sortAndFillEndTimes(list)
    }

    /**
     * Extrae capítulos desde metadatos [Metadata] de AndroidX Media3 (frames [ChapterFrame]).
     */
    @OptIn(UnstableApi::class)
    fun parseId3Metadata(metadata: Metadata): List<Chapter> {
        val list = mutableListOf<Chapter>()
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is ChapterFrame) {
                var title: String? = null
                var link: String? = null

                for (j in 0 until entry.subFrameCount) {
                    when (val sub = entry.getSubFrame(j)) {
                        is TextInformationFrame -> {
                            @Suppress("DEPRECATION")
                            val text = sub.values.firstOrNull() ?: sub.value
                            if (!text.isNullOrBlank()) {
                                title = text.trim()
                            }
                        }
                        is UrlLinkFrame -> {
                            if (!sub.url.isNullOrBlank()) {
                                link = sub.url.trim()
                            }
                        }
                    }
                }

                val startMs = entry.startTimeMs.toLong().coerceAtLeast(0L)
                val endMs = if (entry.endTimeMs > 0 && entry.endTimeMs > entry.startTimeMs) {
                    entry.endTimeMs.toLong()
                } else null

                list.add(
                    Chapter(
                        title = title ?: entry.chapterId.ifBlank { "Capítulo ${list.size + 1}" },
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        imageUrl = null,
                        linkUrl = link
                    )
                )
            }
        }
        return sortAndFillEndTimes(list)
    }

    /**
     * Lee directamente el encabezado ID3v2 de un archivo de audio MP3 en disco
     * y decodifica los frames [ChapterFrame] sin necesidad de reproducir el archivo.
     */
    @OptIn(UnstableApi::class)
    fun extractId3ChaptersFromFile(file: File): List<Chapter> {
        if (!file.exists() || !file.canRead() || file.length() < 10) return emptyList()
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(10)
                if (fis.read(header) != 10) return emptyList()
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return emptyList()
                }

                // Tamaño en ID3v2 codificado en formato syncsafe (7 bits por byte en bytes 6..9)
                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)

                val totalSize = tagSize + 10
                if (totalSize <= 10 || totalSize > 12 * 1024 * 1024) return emptyList() // Límite de 12 MB

                val tagData = ByteArray(totalSize)
                System.arraycopy(header, 0, tagData, 0, 10)
                var bytesRead = 10
                while (bytesRead < totalSize) {
                    val read = fis.read(tagData, bytesRead, totalSize - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                val decoder = Id3Decoder()
                val metadata = decoder.decode(tagData, bytesRead)
                if (metadata != null) parseId3Metadata(metadata) else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Ordena los capítulos por tiempo de inicio y completa los tiempos de finalización ausentes
     * usando el inicio del capítulo consecutivo.
     */
    fun sortAndFillEndTimes(chapters: List<Chapter>): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val sorted = chapters.sortedBy { it.startTimeMs }
        return sorted.mapIndexed { index, chapter ->
            if (chapter.endTimeMs == null && index + 1 < sorted.size) {
                chapter.copy(endTimeMs = sorted[index + 1].startTimeMs)
            } else {
                chapter
            }
        }
    }

    /**
     * Convierte valores numéricos o cadenas de texto ("12.5" o "01:23:45") a milisegundos.
     */
    private fun parseTimeToMs(value: Any?): Long? {
        return when (value) {
            is Number -> (value.toDouble() * 1000.0).toLong()
            is String -> {
                val trimmed = value.trim()
                val asDouble = trimmed.toDoubleOrNull()
                if (asDouble != null) {
                    (asDouble * 1000.0).toLong()
                } else {
                    parseClockToMs(trimmed)
                }
            }
            else -> null
        }
    }

    /**
     * Parsea cadenas de tiempo con formato HH:MM:SS o MM:SS a milisegundos.
     */
    private fun parseClockToMs(clock: String): Long? {
        val parts = clock.split(":").map { it.trim() }
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toDouble()
                    (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1000.0).toLong()
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toDouble()
                    (minutes * 60_000L) + (seconds * 1000.0).toLong()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
