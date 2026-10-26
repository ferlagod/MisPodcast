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

import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.model.TranscriptSegment
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio centralizado para la descarga, parseo y sincronización de transcripciones
 * de audio (Podcasting 2.0 WebVTT, SRT y JSON).
 */
class TranscriptRepository(
    private val okHttpClient: OkHttpClient
) {

    private val transcriptCache = ConcurrentHashMap<String, List<TranscriptSegment>>()

    private val _activeTranscript = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val activeTranscript: StateFlow<List<TranscriptSegment>> = _activeTranscript.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var activeEpisodeId: String? = null

    companion object {
        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * Establece el episodio activo y recupera la transcripción en memoria si ya ha sido descargada.
     */
    fun setActiveEpisodeId(episodeId: String?) {
        activeEpisodeId = episodeId
        _activeTranscript.value = if (episodeId != null) transcriptCache[episodeId].orEmpty() else emptyList()
    }

    /**
     * Obtiene los segmentos de la transcripción para un episodio, descargando el recurso desde
     * [EpisodeEntity.transcriptUrl] y almacenándolo en caché.
     */
    suspend fun getTranscript(episode: EpisodeEntity): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val cached = transcriptCache[episode.id]
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }

        val url = episode.transcriptUrl
        if (url.isNullOrBlank()) {
            return@withContext emptyList()
        }

        _isLoading.value = true
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/vtt, application/x-subrip, application/json, text/plain;q=0.9, */*;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseTranscript(body)
                        if (parsed.isNotEmpty()) {
                            transcriptCache[episode.id] = parsed
                            if (activeEpisodeId == episode.id) {
                                _activeTranscript.value = parsed
                            }
                            return@withContext parsed
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Manejo silencioso ante errores de red o formato
        } finally {
            _isLoading.value = false
        }

        emptyList()
    }

    /**
     * Parsea el contenido de texto detectando automáticamente el formato (JSON, WebVTT o SRT).
     */
    fun parseTranscript(rawContent: String): List<TranscriptSegment> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Detección de formato JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val parsedJson = parseJsonTranscript(trimmed)
            if (parsedJson.isNotEmpty()) return parsedJson
        }

        // 2. Detección y parseo de WebVTT y SRT
        return parseVttOrSrt(trimmed)
    }

    /**
     * Parsea transcripciones estructuradas en WebVTT o SubRip (SRT).
     */
    private fun parseVttOrSrt(content: String): List<TranscriptSegment> {
        val lines = content.lines()
        val segments = mutableListOf<TranscriptSegment>()

        var currentStartMs: Long? = null
        var currentEndMs: Long? = null
        val currentTextLines = mutableListOf<String>()

        fun flushCurrent() {
            val start = currentStartMs
            val end = currentEndMs
            if (start != null && end != null && currentTextLines.isNotEmpty()) {
                val rawText = currentTextLines.joinToString(" ").trim()
                if (rawText.isNotEmpty()) {
                    val (speaker, cleanText) = extractSpeakerAndCleanText(rawText)
                    if (cleanText.isNotEmpty()) {
                        segments.add(
                            TranscriptSegment(
                                startTimeMs = start,
                                endTimeMs = end,
                                text = cleanText,
                                speaker = speaker
                            )
                        )
                    }
                }
            }
            currentStartMs = null
            currentEndMs = null
            currentTextLines.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.contains("-->")) {
                flushCurrent()
                val parts = line.split("-->")
                if (parts.size >= 2) {
                    val startPart = parts[0].trim()
                    val endPart = parts[1].trim().split(Regex("\\s+"))[0]
                    val sMs = parseTimestampToMs(startPart)
                    val eMs = parseTimestampToMs(endPart)
                    if (sMs != null && eMs != null) {
                        currentStartMs = sMs
                        currentEndMs = eMs
                    }
                }
            } else if (currentStartMs != null) {
                if (line.isEmpty()) {
                    flushCurrent()
                } else if (!line.matches(Regex("^\\d+$"))) {
                    // Evitar números de índice SRT que preceden inmediatamente al siguiente subtítulo
                    currentTextLines.add(line)
                }
            }
        }
        flushCurrent()

        return segments.sortedBy { it.startTimeMs }
    }

    /**
     * Parsea transcripciones en formato JSON Podcasting 2.0.
     */
    private fun parseJsonTranscript(jsonStr: String): List<TranscriptSegment> {
        try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val array = root["segments"]?.jsonArray
                ?: root["results"]?.jsonArray
                ?: root["transcript"]?.jsonArray
                ?: return emptyList()

            val list = mutableListOf<TranscriptSegment>()
            for (item in array) {
                val obj = item.jsonObject
                val startSec = obj["start"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: obj["start_time"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: continue
                val endSec = obj["end"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: obj["end_time"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: (startSec + 3.0)
                val text = obj["text"]?.jsonPrimitive?.contentOrNull
                    ?: obj["transcript"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                val speaker = obj["speaker"]?.jsonPrimitive?.contentOrNull

                list.add(
                    TranscriptSegment(
                        startTimeMs = (startSec * 1000.0).toLong(),
                        endTimeMs = (endSec * 1000.0).toLong(),
                        text = text.trim(),
                        speaker = speaker?.trim()?.ifBlank { null }
                    )
                )
            }
            return list.sortedBy { it.startTimeMs }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * Extrae el interlocutor si está anotado con etiquetas WebVTT (<v Nombre>) o prefijo "Nombre: "
     * y elimina cualquier etiqueta HTML remanente.
     */
    private fun extractSpeakerAndCleanText(raw: String): Pair<String?, String> {
        var speaker: String? = null
        var workingText = raw

        // 1. Etiqueta WebVTT <v Interlocutor>
        val vttVoiceMatch = Regex("<v(?:\\.[^>]+)?\\s+([^>]+)>(.*)", RegexOption.DOT_MATCHES_ALL).find(workingText)
        if (vttVoiceMatch != null) {
            speaker = vttVoiceMatch.groupValues[1].trim()
            workingText = vttVoiceMatch.groupValues[2].trim()
        } else {
            // 2. Prefijo clásico "Interlocutor: texto"
            val colonMatch = Regex("^([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]{2,25}):\\s+(.*)", RegexOption.DOT_MATCHES_ALL).find(workingText)
            if (colonMatch != null) {
                speaker = colonMatch.groupValues[1].trim()
                workingText = colonMatch.groupValues[2].trim()
            }
        }

        // Limpiar cualquier etiqueta HTML residual (<b>, <i>, <c>, etc.)
        val cleanText = workingText.replace(Regex("<[^>]+>"), "").trim()
        return Pair(speaker, cleanText)
    }

    /**
     * Convierte timestamps con coma o punto decimal ("01:23:45,678", "01:23.456", "12.5") a milisegundos.
     */
    private fun parseTimestampToMs(timestampStr: String): Long? {
        val normalized = timestampStr.replace(',', '.').trim()
        val parts = normalized.split(':')
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
                1 -> {
                    val seconds = parts[0].toDouble()
                    (seconds * 1000.0).toLong()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
