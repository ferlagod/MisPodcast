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

package com.ferlagod.mispodcast.util

import android.content.Context
import android.content.Intent
import com.ferlagod.mispodcast.R
import java.util.Locale

/**
 * Construye y lanza los textos que se comparten desde la app (episodios, momentos y podcasts).
 *
 * Criterios:
 * - El enlace debe servirle a quien lo recibe: se prefiere la página web del episodio y, si no hay,
 *   el audio **sin prefijos de medición** (Podtrac, Chartable…), aunque el ajuste antirrastreo esté desactivado.
 * - Al compartir un momento concreto, el audio lleva `#t=segundos` (Media Fragments, lo entienden
 *   los navegadores) y el texto indica el minuto.
 * - Las rutas locales (`content://`, `file://`) nunca se comparten: no le sirven a nadie.
 * - La firma con el enlace a la app es una sola línea al final y se puede desactivar en Ajustes.
 */
object ShareHelper {

    /** Página pública del proyecto que se enlaza en la firma. */
    const val APP_PUBLIC_URL = "https://github.com/ferlagod/MisPodcast"

    /** Refleja el ajuste "Firma al compartir"; lo actualiza [com.ferlagod.mispodcast.MisPodcastApplication]. */
    @Volatile
    var includeSignature: Boolean = true

    /** Enlace elegido para compartir un episodio. */
    data class EpisodeLink(val url: String, val isWebPage: Boolean)

    internal fun isWebUrl(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        return u.startsWith("https://", ignoreCase = true) || u.startsWith("http://", ignoreCase = true)
    }

    private fun normalize(url: String): String =
        url.trim().substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")

    /**
     * Elige el mejor enlace para un episodio.
     *
     * Muchos feeds ponen en cada episodio el mismo `<link>` que el del podcast; en ese caso no lleva
     * al episodio y se prefiere el audio, que sí se puede escuchar directamente.
     */
    fun resolveEpisodeLink(
        episodeLink: String?,
        audioUrl: String?,
        podcastLink: String? = null,
        positionMs: Long? = null
    ): EpisodeLink? {
        val page = episodeLink?.trim()
        val pageIsSpecific = isWebUrl(page) &&
            (!isWebUrl(podcastLink) || normalize(page!!) != normalize(podcastLink!!))
        if (pageIsSpecific) return EpisodeLink(page!!, isWebPage = true)

        if (isWebUrl(audioUrl)) {
            val clean = AudioTrackingSanitizer.cleanAudioUrl(audioUrl!!.trim()).substringBefore('#')
            val seconds = (positionMs ?: 0L) / 1000L
            val url = if (seconds > 0) "$clean#t=$seconds" else clean
            return EpisodeLink(url, isWebPage = false)
        }

        // Sin audio público (p. ej. carpeta local): al menos la página, aunque sea la genérica del podcast
        return if (isWebUrl(page)) EpisodeLink(page!!, isWebPage = true) else null
    }

    fun formatTimestamp(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Compone el texto final. Función pura (sin [Context]) para poder probarla.
     *
     * ```
     * Título del episodio — Podcast
     * Escúchalo desde el 12:34      (opcional)
     * "Nota del marcador"           (opcional)
     * https://enlace
     *
     * Firma                          (opcional)
     * ```
     */
    fun composeText(
        headline: String,
        fromTimeLine: String? = null,
        note: String? = null,
        link: String? = null,
        extraLines: List<String> = emptyList(),
        signature: String? = null
    ): String = buildString {
        append(headline.trim())
        fromTimeLine?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        note?.takeIf { it.isNotBlank() }?.let { append("\n\"").append(it.trim()).append('"') }
        link?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        extraLines.filter { it.isNotBlank() }.forEach { append('\n').append(it) }
        signature?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
    }

    private fun signature(context: Context): String? =
        if (includeSignature) context.getString(R.string.share_signature, APP_PUBLIC_URL) else null

    private fun headline(episodeTitle: String, podcastTitle: String?): String =
        if (podcastTitle.isNullOrBlank()) episodeTitle else "$episodeTitle — $podcastTitle"

    /** Texto para compartir un episodio, opcionalmente desde [positionMs] y con una [note]. */
    fun buildEpisodeShareText(
        context: Context,
        episodeTitle: String,
        podcastTitle: String?,
        episodeLink: String?,
        audioUrl: String?,
        podcastLink: String? = null,
        positionMs: Long? = null,
        note: String? = null
    ): String {
        val hasTime = positionMs != null && positionMs >= 1000L
        val link = resolveEpisodeLink(episodeLink, audioUrl, podcastLink, if (hasTime) positionMs else null)
        return composeText(
            headline = headline(episodeTitle, podcastTitle),
            fromTimeLine = if (hasTime) context.getString(R.string.share_from_time, formatTimestamp(positionMs!!)) else null,
            note = note,
            link = link?.url,
            signature = signature(context)
        )
    }

    /** Texto para compartir un podcast: su web y su feed RSS (para añadirlo en cualquier app). */
    fun buildPodcastShareText(
        context: Context,
        podcastTitle: String,
        author: String?,
        websiteUrl: String?,
        feedUrl: String?
    ): String {
        val site = websiteUrl?.trim()?.takeIf { isWebUrl(it) }
        val feed = feedUrl?.trim()?.takeIf { isWebUrl(it) }
        val head = if (author.isNullOrBlank()) podcastTitle else "$podcastTitle — $author"
        return composeText(
            headline = head,
            link = site ?: feed,
            extraLines = if (site != null && feed != null) listOf(context.getString(R.string.share_podcast_rss, feed)) else emptyList(),
            signature = signature(context)
        )
    }

    /** Añade la firma (si está activada) a un texto ya compuesto, p. ej. el resumen Wrapped. */
    fun withSignature(context: Context, text: String): String =
        signature(context)?.let { "${text.trimEnd()}\n\n$it" } ?: text

    /** Abre el selector de Android para enviar [text]. */
    fun launchShare(context: Context, text: String, subject: String?, chooserTitle: String = context.getString(R.string.share_episode)) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareEpisode(
        context: Context,
        episodeTitle: String,
        podcastTitle: String?,
        episodeLink: String?,
        audioUrl: String?,
        podcastLink: String? = null,
        positionMs: Long? = null
    ) {
        val text = buildEpisodeShareText(context, episodeTitle, podcastTitle, episodeLink, audioUrl, podcastLink, positionMs)
        launchShare(context, text, subject = episodeTitle)
    }
}
