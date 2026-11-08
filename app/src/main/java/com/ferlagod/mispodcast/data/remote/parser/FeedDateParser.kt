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

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/**
 * Lectura tolerante de las fechas de los feeds (RFC 822/2822 y variantes de ISO 8601).
 *
 * Los feeds reales usan formatos muy dispares: días de la semana abreviados de forma no estándar
 * («Tues», «Thurs») o incorrectos, zonas horarias por nombre («EST», «CEST»), milisegundos,
 * fechas sin segundos o solo la fecha. Devuelve 0 si no se reconoce la fecha.
 */
object FeedDateParser {

    // Día de la semana inicial («Mon, », «Thurs, »): se descarta, solo aporta errores
    private val leadingWeekday = Regex("^[A-Za-z]{2,9}\\.?,?\\s+")
    private val whitespace = Regex("\\s+")

    // Formatos RFC 822 sin día de la semana, del más al menos completo
    private val rfcPatterns = listOf(
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss z",
        "d MMM yyyy HH:mm Z",
        "d MMM yyyy HH:mm z",
        "d MMM yyyy HH:mm:ss",
        "d MMM yyyy HH:mm",
        "d MMM yy HH:mm:ss Z",
        "d MMM yy HH:mm:ss z",
        "d MMM yyyy"
    )

    private val isoFormatters = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,   // 2025-11-03T10:00:00.000+01:00 / ...Z
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX")
    )

    private val isoLocalFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,    // sin zona: se asume UTC
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    )

    fun parseToMillis(raw: String?): Long {
        val text = raw?.trim()?.replace(whitespace, " ").orEmpty()
        if (text.isEmpty()) return 0L

        if (text.firstOrNull()?.isDigit() == true && text.length >= 10 && text[4] == '-') {
            parseIso(text)?.let { return it }
        }
        return parseRfc(text.replaceFirst(leadingWeekday, "")) ?: parseIso(text) ?: 0L
    }

    private fun parseIso(text: String): Long? {
        for (formatter in isoFormatters) {
            runCatching { return OffsetDateTime.parse(text, formatter).toInstant().toEpochMilli() }
        }
        for (formatter in isoLocalFormatters) {
            runCatching { return LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC).toEpochMilli() }
        }
        return runCatching {
            LocalDate.parse(text.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    private fun parseRfc(text: String): Long? {
        // «UT» y «Z» son válidos en RFC 822 pero SimpleDateFormat no los reconoce como zona
        val normalized = text.replace(Regex(" (UT|Z)$"), " GMT")
        for (pattern in rfcPatterns) {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val position = ParsePosition(0)
            val date = format.parse(normalized, position)
            // Exige consumir todo el texto para no aceptar coincidencias parciales de otro formato
            if (date != null && position.index == normalized.length) return date.time
        }
        return null
    }
}
